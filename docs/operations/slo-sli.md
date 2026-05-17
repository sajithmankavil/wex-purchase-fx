# SLO / SLI Definitions

> **Status:** Phase 5 (Operational Design Session), 2026-05-17. Closes **OQ-014** (SLO target ratification) and **G-P1-2** (SLO bounded by Treasury effective uptime).
>
> SLO targets here are **production-reference**. The case-study build inherits them aspirationally but does not load-test against them (perf-test automation is out of source scope).

---

## 1. Why this document exists

The Phase-3 NFRs proposed availability and latency anchors (NFR-001..006) without measurement. The Phase-2 grill (G-P1-2) flagged that the proposed SLOs are *bounded by Treasury's effective uptime* — we cannot achieve `99.5 %` availability on FR-003 if Treasury runs at `99.0 %` and our cache-hit ratio is meaningfully below 100 %. This document does three things:

1. **Defines the SLIs** (the things we measure).
2. **Pins SLO targets** with a formula that makes the Treasury-uptime dependency explicit.
3. **Sets the error budget** that drives release velocity in [error-budget-policy.md](error-budget-policy.md).

## 2. SLI catalogue

| SLI | What it measures | Source signal | Window |
|---|---|---|---|
| **SLI-A (FR-001 availability)** | Fraction of `POST /api/v1/purchases` requests returning `2xx` over total. | `http.server.requests{uri=/api/v1/purchases, outcome=success} / total` | 30-day rolling. |
| **SLI-B (FR-002 availability)** | Fraction of `GET /api/v1/purchases/{id}` returning `200` or `404` (a 404 with a well-formed id is a valid answer). | `http.server.requests{uri=/api/v1/purchases/{id}, status=~"200\|404"} / total` | 30-day rolling. |
| **SLI-C (FR-003 availability)** | Fraction of `GET /api/v1/purchases/{id}/conversion?currency=…` returning `200` **OR** a terminal domain answer (`422 CONVERSION_RATE_NOT_AVAILABLE`, `400 INVALID_CURRENCY`, `404 PURCHASE_NOT_FOUND`) over total. **`503 UPSTREAM_UNAVAILABLE` is the failure**; the terminal answers are successes from the user's perspective because the service applied the rule correctly. | `http.server.requests{uri=…/conversion, status=~"200\|400\|404\|422"} / total` | 30-day rolling. |
| **SLI-D (FR-001 latency)** | p99 end-to-end latency for `POST /api/v1/purchases` at steady-state load. | `http.server.requests.duration{uri=/api/v1/purchases, quantile=0.99}` | 30-day rolling, weighted by request count. |
| **SLI-E (FR-002 latency)** | p99 for `GET /api/v1/purchases/{id}`. | Same shape. | 30-day. |
| **SLI-F (FR-003 latency, cache hit)** | p99 for `GET /…/conversion` when served from the local cache (no Treasury call). | Conditional histogram bucketing on `wex.cache.source=hot_cache` span attribute. | 30-day. |
| **SLI-G (FR-003 latency, cache miss)** | p99 for `GET /…/conversion` when a Treasury fetch was performed. | Conditional bucketing on `wex.cache.source=treasury`. | 30-day. |
| **SLI-H (correctness — rate orientation)** | Fraction of canary checks where `convertedAmount` matches the fixture within tolerance. | `treasury.contract.orientation_drift.count` (inverse signal). | 7-day. |
| **SLI-I (correctness — alias drift)** | Number of alias-drift events (low cardinality; budget defines acceptable). | `currency_alias.drift.detected.count` | 30-day. |
| **SLI-J (data correctness — rate sanity)** | Fraction of Treasury responses that pass `0 < exchange_rate ≤ 10^30` sanity check. | `treasury.api.failure.count{reason=rate_sanity}` (inverse). | 30-day. |
| **SLI-K (durability — restart)** | Fraction of successfully created purchases that survive an in-process restart. Asserted by `DurabilityRestartIT` continuously in staging. | Test pass rate. | per-release. |

## 3. SLO targets

### 3.1 Availability SLOs

| ID | Target | Window | Error budget | Notes |
|---|---|---|---|---|
| **SLO-A** | SLI-A ≥ **99.9 %** | 30-day | 0.1 % = 43 m 12 s/month | `POST /purchases` does not depend on Treasury → tighter target than FR-003. |
| **SLO-B** | SLI-B ≥ **99.9 %** | 30-day | 43 m 12 s | Same: read path, no upstream. |
| **SLO-C** | SLI-C ≥ **99.5 %** | 30-day | 3 h 36 m | The "Treasury-bounded" SLO. See §4. |

### 3.2 Latency SLOs

| ID | Target | Window | Notes |
|---|---|---|---|
| **SLO-D** | SLI-D (p99 POST `/purchases`) ≤ **150 ms** | 30-day, steady-state load | NFR-001 anchor, ratified. |
| **SLO-E** | SLI-E (p99 GET `/purchases/{id}`) ≤ **80 ms** | 30-day | NFR-002 anchor, ratified. |
| **SLO-F** | SLI-F (p99 GET conversion, cache hit) ≤ **300 ms** | 30-day | NFR-003 cache-hit anchor, ratified. |
| **SLO-G** | SLI-G (p99 GET conversion, cache miss) ≤ **3000 ms** | 30-day | NFR-003 cache-miss anchor, revised by Phase-6 G6-P0-3 (1500 ms → 3000 ms; the original was inconsistent with the Treasury retry budget; see grill for the math). |

### 3.3 Correctness SLOs

| ID | Target | Window |
|---|---|---|
| **SLO-H** | SLI-H ≥ **100 %** (rate orientation correct on every canary) | 7-day |
| **SLO-I** | Alias drift events ≤ **2/month** (anything higher is a Treasury catalogue churn requiring PR; investigate). | 30-day |
| **SLO-J** | SLI-J ≥ **99.99 %** (rate sanity passes; failure is a Treasury bug) | 30-day |
| **SLO-K** | SLI-K = **100 %** (durability test green per release) | per-release |

## 4. The Treasury-uptime ceiling formula (closes G-P1-2)

The Phase-2 grill flagged that SLO-C is bounded by Treasury's availability times our cache-hit ratio. The formula:

```
SLO-C ≤ p_cache_hit + (1 − p_cache_hit) × p_treasury_available
```

Where:
- `p_cache_hit` = fraction of conversion requests served from the local cache (DB or hot cache) without needing Treasury.
- `p_treasury_available` = fraction of time Treasury responds 2xx within our retry/CB budget.

### 4.1 Treasury availability — observed baseline

**Method:** passive observation. We do **not** ask Treasury for a published SLA (none exists). Instead, the synthetic Treasury upstream canary (every 1 h; see [observability.md](observability.md) §7) provides a sample point per hour.

**Working assumption:** Treasury availability ≥ **99.0 %** monthly. This is conservative for a U.S. federal-government public data API; published anecdotes suggest 99.5 %+. We anchor at 99.0 % and revise after 90 days of passive observation.

### 4.2 Cache-hit ratio — design target

Per ADR-0001 D-10 (refined by Phase-4 G4-P0-2), the hot cache + DB cache holds rates keyed by `(country_currency_desc, record_date)`. Once warm, the cache holds essentially every rate Treasury has ever published for queried currencies. **Steady-state hit ratio target: ≥ 99 %.**

### 4.3 SLO-C ceiling check

```
SLO-C upper bound = 0.99 + (1 − 0.99) × 0.99
                  = 0.99 + 0.0099
                  = 0.9999  (99.99 %)
```

`99.5 %` target is comfortably under the ceiling. **SLO-C is feasible.**

If `p_cache_hit` drops to 95 %:

```
SLO-C upper bound = 0.95 + 0.05 × 0.99 = 0.9995  (99.95 %)
```

Still feasible. The SLO is at risk only if **both** the cache hit ratio falls below ~50 % **and** Treasury is below ~99 %. That combination is monitored:

- Cache hit ratio alert: fires at < 80 % over 30 min (P3 informational; correlate with deploys).
- Treasury availability alert: tracked via the upstream canary; fires P2 if < 95 % over 6 h.

### 4.4 Conditions under which SLO-C must be revised down

| Condition | Trigger | Action |
|---|---|---|
| 90-day passive observation shows Treasury < 98 % monthly | Phase 5/6/13 measurement | Drop SLO-C to 99.0 %; document. |
| Real-world cache hit ratio < 50 % steady-state | Anomaly investigation | Diagnose (likely deploy churn or pathological request distribution); refine warm-up. |
| Multi-region requirement introduced (OQ-012) | Product decision | Re-derive ceiling per region. |

## 5. SLO ownership

Per the [service-catalog.md](service-catalog.md):

| SLO | Owner | Approver |
|---|---|---|
| SLO-A..C (availability) | SRE | Service owner |
| SLO-D..G (latency) | SRE | Service owner |
| SLO-H (orientation correctness) | Architect | SecArch |
| SLO-I (alias drift) | Architect | Service owner |
| SLO-J (rate sanity) | Architect | SRE |
| SLO-K (durability) | QA lead | Architect |

## 6. SLO reporting cadence

| Cadence | Audience | Output |
|---|---|---|
| Weekly | On-call + SRE | Burn-rate summary; alert deltas. |
| Monthly | Service owner + Product | Full SLO compliance report; error-budget remaining; release-velocity implications. |
| Quarterly | Auditor / compliance | Trend report; PCI evidence touch-point. |

## 7. Linked artefacts

- [observability.md](observability.md) — RED / USE / cardinality budget; trace + log + metric contract.
- [error-budget-policy.md](error-budget-policy.md) — what happens when burn rate breaches.
- [monitoring-alerting.md](monitoring-alerting.md) — concrete burn-rate alert wiring (multi-window multi-burn-rate per Google SRE workbook).
- [capacity-scalability-plan.md](capacity-scalability-plan.md) — load-test plan that exercises the latency SLOs.
- [failure-modes-and-resilience.md](failure-modes-and-resilience.md) — failure modes that consume the error budget.
- Phase-3 NFRs: [non-functional-requirements.md](../requirements/non-functional-requirements.md) NFR-001..007 — original anchors; SLOs here are the ratified versions.
- Phase-2 grill G-P1-2: closed here.
