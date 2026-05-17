# Monitoring and Alerting

> **Status:** Phase 5 (Operational Design Session), 2026-05-17. Closes **G4-P1-27** (`RateOrientationContractCheck` fail-closed threshold). G4-P1-17 (CB calibration) handled in [failure-modes-and-resilience.md](failure-modes-and-resilience.md) §3.
>
> Concrete alert catalogue, routing, dashboard specifications, and noise-control policy. Builds on [observability.md](observability.md) (the telemetry contract) and feeds [oncall-escalation.md](oncall-escalation.md) (severity ladder) and [runbook.md](runbook.md) (operator action).

---

## 1. Alert philosophy

Every alert obeys five rules:

1. **Actionable.** The on-call can do something concrete. No "informational page" alerts.
2. **Tied to user impact or imminent risk.** Resource gauges alert only when saturation is *predictive* of user impact.
3. **Linked to a runbook.** Each alert row in §3 includes a runbook anchor.
4. **Severity-mapped.** SEV1 pages with a 5-min response target; SEV2 pages with 30 min; SEV3 is ticket-based.
5. **Owned.** Each alert has an owner who is accountable for its precision (no perpetual flapping).

Alerts that violate any of these rules are tuned or removed at the monthly alert review (§5).

## 2. Burn-rate alerts (SLO-driven; the page-worthy core)

Multi-window multi-burn-rate per Google SRE workbook chapter 5. Each availability SLO gets a pair:

| Alert | SLI | Burn rate × window | Severity | Runbook anchor |
|---|---|---|---|---|
| **A-SLO-A-fast** | SLO-A POST availability | 14.4× over 5 min **AND** 14.4× over 1 h | SEV1 | [runbook.md §6.1](runbook.md#61-fast-burn-on-slo-a-or-b) |
| **A-SLO-A-slow** | Same | 6× over 1 h **AND** 6× over 6 h | SEV2 | Same |
| **A-SLO-B-fast** | SLO-B GET availability | 14.4× / 5 min + 1 h | SEV1 | [runbook.md §6.1](runbook.md#61-fast-burn-on-slo-a-or-b) |
| **A-SLO-B-slow** | Same | 6× / 1 h + 6 h | SEV2 | Same |
| **A-SLO-C-fast** | SLO-C conversion availability | 14.4× / 5 min + 1 h | SEV1 | [runbook.md §6.2](runbook.md#62-fast-burn-on-slo-c) |
| **A-SLO-C-slow** | Same | 6× / 1 h + 6 h | SEV2 | Same |

The two-window requirement (5 min **AND** 1 h must both burn at 14.4×) makes the alert robust to one-off spikes. Slow burns require both 1 h and 6 h to cross threshold — they capture sustained degradation without paging on transients.

## 3. Alert catalogue (everything else)

| Alert | Signal | Threshold (anchor) | Window | Severity | Owner | Runbook | User impact | Auto-remediation |
|---|---|---|---|---|---|---|---|---|
| **A-001 Service 5xx rate** | `http.server.requests{status=5xx} / total` | > 1 % | 5 min | SEV2 | On-call | [runbook §6.3](runbook.md#63-elevated-5xx-rate) | All endpoints affected | None — page |
| **A-002 p99 latency saturation — POST** | `http.server.requests.duration{uri=/api/v1/purchases, p99}` | > 2× NFR-001 (300 ms) | 10 min | SEV2 | On-call | [runbook §6.4](runbook.md#64-latency-saturation) | POST slow | None — page |
| **A-003 p99 latency saturation — GET conversion (cache hit)** | duration p99 conditional on cache-hit | > 2× SLO-F (600 ms) | 10 min | SEV2 | On-call | [runbook §6.4](runbook.md#64-latency-saturation) | Conversion slow | None |
| **A-004 p99 latency saturation — GET conversion (cache miss)** | duration p99 conditional on cache-miss | > 2× SLO-G (3000 ms) | 10 min | SEV2 | On-call | [runbook §6.4](runbook.md#64-latency-saturation) | First-fetch slow | None |
| **A-005 Treasury CB open** | `resilience4j.circuitbreaker.state{state=open}` | == 1 | sustained > 60 s | SEV2 | On-call | [runbook §6.5](runbook.md#65-treasury-cb-open) | Cache-miss path returns 503 | Wait for half-open |
| **A-006 Treasury 5xx rate** | `treasury.api.failure.count{reason=http_5xx} / treasury.client.request.count` | > 5 % | 10 min | SEV2 | On-call | [runbook §6.6](runbook.md#66-treasury-degraded) | Latency cliff for cache-miss | None |
| **A-007 Treasury timeout rate** | `treasury.api.failure.count{reason=timeout} / total` | > 10 % | 10 min | SEV2 | On-call | [runbook §6.6](runbook.md#66-treasury-degraded) | Same | None |
| **A-008 Rate-orientation drift (G4-P1-27)** | `treasury.contract.orientation_drift.count` | ≥ 3 in 24 h **OR** any single canary drift > 5 % | 24 h / per canary | SEV1 / SEV2 | Architect + on-call | [runbook §6.7](runbook.md#67-rate-orientation-drift) | Silent 1/x correctness risk for affected currency | None — page; feature-flag-disable that currency |
| **A-009 Treasury schema drift** | `treasury.contract.schema_drift.count` | > 0 | 24 h | SEV2 | Architect | [runbook §6.8](runbook.md#68-treasury-schema-drift) | Future requests rejected as `502 UPSTREAM_BAD_RESPONSE` | None |
| **A-010 Rate-sanity rejection** | `treasury.api.failure.count{reason=rate_sanity}` | > 0 | 10 min | SEV2 | On-call + Architect | [runbook §6.8](runbook.md#68-treasury-schema-drift) | Some rates rejected | Already auto-rejected |
| **A-011 Hot-cache hit rate degraded** | `exchange_rate.cache.hit / (hit + miss)` | < 80 % | 30 min | SEV3 | On-call | [runbook §6.9](runbook.md#69-cache-hit-degraded) | Latency cliff likely | None |
| **A-012 Hikari pool saturated** | `hikaricp.connections.pending` | > 0 | sustained > 60 s | SEV1 | On-call | [runbook §6.10](runbook.md#610-db-pool-saturated) | All requests blocked | None — page |
| **A-013 DB unreachable** | Readiness probe `db` indicator | DOWN | sustained > 30 s | SEV1 | On-call | [runbook §6.11](runbook.md#611-db-unreachable) | Full outage | LB drains automatically |
| **A-014 JVM heap saturation** | `jvm.memory.used / max` (heap) | > 80 % | sustained 5 min | SEV2 | On-call | [runbook §6.12](runbook.md#612-jvm-heap-saturation) | OOM risk | None |
| **A-015 GC pause spike** | `jvm.gc.pause` max | > 500 ms | 5 min | SEV3 | On-call | [runbook §6.12](runbook.md#612-jvm-heap-saturation) | Latency spikes | None |
| **A-016 Tomcat threads saturated** | `tomcat.threads.busy / config.max` | > 90 % | 5 min | SEV2 | On-call | [runbook §6.13](runbook.md#613-tomcat-threads-saturated) | Slow-loris or load spike | None |
| **A-017 Bulkhead saturated** | `resilience4j.bulkhead.available.concurrent.calls` (treasury) | == 0 | sustained 30 s | SEV2 | On-call | [runbook §6.14](runbook.md#614-bulkhead-saturated) | Conversions queue or fail | None |
| **A-018 Readiness flapping** | `readiness_state_changed` count | > 3 transitions | 5 min | SEV1 | On-call | [runbook §6.15](runbook.md#615-readiness-flapping) | Traffic instability | None — page |
| **A-019 Container restart loop** | Container restart count (platform) | > 3 | 15 min | SEV1 | On-call | [runbook §6.16](runbook.md#616-container-restart-loop) | Capacity loss | None — page |
| **A-020 Alias drift detected** | `currency_alias.drift.detected.count` | > 0 | 24 h | SEV3 | Architect | [runbook §6.17](runbook.md#617-alias-drift) | Some inputs rejected as `400 INVALID_CURRENCY` | PR-driven fix |
| **A-021 Content-guard fires above baseline** | `description.content_guard.fired.count{reason}` | > 10 in 5 min (acute probe); **> 0.1 % of legitimate POSTs over 30 d** (false-positive calibration threshold per Phase-8 G8-P1-7) | 5 min / 30 d | SEV2 / SEV3 | SecArch | [runbook §6.18](runbook.md#618-content-guard-spike) | Possible attacker probing OR false-positive accumulation | Already auto-rejected; investigate; if false-positive baseline > 0.1 % sustained, recalibrate guard regex |
| **A-022 Single-flight loser timeout** | `single_flight.loser.polled.count{outcome=timeout}` | > 5 in 5 min | 5 min | SEV3 | On-call | [runbook §6.19](runbook.md#619-single-flight-loser-timeout) | Some conversions delayed | None |
| **A-023 Warm-up failure on deploy** | Absent `warmup_completed` log event 60 s after `readiness=UP` | 60 s | per deploy | SEV3 | On-call | [runbook §6.20](runbook.md#620-warmup-failed) | New-replica cold-start cliff | Manual trigger of warm-up |
| **A-024 Treasury upstream canary RED** | Synthetic canary 200-rate over 1 h | < 95 % | 1 h | SEV2 | On-call | [runbook §6.21](runbook.md#621-treasury-canary-red) | Cache-miss path degraded | None — Treasury-side |
| **A-025 Audit-event spike** | `purchase_validation_failed{reason=pan_pattern\|luhn-encoded\|track1\|track2}` | > 100 in 1 h | 1 h | SEV2 | SecArch | [runbook §6.22](runbook.md#622-audit-event-spike) | Probing pattern detected | None |
| **A-026 Service start failure** | Multiple `service_started` events not emitted; restart loop | 3× in 15 min | 15 min | SEV1 | On-call | [runbook §6.23](runbook.md#623-service-start-failure) | Full outage | None — page |
| **A-027 Log-hash key absent (prod)** | Absent / unexpected `v0:` prefix in production digests | sustained 5 min | 5 min | SEV1 | SecArch + on-call | [runbook §6.24](runbook.md#624-log-hash-key-issue) | Compliance violation | None — page |
| **A-028 OpenAPI breaking change** | CI `oasdiff` gate failure on PR | per PR | n/a | block-build (no page) | API designer | runbook §6.25 | Would break clients | Block merge |

### 3.1 `RateOrientationContractCheck` calibration (G4-P1-27 closure)

The Phase-3 design left this WARN-only. Phase 5 ratifies the fail-closed path:

| Condition | Action |
|---|---|
| Single WARN event | No alert; log only. |
| **≥ 3 WARN events** in any 24 h window for the **same currency** | **SEV2 alert (A-008).** Architect reviews; canary check run manually. |
| **> 5 % drift** on a single weekly canary check vs fixture for any currency | **SEV1 alert (A-008).** Feature-flag-disable conversions for that currency; emit `503 UPSTREAM_UNAVAILABLE` with `details.reason=orientation_quarantine`. Patch fixture; ship hotfix; re-enable. |
| **> 0 % drift** on three consecutive canaries for any currency | **SEV1 alert.** Same as above. |

Implementation: a separate scheduled task (Phase 13) runs the canary every **24 hours** (revised by Phase-6 G6-P1-8 from weekly; cost is ~3 Treasury requests/day for 3 reference currencies — negligible; reduces the silent-bug window from 7 days to 24 h) against three reference currencies (CAD, EUR, JPY by default; configurable via `WEX_CANARY_CURRENCIES`).

## 4. Routing

| Severity | Channel | Target response |
|---|---|---|
| SEV1 | Pager (PagerDuty / equivalent) — primary on-call | 5 min ack |
| SEV2 | Pager (low-urgency) — primary on-call | 30 min ack |
| SEV3 | Ticket queue (Jira / GitHub Issues) | Next business day |
| Block-build | Pull-request status check; no page | Per PR |

Multi-alert dedupe: alerts from the same SLO within 5 min collapse to one notification. Maintenance windows suppress all alerts except SEV1.

## 5. Dashboards

Five dashboards in Grafana (definitions live in `monitoring/dashboards/*.json`, Phase 13). Each dashboard panel cites a metric from [observability.md](observability.md) §3.

### 5.1 Service health (operator default)

- **Row 1 (RED):** request rate by endpoint; error rate by endpoint; p50/p95/p99 latency by endpoint.
- **Row 2 (USE):** JVM heap; GC pause; Tomcat threads busy vs max; Hikari active vs max vs pending.
- **Row 3 (SLO):** burn rate per SLO; remaining error budget per SLO.

### 5.2 Dependency health (Treasury)

- Treasury request rate; success / 4xx / 5xx / timeout breakdown.
- CB state timeline; bulkhead concurrent calls.
- Treasury client latency histogram (p50 / p95 / p99).
- `treasury.contract.orientation_drift.count`; `treasury.contract.schema_drift.count`.
- Canary status (last 30 days).

### 5.3 Cache + rate-lookup

- Hot-cache size; hit/miss/eviction rates.
- Rate-lookup source breakdown (`hot_cache` / `db` / `treasury`).
- `single_flight.loser.polled.count` by outcome.

### 5.4 Business workflow

- Conversions per minute (overall).
- Conversions per minute per allow-listed currency (top-10).
- `CONVERSION_RATE_NOT_AVAILABLE` rate.
- Alias-drift events timeline.
- Content-guard fires by reason (PAN, track, encoded-PAN).

### 5.5 Rollout health (deploy-only view)

- Per-version request rate and error rate during the deploy window.
- Readiness transitions per replica.
- Warm-up completion timing per replica.
- `single_flight.gate.released_on_shutdown` counter delta during deploy.

## 6. Synthetic checks (cross-reference)

Defined in [observability.md](observability.md) §7. Highlights:

- **Conversion canary** every 5 min (recorded-fixture comparison; ±1 cent tolerance).
- **Liveness / readiness probes** every 30 s (platform).
- **Treasury upstream canary** every 1 h (direct hit, not via app).
- **Rate-orientation canary** weekly (CAD/EUR/JPY against fixture set; feeds A-008).
- **OpenAPI lint** per release.

## 7. Noise controls

| Mechanism | Detail |
|---|---|
| Maintenance windows | All non-SEV1 alerts suppressed. Window scheduling per platform. |
| Deduplication | Alerts from the same SLO within 5 min collapse. |
| Grouping | Alerts under one incident channel; correlation via `correlationId`. |
| Sampling for high-cardinality events | Audit-event count alerts (A-021/A-025) are rate-based, not per-event. |
| Alert ownership | Each alert in §3 has an Owner who reviews precision monthly. |
| Monthly alert review | First Monday of month: on-call + SRE + service owner walk through alerts that fired or *should have fired*. Tune or remove. |

## 8. Linked artefacts

- [observability.md](observability.md) — telemetry contract (metric names, event taxonomy).
- [slo-sli.md](slo-sli.md) — SLOs that drive the burn-rate alerts.
- [error-budget-policy.md](error-budget-policy.md) — what burn alerts mean for release velocity.
- [oncall-escalation.md](oncall-escalation.md) — severity ladder and escalation paths.
- [runbook.md](runbook.md) — every alert links to a runbook anchor.
- [failure-modes-and-resilience.md](failure-modes-and-resilience.md) — failure-mode → detection metric mapping.
