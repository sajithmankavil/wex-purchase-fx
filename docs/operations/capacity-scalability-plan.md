# Capacity and Scalability Plan

> **Status:** Phase 5 (Operational Design Session), 2026-05-17. Closes **G4-P1-21** (warm-up currency list + N) and **G4-P1-25** (capacity-anchor verification). Per source, performance-test **automation** is out of scope; the load-test plan here is documented manual procedure.
> **Phase-6 grill refinements (2026-05-17):** DB pool size 10 → 20 (G6-P0-4); load-test mix profile pinned (60/30/10); bulkhead 50 → 10 (G6-P1-2); H2 single-writer note (G6-P1-6); replica-count Postgres-connections ceiling note (G6-P1-10).

---

## 1. Expected load

**H2 single-writer note (Phase-6 G6-P1-6).** Case-study local mode uses H2 file mode. H2's MVStore serialises writes; concurrent writes queue. At case-study load (~60 POST/s with the 60/30/10 mix), this is well within H2's headroom (~10 000 writes/s in benchmarks). For production deployments, PostgreSQL is used and provides multi-writer concurrency without serialisation.


The case study has no published volume target. The production reference is a single-region, single-team service; the numbers below are anchors that v1 sizing supports, validated by the Phase-6 reliability grill and the manual load-test plan in §5.

| Dimension | Anchor (per-replica) | Anchor (cluster, 2 replicas) | 12-month growth | Source |
|---|---:|---:|---:|---|
| Requests/sec (mixed) | 100 | 200 | 2× | NFR-008 (proposed), ratified here. |
| Peak burst | 250 for ≤ 60 s | 500 | 2× | Operational hedge. |
| Steady-state cache-hit ratio | ≥ 99 % | n/a | flat | ADR-0001 D-10 (Phase-4 refined hot cache key). |
| Treasury upstream rate | ≤ 0.1 req/s | ≤ 0.1 req/s (single-flight dedupes across replicas) | flat | Quarterly publish cadence + cache. |
| `purchase_transactions` row count | n/a | up to 10⁷ over horizon | up to 10× | NFR-010 (proposed). |
| `exchange_rates` row count | n/a | ~2 000 steady-state | +200/quarter | Empirical from Phase-3 prototype (24 records sampled over 2 years for 3 currencies; scales linearly with currencies × quarters × revisions). |
| Concurrent in-flight conversions | **10** | 20 | 2× | Resilience4j bulkhead size (revised by Phase-6 G6-P1-2; reduced from 50 because single-flight collapses concurrent Treasury fetches to ≤ 5 in practice). |

## 2. Bottlenecks

| Bottleneck | Detection metric | Risk | Mitigation v1 | Scale strategy if exceeded |
|---|---|---|---|---|
| **Treasury upstream rate-limit** | `treasury.api.failure.count{reason=http_4xx}` (anonymous API; rate-limit unpublished) | Hit a hidden quota under load | Single-flight gate keyed by `(currency, treasury_quarter_end)` (Phase-4 G4-P0-1); request fanout collapses to ~1/quarter at cluster level | Add a request-coalescing layer at the gateway; cache TTL 24h → 7d; raise the warm-up list |
| **DB connection pool** | `hikaricp.connections.pending`, `…active` vs `…max` | Pool saturation blocks request handling | Hikari size = **20** per replica (raised from 10 by Phase-6 G6-P0-4); readiness fails if `active >= max - 1` sustained 5 s (Phase-4 G4-P1-19). **Replica-count ceiling note:** at 5 replicas × 20 conns = 100 = default Postgres `max_connections`. For > 5 replicas, raise Postgres `max_connections` to 200 OR introduce PgBouncer transaction-pool mode (Phase 12 pre-prod). | Increase pool size or replica count; tune `db.connection.timeout`; consider read-replica for `GET` paths |
| **JVM heap** | `jvm.memory.used / max` (heap) | OOM kills the process | 1 GiB heap limit, 512 MiB request; G1GC default; Caffeine bounded to ~10 k entries | Increase heap; tune Caffeine eviction; reduce request payload limit |
| **Tomcat connector threads** | `tomcat.threads.busy` vs `tomcat.threads.config.max` | Slow-loris or thread-pool saturation | Default 200 max; connection-timeout 10 s; per-request timeout via filter | Lower max-connections to push back at the LB layer |
| **OTel collector queue** | OTel collector lag metric (out-of-process) | Telemetry loss under high RPS | Sampling at 100 % v1 (low volume); tail-based sampling planned Phase 5/6 | Tail-based sampling; head-sampling fallback |
| **Hot cache thrash** | `exchange_rate.cache.eviction.count` | Cache thrash on diverse currencies → low hit ratio → cold-path latency | Cache size unlimited (Caffeine `maximumSize=2000`; comfortable headroom over ~1 600 steady-state) | Increase `maximumSize`; partition cache by currency if needed |
| **Single-flight gate contention** | `single_flight.loser.polled.count` + `wex.gate.wait_ms` span attribute | High concurrent first-fetches starve losers | Quarter-end-keyed gate + bounded 200 ms wait + DB poll (G4-P1-6) | Increase loser bounded-wait; pre-populate via warm-up |

## 3. Scale strategy

| Strategy | When | How |
|---|---|---|
| **Horizontal (preferred)** | Steady-state load > 80 % of per-replica anchor sustained 10 min | Add replicas via platform; LB drains automatically; new replicas warm up in ≤ 30 s (§4) |
| **Vertical** | Memory pressure on heap; not throughput-bound | Increase pod memory limit + heap; restart |
| **Partitioning / sharding** | Out of scope v1 (single-tenant) | Future: tenant partition key on `purchase_transactions` |
| **Queueing** | Not applicable v1 (synchronous request/response only) | Future: if event publication lands (OQ-013), introduce a queue |
| **Caching** | Already in design (DB-primary + Caffeine; D-10) | Tune TTL 24h → 7d in production for lower upstream traffic |
| **Rate limiting at gateway** | Always-on for production | Platform-level token-bucket; service-layer rate-limiter optional (G4-P1-15 Phase 7) |

## 4. Warm-up job (closes G4-P1-21)

Phase-4 observability.md §8 documented the warm-up shape; this section pins the parameters.

**Currency list (`WEX_WARMUP_CURRENCIES`).** Default:

```
Canada-Dollar
Euro Zone-Euro
Japan-Yen
UK-Pound Sterling
Australia-Dollar
Mexico-Peso
China-Yuan Renminbi
India-Rupee
Brazil-Real
Switzerland-Franc
```

Ten currencies; chosen for coverage of: USD trading partners (CA, MX), major reserve currencies (EUR, GBP, JPY, CHF), high-volume corridors (CNY, INR, BRL), and a regional sanity check (AUD). The list is overridable via env var.

**N (number of currencies to warm).** N = 10 default; raise via `WEX_WARMUP_CURRENCIES`. Cost: 10 currencies × ~200 ms per `findEligibleRate(currency, today, today.minusMonths(6))` (DB lookup) ≈ 2 s aggregate (mostly parallelisable).

**Trigger.** On container start, after `readiness=UP`. Single async task; bounded by `WEX_WARMUP_TIMEOUT_SECONDS` (default 30 s). On timeout, the service stays UP and emits a WARN.

**Failure mode.** If the DB has no eligible rate for a warmed currency, the warm-up triggers a Treasury fetch under the single-flight gate. Resilience4j applies as normal — bounded timeout / retry / CB. On Treasury failure during warm-up, the currency stays cold; first user request will retry naturally.

**Observed cost.** Warm-up emits a `warmup_completed` log event with `{ currencies, hits, misses, fetches, latencyMs }` for ops review.

## 5. Load-test plan (closes G4-P1-25)

**Out of CI** per source-rule (perf-test automation not required). The load tests below are **manual, documented procedures** run in `staging` before any production cutover.

**Tool.** [k6](https://k6.io) chosen for v1 (JavaScript scenarios; native HTTP support; reasonable throughput from a single test runner). Alternatives recorded: Gatling (JVM-native; heavier), JMeter (mature; verbose configuration).

**Environment.** `staging` deployment, two replicas, production-mirroring DB (Postgres). Treasury client points at WireMock fixtures (no live Treasury during load tests; avoids polluting Treasury's logs).

| Test | Scenario | Passing criteria | Frequency | Evidence location |
|---|---|---|---|---|
| **Baseline load** | 100 req/s mixed (**60 % POST, 30 % GET conversion, 10 % GET-by-id** — pinned by Phase-6 G6-P1-9 as the canonical "mixed" profile), 10 min, default config | p99 latency within SLO-D/E/F/G anchors; 0 % error rate; cache hit ratio ≥ 95 % by end of test | Per release with material change to request path | `docs/release/test-plan.md` results section |
| **Peak load** | 250 req/s for 60 s burst on top of 100 req/s steady, mixed | p99 stays within 2× SLO anchors; no readiness flap; recovers within 30 s post-burst | Per release with capacity-affecting change | Same |
| **Soak** | 100 req/s steady for 4 hours | No memory leak (heap stable post-GC); no connection-pool drift; throughput stable | Quarterly | Same |
| **Failure injection — Treasury down** | Treasury client returns 503 for 30 min during steady load | All requests served from cache hit path; 0 unsolicited 503s when local rates eligible | Per quarter | Same |
| **Failure injection — DB pool saturation** | Reduce pool to 2 connections; sustain 100 req/s | Readiness flips DOWN within 30 s; LB drains; no data corruption | Per release with DB-touching change | Same |
| **Failure injection — single-flight thundering herd** | 50 concurrent first-fetches for same `(currency, quarter)` | Exactly 1 Treasury upstream call (AC-027b/d/e) | Per release | Same |
| **Cold-start cliff** | New replica deploys; warm-up runs; first 100 req hit before warm-up completes | First 100 req latency ≤ NFR-003 cache-miss anchor (1500 ms p99) | Per release with warm-up change | Same |

## 6. Cost controls

Production-reference deployment uses platform-default container sizing. Cost ceiling per replica: 1 vCPU + 1.5 GiB memory + ~ 5 GB disk → small-instance class on any platform. Two replicas in `prod` ≈ within free-tier or low-budget operating range.

Treasury API: anonymous, no cost.

Cost monitoring is platform-owned (E1 owner per [oncall-escalation.md](oncall-escalation.md)); the service itself does not implement cost guardrails beyond the cardinality budget (≤ 5 000 metric series).

## 7. RTO / RPO (cross-reference)

From NFR-007 and [rollback-plan.md](rollback-plan.md) §10:

- **RPO:** ≤ 5 min for stored purchases (Postgres synchronous replica in same region).
- **RTO:** ≤ 30 min for single-region restart (automated replica replacement).
- **Multi-region DR:** out of scope v1 (OQ-012); trigger to escalate documented in design-session.md §10.

## 8. Linked artefacts

- [slo-sli.md](slo-sli.md) — latency SLOs the load tests verify.
- [error-budget-policy.md](error-budget-policy.md) — release-velocity policy tied to SLOs.
- [monitoring-alerting.md](monitoring-alerting.md) — saturation alerts that detect approach to anchors.
- [runbook.md](runbook.md) — operator action when bottlenecks surface.
- [failure-modes-and-resilience.md](failure-modes-and-resilience.md) — failure-injection scenarios mirror this load-test catalogue.
- [docs/release/test-plan.md](../release/test-plan.md) — load-test evidence destination (Phase 13 onwards).
- [docs/architecture/deployment-architecture.md](../architecture/deployment-architecture.md) §4 — replica topology.
