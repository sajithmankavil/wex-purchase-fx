# ADR-0001: Core Architecture

## Status

**Accepted** — 2026-05-17 (Phase 3, Architecture & Design Session)

This ADR records the foundational architectural decisions for the WEX Purchase Currency Conversion Service. It pulls forward the ratified stack (Day-1 D-6), the rate-orientation finding from the Phase-3 prototype, and the new design positions arising from the Phase-2 Requirements Grill (G-P0-2..G-P0-5, A-018, A-021).

## Context

The service stores USD purchase transactions and retrieves them converted to a target currency using the U.S. Treasury Reporting Rates of Exchange API, applying a 6-month rate-selection rule. The case study requires the service to run locally on a stock JDK 21 with no separately installed database, web server, or servlet container; the same codebase must be production-deployable behind an ingress with a PostgreSQL-compatible profile.

Key external context:

- The Treasury Fiscal Data API is the sole external dependency. It is anonymous-accessible, publishes quarterly, and (as empirically verified in [docs/planning/phase-3-prototype-log.md](../planning/phase-3-prototype-log.md)) expresses `exchange_rate` as "units of foreign currency per ONE U.S. dollar."
- PCI Tier-1 hygiene is in force (`security-profile.yml`) even though the service is **out-of-CDE**: no PAN, SAD, CVV/CVC, track data, PIN, or session tokens. The free-text `description` field is the only attack surface for accidental cardholder data and is governed by content guards.
- The case study is a single-developer assignment first; the architecture must remain credible as a production starting point.

## Decisions

The decisions below are accepted. Each is cited in subsequent architecture docs.

### D-1 — Application architecture: **modular monolith**

A single deployable, structured by clean architecture (ports/adapters) into seven packages under `com.example.purchaseconversion`:

```
api              ← controllers, request/response DTOs, problem-details handler
application      ← orchestration: PurchaseService, ConversionService (rate selection, single-flight)
domain           ← framework-free POJOs: Purchase, ExchangeRate, RateSelectionRule, Money
infrastructure   ← repositories (JPA), TreasuryClient (Resilience4j-wrapped), CurrencyAliasTable
config           ← Spring profiles, env binding, beans
observability    ← Micrometer/OTel adapters, log-hashing
exception        ← centralised exception → RFC 9457 mapping
```

`domain` has no Spring/JPA dependencies. `infrastructure` and `application` may depend on `domain`. ArchUnit rules enforce the layering.

### D-2 — Language and runtime stack (ratified Day-1; Phase-4 refinement: UUID v7 generator)

Java 21 · Spring Boot 3.x (Web, Validation, Data JPA, Actuator) · Maven (`./mvnw`) · H2 file-mode (local) / PostgreSQL (prod profile) · Flyway · Resilience4j · springdoc-openapi · Micrometer + OpenTelemetry · SLF4J/Logback (JSON) · JUnit 5 · AssertJ · Mockito · WireMock · Pitest (≥ 70 % mutation score on `domain` and `application`) · ArchUnit · jqwik · Caffeine (in-process cache) · **`com.github.f4b6a3:uuid-creator:5.x`** (UUID v7 generator; Apache 2.0). Container base: Eclipse Temurin JRE 21 (Distroless option recorded under §Options Considered).

**Phase-4 grill follow-on (2026-05-17 — G4-P0-4).** Java 21's `java.util.UUID.randomUUID()` returns v4. D-7 requires v7. The `uuid-creator` library is added to the ratified stack to provide the v7 generator. Alternatives recorded: `io.hypersistence:hypersistence-utils-hibernate-63` (broader Hibernate integrations, heavier dependency); custom ~50-LOC RFC 9562 implementation (recorded but not chosen).

### D-3 — Exchange-rate persistence: **versioned by `effective_date`**

The `exchange_rates` table is keyed by the triple `(country_currency_desc, record_date, effective_date)`. When Treasury republishes a record for an existing `(country_currency_desc, record_date)`, the new revision is persisted as an additional row, never as an in-place mutation. The eligible-rate query selects max(`effective_date`) for the eligible `record_date`. Previously-served HTTP responses are not retroactively re-computed. (Closes G-P0-4 / A-018 / AC-026b / OQ-021 / R-027.)

Empirical note from the Phase-3 prototype: across 24 records spanning 2024-06-30 to 2026-03-31, no `effective_date ≠ record_date` revisions were observed for CAD/EUR/JPY. The versioning is **defensive**, not load-bearing, and adds one column + one storage row per revision. The cost is negligible; the alternative (upsert-without-versioning) would produce silent rate drift on republish.

### D-4 — Rate orientation: **`convertedAmount = amountUsd × exchangeRate`** (multiplicative). Rate normalised to `DECIMAL(19,6)` in storage and response.

Empirically verified against three reference currencies over eight quarters. Treasury publishes `exchange_rate` as foreign-currency-units per USD. The case-study example (`123.45 × 1.3700 = 169.13`) is consistent with this convention. See [phase-3-prototype-log.md](../planning/phase-3-prototype-log.md) §Verdict. (Closes G-P0-1 / OQ-017 / R-021.)

A contract test (`TreasuryRateOrientationFixtureTest`) asserts the formula against a recorded fixture set spanning CAD, EUR, JPY at known quarter-ends. The test runs on every PR build; a daily or weekly canary against a live Treasury fetch is a Phase-5 follow-up (`docs/operations/monitoring-alerting.md`).

**Phase-4 grill follow-on (2026-05-17 — G4-P0-3).** Treasury publishes `exchange_rate` at variable decimal scale (1, 2, 3, 4 observed). The service **normalises to scale 6** on persistence and **surfaces scale 6** in the API response. The earlier wording "full Treasury precision" is dropped. Consequences: `"148.0"` → `"148.000000"` on the wire; `"1.37"` → `"1.370000"`; `"159.41"` → `"159.410000"`. Decimal arithmetic semantics are unchanged. Contract tests assert exact scale 6 regardless of upstream scale.

### D-5 — 6-month window: **calendar months + EOM clamp + inclusive both ends**

`transactionDate.minusMonths(6) ≤ record_date ≤ transactionDate`, where `minusMonths(6)` follows Java's `LocalDate` end-of-month clamp semantics. The window length therefore varies 178–187 days. AC-018b and AC-019b lock the clamp boundary. (Closes G-P0-2 / OQ-003.)

Acknowledged asymmetry: a purchase on `2026-08-31` admits rates from `2026-02-28` (184 days back); a purchase on `2026-05-15` admits rates from `2025-11-15` (182 days back). This is consistent with the source's word "months" and matches accounting convention; replacing it with a strict day count (183 / 180) would be a different rule and would change boundary outcomes.

### D-6 — Monetary rounding: **`RoundingMode.HALF_UP`** to scale 2

Applied only on the final `convertedAmount` after the multiplication; intermediate scale is preserved at ≥ 12. (A-004; closes OQ-006.)

Counter-argument recorded: `HALF_EVEN` (banker's rounding) avoids the systemic positive bias on ties and is the regulator-preferred mode in some EU/UK consumer-finance jurisdictions. `HALF_UP` is chosen because (a) the source says "rounded to the nearest cent," which is the lay reading of HALF_UP; (b) the project guideline (`AGENT_PROJECT_INSTRUCTIONS.md` §5) pins HALF_UP; (c) the bias is statistically immaterial at our expected volume. Should a regulator-bound deployment require HALF_EVEN, the change is a one-line constant in `Money` and a re-baselining of `RoundingPropertyTest`.

### D-7 — Identifier: **UUID v7**

Server-generated, opaque, globally unique, time-ordered. Time-ordering improves index locality on the PK; opacity prevents enumeration. ULID is a documented alternative (same time-ordering, Crockford-base32 encoding); UUID v7 is chosen because the Java ecosystem ships native generators (Java 21 `UUID`, third-party libraries like `uuid-creator`) and existing tooling treats UUIDs as a known shape. AC-013 (`MALFORMED_IDENTIFIER`) accepts either UUID or ULID syntactic shape; the v1 implementation issues UUID v7.

### D-8 — Currency input: **dual-mode with curated alias table**

Path parameter `currency` accepts either Treasury `country_currency_desc` (case-insensitive) or ISO 4217 three-letter codes. ISO codes resolve to exactly one canonical `country_currency_desc` via a source-controlled JSON alias file at `infrastructure/resources/currency-aliases.json`. Readiness check refuses UP if the alias table is absent or fails to parse. (A-001; OQ-016.)

Phase-3 prototype evidence (collateral finding P-1): the Eurozone descriptor is `Euro Zone-Euro` — note the **space**, not a hyphen. The alias table v1 must contain at minimum: USD, EUR (→ `Euro Zone-Euro`), GBP, JPY (→ `Japan-Yen`), CAD (→ `Canada-Dollar`), AUD, CHF, CNY (→ `China-Yuan Renminbi`), INR. Drift detection is a daily reconciliation job (`currency_alias.drift.detected.count` metric).

### D-9 — Treasury client: **Resilience4j-wrapped, single-flight, schema-validated**

The Treasury client is an interface in `application`, implemented in `infrastructure`. The implementation wraps an HTTP call with Resilience4j (timeout, retry with exponential backoff and jitter, circuit breaker, bulkhead), a JSON schema validator pinned to the Fiscal Data API's published shape, a sanity check on `exchange_rate` (> 0 and ≤ 10³⁰; widened from 10⁹ per Phase-4 grill G4-P1-3 to admit hyperinflation currencies), and a single-flight gate so concurrent cache misses fan in to a single upstream call (AC-027b/d/e).

The User-Agent header is configured to `wex-purchase-fx/<version> (contact:<email>)` per Treasury best practice (grill G-P2-5).

**Phase-4 grill follow-on (2026-05-17 — G4-P0-1 / G4-P1-6 / G4-P1-20).** The single-flight gate is keyed by **`(country_currency_desc, treasury_quarter_end)`**, where `treasury_quarter_end = ceil(transactionDate, quarter-end)`. Treasury publishes quarterly; this gate granularity matches the actual upstream-batch granularity and deduplicates concurrent first-fetches across purchases whose `transactionDate` values fall in the same quarter (AC-027e). The earlier wording "per-`(country_currency_desc, window)`" — where `window` was the per-purchase 6-month lookup window — produced too many gate keys to be useful.

Loser semantics: a request that finds the gate held does **not** block indefinitely on the winner's future. It waits a bounded interval (200 ms default), then re-checks the database for a freshly persisted eligible rate; if found, it returns success; if not, it returns the same outcome the winner would have produced. Shutdown semantics: on SIGTERM the gate releases all locks; in-flight losers fail-fast with `503 UPSTREAM_UNAVAILABLE` (consistent with the winner being aborted mid-fetch).

### D-10 — Cache strategy: **DB-primary + in-memory hot path, keyed by `(country_currency_desc, record_date)`**

The local `exchange_rates` table is the primary cache. A small in-memory cache (Caffeine) holds rate rows keyed by `(country_currency_desc, record_date)` for hot-path latency. **The cache is keyed on the rate's own identity (not the purchase-derived lookup window).** Lookup is: search the cache for entries whose `record_date` falls in the purchase's eligible window; pick max(`record_date`). Cache size at steady state is bounded by Treasury's catalogue (~ 200 currencies × ~ 8 visible quarter-ends ≈ 1 600 entries). The cache is **rates-only**; conversion results are never cached (AGENT §10). Cache TTL is **24 hours** by default; a 7-day production override is documented as the recommended setting given Treasury's quarterly publish cadence (collateral finding P-4). The change is a config knob, not a code change.

**Phase-4 grill follow-on (2026-05-17 — G4-P0-2).** The earlier wording keyed the cache on `(country_currency_desc, lookup_window)` where the window was per-purchase. That fragmented the cache to ~ `currencies × purchase-dates` entries and made the NFR-003 95 % cache-hit ratio unreachable. The corrected key (above) puts the cache in line with the data it caches.

**Cache invalidation on Treasury revision.** When `ExchangeRateRepositoryAdapter.upsertVersioned()` lands a new row that becomes the max-`effective_date` for an existing `(currency, record_date)` key, it invalidates the corresponding hot-cache entry; the next read repopulates from DB. Phase 5 ratifies the TTL-vs-explicit-invalidation trade-off in the operational design.

### D-11 — Errors: **RFC 9457 Problem Details with `application/problem+json`**

All error responses are `application/problem+json` (AC-T-5) with `type`, `title`, `status`, `detail`, `instance`, plus the project's `errorCode` and `details` extension members. The decision table in [api-contracts.md](api-contracts.md) §Error Decision Table pins the boundary between `CONVERSION_RATE_NOT_AVAILABLE` (terminal: rule applied) and `UPSTREAM_UNAVAILABLE` (inability: rule could not be applied). (Closes G-P0-3 / OQ-018.)

### D-12 — Observability: **JSON logs + Micrometer + OpenTelemetry**

Structured JSON logs (Logback) with `traceId`, `spanId`, `correlationId`, `event`, bounded `context`. The `description` field is **never** logged in plain text; only `description.length` and an HMAC-SHA-256 digest prefixed with the key version (`vN:<hex>`). The HMAC key is loaded from `WEX_LOG_HASH_KEY`; production refuses to start without it. (NFR-017; A-020; AC-032b; closes G-P1-4 / OQ-019 / R-023.)

Metrics: RED per endpoint; USE for thread pools and DB pool; cache hit/miss/eviction; outcome counters for conversion and validation (`purchase.create.validation_error.count{reason}`, including `pan_pattern`, `track_data`, `length`, etc.); Treasury client counters (`treasury.api.failure.count{reason}`); alias drift (`currency_alias.drift.detected.count`). Cardinality budget ≤ 5 000 active series; default RED counters do not carry the `currency` label.

Traces: W3C `traceparent` propagated to the Treasury client and DB span.

### D-13 — Security posture: **out-of-CDE, contract-first; guards perform detection-and-alert with encoded-input pre-pass**

The primary PCI control is the API contract's prohibition on payment data (A-017). Defense-in-depth is a **detection-and-alert** stack at the API boundary on the `description` field:

1. **Pre-pass: decode candidate encodings.** Decode the raw `description` through a small candidate-decoder pipeline: base64 (URL-safe and standard), hex, URL-encoded. Each successful decoding produces a candidate string.
2. **Apply guards to each candidate string** (raw + decoded candidates): PAN-Luhn (AC-010b), track-data shape (AC-010c).
3. **Reject** the payload with `400 PAN_PATTERN_DETECTED` and `details.reason ∈ {luhn, track1, track2, luhn-encoded}` on any hit. Emit `purchase_validation_failed{reason=…}` audit event; payload not logged.

Out-of-CDE evidence (Phase 7) records the broader attack surface (Unicode confusables, split PAN across future free-text fields, CVV-shaped digits) and the explicit residual acceptance for v1. v1 has no app-layer authentication; the service runs behind a trusted gateway in real deployments (A-007, OQ-010 BLOCKING-for-prod).

**Phase-4 grill follow-on (2026-05-17 — G4-P0-5 / AC-010d).** The encoded-input pre-pass is added by AC-010d. Full implementation in Phase 7 (PCI security design). The guards' status moves from "defense in depth" to "detection-and-alert" — they are not the primary control (A-017 still is) but they are now credible evidence for the QSA.

### D-14 — H2 data directory: **off cloud-sync drives by default**

Local H2 file path is `${WEX_DATA_DIR}/wex.mv.db` with default `${user.home}/.wex-purchase-fx/data`. Startup emits a WARN log if the resolved path is under a known cloud-sync prefix (`OneDrive`, `Dropbox`, `iCloud Drive`). (A-021; closes G-P1-1 / OQ-023 / R-022.)

## Options Considered

### Application architecture
| Option | Why considered | Why not chosen |
|---|---|---|
| **Modular monolith (chosen)** | Single deployable; satisfies "no external servlet container"; keeps domain logic cohesive for a single-team service. | — |
| Microservices (purchase-svc + conversion-svc) | Independent scaling; clean ownership boundaries. | Over-engineered for a single bounded context; doubles deployment surface; case-study explicitly forbids the operational complexity. |
| Serverless (Lambda + DynamoDB) | Zero infra. | Cannot satisfy "runnable without separately installed databases / web servers"; cold-start latency would burn the p99 budget; not Java 21-idiomatic. |

### Runtime stack
| Option | Why considered | Why not chosen |
|---|---|---|
| **Spring Boot 3.x + Maven (chosen)** | Mature, well-known to Java reviewers, embedded Tomcat, broad ecosystem. | — |
| Quarkus + Maven | Faster startup, smaller memory footprint, native-image option. | Smaller ecosystem; native-image build complicates the case-study local run; reviewer familiarity skews to Spring. |
| Micronaut + Gradle | Compile-time DI, fast startup. | Same friction as Quarkus; ratification of D-6 stack is final. |

### Persistence (local)
| Option | Why considered | Why not chosen |
|---|---|---|
| **H2 file-mode (chosen)** | Embedded; durable across restart; satisfies source's "no separate database"; PostgreSQL-compatible SQL subset. | — |
| H2 in-memory | Simplest; zero config. | Loses data across restart, violating AC-010. Reserved for unit/integration tests only. |
| SQLite | Embedded, durable. | JPA support is second-class; PostgreSQL portability path is weaker than H2's. |
| Embedded Postgres | Production parity. | Heavier; binary-bundling problems on Windows; defeats single-jar simplicity. |

### Exchange-rate persistence model
| Option | Why considered | Why not chosen |
|---|---|---|
| **Versioned `(currency, record_date, effective_date)` (chosen)** | Defensive against rare Treasury republish; preserves audit trail; query is `max(effective_date)`. | — |
| Upsert without versioning | Simpler schema, smaller storage. | Silent retroactive change of `convertedAmount` for any purchase whose conversion was served before the republish. Auditor finding. |
| Append-only event log (`rate_events`) + projection | Maximally defensive. | Two tables to maintain, projection-lag failure mode added. Versioned-row approach wins on cost/benefit. |

### Rate orientation (decided by prototype, not by option choice)
The prototype validated multiplicative directly; no alternative was credible after the data came in.

### Rounding mode
| Option | Why considered | Why not chosen |
|---|---|---|
| **`HALF_UP` (chosen)** | Lay reading of "rounded to the nearest cent"; project guideline pin. | — |
| `HALF_EVEN` | Banker's rounding; no systemic positive bias; regulator default in some EU/UK consumer-finance contexts. | Differs from project guideline; ties are statistically rare at our volume; would need re-baselining. Documented as a trivial future change. |
| `HALF_DOWN`, `DOWN`, `UP`, `FLOOR`, `CEILING` | — | None of these are reasonable for monetary conversion. |

### Identifier format
| Option | Why considered | Why not chosen |
|---|---|---|
| **UUID v7 (chosen)** | Time-ordered, opaque, native to Java tooling, k-sortable for index locality. | — |
| ULID | Same time-ordering; Crockford-base32 is human-readable; shorter on wire. | Less Java tooling; the readability advantage is marginal for opaque ids. Documented alternative. |
| Auto-increment integer | Simple. | Enumerable; not globally unique across replicas; not production-ready. |
| Random UUID v4 | Globally unique, opaque. | Random PK kills index locality; v7 dominates strictly. |

### Cache TTL
| Option | Why considered | Why not chosen |
|---|---|---|
| **24 h (chosen for v1)** | Aggressive freshness; cheap correctness margin. | — |
| 7 days | Aligned to Treasury's quarterly publish cadence; lower upstream traffic. | Less safe against an unannounced corrective publish. Documented as production override. |
| No TTL (rates immutable once persisted) | Closest to Treasury's semantic. | Loses the periodic refresh-from-source check; the daily reconciliation job already handles drift, so this is salvageable, but v1 conservatism wins. |

## Consequences

### Positive

- Single deployable, single command to run locally; satisfies the explicit "no external DB / web server / servlet container" constraint.
- Domain layer is framework-free and testable in pure JUnit; ArchUnit guards prevent layer leakage.
- Treasury orientation is empirically pinned, not assumed; the silent 1/x bug class is closed.
- Versioned rate persistence makes the rare-but-real Treasury republish a non-incident.
- RFC 9457 + `application/problem+json` gives clients machine-readable error codes without bespoke envelope design.
- HMAC-hashed `description` logging keeps PCI scope reduction credible without losing log correlation.

### Negative / Risks

- **Stack opinionation.** Choosing Spring Boot + Maven excludes reviewers who'd score higher on Quarkus or Gradle. Acceptable: D-2 is ratified.
- **HALF_UP bias.** Statistically immaterial at expected volume; if a future regulator-bound deployment requires HALF_EVEN, the change is mechanical but does affect baseline tests.
- **24 h cache TTL is aggressive.** Adds Treasury traffic that the quarterly publish cadence does not require. Mitigated by single-flight + DB-primary cache; production override to 7 d is documented.
- **No app-layer auth in v1.** Acceptable for case study; OQ-010 keeps it BLOCKING for any production deployment.
- **Versioned rate rows accumulate.** At Treasury's observed revision rate (zero in 24 records over two years), storage growth is negligible. Mitigated.

### Mitigations

| Risk | Mitigation |
|---|---|
| Floating-point money error | ArchUnit forbids `double`/`float` in `domain` / `application`; jqwik property tests on `Money`. |
| Treasury orientation flips for a future currency | Contract test asserts multiplicative formula against fixture set; weekly canary against live Treasury (Phase 5). |
| Cache TTL too aggressive | Single-flight gate + DB-primary persistence; production override to 7 d. |
| H2 file on OneDrive | `WEX_DATA_DIR` default off cloud-sync prefixes; startup WARN. |
| Versioned-row storage growth | Negligible at observed revision rate; periodic rate-cleanup job is a P3 follow-up. |

## Rollback / Migration Notes

- The chosen stack supports zero-downtime rollback at the container level (drop replicas one at a time, deploy previous image).
- The H2 → PostgreSQL migration is a profile flip; Flyway migrations target both engines with a shared dialect-agnostic subset (no H2-specific extensions in DDL).
- Versioned-rate persistence can be retroactively rolled back to upsert semantics only by dropping the `effective_date` column from the unique key and selecting one row per `(currency, record_date)` — a destructive migration. Rollback is not anticipated.
- Rounding mode change (HALF_UP → HALF_EVEN, or scale 2 → scale 3) requires re-baselining `RoundingPropertyTest` and a release note to API consumers.
- ADR-0001 itself is superseded by a new ADR; this document is never silently edited after acceptance.

## Linked artefacts

- Phase-2 grill: [docs/planning/requirements-grill.md](../planning/requirements-grill.md)
- Phase-3 prototype: [docs/planning/phase-3-prototype-log.md](../planning/phase-3-prototype-log.md)
- Day-1 ratifications: [docs/planning/day-1-ratifications.md](../planning/day-1-ratifications.md)
- Requirements analysis: [docs/requirements/requirements-analysis.md](../requirements/requirements-analysis.md)
- System context: [docs/architecture/system-context.md](system-context.md)
- Component design: [docs/architecture/component-design.md](component-design.md)
- Data model: [docs/architecture/data-model.md](data-model.md)
- API contracts: [docs/architecture/api-contracts.md](api-contracts.md)
- Deployment architecture: [docs/architecture/deployment-architecture.md](deployment-architecture.md)
