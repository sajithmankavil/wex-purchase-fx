# Failure Modes and Resilience

> **Status:** Phase 5 (Operational Design Session), 2026-05-17.
> Consolidated failure-mode table (extends Phase-3 design-session.md §8) with detection, mitigation, recovery, and test mapping. The Phase-6 reliability/scalability grill attacks these as plausible enough to ship vs. needing chaos validation.

---

## 1. Failure-mode catalogue

| # | Failure mode | Impact | Detection | Mitigation | Recovery | Test |
|---|---|---|---|---|---|---|
| **F-01** | Treasury API timeout | Conversion request slows; eventual `503 UPSTREAM_UNAVAILABLE` if no local rate | Resilience4j timeout (2 s); `treasury.api.failure.count{reason=timeout}`; `treasury.client.duration` p99 | Bounded retry (3× with exponential backoff + jitter); circuit breaker opens after 50 % failure / 20 calls; local cache serves if eligible | Automatic on Treasury recovery; CB half-opens; single-flight gate releases on SIGTERM (G4-P1-20) | `TreasuryClientTimeoutIT` (WireMock slow response) |
| **F-02** | Treasury API 5xx | Same | `treasury.api.failure.count{reason=http_5xx}` | Retry budget + CB | Automatic | `TreasuryClient5xxIT` |
| **F-03** | Treasury API malformed payload | `502 UPSTREAM_BAD_RESPONSE`; rate not persisted | JSON schema validator | Reject upstream; do not persist; alert; never serve synthetic rates | Manual: investigate schema drift; update WireMock fixtures; ship hotfix if Treasury renamed a field | `UpstreamMalformedResponseIT` |
| **F-04** | Treasury rate outside sanity bounds | `502 UPSTREAM_BAD_RESPONSE`; metric | `treasury.api.failure.count{reason=rate_sanity}` | Reject offending rate; do not persist; the rest of the response is accepted | Manual: investigate; if legitimate (hyperinflation), raise bound (currently 10³⁰) | `RateSanityRejectionTest` |
| **F-05** | Treasury rate orientation drift (a currency flips published convention) | Silent 1/x conversion error for that currency | `RateOrientationContractCheck` WARN; canary alert (weekly, Phase 5) | v1: WARN-only; Phase-5 ratified threshold: ≥ 3 WARN/24 h alert; > 5 % drift on quarterly canary → fail-closed for that currency (G4-P1-27) | Manual: feature-flag-disable conversions for affected currency; update fixture set; ship hotfix; update ADR-0001 D-4 | **TBD** — `OrientationContractFixtureTest` not yet implemented; the invariant is asserted today by the production-data fixture set in `src/test/resources/treasury-fixtures/` consumed by integration tests, and by the weekly canary cron (Phase-12-equivalent). Tracked in Phase 11 30-review §F-LOW-1. |
| **F-06** | Treasury republishes a rate for an existing `(currency, record_date)` | Subsequent conversions use revised value; previously-served responses unchanged | UNIQUE-key INSERT-on-conflict resolved by INSERT of new version row | Versioned persistence (D-3 / A-018; composite PK includes `effective_date`); `exchange_rate_revision_persisted` event | Automatic; no action needed | `TreasuryRateRevisionVersioningIT` (AC-026b) |
| **F-07** | Alias-table missing or parse-error on startup | Service refuses to UP | Startup `ApplicationContextException`; readiness DOWN | `currency-aliases.json` is a classpath resource (default) or env-pointed file with read-only mount in prod | Manual: fix the file; restart | `AliasTableStartupIT` |
| **F-08** | Alias-table drift (Treasury renames a `country_currency_desc`) | Possible `400 INVALID_CURRENCY` for an alias-known input; or silent canonical mismatch | `currency_alias.drift.detected.count`; daily reconciliation job; per-request detection (AC-021b, AC-021c) | Audit event; service falls back to Treasury's canonical (AC-021c) or returns `400` (AC-021b) | Manual: PR updates `currency-aliases.json`; daily reconciliation alerts ops | `AliasDriftIT` |
| **F-09** | Concurrent first-fetches thundering herd (cold cache + diverse `(currency, txDate)`) | Treasury upstream over-fanout | `single_flight.loser.polled.count`; `wex.gate.wait_ms` span attribute | Single-flight gate keyed by `(country_currency_desc, treasury_quarter_end)`; bounded loser wait + DB poll (G4-P1-6) | Automatic | `SingleFlightCacheConcurrencyTest` (AC-027b/d/e) |
| **F-10** | Database connection unreachable | Readiness DOWN; LB drains; service degrades | Hikari `db.connection.timeout` count; readiness probe | Readiness probe checks `SELECT 1` + pool capacity (Phase-4 G4-P1-19) | Automatic on DB recovery; container restart if persistent | `DbUnavailableReadinessIT` |
| **F-11** | DB connection-pool exhausted | Requests block; latency spikes | `hikaricp.connections.pending > 0` sustained 60 s (P1 page) | Pool size = 10 per replica with `connection-timeout` 5 s; readiness fails if `active >= max - 1` sustained 5 s | Manual: scale replicas; increase pool size in capacity-plan if recurring | Load-test failure-injection §5 of capacity-scalability-plan.md |
| **F-12** | JVM heap saturation / OOM | Process killed by OOMKiller; container restart | `jvm.memory.used / max` > 80 % sustained 5 min (P2) | 1 GiB heap limit; G1GC; bounded Caffeine cache size | Container restart restores service; investigate cause | Soak test (capacity-plan §5) detects leaks |
| **F-13** | Tomcat thread-pool saturation (slow-loris) | New requests blocked | `tomcat.threads.busy / config.max` > 90 % | Tomcat connection-timeout 10 s; max-connections 200; LB layer | Automatic; LB reschedules to healthy replicas | Slow-loris simulation in soak |
| **F-14** | Single-flight gate held across SIGTERM | Losers block until JVM kill | `single_flight.gate.released_on_shutdown` event | Gate explicitly releases all locks on SIGTERM (G4-P1-20); losers fail-fast `503` | Automatic | `GracefulShutdownIT` |
| **F-15** | Duplicate `POST /purchases` without `Idempotency-Key` | Two purchases with two ids (AC-001b) | n/a — documented behaviour | Idempotency-Key support is P1 v1 / BLOCKING-for-prod (OQ-009) | Manual: client must use the header; service does not de-dup without it | `IdempotencyAbsentSemanticsTest` (AC-001b) |
| **F-16** | `description` carries PAN, track data, or encoded payment data | `400 PAN_PATTERN_DETECTED` with `reason ∈ {luhn,track1,track2,luhn-encoded}` | `description.content_guard.fired.count{reason}` | Boundary content guard with encoded-input pre-pass (Phase-4 G4-P0-5; AC-010d) | Automatic rejection; payload not logged; audit event emitted | `PanPatternGuardTest`, `TrackDataGuardTest`, `EncodedPanGuardTest` |
| **F-17** | H2 file corruption on cloud-sync drive (local mode) | Service fails to start or reads corrupted data | Startup WARN if `WEX_DATA_DIR` under sync prefix; H2 SQLException on read | A-021: default data dir `${user.home}/.wex-purchase-fx/data` (off OneDrive/Dropbox/iCloud) | Manual: restore backup; relocate data dir | **TBD** — `DataDirSyncPrefixWarningTest` not yet implemented; A-021 default lives in `application.yml` `wex.data.dir`. |
| **F-18** | Logging HMAC key absent in prod/staging | Service refuses to start (refuse-to-start invariant) | `ApplicationContextException` from `DescriptionHasher` bean | A-020 / NFR-017: refuse-to-start; restart loop visible | Manual: restore env var; restart | Constructor-level enforcement covered by `DescriptionHasherTest`. **TBD** — dedicated startup-failure IT (`LoggingHashKeyStartupTest`, AC-032b) not yet implemented. |
| **F-19** | Configuration regression (bad env var rollout) | New behaviour breaks; metric anomalies | Recent deploy correlation + RED dashboard | Roll back via [rollback-plan.md](rollback-plan.md) Class B | Within 2 min platform revert + pod restart | Class-B rehearsal in `staging` |
| **F-20** | Schema migration introduces deadlock or wrong shape | App fails to start; or runtime errors on the affected column | Flyway migration log; Spring context failure | Forward-only migrations; revert via new `V<N+1>__revert_…sql` (rollback-plan Class C) | Per Class C in rollback-plan.md | Migration test per release |
| **F-21** | Bulk wrong rate persisted via Treasury bug | Conversions for affected `(currency, record_date)` are wrong | `treasury.contract.orientation_drift.count` or downstream complaints | Versioned persistence (D-3) limits blast radius to the affected record set | Class D PITR + targeted SELECT to validate | Phase-7 PCI tabletop |
| **F-22** | Secrets backend compromise (HMAC key exfiltration) | Past-log `description.hash` values become invertible (for short / low-entropy descriptions) | SIEM alert from secrets backend | Rotate key (NFR-013b ≥ 256 bits); ship new `vN+1:` prefix; old digests stay correlatable within v_N window | Phase-7 IR playbook (rollback Class F) | Phase-7 tabletop |
| **F-23** | Container-image CVE post-release | Vulnerability disclosed in dependency | CVE feeds; image scan in CI | Vulnerability-management SLA (G4-P1-26 → Phase 7): HIGH within 7 days, CRITICAL within 24 h | Rebuild image; rolling deploy | Quarterly drill |
| **F-24** | Audit-log destination unreachable | Audit events lost or buffered | Audit sink lag metric (platform) | Platform-managed buffer; PCI evidence retention from secondary source | Manual: investigate sink; replay buffered if available | Phase-7 |
| **F-25** | Time-zone misalignment on `transactionDate` boundary | Edge-case selection of wrong-day rate | Date-boundary integration test | UTC everywhere; `transactionDate` is a DATE (no time, no zone) | Automatic by design | Boundary integration test (AC-018/019/018b/019b) |
| **F-26** | Cache invalidation gap on Treasury revision within TTL | Stale rate served until TTL eviction | Latency-by-cause metric | ADR-0001 D-10 (Phase-4 pinned): `upsertVersioned()` invalidates the affected hot-cache entry | Automatic | `CacheInvalidationOnRevisionTest` |
| **F-27** | OpenAPI spec drift (breaking change ships unintentionally) | Clients break | CI `oasdiff` gate against previous-main snapshot | Spec generated from code; CI fail-closed on incompatible diff | Block release; fix; reissue | CI gate (Phase 13) |

## 2. Resilience patterns (cross-reference)

| Pattern | Where |
|---|---|
| Bounded timeouts | Treasury client (Resilience4j) — 2 s default; configurable via `WEX_TREASURY_TIMEOUT_MS` |
| Bounded retries with exponential backoff + jitter | Treasury client — 3 attempts, 100 ms × 2ⁿ + jitter |
| Circuit breaker | Treasury client — 50 % over 20 calls → open 30 s (G4-P1-17 — Phase 5 ratifies tuning; see §3 below) |
| Bulkhead | Treasury client — 50 concurrent permits |
| Single-flight | `(currency, treasury_quarter_end)`; bounded 200 ms loser wait + DB poll; release on SIGTERM |
| Idempotency | Postgres `ON CONFLICT DO NOTHING` on rate inserts; `Idempotency-Key` on POST is P1 / BLOCKING-for-prod |
| Backpressure | Tomcat connection-timeout + max-connections at LB |
| Graceful degradation | Treasury outage + local rate eligible → success; only `503` if no eligible rate |
| Kill switches | `WEX_FORCE_READINESS_DOWN=true` to force drain (rollback-plan Class D) |
| Versioned persistence | `(currency, record_date, effective_date)` composite PK; max(`effective_date`) on query |

## 3. Circuit-breaker calibration (closes G4-P1-17; revised by Phase-6 G6-P0-1)

Phase-3 set defaults (50 % failure / 20 calls / 30 s open). Phase-4 grill flagged as plausibly too sensitive for a quarterly-publish dependency. Phase-5 ratified count-based 100-calls. **Phase-6 grill (G6-P0-1) corrected to time-based**: count-based CB is dead at single-flight-deduplicated Treasury traffic (~0.1 req/s cluster-wide → 100 calls ≈ 17 min sample window, far longer than typical outage). The corrected calibration:

| Parameter | Value | Rationale |
|---|---|---|
| `sliding-window-type` | **TIME_BASED** | Closes G6-P0-1. Count-based dead at design traffic. |
| `sliding-window-size` (seconds) | **300** (5 min) | Captures genuine outages without overreacting to single-call jitter. |
| Failure rate threshold | 50 % | Standard; balances noise vs. responsiveness. |
| Minimum number of calls | **5** | Lowered from 100. Any 5+ samples in 5 min is enough to open on sustained failure. |
| Wait duration in open state | **5 min (300 s)** | Treasury outages tend to be minutes-to-hours; gives upstream room to recover. |
| Permitted calls in half-open state | **3** | Lowered from 5. Smaller recovery sample to match the smaller failure sample. |
| Slow-call threshold (latency) | 1 s | Treasury p99 observed ~500 ms; 1 s flags genuine slowness. |
| Slow-call rate threshold | 100 | Slow-call CB disabled (failure-rate CB does the work). |

Knob names (Spring Boot config):

```yaml
resilience4j.circuitbreaker.instances.treasury:
  sliding-window-type: TIME_BASED
  sliding-window-size: 300
  minimum-number-of-calls: 5
  failure-rate-threshold: 50
  wait-duration-in-open-state: 5m
  permitted-number-of-calls-in-half-open-state: 3
  slow-call-duration-threshold: 1s
  slow-call-rate-threshold: 100
```

Phase 13 implementation validates these via the WireMock-backed "Treasury 5xx sustained" fixture in AC-T-3 (must observe CB open within 5 min).

## 4. RTO / RPO

| Objective | Target | Mechanism |
|---|---|---|
| **RPO** (Recovery Point Objective) | ≤ 5 min for `purchase_transactions` | Postgres synchronous replication to in-region secondary. Local mode: snapshot cadence as per `runbook.md`. |
| **RTO** (Recovery Time Objective) | ≤ 30 min for single-region restart | Automated replica replacement + PITR on managed Postgres. |
| **MTTR** (Mean Time To Recovery) — SEV1 | < 30 min | Operational target; verified by rehearsals + PIRs. |
| **MTTD** (Mean Time To Detect) — SEV1 | < 5 min | Alert design (multi-window burn rate + dependency alerts). |
| **DR** (Disaster Recovery) — cross-region | Out of scope v1 (OQ-012) | Escalation trigger: real RTO < 30 min requirement OR multi-region read-locality requirement. |

## 5. Linked artefacts

- [slo-sli.md](slo-sli.md) — SLOs against which these failure modes consume budget.
- [error-budget-policy.md](error-budget-policy.md) — release-velocity implications.
- [monitoring-alerting.md](monitoring-alerting.md) — alerts that fire on detection signals above.
- [runbook.md](runbook.md) — operator step-by-step for each failure class.
- [rollback-plan.md](rollback-plan.md) — six rollback classes; many failure modes terminate in one of them.
- [capacity-scalability-plan.md](capacity-scalability-plan.md) §5 — failure-injection load-test plan.
- Phase-4 grill: [design-grill.md](../planning/design-grill.md) §1 — original P0/P1 findings that shaped many of these.
