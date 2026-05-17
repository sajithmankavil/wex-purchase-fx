# Design Grill — Phase 4

> **Status:** COMPLETED — 2026-05-17
> **Verdict:** **CONDITIONAL PASS** — Phase 4 hands over to Phase 5 only after the five P0 findings below are addressed (in-place pins to the Phase-3 docs land in this same phase; the remaining narrative resolutions move to Phase 5 / Phase 7 / Phase 13 as called out per finding).
>
> **Adversarial panel:** Principal Java engineer · Security reviewer with PCI background · SRE who has been paged at 2 a.m. for currency-conversion regressions · QA / test engineer · Auditor / QSA.
> **Inputs reviewed:**
> - [ADR-0001](../architecture/adr-0001-core-architecture.md)
> - [system-context.md](../architecture/system-context.md), [component-design.md](../architecture/component-design.md), [data-model.md](../architecture/data-model.md), [api-contracts.md](../architecture/api-contracts.md), [deployment-architecture.md](../architecture/deployment-architecture.md)
> - [design-session.md](design-session.md) §10 forward-looking gaps (this grill attacks each one)
> - [phase-3-prototype-log.md](phase-3-prototype-log.md), [requirements-grill.md](requirements-grill.md), [day-1-ratifications.md](day-1-ratifications.md)
> - All Phase-1 / Phase-2 requirements set
>
> **Scope note.** The grill is allowed (and required) to attack proposed P0 design positions where the design *baked them in*. Where the Phase-3 docs already correctly leave decisions to Phase 5 (capacity anchors, alert thresholds, etc.), the grill flags only if the leave-to-later is itself a risk.

---

## 0. Executive verdict

The Phase-3 architecture is internally consistent and satisfies the case-study constraints. It also has **five concrete correctness/release-blocking flaws** that crystallised once the documents were read as a system rather than a sequence:

1. **The single-flight gate key is too narrow.** Most concurrent first-fetch requests **will not** deduplicate.
2. **The hot cache key has the same flaw.** Cache-hit ratio target (95 %) is unreachable as designed.
3. **`exchange_rate` scale normalisation contradicts the API contract** ("full Treasury precision" in the response vs. `DECIMAL(19,6)` in storage).
4. **UUID v7 generator dependency is not declared** in D-2. Java 21's `UUID.randomUUID()` is v4.
5. **PCI defense-in-depth has a credible bypass** (base64 / URL-encoded payment data in `description`), and the architecture's "out-of-CDE" claim leans on the guards as defense in depth.

Each is fixable; none requires a redesign. The first two are linked (they share a root cause — the per-purchase lookup window as a cache/lock key — and resolve together by re-keying on the Treasury record-date rather than the purchase-derived window). All five P0 corrections are **pinned into the Phase-3 docs in this phase** (see §6); the grill record below explains the reasoning and the test surface.

Beyond the P0s, twenty P1 findings span correctness, security, SRE, testability, and audit dimensions, and ten P2 / NICE items are recorded for future hygiene. The Phase-3 design holds at the level of approach; what changes is the precision of a handful of components and the depth of a few defenses.

---

## 1. P0 findings — block Phase-5 handover until pinned

### G4-P0-1 — Single-flight gate key is too narrow (correctness gap)

**Observation.** ADR-0001 D-9 and [component-design.md](../architecture/component-design.md) §3.2 specify `SingleFlightGate` keyed by `(country_currency_desc, lookup_window)`, where `lookup_window = [txDate.minusMonths(6, EOM), txDate]`. Two purchases on different `transactionDate` values produce **different** windows even when they would resolve to the **same** Treasury rate. The gate therefore admits multiple concurrent fetches that should be deduplicated. AC-027b's "at most one upstream call" guarantee holds only for the narrow case of identical `(currency, transactionDate)`.

**Lens.** Principal engineer (correctness); SRE (Treasury load).

**Evidence.** ADR-0001 D-9; component-design.md §3.2 / §3.3; AC-027b in [acceptance-criteria.md](../requirements/acceptance-criteria.md).

**Risk.** Treasury upstream load multiplied by the number of distinct `transactionDate` values in flight. At 100 req/s with reasonable date distribution, this could be 10×–50× the intended upstream rate. Burns Treasury's (unpublished) rate-limit budget; defeats single-flight's purpose; degrades user-visible latency on cold cache.

**Required fix (pinned in Phase 4).** Re-key the gate on `(country_currency_desc, treasury_quarter_end)` where `treasury_quarter_end = ceil(txDate, quarter-end)`. Treasury publishes quarterly; the gate granularity should match. Three purchases with `transactionDate` ∈ `{2026-04-01, 2026-05-14, 2026-06-30}` all map to the same Q2-2026 fetch window and dedupe to a single Treasury call.

**Acceptance update.** AC-027b text remains correct (same Treasury rate ⇒ at most one upstream call); add `AC-027e` asserting that two simultaneous conversions for purchases in the same calendar quarter deduplicate.

**Owner.** Architect.

**Target gate.** Pinned in this Phase-4 grill (component-design.md and ADR-0001 D-9 updated in-place). Phase 13 implementation realises the new key.

---

### G4-P0-2 — Hot cache key is per-purchase-window; cache-hit ratio target unreachable (operability gap)

**Observation.** ADR-0001 D-10 and component-design.md §1 specify `ExchangeRateHotCache` keyed by `(country_currency_desc, lookup_window)`. Same flaw as G4-P0-1: per-purchase-date windows fragment the cache. With 100 distinct purchase dates per currency the hot cache has 100 entries per currency for what is functionally one rate row. The architecture target of "≥ 95 % cache-hit ratio steady-state" (NFR-003 / capacity anchor in deployment-architecture.md §11) is not achievable with this key.

**Lens.** Principal engineer; SRE.

**Evidence.** ADR-0001 D-10; component-design.md §1 (`ExchangeRateHotCache`); deployment-architecture.md §11; NFR-003.

**Risk.** Real-world cache-hit ratio drops to roughly `1 / unique_dates_per_currency`. p99 latency on FR-003 sits at the cache-miss path (≤ 1500 ms per NFR-003) rather than the cache-hit path (≤ 300 ms). Treasury is hit far more often than intended.

**Required fix (pinned in Phase 4).** Key the hot cache by `(country_currency_desc, record_date)` — the rate's own identity, not the purchase-derived window. Eligible-rate lookup becomes: search the cache for entries whose `record_date` falls in the purchase's window; pick max(`record_date`). With ~ 200 currencies × ~ 8 quarter-ends, the cache holds ~ 1 600 entries — small and dense. Hit ratio rises to ≥ 99 % once warm.

**Acceptance update.** Refine AC-027b text to clarify the gate granularity (see G4-P0-1). Add a test that asserts the cache hit-rate-by-currency after a warm-up.

**Owner.** Architect.

**Target gate.** Pinned in this Phase-4 grill (ADR-0001 D-10, component-design.md §1 / §3.2 updated in-place). Phase 13 implementation realises the new key.

---

### G4-P0-3 — `exchange_rate` scale normalisation contradicts the API contract (correctness gap)

**Observation.** [data-model.md](../architecture/data-model.md) §4 specifies `exchange_rate DECIMAL(19,6)`; the column normalises Treasury's variable-scale string (`"1.37"`, `"1.393"`, `"148.0"`, `"159.41"`) to scale 6. [api-contracts.md](../architecture/api-contracts.md) §1 / §5 says the API surfaces `exchangeRate` "at full Treasury precision." After the round-trip through storage, the original scale is lost — `"148.0"` becomes `"148.000000"`. The two documents contradict.

**Lens.** Principal engineer (correctness); QA (contract testability).

**Evidence.** data-model.md §4; api-contracts.md §1 / §5; Phase-3 prototype P-3.

**Risk.** API consumers writing strict equality assertions (e.g., contract tests, fixture replays) fail on the scale drift. The current case-study example value `"1.3700"` is itself inconsistent with the prototype-observed `"1.393"` — scale 4 vs scale 3.

**Required fix (pinned in Phase 4).** Pick one of two:

- **Option A (chosen).** Drop the "full Treasury precision" promise. Document that the service stores rates at scale 6 and surfaces them at scale 6 (zero-padded as needed). The API consumer can trim trailing zeros if they wish. This is the simplest semantics; it survives all observed Treasury scales (1, 2, 3, 4 — all ≤ 6); it makes contract tests stable.
- Option B. Store the original Treasury string alongside the decimal (`exchange_rate_original_text VARCHAR(32)`); surface that string in the response. Costlier; preserves bit-exact precision; not required by any acceptance criterion.

Phase 4 selects **Option A**. ADR-0001 D-4 / D-10 and api-contracts.md §1 / §5 pin scale-6 normalisation as the contract.

**Acceptance update.** AC-014 example response becomes `"exchangeRate": "1.370000"`. AC-T-3 fixture set is refined to assert scale-6 normalisation regardless of upstream scale.

**Owner.** Architect / API designer.

**Target gate.** Pinned in this Phase-4 grill (in-place edits to api-contracts.md and data-model.md). Phase 13 realises.

---

### G4-P0-4 — UUID v7 generator dependency not declared in the stack (build gap)

**Observation.** ADR-0001 D-7 chooses UUID v7 as the identifier format. D-2's ratified stack ("Java 21, Spring Boot 3.x, Maven, JUnit 5, AssertJ, Mockito, WireMock, Pitest, ArchUnit, jqwik") does not include a UUID-v7 generator. Java 21's standard `java.util.UUID.randomUUID()` is v4. A Phase-13 implementer would either (a) reach for v4 and silently violate D-7, or (b) pull in an undeclared dependency.

**Lens.** Principal engineer; auditor.

**Evidence.** ADR-0001 D-2 / D-7; openjdk-21 API docs.

**Risk.** Either correctness drift (v4 instead of v7, killing the index-locality argument that justified D-7) or undocumented dependency introduction (defeats D-2's ratification).

**Required fix (pinned in Phase 4).** D-2 augmented to include `com.github.f4b6a3:uuid-creator:5.x` (Apache 2.0) as the v7 generator. Alternatives recorded: `io.hypersistence:hypersistence-utils-hibernate-63` (broader Hibernate integrations, heavier dependency), or a small custom implementation (~ 50 LOC) per RFC 9562. The library choice is recorded; the Phase-13 implementer is not forced to write the generator.

**Acceptance update.** None at the AC level; AC-013 already accepts both UUID and ULID syntactic shapes.

**Owner.** Architect.

**Target gate.** Pinned in this Phase-4 grill (ADR-0001 D-2 updated in-place).

---

### G4-P0-5 — PCI defense-in-depth has a credible bypass (encoded payment data) (compliance gap)

**Observation.** Phase-3 D-13 frames the primary PCI control as the API contract's prohibition on payment data (A-017), with the PAN-Luhn (AC-010b) and track-data (AC-010c) guards as defense in depth. Both guards operate on the **raw `description` string**. An attacker submitting `description=NDI0MiAyNDIyIDQyNDIgNDI0Mg==` (base64 of `"4242 4242 4242 4242"`) bypasses both. Equivalent bypasses: hex (`34323432`), URL-encoded (`4242%204242%20…`), Unicode-confusable digits.

**Lens.** Security reviewer / PCI auditor.

**Evidence.** D-13 / NFR-016 in non-functional-requirements.md; AC-010b / AC-010c.

**Risk.** The PCI-out-of-CDE claim leans on the guards as credible defense in depth. The contract-level prohibition is necessary but not sufficient evidence for the QSA: the auditor will ask, "how do you know an attacker can't smuggle PAN past your guards?" "We don't decode base64 / hex" is a finding.

**Required fix.** Two-part. Phase 4 records the direction; Phase 7 (PCI security design) executes:

- Promote the guards from defense-in-depth to a **detection-and-alert** stack. Decode the `description` field through a small candidate-decoder pipeline (base64, hex, URL-encoded) and re-run the Luhn + track-data guards on each candidate decoding. A hit at any stage rejects the payload.
- Document the residual surface (Unicode confusables, split across future free-text fields) explicitly and accept it as residual at v1; mark for Phase 13+1 review.

**Acceptance update.** New AC-010d: `description` containing a base64- / hex- / URL-encoded Luhn-valid PAN sequence is rejected as `400 PAN_PATTERN_DETECTED` with `details.reason="luhn-encoded"`.

**Owner.** SecArch (Phase 7).

**Target gate.** **Direction pinned now; full design in Phase 7.** D-13 in ADR-0001 and §6.2 of api-contracts.md updated in-place to reflect that the guards are detection-and-alert, not just defense-in-depth.

---

## 2. P1 findings — must resolve or explicitly accept before Phase 9 (implementation readiness)

### Correctness / maintainability (Principal engineer lens)

| ID | Title | Observation | Required action |
|---|---|---|---|
| **G4-P1-1** | ArchUnit rule "no `description` in log calls" can't be expressed in ArchUnit | ArchUnit operates on class structure; method-argument string analysis is not its domain. The intended check is a *coding-standard* rule (PMD / Checkstyle / Spotless / an Error Prone bug pattern). | Reformulate as a PMD custom rule or Error Prone check in Phase 13. ArchUnit rules in component-design.md §6 retain layer + money-type checks; logging hygiene moves to the linter ruleset. |
| **G4-P1-2** | `effective_date >= record_date` CHECK is empirically unverified | All 24 records observed had equality; the relation could in theory go either way for corrections. | Soften to `effective_date IS NOT NULL`; document the working semantic ("`effective_date` is Treasury's published correction timestamp, normally equal to `record_date`"). Phase 5 issues a longer-window Treasury fetch to confirm. |
| **G4-P1-3** | `exchange_rate ≤ 10⁹` sanity bound rejects hyperinflation currencies | Zimbabwe (peak ~ 10²⁵), Venezuela (~ 10⁶), Lebanon (~ 10⁵), Iran (~ 10⁵) are all material in macro history; Zimbabwe peak exceeds 10⁹. | Raise the bound to **10³⁰** and document. The probability of a Treasury-published rate exceeding that is effectively zero; the probability of a Treasury bug producing `10¹²` or higher is non-zero. Tradeoff: higher sanity bound = weaker upstream-bug detector. |
| **G4-P1-4** | Surrogate `id` on `exchange_rates` is redundant with the natural unique key | `(country_currency_desc, record_date, effective_date)` is already unique; the `id VARCHAR(36)` adds storage and an index for no benefit. | Drop the surrogate; promote the triple to PK. Data-model.md updated in-place (Phase 4) as a P1 pin. |
| **G4-P1-5** | `@Transactional` boundaries in `ConversionService` are unclear | The service composes: read purchase (tx 1) → maybe external call (no tx) → upsert rates (tx 2) → re-read (tx 3). Method-level `@Transactional` cannot express this. | Use explicit `TransactionTemplate` for the upsert step. Document the three-transaction shape in component-design.md §3.2. Phase 13 implements; Phase 4 documents. |
| **G4-P1-6** | Single-flight loser blocks on winner's future; should poll DB on bounded timeout | Current design: losers `await(future)`. If winner hits Treasury retry budget (~6 s including jitter), losers block 6 s too. Better: losers block 200 ms then re-check DB for the persisted result and return what they find (which may be the winner's freshly-persisted row, or a `503` if winner is still mid-flight). | Update `SingleFlightGate` contract: bounded wait + check-DB-on-timeout. Component-design.md §3.2 updated in-place. |
| **G4-P1-7** | AC-026b's "previously-served responses not retroactively mutated" is not testable through the API alone | The API regenerates the response on every conversion request; if the rate revision lands between requests A and B, B sees the new value. The AC's intent is about persistence (don't mutate the original row), not response idempotency. | Re-word AC-026b to be persistence-centric. Add an explicit integration test on `ExchangeRateRepositoryAdapter` that asserts row-count after a revision (1 → 2 rows). Phase 4 pins the AC update. |
| **G4-P1-8** | Alias-table-drift dual case not covered | AC-021b covers "alias-known input, Treasury rejects." The reverse — alias-known input resolves to canonical X but Treasury *accepts* and returns rates under canonical X' (renamed descriptor) — is not covered. | Add **AC-021c**: alias-known input resolves to canonical X; Treasury returns rates with canonical X'; persistence is under X' (Treasury's truth); response carries Treasury's X' as `targetCurrency` and emits `currency_alias_drift_detected`. Phase 4 pins. |
| **G4-P1-9** | Per-class mutation-test threshold absent | Pitest ≥ 70 % is a package average. A high-coverage simple class can hide a critical low-coverage class. | Add `RateSelectionPolicy` and `Money` to a class-level minimum (≥ 85 %) in NFR-021. Phase 5 pins the catalog. |
| **G4-P1-10** | OpenAPI 3.1 generator surface for dual-mode currency input | springdoc-openapi will document `currency` as a `string` with examples; some clients (notably Java client-codegen) prefer enums. The architecture chose examples not enum to preserve catalogue drift tolerance, but Phase-13 must verify the generated OAS is consumer-usable. | Mark Phase-13 as the verification point; Phase-13 plan adds an `OpenApiSchemaIT` that asserts the dual-mode shape. |

### Security (Security reviewer lens)

| ID | Title | Observation | Required action |
|---|---|---|---|
| **G4-P1-11** | HMAC log-hash key in env var is reachable by host-root | Acceptable v1 (platform secrets); not acceptable in a Tier-1 PCI deployment without a sidecar/mounted-secret pattern. | Phase 7 PCI design picks the production retrieval pattern (Vault Agent, mounted file, CSI driver). v1 documents the residual. |
| **G4-P1-12** | TLS 1.2 floor is too lax for 2026 | PCI DSS 4.0.1 keeps TLS 1.2 as minimum but is deprecating; mainstream guidance favours TLS 1.3 with TLS 1.2 fallback for client compatibility. | NFR-011/012 updated to "TLS 1.3 preferred; TLS 1.2 minimum." Phase 4 pins. |
| **G4-P1-13** | `X-Correlation-Id` is fully client-controlled | A malicious / mis-configured client can re-use a correlation id to confuse log search and create fake correlations. | Service binds a server-side prefix to the client value: `<svcInstance>-<clientValueOrNew>`. Echo the bound value in the response header. Phase 4 pins observability.md. |
| **G4-P1-14** | PostgreSQL connection URL with password is a leak vector | If `WEX_DB_URL` (which would conventionally contain `user:pass@host`) is echoed in any debug dump, the password leaks. | Split into `WEX_DB_HOST`, `WEX_DB_PORT`, `WEX_DB_NAME`, `WEX_DB_USERNAME`, `WEX_DB_PASSWORD` env vars. Deployment-architecture.md §5 updated in-place. |
| **G4-P1-15** | No app-layer rate limiting; relies entirely on gateway | A mis-deployed instance (no gateway) is open. Defense-in-depth: a small token bucket in the service is cheap. | Phase 7 to decide whether to add a Resilience4j RateLimiter at the controller layer. Phase 4 records the question. |
| **G4-P1-16** | Audit-log destination unnamed; tamper-evident mechanism unspecified | Phase 3 says "external sink, append-only / signed / WORM"; doesn't pick. | Phase 7 picks one (S3 with object-lock, CloudWatch Logs with replay disabled, or a managed audit-log service). v1 case-study writes to stdout; tampering risk is local-only. |

### SRE / operations (SRE lens)

| ID | Title | Observation | Required action |
|---|---|---|---|
| **G4-P1-17** | Circuit-breaker calibration (50 % over 20 calls, 30 s open) is plausibly too sensitive for a quarterly-publish dependency | Treasury could be down for hours; current calibration jitters between open/half-open every 30 s. | Phase 5 ratifies against capacity-plan data. Working: longer windows (e.g., 50 % over 100 calls, 5 min open) when single-flight is in play. Phase 4 records the question. |
| **G4-P1-18** | `Retry-After: 30` default may amplify Treasury outage | Hour-long outage × 30 s retry = 120 retries/hour per client. | Set default to 300 s; tune to CB-open-window. Phase 4 pins api-contracts.md §7 and observability.md. |
| **G4-P1-19** | DB-pool exhaustion isn't a readiness signal | DB reachable + pool at limit = requests block. Readiness should consider pool headroom. | Readiness probe checks `pool.activeConnections < pool.maxConnections - 1` for ≥ 5 s. Component-design.md §3.4 and observability.md updated in-place. |
| **G4-P1-20** | Single-flight gate holds locks across SIGTERM | A Treasury fetch mid-retry at SIGTERM blocks losers and shutdown drain. | Single-flight gate releases all locks on shutdown signal; losers fail-fast with `503` (still correct: their Treasury fetch wouldn't have completed either). Component-design.md §3.2 pinned. |
| **G4-P1-21** | No deploy-time warm-up automation | Cold-start cliff on new replicas until manual / Phase-5 warm-up job runs. | Define the warm-up job in operations/observability.md and operations/runbook.md: on container start, after readiness UP, fetch top-N currencies for the latest quarter-end record date. Phase 5 ratifies N. |

### Test engineering (QA lens)

| ID | Title | Observation | Required action |
|---|---|---|---|
| **G4-P1-22** | AC-T-3 fixture set incomplete | "Rate with zero decimals" is the only edge. Need: zero rate (rejected), negative rate (rejected), null rate (schema fail), trailing zero, leading zero (`0.085`), very-precise rate (≥ 6 fractional digits), Treasury 200 with `null` exchange_rate (schema fail). | Phase 4 pins AC-T-3 expansion in acceptance-criteria.md. |
| **G4-P1-23** | No documented test for graceful shutdown | Single-flight lock release, in-flight HTTP drain, readiness-DOWN-at-SIGTERM are runtime behaviours. | Add `GracefulShutdownIT` to the test plan. Phase 13 implementation. |
| **G4-P1-24** | Idempotency-Key tests undocumented | OQ-009 is P1 for v1, BLOCKING for prod. When it lands, what does the test set look like? | Phase 4 documents the test set in component-design.md §8 even though the implementation is deferred. |

### Auditor (QSA / change-control lens)

| ID | Title | Observation | Required action |
|---|---|---|---|
| **G4-P1-25** | Capacity anchors (100 req/s, 95 % cache-hit) are unverified | Phase-3 deployment-architecture.md §11 records them as anchors; without measurement they are an assertion. | Phase 5 load-test plan ratifies or rejects. Same as OQ-014; Phase 4 escalates to "must close in Phase 6 (Reliability/Scalability Grill)." |
| **G4-P1-26** | Vulnerability-management cadence + SLA undocumented | NFR-011/014 require SAST + dependency scan in CI; nothing says "patch HIGH within 7 days, CRITICAL within 24 h." | Phase 7 produces `docs/security/vulnerability-management-pci.md` with the cadence and SLA. |
| **G4-P1-27** | `RateOrientationContractCheck` is WARN-only at v1; threshold to fail-closed unspecified | Phase-3 says "Phase 5 promotes to fail-closed"; needs an explicit signal. | Phase 5 picks the threshold (e.g., "if WARN fires ≥ 3 times in a 24 h window, alert; if drift > 5 % on a quarterly canary, fail closed"). |

---

## 3. P2 / NICE findings

| ID | Title | Recommendation |
|---|---|---|
| **G4-P2-1** | Temurin vs Distroless re-attack | Phase 7 / 8 (PCI grill) re-attacks once the dependency-CVE surface is fully understood. |
| **G4-P2-2** | CORS necessity | Default no-CORS stays. Re-open only if a browser client is identified. |
| **G4-P2-3** | Multi-region escalation trigger | OQ-012 stays deferred; trigger = "real RTO requirement < 30 min or multi-region read locality required." |
| **G4-P2-4** | PII length side-channel via `description.length` | Document residual; not material at expected volume. |
| **G4-P2-5** | Connection-pool sizing math in deployment-architecture.md | Add the math (10 conns × 100 req/s × p99 80 ms ≈ saturation at 125 req/s; safe headroom). Phase 5. |
| **G4-P2-6** | Heap-saturation alert threshold | Phase 5 picks (e.g., heap > 80 % for 5 min). |
| **G4-P2-7** | Per-test SAST/scan cadence | Phase 7 documents (e.g., dependency-scan on every PR; SAST nightly; image scan on every image build). |
| **G4-P2-8** | PMD/Checkstyle/Spotless ruleset for logging hygiene | Phase 13 task; ruleset documented in Phase 7's secure-SDLC doc. |
| **G4-P2-9** | Treasury revision-rate empirical baseline | Phase 5 issues a 10-year-window Treasury fetch and records the observed revision frequency. |
| **G4-P2-10** | Cache invalidation on Treasury revision within TTL | Treasury revision happens → DB upsert lands → hot cache still serves old rate until TTL expires (24 h). Document; or invalidate the affected hot-cache entry on upsert. Phase 5 ratifies the trade-off. |

---

## 4. Per-lens summary

### Principal engineer lens
Five findings concentrated on internal correctness: single-flight key, cache key, scale normalisation, transaction boundaries, ArchUnit limits. The architecture's shape holds; the per-component contracts need tightening. Most fixes are local.

### Security lens
PCI defense-in-depth bypass (G4-P0-5) is the headline. Beyond it: HMAC key sourcing, TLS floor, correlation-id provenance, DB credentials shape, audit destination, rate limiting. Phase 7 inherits most of these; Phase 4 pins direction.

### SRE lens
Operability gaps: cache-hit ratio target unreachable (G4-P0-2), circuit-breaker calibration, retry-after default, pool-exhaustion readiness signal, single-flight lock release at shutdown, deploy-time warm-up. Phase 5 (operational design) is where these crystallise into thresholds and runbook entries.

### Test engineering lens
The Phase-2 grill added 12 ACs; Phase 4 finds three more (AC-010d, AC-021c, AC-027e) plus widens AC-T-3 fixtures. No mutation-test discipline at the class level. Graceful-shutdown and idempotency tests need documented placeholders.

### Auditor lens
Capacity anchors are unverified (P1, but a P1 with implementation-readiness teeth). Vulnerability-management SLA missing. RateOrientationContractCheck enforcement threshold unspecified. Audit-log destination unnamed. None of these block Phase 5 entry; all block Phase 11 (PCI readiness gate) entry.

---

## 5. New / updated acceptance criteria from this grill

These are pinned into [acceptance-criteria.md](../requirements/acceptance-criteria.md) and [traceability-matrix.md](../requirements/traceability-matrix.md) as part of Phase 4.

| AC | Purpose | Closes |
|---|---|---|
| **AC-010d** | `description` containing base64- / hex- / URL-encoded Luhn-valid PAN sequence → `400 PAN_PATTERN_DETECTED` with `details.reason="luhn-encoded"`. | G4-P0-5 |
| **AC-021c** | Alias-known input resolves to canonical X; Treasury returns rates under canonical X' (renamed/drifted descriptor); persistence is under X'; response carries X' as `targetCurrency`; emits `currency_alias_drift_detected`. | G4-P1-8 |
| **AC-027e** | Two simultaneous conversions for purchases with `transactionDate` values that map to the same Treasury quarter (e.g., 2026-04-01 and 2026-06-30 both target Q2-2026) dedupe to **at most one** Treasury upstream call. | G4-P0-1 |
| **AC-026b** (refined) | Phrasing rewritten to be persistence-centric ("after a Treasury revision lands, the original row is preserved and the row count for `(currency, record_date)` is 2"). | G4-P1-7 |

AC-T-3 fixture set widened to include: zero rate (rejected), negative rate (rejected), null rate (schema fail), trailing zero (`148.0` → `148.000000` on read), leading zero (`0.085`), high-precision (≥ 6 fractional digits).

---

## 6. Pinned corrections to Phase-3 docs (in-place edits in this phase)

These are the unambiguous fixes the P0 findings produce. The Phase-3 docs are edited in place; Phase-3 history is preserved because ADR-0001 records both the original decision and the Phase-4 refinement as a follow-on dated note.

| Doc | Pin | Reason |
|---|---|---|
| [ADR-0001](../architecture/adr-0001-core-architecture.md) D-2 | Add `com.github.f4b6a3:uuid-creator:5.x` to the ratified stack with rationale. | G4-P0-4 |
| ADR-0001 D-9 | Re-key `SingleFlightGate` to `(country_currency_desc, treasury_quarter_end)`. | G4-P0-1 |
| ADR-0001 D-10 | Re-key `ExchangeRateHotCache` to `(country_currency_desc, record_date)`. | G4-P0-2 |
| ADR-0001 D-4 + D-10 | Pin scale-6 normalisation as the contract for `exchange_rate`. | G4-P0-3 |
| ADR-0001 D-13 | Re-state guards as detection-and-alert with encoded-input pre-pass; full design in Phase 7. | G4-P0-5 |
| [component-design.md](../architecture/component-design.md) §1 / §3.2 | Reflect new cache and gate keys; document single-flight loser-polls-DB pattern; document gate-release-on-shutdown. | G4-P0-1 / G4-P0-2 / G4-P1-6 / G4-P1-20 |
| [data-model.md](../architecture/data-model.md) §2 / §4 | Drop surrogate `id` on `exchange_rates`; promote `(country_currency_desc, record_date, effective_date)` to PK; pin scale-6 normalisation. Raise `chk_rate_sanity` upper bound to 10³⁰; soften `chk_effective_ge_record` to `effective_date IS NOT NULL`. | G4-P1-3 / G4-P1-4 / G4-P0-3 / G4-P1-2 |
| [api-contracts.md](../architecture/api-contracts.md) §1 / §5 / §6.2 / §7 | Pin scale-6 normalisation; revise the example `exchangeRate` field; update `Retry-After` default to 300 s. | G4-P0-3 / G4-P1-18 |
| [deployment-architecture.md](../architecture/deployment-architecture.md) §5 | Replace `WEX_DB_URL` env var with `WEX_DB_HOST` / `WEX_DB_PORT` / `WEX_DB_NAME` / `WEX_DB_USERNAME` / `WEX_DB_PASSWORD`; tighten TLS line to "TLS 1.3 preferred; TLS 1.2 minimum." | G4-P1-14 / G4-P1-12 |
| [non-functional-requirements.md](../requirements/non-functional-requirements.md) NFR-011 / NFR-012 | Same TLS update. | G4-P1-12 |
| [acceptance-criteria.md](../requirements/acceptance-criteria.md) | Add AC-010d, AC-021c, AC-027e; refine AC-026b wording; widen AC-T-3 fixture set. | G4-P0-5 / G4-P1-7 / G4-P1-8 / G4-P0-1 |
| [traceability-matrix.md](../requirements/traceability-matrix.md) | Map each G4-P*-* to FRs/NFRs/ACs/OQs/risks. | bookkeeping |

---

## 7. Open design questions (carried forward)

The grill resolves the Phase-3 design-session.md §10 forward-looking gaps as follows:

| Phase-3 gap | Grill resolution | Status |
|---|---|---|
| Single-flight design | Re-keyed (G4-P0-1); loser-polls-DB pattern (G4-P1-6); release-on-shutdown (G4-P1-20) | Pinned this phase |
| Versioned rate persistence edge cases | Surrogate id dropped (G4-P1-4); sanity bound raised (G4-P1-3); CHECK softened (G4-P1-2); AC-026b clarified (G4-P1-7) | Pinned this phase |
| Rate-orientation contract-check threshold | Deferred to Phase 5 with explicit signal (G4-P1-27) | Phase 5 |
| PCI defense in depth | Encoded-input pre-pass added (G4-P0-5 / AC-010d); full Phase 7 design | Pinned direction; Phase 7 |
| Cache invalidation on Treasury revision | Recorded as P2-10; Phase 5 ratifies trade-off | Phase 5 |
| Idempotency-absent client communication | AC-001b already covers; OpenAPI doc must surface (Phase 13) | Phase 13 |
| Capacity anchors | P1-25 escalation to Phase 6 grill | Phase 5/6 |
| Temurin vs Distroless | P2-1; Phase 7/8 | Deferred |
| CORS necessity | P2-2; default no-CORS stays | Deferred |
| Multi-region escalation trigger | P2-3; OQ-012 | Deferred |

---

## 8. Risks added / updated

| ID | Risk | L | I | Score | Status |
|---|---|---|---|---|---|
| **R-028** | Hot-cache key fragmentation reduces hit ratio to ~1/(distinct purchase dates), inflating Treasury upstream traffic. | 3 | 3 | 9 (Medium) | Mitigated by G4-P0-2 re-keying. |
| **R-029** | Single-flight key fragmentation defeats deduplication under realistic concurrency. | 3 | 3 | 9 (Medium) | Mitigated by G4-P0-1 re-keying. |
| **R-030** | UUID v7 unavailable in JDK 21 stdlib; implementer falls back to v4. | 2 | 2 | 4 (Low) | Mitigated by D-2 dependency declaration. |
| **R-031** | Base64- / encoded-PAN bypass of content guards. | 2 | 4 | 8 (Medium) | Mitigated by AC-010d (Phase 7 implements). |
| **R-032** | `exchange_rate` scale drift between storage and API response confuses contract assertions. | 3 | 2 | 6 (Low) | Mitigated by scale-6 normalisation pin (G4-P0-3). |

R-021 (rate orientation) residual unchanged. R-022/R-024/R-026/R-027 unchanged.

---

## 9. Exit-criteria checklist

| Criterion | Status |
|---|---|
| Five-lens adversarial review performed (principal engineer, security, SRE, QA, auditor). | ✅ |
| P0 findings each carry observation / evidence / risk / fix / owner / target gate. | ✅ |
| P1 findings each carry observation / required action / owner / target gate. | ✅ |
| Phase-3 design-session.md §10 forward-looking gaps each addressed. | ✅ |
| Acceptance-criteria additions (AC-010d, AC-021c, AC-027e; AC-026b refined; AC-T-3 widened) drafted. | ✅ |
| In-place pins to Phase-3 docs identified per §6. | ✅ (executed as part of this phase — see commit ledger) |
| Threat model, observability, rollback plan authored. | ✅ (this phase) |
| Verdict recorded. | ✅ |
| `.human-approvals/` untouched. | ✅ |
| Source-requirements.md untouched. | ✅ |
| No implementation files created. | ✅ |

## 10. Verdict

**CONDITIONAL PASS to Phase 5 (Operational Design Session).**

Conditions, all closed in this phase:

1. The five P0 in-place pins are landed (§6).
2. The four new / refined ACs (AC-010d, AC-021c, AC-027e; AC-026b refined) are added.
3. The threat model, observability spec, and rollback plan reflect the grill findings (§Phase 4 deliverables 2–4 of Gate 3).

Items deferred to later gates:

- Phase 5: circuit-breaker calibration, capacity-anchor ratification, cache-invalidation policy, warm-up job spec, RateOrientationContractCheck threshold.
- Phase 6 (Reliability & Scalability Grill): re-attack on capacity anchors + single-flight fairness under load.
- Phase 7 (PCI Security Design): HMAC key sourcing, encoded-PAN guard implementation, audit-log destination, rate limiting, vulnerability-management SLA, TLS posture finalization.
- Phase 13 (Implementation): graceful-shutdown test, idempotency-key test set, ArchUnit / PMD/Checkstyle ruleset realisation, OpenAPI consumer-usability verification.

Phase 4 hands over to Phase 5 with:
- 5 P0 design corrections pinned
- 20 P1 findings recorded, with ~10 pinned this phase and ~10 deferred to later gates
- 10 P2 / NICE findings recorded
- 4 new / refined acceptance criteria
- 5 new risks (R-028..R-032)
- 0 source-requirements.md edits
- 0 human-approval-marker creations
- 0 implementation files created or edited

Pause cadence: Phase 5 begins on explicit "proceed to Phase 5" approval.
