# Reliability & Scalability Grill — Phase 6

> **Status:** COMPLETED — 2026-05-17
> **Verdict:** **CONDITIONAL PASS** to Phase 7 (PCI Security Design Session). Four P0 corrections pinned this phase; twelve P1 findings deferred to Phase 13 (implementation) or carried as accepted-residuals into Phase 9 (implementation readiness).
>
> **Adversarial panel:** Principal performance engineer · SRE who has been paged for capacity issues · QA / chaos engineer · Auditor (capacity / DR perspective).
> **Inputs reviewed:**
> - [Phase 5 operational design](operational-design-session.md) and its 10 deliverables
> - [Phase 4 design grill](design-grill.md) — anchors and calibrations carried forward
> - [Phase 3 prototype](phase-3-prototype-log.md) — Treasury empirical data
> - Phase 1–5 requirements, architecture, and operational docs

**Scope.** The Phase-5 anchors and calibrations (SLOs, CB calibration, single-flight semantics, capacity numbers, warm-up parameters, alert thresholds) are *plausible*. Whether they survive contact with real load is what this grill attacks. Most issues found are correctness-of-calibration, not correctness-of-design.

---

## 0. Executive verdict

Four things are wrong enough to fix in-phase:

1. **CB calibration is functionally dead at the design's traffic level.** A count-based circuit breaker with `minimum-number-of-calls=100` never accumulates enough samples to open against single-flight-deduplicated Treasury traffic (~0.1 req/s cluster-wide). **Switch to a time-based sliding window.**
2. **Single-flight loser semantics violate AC-027d.** Losers fail-fast at 200 ms while the winner is still mid-fetch with up to 6 s of retry budget. Split outcomes happen. **Loser wait must be ≥ Treasury worst-case attempt budget, or the loser must wait on the winner's future.**
3. **The p99 cache-miss latency anchor (1500 ms) is mathematically inconsistent** with the retry budget the Treasury client is configured for. Three retries × 2 s × backoff is ~6 s worst case. **Raise the anchor or reduce the retry budget — current state is unverifiable.**
4. **DB connection pool sizing is at 80 % of theoretical capacity at the steady-state anchor.** No headroom for burst. **Increase pool size, or revise the burst anchor downward.**

The Phase-5 operational design holds at the level of approach. The numbers and one resilience-pattern detail are off. All P0s pinned in this gate.

---

## 1. P0 findings — block Phase-7 handover until pinned

### G6-P0-1 — Circuit breaker is functionally dead at design traffic

**Observation.** Phase 5 pinned (in [failure-modes-and-resilience.md §3](../operations/failure-modes-and-resilience.md#3-circuit-breaker-calibration-closes-g4-p1-17)):

```
wex.treasury.cb.minimum-number-of-calls=100
wex.treasury.cb.wait-duration-in-open-state=PT5M
```

The sliding window is **count-based** by default in Resilience4j. At the design traffic — single-flight gate deduplicates Treasury calls to ~0.1 req/s cluster-wide (one fetch per `(currency, quarter)` first-time miss; quarterly publish cadence keeps the steady rate low) — accumulating 100 calls takes **~17 minutes**. A Treasury outage of 5 minutes triggers exactly *zero* CB opens because the sample window never fills.

**Lens.** SRE, principal performance engineer.

**Risk.** The CB is a no-op. All "Treasury degraded" alerts (A-005, A-006, A-007) lean on the CB state for routing logic in the runbook (§6.5, §6.6). When Treasury is down, individual calls time out and retry; the service correctly returns `503 UPSTREAM_UNAVAILABLE` when no local rate is eligible, but **no CB opens, no bulkhead trips earlier, no half-open recovery testing** — the system has no concept of "Treasury is degraded; back off."

**Required fix (pinned in Phase 6).** Switch Resilience4j to a **time-based** sliding window:

```yaml
resilience4j.circuitbreaker.instances.treasury:
  sliding-window-type: TIME_BASED
  sliding-window-size: 300            # 300 seconds (5 min)
  minimum-number-of-calls: 5          # any 5+ samples in 5 min counts
  failure-rate-threshold: 50
  wait-duration-in-open-state: 5m
  permitted-number-of-calls-in-half-open-state: 3
  slow-call-duration-threshold: 1s
  slow-call-rate-threshold: 100       # disable slow-call CB (handled by failure-rate)
```

The 5-minute time-window captures genuine outages; the low minimum-number-of-calls (5) ensures the CB can open on a sustained problem even at very low traffic. Half-open uses 3 trial calls (down from 5) because the recovery sample is smaller too.

**Acceptance.** No new ACs; AC-T-3 already covers CB behaviour, but the WireMock fixture for "Treasury 5xx sustained" must verify the time-based CB opens within 5 min (Phase 13).

**Owner.** SRE.

**Target gate.** Pinned in this Phase-6 grill (failure-modes-and-resilience.md §3 updated in-place). Phase 13 implementation realises.

---

### G6-P0-2 — Single-flight loser semantics violate AC-027d

**Observation.** Phase 4 pinned (in [adr-0001-core-architecture.md D-9](../architecture/adr-0001-core-architecture.md#d-9--treasury-client-resilience4j-wrapped-single-flight-schema-validated)):

> Loser semantics: a request that finds the gate held does not block indefinitely on the winner's future. It waits a bounded interval (**200 ms** default), then re-checks the database for a freshly persisted eligible rate; if found, it returns success; if not, it returns the same outcome the winner would have produced.

But the winner can take much longer than 200 ms. Single Treasury attempt = up to 2 s timeout. **Three retries with exponential backoff × jitter = 100 ms + 200 ms + 400 ms backoff + 3 × 2 s timeout ≈ ~6 s worst case.** A loser at 200 ms re-checks the DB, finds nothing (winner is still mid-retry), and… what? Returns `503`. The winner then succeeds at ~5 s and returns `200`. **AC-027d says "either both succeed with the same exchangeRate or both fail with the same errorCode."** This is a split outcome.

**Lens.** Principal engineer, QA.

**Risk.** AC-027d is structurally unenforceable with a 200 ms loser wait. Concurrent first-fetch requests for the same `(currency, quarter)` will return inconsistent outcomes under any Treasury slowness or retry behaviour.

**Required fix (pinned in Phase 6).** Two-part:

1. **Loser-wait** raised to **10 s** (= Treasury worst-case retry budget `timeout × (retry_count + 1) + sum(backoff) ≈ 2 × 4 + 0.1 + 0.2 + 0.4 = 8.7 s`; round to 10 s safety margin). This makes loser wait the same upper bound as the winner's possible completion time.
2. **Loser polling pattern** corrected: poll the DB every 100 ms during the wait window; return as soon as the persisted result appears. On final timeout, look at the winner's outcome (via a thread-safe outcome ref in the gate); mirror that outcome exactly.

```java
SingleFlightGate.runOnce(key, () -> winner.fetch())
  // for losers:
  while (Duration.between(startTime, now()) < Duration.ofSeconds(10)) {
      Optional<ExchangeRate> rate = rateRepo.findEligible(currency, window);
      if (rate.isPresent()) return ConversionResult.ofRate(rate.get());
      if (winner.hasFailed()) throw winner.failureType();   // mirror exact errorCode
      Thread.sleep(Duration.ofMillis(100));
  }
  throw new UpstreamUnavailableException("loser_timeout");  // last-resort
```

The implementation tracks the winner's success or failure in an `AtomicReference<WinnerOutcome>` stored in the gate's per-key state. Losers either find the persisted row (winner succeeded), mirror the winner's exception type (winner failed), or fall through to `loser_timeout` (rare — only when winner exceeded the worst-case budget).

**Acceptance.** AC-027d wording stays — but Phase 13 must implement against the corrected gate semantics. The test must use a WireMock barrier that holds the winner for 5 s (within budget) and 8 s (past budget) and assert both losers see consistent outcomes in both cases.

**Owner.** Architect.

**Target gate.** Pinned in this Phase-6 grill (D-9 in ADR-0001 and component-design.md §3.2 updated in-place). Phase 13 implementation realises.

---

### G6-P0-3 — p99 cache-miss latency anchor is mathematically inconsistent with retry budget

**Observation.** NFR-003 / SLO-G anchor for p99 cache-miss conversion latency: **1500 ms**. The Treasury client retry budget per G6-P0-2 above is ~6 s worst case (4 attempts × 2 s timeout + backoffs). Even single-attempt success at p99 Treasury latency (~500 ms observed in prototype) plus DB round-trips and conversion arithmetic is ~600–800 ms. Add one retry on a timeout and we are past 2.5 s. **The 1500 ms anchor is only achievable if 95+ % of cache-miss requests succeed on the first Treasury attempt.**

**Lens.** Principal performance engineer.

**Risk.** Phase-13 implementer cannot meet the published SLO if Treasury has any sustained latency above ~400 ms. The SLO is unverifiable (Phase-6 load tests with WireMock will pass it; production may not).

**Required fix (pinned in Phase 6).** Choose one:

- **Option A (chosen).** **Raise the SLO-G anchor to 3000 ms (3 s).** Two Treasury attempts fit comfortably. The cache-miss path is rare (≤ 1 %); 3 s p99 cache-miss is operationally acceptable.
- Option B. Reduce retry budget — drop third retry. Risks transient Treasury jitter. Reject.
- Option C. Tighten Treasury timeout to 1 s. Risks legitimate slow responses becoming artificial timeouts. Reject.

Phase 6 selects **Option A** (anchor 3000 ms).

**Acceptance.** SLO-G ([slo-sli.md §3.2](../operations/slo-sli.md#32-latency-slos)) and NFR-003 are updated to 3 s. Load-test scenario in capacity-scalability-plan.md §5 inherits the new anchor.

**Owner.** SRE + Architect.

**Target gate.** Pinned in this Phase-6 grill (slo-sli.md and non-functional-requirements.md NFR-003 updated in-place).

---

### G6-P0-4 — DB connection pool sizing is at 80 % theoretical capacity at the steady-state anchor

**Observation.** From [capacity-scalability-plan.md](../operations/capacity-scalability-plan.md):

```
Per-replica steady-state: 100 req/s mixed
DB connection pool per replica: 10 (Hikari default)
Peak burst: 250 req/s for ≤ 60 s
```

Theoretical max throughput per replica = `pool_size / avg_request_db_time`. At avg DB time ~80 ms (the GET path p99 anchor), theoretical max = `10 / 0.08 = 125 req/s`. **Steady-state 100 req/s = 80 % pool utilisation.** No headroom. For the **250 req/s burst**, theoretical max with a 10-conn pool is **125 req/s** — the burst exceeds pool capacity by 2×. Excess work queues in Tomcat (200 threads) and waits on the pool; p99 latency degrades sharply.

**Lens.** Principal performance engineer.

**Risk.** Under burst, requests queue on the DB connection pool. p99 latency spikes, A-002/A-003 alerts fire, and the system reports as "degraded" when in fact it is correctly pool-bound. Operationally noisy. Worse: a single slow DB query holds a connection longer than 80 ms, reducing effective throughput further.

**Required fix (pinned in Phase 6).** Increase pool size to **20** per replica (default). Math: at 100 req/s with 20 conns, utilisation ~40 % (comfortable). At 250 req/s burst, theoretical max = `20 / 0.08 = 250 req/s` — exactly the burst anchor with no headroom; but burst is by definition ≤ 60 s, so 100 % utilisation for 60 s is acceptable. Phase 13 implementation: `WEX_DB_POOL_SIZE=20`.

Secondary effect: at 5 replicas, total pool to Postgres = 100 connections (= default `max_connections`). Phase 12 pre-prod readiness: either raise Postgres `max_connections` to 200, or document the replica-count ceiling.

**Acceptance.** No new ACs; this is a config knob. capacity-scalability-plan.md §1/§2 updated with the new pool size and the replica-count ceiling note.

**Owner.** SRE.

**Target gate.** Pinned in this Phase-6 grill.

---

## 2. P1 findings — must resolve or explicitly accept before Phase 9

### Capacity / performance

| ID | Title | Observation | Required action | Owner | Target |
|---|---|---|---|---|---|
| **G6-P1-1** | Cold-currency request cliff | Warm-up covers 10 of ~170 Treasury currencies. First request for a non-warmed currency hits Treasury (cold path). | Document cold-cliff residual in [runbook §6.9](../operations/runbook.md#69-cache-hit-degraded). Phase 13: consider a deferred warm-up (all-currencies background fetch over 10 min after readiness UP). v1 accepts residual. | SRE | Phase 13 or accept |
| **G6-P1-2** | Bulkhead at 50 permits is over-sized given single-flight | Single-flight reduces concurrent Treasury fetches to ≤ ~5. Bulkhead 50 wastes thread state. | Reduce to **10 permits**. | SRE | Phase 13 config |
| **G6-P1-3** | Hot cache range query not native to Caffeine | Eligible-rate lookup is `MAX(record_date) WHERE currency = X AND record_date BETWEEN A AND B`. Caffeine is a Map. | Maintain a `ConcurrentNavigableMap<LocalDate, ExchangeRate>` per-currency inside the hot cache, or use two-level Caffeine: outer keyed by currency, inner by `record_date`. Document in component-design.md §1. | Architect | Phase 13 |
| **G6-P1-4** | Graceful shutdown 30 s may be insufficient for in-flight peak drain | Tomcat 200 threads × p99 1.5–3 s = up to 600 s of accumulated work in-flight at peak. 30 s drain abandons most of it. | Raise `spring.lifecycle.timeout-per-shutdown-phase` to **60 s** for prod. Document that under peak, deploys should be canary not rolling. | SRE | Phase 5/13 |
| **G6-P1-9** | 100 req/s mixed anchor lacks POST/GET ratio | Load-test results vary by mix; the anchor is incomplete. | Specify **60 % POST, 30 % GET-conversion, 10 % GET-by-id**. | SRE | Phase 6/13 (pinned via in-place update) |
| **G6-P1-10** | Postgres connection-count ceiling at scale-out | At 5 replicas × 20-conn pools = 100 = default `max_connections`. | Pre-prod: raise to 200 or introduce PgBouncer transaction-pool mode. Document in deployment-architecture.md §4. | Platform / SRE | Phase 12 |
| **G6-P1-11** | JVM heap 1 GiB not stress-tested under burst | Burst can spike heap 200–300 MiB; GC pauses may exceed 200 ms. | Phase 6/13 load test must monitor heap. If GC p95 > 200 ms, raise heap to 1.5 GiB or tune `-XX:MaxGCPauseMillis`. | SRE | Phase 13 |
| **G6-P1-12** | 100 % trace sampling unsustainable at scale | 100 req/s × 4 spans = ~35 M spans/day per service. | Phase 13: tail-based sampling at OTel collector — keep 100 % of error + slow traces + 10 % of normal. | SRE | Phase 13 |

### Reliability / observability

| ID | Title | Observation | Required action | Owner | Target |
|---|---|---|---|---|---|
| **G6-P1-5** | Audit-event emission has no service-side rate limiter | Coordinated attack (e.g., automated PAN spray) could overwhelm audit sink. | Phase 7 documents sink-side rate-limit posture; v1 accepts the residual. If sink doesn't rate-limit, add Resilience4j RateLimiter on audit emission. | SecArch | Phase 7 |
| **G6-P1-6** | H2 single-writer bottleneck under load (case-study only) | H2 MVStore serialises writes. Case-study load (60 writes/s) is well within H2 limits (~10 k/s). | Document as case-study residual. Postgres has multi-writer concurrency. | Architect | Phase 6 (done in-place) |
| **G6-P1-7** | Multi-region DR escalation trigger too abstract | "RTO < 30 min OR read-locality" is vague. | Concrete: *"(a) business RTO < 15 min, OR (b) cross-region read latency > 200 ms avg, OR (c) compliance read-locality (EU residency)."* Update OQ-012 wording. | Product owner | Phase 9 |
| **G6-P1-8** | Rate-orientation canary cadence (7-day) too slow | Convention flip silent for up to 7 days. | Two-tier: **fixture test on every PR** (in AC-T-3) + **live canary every 24 h** for 3 reference currencies. ~3 req/day cost — negligible. | SRE | Phase 5/13 (pinned via in-place update to monitoring-alerting.md) |

---

## 3. P2 / NICE findings

| ID | Title | Recommendation |
|---|---|---|
| **G6-P2-1** | CB wait-duration 5 min vs longer outage patterns | Acceptable; intermittent half-open retries during a 30 min Treasury outage are cheap. Document. |
| **G6-P2-2** | Hot cache `maximumSize=2000` over-provisioned | Steady-state need ~1600. Headroom is fine; document. |
| **G6-P2-3** | `ON CONFLICT DO NOTHING` race on concurrent revisions | Race-free under Postgres READ_COMMITTED; document. |
| **G6-P2-4** | Tail-based sampling for traces | Covered by G6-P1-12. |
| **G6-P2-5** | Empirical Treasury revision-rate study (G4-P1-2) | Defer; versioning is defensive regardless. |
| **G6-P2-6** | Daily vs weekly rate-orientation canary | Covered by G6-P1-8. |
| **G6-P2-7** | Restart-loop alert (A-019) latency | Verify A-019 fires before SLO burn alerts on env-var miss. Phase 13. |
| **G6-P2-8** | Caffeine `expireAfterWrite` vs `expireAfterAccess` | Pick `expireAfterWrite` (TTL semantics). Phase 13. |
| **G6-P2-9** | Cold-currency cliff metric | Add `exchange_rate.first_fetch.duration_by_currency` to observability.md. |
| **G6-P2-10** | Single-flight fairness under massive thundering herd | Not formally bounded; v1 case-study traffic low. Document. |

---

## 4. Anchor verification math (informative)

### 4.1 Steady-state throughput per replica (post-G6-P0-4 pool=20)

```
Throughput = min(pool_size / avg_db_time, thread_count / avg_request_time)

60 % POST (DB write, ~120 ms) / 30 % GET-conversion (cache hit ~80 ms) / 10 % GET-by-id (~80 ms):
  avg_db_time = 0.6 × 0.12 + 0.3 × 0.08 + 0.1 × 0.08 = 0.104 s
  pool-bound max = 20 / 0.104 = 192 req/s
  thread-bound max = 200 / 0.104 = 1923 req/s
  → effective max = 192 req/s
```

**100 req/s anchor at ~52 % pool utilisation. ✅ Headroom = 2×.**

### 4.2 Burst capacity

```
Burst 250 req/s with the mix above:
  Effective max = 192 req/s pool-bound (sustained)
  Burst exceeds by 250 - 192 = 58 req/s for ≤ 60 s
  Tomcat thread queue absorbs; p99 latency degrades ~2× during burst
  → A-002 / A-003 fires (expected; documented behaviour)
  Post-burst recovery: < 30 s
```

**Burst anchor 250 req/s is *briefly exceeded* in pool terms; degrades but does not fail. Sustained > 192 req/s/replica requires scale-out.**

### 4.3 SLO-C ceiling formula re-check (post-G6-P0-3)

```
SLO-C upper bound = p_cache_hit + (1 − p_cache_hit) × p_treasury_available

At p_cache_hit = 0.99, p_treasury_available = 0.99 (anchor):
  upper bound = 0.99 + 0.01 × 0.99 = 99.99 %

SLO-C target = 99.5 % → comfortably feasible.
```

**SLO-C remains feasible after Phase-6 corrections.** ✅

### 4.4 Cache-miss p99 latency budget (new 3 s anchor)

```
Cache-miss path, first attempt:
  DB lookup + miss                  ≈ 10 ms
  Gate acquire                       ≈ 1 ms
  HTTP connect + send                ≈ 50 ms
  Treasury response (p99)            ≈ 500 ms
  JSON parse + schema validate       ≈ 10 ms
  Sanity + orientation check         ≈ 1 ms
  DB upsert versioned                ≈ 20 ms
  DB re-read eligible                ≈ 10 ms
  Conversion arithmetic + serialise  ≈ 5 ms
  Hot-cache populate                 ≈ 1 ms
  -----
  First-attempt total                ≈ 609 ms

With 1 retry  : + ~700 ms (timeout 2s × 0.3 probability of retry trigger + 200ms backoff)
                          ≈ 1,300 ms p99 if 30 % see retry
With 2 retries: + ~900 ms ≈ 2,000 ms (rare)
With 3 retries: + ~900 ms ≈ 2,900 ms (very rare; <1 % of requests)
```

**G6-P0-3 anchor of 3 s covers the 99th percentile with realistic retry distribution.** ✅

---

## 5. Pinned corrections to Phase-5 docs

| Doc | Pin | Reason |
|---|---|---|
| [failure-modes-and-resilience.md §3](../operations/failure-modes-and-resilience.md#3-circuit-breaker-calibration-closes-g4-p1-17) | CB → TIME_BASED, 300 s window, min-calls=5 | G6-P0-1 |
| [adr-0001-core-architecture.md D-9](../architecture/adr-0001-core-architecture.md#d-9--treasury-client-resilience4j-wrapped-single-flight-schema-validated) | Loser wait 200 ms → **10 s**; outcome-ref pattern | G6-P0-2 |
| [component-design.md §3.2](../architecture/component-design.md#32-get-apiv1purchasesidconversioncurrency--convert-and-retrieve) | Same loser-poll + winner-outcome-ref pattern | G6-P0-2 |
| [non-functional-requirements.md NFR-003](../requirements/non-functional-requirements.md) | Cache-miss p99 1500 ms → **3000 ms** | G6-P0-3 |
| [slo-sli.md §3.2](../operations/slo-sli.md#32-latency-slos) | SLO-G updated to 3000 ms | G6-P0-3 |
| [capacity-scalability-plan.md §1 / §2 / §5](../operations/capacity-scalability-plan.md) | Pool 10 → 20; replica-count Postgres ceiling note; 60/30/10 mix; bulkhead 50 → 10; H2 single-writer note | G6-P0-4 / G6-P1-2 / G6-P1-6 / G6-P1-9 |
| [deployment-architecture.md §4.3](../architecture/deployment-architecture.md#43-graceful-shutdown) | Graceful shutdown 30 s → 60 s for prod | G6-P1-4 |
| [monitoring-alerting.md §3.1](../operations/monitoring-alerting.md#31-rateorientationcontractcheck-calibration-g4-p1-27-closure) | Canary cadence weekly → **24 h** | G6-P1-8 |
| [traceability-matrix.md](../requirements/traceability-matrix.md) | Map G6-P*-* to FRs/NFRs/ACs/OQs/risks | Bookkeeping |
| [risk-register.md](../requirements/risk-register.md) | Add R-033..R-037 | Bookkeeping |

---

## 6. New risks

| ID | Risk | L | I | Score | Status |
|---|---|---|---|---|---|
| **R-033** | CB never opens at design traffic → no graceful degradation when Treasury degraded | 4 | 3 | 12 (Med) | Mitigated by G6-P0-1 time-based switch |
| **R-034** | Split outcomes for concurrent first-fetches violate AC-027d | 3 | 3 | 9 (Med) | Mitigated by G6-P0-2 loser-wait + outcome-ref |
| **R-035** | Pool exhaustion under burst at sub-anchor traffic | 3 | 3 | 9 (Med) | Mitigated by G6-P0-4 pool 10 → 20 |
| **R-036** | Cold-currency request cliff (top-10 warm-up doesn't cover) | 2 | 2 | 4 (Low) | Documented residual; G6-P1-1 |
| **R-037** | 30 s drain abandons in-flight under peak burst | 2 | 2 | 4 (Low) | Mitigated by G6-P1-4 30 s → 60 s |

---

## 7. Exit-criteria checklist

| Criterion | Status |
|---|---|
| Adversarial review across 4 lenses (perf, SRE, QA, auditor). | ✅ |
| Anchor math verified for steady-state and burst. | ✅ §4 |
| CB calibration error (G6-P0-1) corrected in-phase. | ✅ |
| Single-flight loser semantics error (G6-P0-2) corrected in-phase. | ✅ |
| Latency anchor inconsistency (G6-P0-3) corrected in-phase. | ✅ |
| Pool sizing error (G6-P0-4) corrected in-phase. | ✅ |
| P1 findings each carry owner + target gate. | ✅ |
| Pinned corrections to Phase-5 docs identified per §5. | ✅ |
| Risk register updated (R-033..R-037). | ✅ |
| Source-requirements.md untouched. | ✅ |
| `.human-approvals/` untouched. | ✅ |
| No implementation files created. | ✅ |
| Verdict recorded. | ✅ |

## 8. Verdict

**CONDITIONAL PASS to Phase 7 (PCI Security Design Session).**

Conditions, all closed this phase:

1. Four P0 corrections pinned into Phase-5 docs (CB sliding-window type, single-flight loser semantics, cache-miss p99 anchor, DB pool size).
2. Twelve P1 findings tracked with owners and target gates (Phase 7 / Phase 13 / accepted residuals).
3. Anchor math documented and ratifies the (corrected) numbers.

Phase 6 hands over to Phase 7 with:
- 4 P0 corrections pinned
- 12 P1 findings recorded with owners
- 10 P2 / NICE findings recorded
- 5 new risks (R-033..R-037) — all mitigated by Phase-6 pins
- 0 source-requirements.md edits
- 0 human-approval-marker creations
- 0 implementation files

Pause cadence: Phase 7 begins on explicit "proceed to Phase 7" approval.
