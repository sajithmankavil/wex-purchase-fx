# Design Session — Phase 3

> **Status:** COMPLETED — 2026-05-17
> **Verdict:** **READY FOR PHASE 4 — DESIGN GRILL** (conditional on the Phase-3 prototype's residual follow-ups being accepted).
>
> This is the Phase-3 meta document: it records the options the design considered, the trade-offs that produced the decisions, the rejected alternatives, the open design questions, and the exit-criteria checklist. The decisions themselves live in [adr-0001-core-architecture.md](../architecture/adr-0001-core-architecture.md). The architecture artefacts produced by this session are:
>
> - [docs/architecture/adr-0001-core-architecture.md](../architecture/adr-0001-core-architecture.md)
> - [docs/architecture/system-context.md](../architecture/system-context.md)
> - [docs/architecture/component-design.md](../architecture/component-design.md)
> - [docs/architecture/data-model.md](../architecture/data-model.md)
> - [docs/architecture/api-contracts.md](../architecture/api-contracts.md)
> - [docs/architecture/deployment-architecture.md](../architecture/deployment-architecture.md)
>
> Plus, from the prototype-first sequencing:
> - [docs/planning/phase-3-prototype-log.md](phase-3-prototype-log.md)
> - [docs/planning/day-1-ratifications.md](day-1-ratifications.md)

---

## 1. Design objective

Design a Java service that **stores** purchase transactions in USD and **retrieves** them converted to a target currency using the U.S. Treasury Reporting Rates of Exchange dataset, applying a 6-month rate-selection rule.

The design must satisfy three crossing constraints:

1. **Case-study runnability.** One command on a stock JDK 21 starts the service, with no separately installed databases, web servers, or servlet containers.
2. **Production credibility.** The same codebase must deploy behind a real ingress with a PostgreSQL-compatible profile, multi-replica HA, observability, and rollback.
3. **PCI Tier-1 hygiene, out-of-CDE.** The service does not store, process, or transmit cardholder data, and the architecture must make that claim defensible to an auditor.

## 2. Inputs

| Input | Status |
|---|---|
| `docs/requirements/source-requirements.md` (verbatim) | Closed; never edited. |
| Phase-1 requirements set (FR/NFR/AC/A/OQ/R + traceability) | Closed; reconciled with `AGENT_PROJECT_INSTRUCTIONS.md` on 2026-05-14. |
| Phase-2 requirements grill | Closed; 5 P0 + 11 P1 + 10 P2 findings; 12 new ACs; 6 new assumptions A-017..A-022; 6 new OQs OQ-018..OQ-023; 7 new risks R-021..R-027. |
| Day-1 ratifications (P0s + stack + sequencing) | Closed at end of Phase 2. |
| Phase-3 prototype (Treasury rate orientation) | **Closed during Phase 3.** Multiplicative formula confirmed; 24 records examined; 5 collateral findings recorded. |

## 3. Architecture options considered

### 3.1 Application architecture

| Option | Description | Pros | Cons | Decision |
|---|---|---|---|---|
| **Modular monolith** (chosen) | Single Spring Boot deployable structured by clean architecture into `api/application/domain/infrastructure/config/observability/exception`. | Satisfies single-jar constraint; clean layering; ArchUnit-enforceable; familiar to Java reviewers. | Couples deploy boundaries to the team boundary (only one team v1 — fine). | **Accepted (D-1).** |
| Microservices (purchase-svc + conversion-svc) | Separate services per use case. | Independent scaling and ownership boundaries. | Over-engineered for a single bounded context; doubles deployment surface; case-study constraint forbids the operational complexity. | Rejected. |
| Serverless (Lambda + DynamoDB) | Zero infra. | Operationally simple. | Cannot satisfy "runnable without external DB/web server"; cold-start would burn p99 budget; not Java 21-idiomatic. | Rejected. |

### 3.2 Runtime stack

| Option | Description | Decision |
|---|---|---|
| **Java 21 + Spring Boot 3.x + Maven** (chosen) | Mature, ubiquitous, embedded Tomcat. | **Accepted (D-2). Day-1 ratified.** |
| Quarkus + Maven | Faster startup, smaller footprint, native option. | Rejected. Smaller ecosystem; native-image complicates the case-study local run; reviewer familiarity favours Spring. |
| Micronaut + Gradle | Compile-time DI, fast startup. | Rejected. Same friction as Quarkus. |
| JDK 17 fallback | Earlier LTS; broader CVE history. | Rejected v1; revisit only if a Java 21 CVE creates urgent migration pressure (W-002). |

### 3.3 Persistence (local mode)

| Option | Description | Decision |
|---|---|---|
| **H2 file mode** (chosen) | Embedded, durable across restart, PostgreSQL-compatible SQL subset. | **Accepted (D-2).** |
| H2 in-memory | Simplest; no path config. | Rejected for `local` profile (loses data on restart, violating AC-010); reserved for `test`. |
| SQLite | Embedded, durable. | Rejected. JPA support second-class; weaker portability path to PostgreSQL. |
| Embedded Postgres (e.g., zonky.io test-Postgres) | Production parity. | Rejected. Heavier; binary-bundling friction on Windows; defeats single-jar simplicity. |

### 3.4 Persistence (production mode)

| Option | Decision |
|---|---|
| **PostgreSQL** (chosen) | **Accepted.** |
| MySQL / MariaDB | Rejected. PostgreSQL has stronger DECIMAL semantics for monetary work; team familiarity favours Postgres. |
| Managed cloud DB (RDS / CloudSQL / Aurora) | Acceptable wrapper; the schema is engine-agnostic. |

### 3.5 Exchange-rate persistence model

| Option | Description | Decision |
|---|---|---|
| **Versioned `(currency, record_date, effective_date)`** (chosen) | New revisions land as additional rows; queries select max `effective_date`. | **Accepted (D-3 / A-018).** Closes G-P0-4 / R-027. |
| Upsert without versioning | Simpler schema; smaller storage. | Rejected. Silent retroactive change of `convertedAmount` on Treasury republish; auditor finding. |
| Append-only event log + projection | Maximally defensive. | Rejected. Adds projection lag and two-table maintenance; versioning wins on cost/benefit. |

### 3.6 Rate orientation (decided by prototype, not by deliberation)

The Phase-3 prototype confirmed the multiplicative reading against three currencies over eight quarters. No alternative was credible after the data came in. **Decision recorded in D-4.** Closes G-P0-1 / OQ-017 / R-021.

### 3.7 6-month window semantics

| Option | Description | Decision |
|---|---|---|
| **Calendar months + EOM clamp + inclusive** (chosen) | Java `LocalDate.minusMonths(6)` semantics; window length varies 178–187 days. | **Accepted (D-5 / A-003).** Closes G-P0-2 / OQ-003. |
| 183 days | Symmetric and crisp. | Rejected. Source word is "months," not "days"; reviewer expectation favours the calendar reading. |
| 180 days (6 × 30) | Approximation. | Rejected. Same reason. |

### 3.8 Rounding mode

| Option | Decision |
|---|---|
| **HALF_UP** (chosen) | **Accepted (D-6 / A-004).** Closes OQ-006. Counter-argument (HALF_EVEN bank's rounding, regulator preference in EU/UK consumer-finance) recorded in ADR-0001 and `assumptions-and-open-questions.md`. |
| HALF_EVEN | Documented as a future change of trivial code surface but non-trivial baseline-test re-baselining. |

### 3.9 Identifier

| Option | Decision |
|---|---|
| **UUID v7** (chosen) | **Accepted (D-7 / A-005).** Time-ordered for index locality; opaque; native to Java tooling. |
| ULID | Documented alternative. Slightly more readable; less Java tooling. |
| Auto-increment integer | Rejected. Enumerable; not production-credible. |
| UUID v4 | Rejected. Random PK kills index locality vs. v7. |

### 3.10 Currency input format

| Option | Decision |
|---|---|
| **Dual-mode (Treasury descriptor + ISO 4217) via curated alias** (chosen) | **Accepted (D-8 / A-001).** OpenAPI uses examples, not enum. Alias table loaded at startup with readiness-fails-on-missing. Eurozone-descriptor space caveat baked in (collateral P-1). |
| Treasury descriptor only | Rejected. Client ergonomics; `Euro Zone-Euro` is non-obvious. |
| ISO 4217 only | Rejected. Lossy against Treasury catalogue (some descriptors don't have a canonical ISO mapping). |

### 3.11 Cache strategy

| Option | Decision |
|---|---|
| **DB-primary + in-memory hot path (Caffeine), single-flight per `(currency, window)`** (chosen) | **Accepted (D-10).** Conversion results not cached (per AGENT §10). |
| In-memory only | Rejected. Treasury outage + cache miss = `503` indefinitely. |
| DB-only (no in-memory) | Acceptable but pays a DB hop per conversion; Caffeine is cheap. |
| External cache (Redis) | Rejected v1. Adds an external dependency the case-study forbids; consider at scale. |

### 3.12 Cache TTL

| Option | Decision |
|---|---|
| **24 h (default v1)** (chosen) | Aggressive freshness; cheap correctness margin. |
| 7 d (recommended prod override) | Aligned to Treasury's quarterly cadence; lower upstream traffic; documented as a single config knob. |
| No TTL | Rejected v1; refresh-on-read remains valuable belt-and-suspenders against the daily reconciliation job (R-014). |

### 3.13 Error envelope

| Option | Decision |
|---|---|
| **RFC 9457 Problem Details with `errorCode` + `details` extensions, `application/problem+json`** (chosen) | **Accepted (D-11 / A-015).** |
| Custom envelope (`{ "error": …, "message": … }`) | Rejected. Reinvents the wheel; the project guideline pins RFC 9457. |
| RFC 7807 (predecessor) | Rejected. RFC 9457 supersedes; clients should target the current spec. |

### 3.14 Observability stack

| Option | Decision |
|---|---|
| **Micrometer + OpenTelemetry + SLF4J/Logback (JSON)** (chosen) | **Accepted (D-12 / D-2).** Standard Spring Boot 3.x telemetry; vendor-neutral. |
| Vendor-coupled (e.g., New Relic Agent, Datadog Agent) | Rejected v1. Adds a runtime dependency; OTel collector decouples sink choice from app. |
| StatsD / OpenCensus | Rejected. OTel is the consolidated successor. |

### 3.15 Security posture

| Option | Decision |
|---|---|
| **Out-of-CDE, contract-first (no payment data at all)** (chosen) | **Accepted (D-13 / A-017).** Closes G-P0-5. |
| Tokenisation-aware (accept tokens but never PAN) | Rejected v1. No token issuer in the architecture; adds complexity for no v1 benefit. |
| Full PCI CDE | Rejected. Source has no payment data; bringing the service into scope would be ceremony. |

### 3.16 H2 data directory default

| Option | Decision |
|---|---|
| **`${user.home}/.wex-purchase-fx/data`, with cloud-sync-prefix WARN** (chosen) | **Accepted (D-14 / A-021).** Closes G-P1-1 / R-022. |
| `./data/` (working-directory-relative) | Rejected. Catastrophic on a OneDrive-synced project root. |
| OS temp | Rejected. Loses durability across reboot. |

### 3.17 Container base image

| Option | Decision |
|---|---|
| **Eclipse Temurin JRE 21 (Jammy)** (chosen) | **Accepted (D-2 / deployment).** `jcmd`/`jstack` available; broadly known. |
| Distroless `gcr.io/distroless/java21-debian12` | Documented Option. Smaller surface; less diagnostics. Phase-4 grill may re-attack. |
| Alpine | Rejected. Musl libc complications with some Java native libs. |

## 4. Selected architecture (one-paragraph summary)

A **single-process Spring Boot 3.x application on JDK 21**, packaged as a runnable jar locally and a container image in production, structured by **clean architecture** with a framework-free `domain` layer enforced by ArchUnit. Persistence is **H2 file mode locally / PostgreSQL in production** behind a shared JPA layer; the `exchange_rates` table is **versioned by `effective_date`** so Treasury revisions never retroactively mutate prior conversions. The Treasury Fiscal Data API is the **sole external dependency**, consumed through a **Resilience4j-wrapped, schema-validated, sanity-checked, single-flight-gated client**. **Conversion arithmetic is multiplicative** (`amountUsd × exchangeRate`), empirically verified against the live Treasury dataset for three reference currencies. Errors follow **RFC 9457 with `application/problem+json`** and a fixed decision table separating the terminal "rule applied, no rate" answer from the inability "upstream unreachable" answer. The service is **out-of-CDE** by API-contract design, with PAN-pattern and track-data guards as defense in depth, and structured-log description-hashing under a versioned HMAC key.

## 5. Key design decisions

| Decision | Rationale | ADR link | Risk addressed |
|---|---|---|---|
| Modular monolith | Single deployable; satisfies the case-study constraint. | ADR-0001 D-1 | — |
| Java 21 + Spring Boot 3.x + Maven | Day-1 ratified stack. | ADR-0001 D-2 | — |
| Versioned rate persistence | Defensive against Treasury republish. | ADR-0001 D-3 | R-027 |
| Multiplicative formula (verified) | Empirical Phase-3 prototype. | ADR-0001 D-4 | R-021 |
| 6-month window, calendar + EOM clamp + inclusive | Source semantics; AC-018b/AC-019b lock the boundary. | ADR-0001 D-5 | R-005 |
| HALF_UP scale 2 | Source lay reading; project guideline pin. | ADR-0001 D-6 | — |
| UUID v7 ids | Time-ordered, opaque, native to Java. | ADR-0001 D-7 | — |
| Dual-mode currency input with curated alias | Client ergonomics + Treasury fidelity. | ADR-0001 D-8 | R-014 |
| Resilience4j-wrapped, schema-validated, single-flight TreasuryClient | Bounded latency under failure; idempotent fetch. | ADR-0001 D-9 | R-001, R-002, R-009, R-015 |
| DB-primary + Caffeine hot cache; conversion results never cached | Avoids retroactive correctness drift on rate revision. | ADR-0001 D-10 | R-009 |
| RFC 9457 + `application/problem+json`; decision table for CONV vs UPSTREAM | Stable wire shape; closes G-P0-3. | ADR-0001 D-11 | R-026 |
| Micrometer + OTel + JSON logs; HMAC-hashed `description` with `vN:` key version | Production telemetry; PCI-aligned logging. | ADR-0001 D-12 | R-023 |
| Out-of-CDE, contract-first | Primary control = no payment data accepted. | ADR-0001 D-13 | R-007 |
| `WEX_DATA_DIR` default off cloud-sync prefixes | Avoids H2 corruption on OneDrive/etc. | ADR-0001 D-14 | R-022 |

## 6. Security design (Phase 3 view)

- **Authentication.** None at the application layer in v1 (A-007). Identity established at the upstream gateway in any non-case-study deployment (OQ-010 BLOCKING-for-prod).
- **Authorization.** None at the application layer. All authenticated requests at the gateway are equally privileged on the service surface.
- **Secrets management.** Env-only loading; refuse-to-start in `prod`/`staging` if `WEX_LOG_HASH_KEY` is unset; rotation procedure in `security/secrets-policy.md` (Phase 7).
- **Data protection.** TLS 1.2+ end-to-end. No PAN/SAD/CVV/track data/PIN/payment tokens. `description` is the only attack surface and is governed by PAN-Luhn + track-data guards at the API boundary (AC-010b, AC-010c), audit-logged with payload redaction, hashed in logs.
- **Audit logging.** Audit events emitted via structured JSON logs to a WORM / append-only sink; retention ≥ 1 yr, 3 mo online (NFR-016b). Detail in Phase 7 PCI design.
- **Abuse / fraud controls.** None at the service layer in v1; ingress gateway provides rate limiting and WAF.

Full Phase-7 PCI evidence chain: see `docs/security/`.

## 7. Operational design (Phase 3 view; refined in Phase 5)

- **Deployment model.** Single binary local; rolling deploy of N stateless replicas in production behind an internal LB.
- **Health checks.** Liveness (JVM), readiness (DB + alias-table + hot cache), aggregated `/actuator/health`.
- **Metrics.** RED per endpoint; USE for pools; cache hit/miss/eviction; conversion outcome counters; Treasury client counters; alias drift; rate-orientation contract drift. Cardinality budget ≤ 5 000 series. Detail in `operations/observability.md`.
- **Logs.** Structured JSON with `traceId`, `spanId`, `correlationId`, `event`, `context`; description never plain text.
- **Traces.** OpenTelemetry on inbound HTTP, DB, and Treasury client; W3C `traceparent` propagation.
- **Alerts.** SLO multi-burn-rate (Google SRE workbook), Treasury 5xx rate, Treasury circuit-open, p99 latency saturation, DB pool saturation, JVM heap saturation, container restart loop, rate-orientation contract drift (Phase 5 ratifies thresholds).
- **Dashboards.** Service health, dependency health (Treasury), business (conversions per minute per currency, CONV_RATE_NOT_AVAILABLE rate), rollout health.
- **Rollback.** Container rollback ≤ 5 min; Postgres PITR ≤ 30 min RTO; migration-by-forward-only with explicit revert migrations.
- **Disaster recovery.** Single-region v1; multi-region deferred to OQ-012 (Phase 5).

## 8. Failure modes (from `operations/failure-modes-and-resilience.md`)

| Failure mode | Detection | Mitigation | Test required |
|---|---|---|---|
| Treasury timeout | RestClient/Resilience4j timeout | Retry with backoff+jitter; CB; single-flight; local cache fallback | `TreasuryClientTimeoutIT` (slow WireMock) |
| Treasury 5xx | Status code | Retry budget; CB | `TreasuryClient5xxIT` |
| Treasury malformed payload | JSON schema validator | `502 UPSTREAM_BAD_RESPONSE`; do not persist | `UpstreamMalformedResponseIT` |
| Treasury rate outside sanity bounds | Sanity check | `502 UPSTREAM_BAD_RESPONSE`; do not persist; metric | `RateSanityRejectionTest` |
| Treasury revises a rate for an existing record_date | UNIQUE-key with versioning | Persist as new version row; query selects latest `effective_date` | `TreasuryRateRevisionVersioningIT` |
| Treasury rate orientation flips for a future currency | `RateOrientationContractCheck` WARN | Phase 5 ratifies fail-closed alerting; weekly canary | `OrientationContractFixtureTest` |
| Alias-table missing or parse-error | Readiness probe | Refuse to UP; restart container with corrected resource | `AliasTableStartupIT` |
| Alias-table drift (Treasury renames a descriptor) | Daily reconciliation job emits drift event | Alert; PR fixes the alias entry | `currency_alias.drift.detected.count` |
| Concurrent first-request thundering herd | Single-flight gate | Per-`(currency, window)` lock | `SingleFlightCacheConcurrencyTest` |
| Database unavailable | Readiness probe | Flip DOWN; LB drains | `DbUnavailableReadinessIT` |
| Duplicate POST without `Idempotency-Key` | Documented contract (AC-001b) | Documented as accepted v1; P0 for prod (OQ-009) | `IdempotencyAbsentSemanticsTest` |
| `description` contains PAN-like or track-data shape | API boundary guard | Reject `400 PAN_PATTERN_DETECTED`; audit | `PanPatternGuardTest`, `TrackDataGuardTest` |
| H2 file corruption on cloud-sync drive | Startup WARN; potential SQLException | `WEX_DATA_DIR` default off sync prefixes; documented runbook | `DataDirSyncPrefixWarningTest` |
| Logging HMAC key absent in prod | Bean factory refuses to start | Container restart loop; alert | `LoggingHashKeyStartupTest` |

## 9. Open design questions (carried into Phase 4)

| ID | Question | Owner | Impact | Status |
|---|---|---|---|---|
| OQ-002 | Tie-breaker for multiple Treasury records sharing the same `record_date` *and* `effective_date` — has not been observed empirically but is theoretically possible. | Architect | Phase 4 grill | Working: stable order `(record_date desc, effective_date desc, id asc)`. |
| OQ-004 | Canonical supported-currency universe — live Treasury catalogue vs. frozen subset. | Architect + API consumer | Phase 4 grill | Working: live; OpenAPI uses examples, not enum. |
| OQ-005 | "50 characters" semantics. | Architect | Phase 4 grill | Working: UTF-16 code units. |
| OQ-009 | `Idempotency-Key` support on POST. | Architect | Phase 4 grill | Working: P1 v1; BLOCKING for prod. |
| OQ-012 | Single-region v1 vs cross-region DR. | SRE + product owner | Phase 5 | Working: single region v1. |
| OQ-013 | Downstream WEX-system compatibility (CSV/event-bus). | Product owner | Phase 4 grill | Working: none v1. |
| OQ-014 | SLO target ratification (NFR-005/006). | SRE + product owner | Phase 5 + 6 | Working anchors; bounded by Treasury effective uptime per G-P1-2. |
| OQ-015 | Surface rate as rational (numerator/denominator). | Architect | Phase 4 grill | Working: decimal string at full Treasury precision. |
| OQ-016 | Alias-table ownership and drift detection. | Architect | Phase 4 grill | Working: source-controlled JSON; daily reconciliation. |
| OQ-018 | CONV-vs-UPSTREAM intermediate-case decision table. | Architect | Phase 4 grill | **Closed in Phase 3** by [api-contracts.md](../architecture/api-contracts.md) §6. |
| OQ-019 | Logging HMAC key origin/rotation. | SecArch | Phase 7 | Working answer in A-020. |
| OQ-020 | CORS policy. | Architect | Phase 4 grill | Working: default no CORS; env-var allow-list. |
| OQ-021 | Treasury same-`record_date` revision conflict. | Architect | Phase 4 grill | **Closed in Phase 3** by D-3 / A-018 (versioned persistence). |
| OQ-022 | Audit-log retention / integrity. | SecArch | Phase 7 | Working: ≥ 1 yr / 3 mo online; WORM in prod. |
| OQ-023 | H2 file path on cloud-sync drive. | Architect | Phase 4 grill | **Closed in Phase 3** by D-14 / A-021. |

OQ-001, OQ-003, OQ-017 were closed before Phase 3 (Phase-2 ratifications + Phase-3 prototype). OQ-006 was closed at Phase 1.

## 10. Forward-looking gaps (the Phase-4 design grill attacked these; status below as of 2026-05-17)

These are the points the Phase-3 design left plausible but not stress-tested, the Phase-4 grill challenged, and the resolution status now.

| Area | Specific challenge | Phase-4 resolution |
|---|---|---|
| **Single-flight design** | Fairness, loser timeout policy, persisted-result-on-retry. | **Closed.** G4-P0-1 re-keyed to `(country_currency_desc, treasury_quarter_end)`; G4-P1-6 added loser-polls-DB pattern (200 ms bounded wait); G4-P1-20 added gate-release-on-SIGTERM. Pinned in ADR-0001 D-9 and component-design.md §3.2 / §3.4. |
| **Versioned rate persistence** | Same-`record_date` revisions; surrogate `id` cost; CHECK strictness. | **Closed.** G4-P1-4 dropped surrogate `id` (composite PK is unique); G4-P1-2 softened `effective_date >= record_date` to NOT-NULL; G4-P1-3 widened sanity bound to 10³⁰. AC-026b refined to persistence-centric wording (G4-P1-7). |
| **Rate-orientation residual** | Contract check WARN-only; fail-closed threshold. | **Deferred to Phase 5** (G4-P1-27). Working: alert at ≥ 3 WARN/24 h; fail-closed at > 5 % drift on quarterly canary. |
| **PCI defense in depth** | Base64-encoded PAN; split-field; false-positive baseline; Unicode confusables. | **Partially closed.** Encoded-PAN pre-pass added by G4-P0-5 (AC-010d); guards promoted from defense-in-depth to detection-and-alert. Unicode confusables + split-field deferred to Phase 7 (PCI security design) + Phase 8 (PCI grill). |
| **Cache invalidation** | Treasury revision within TTL not picked up by hot cache. | **Closed.** ADR-0001 D-10 (Phase-4 pinned): `upsertVersioned()` invalidates the affected hot-cache entry; next read repopulates from DB. |
| **Idempotency-absent semantics** | Client informed? OpenAPI clear? | **Closed.** AC-001b documents the contract; FR-001 narrative expanded; OpenAPI consumer-usability verification deferred to Phase 13 (G4-P1-10). |
| **Capacity anchors** | 100 req/s × 95 % cache-hit — defensibility. | **Deferred to Phase 5** (capacity plan) and **Phase 6** (reliability grill). G4-P1-25 escalates from "anchor" to "must close in Phase 6." |
| **Container-image choice** | Temurin vs Distroless. | **Deferred to Phase 7/8** (PCI grill). G4-P2-1 records the re-attack agenda. |
| **CORS / browser clients** | Default no-CORS sufficient? | **Closed.** Default no-CORS stays (G4-P2-2); env-var allow-list available; re-open only if a browser client is identified. |
| **Multi-region** | OQ-012 deferred. Escalation trigger? | **Deferred to Phase 5** with explicit trigger: "real RTO < 30 min requirement OR multi-region read-locality requirement" (G4-P2-3). |

**Additional gaps surfaced by Phase 4 (not in the original §10):** five P0 corrections pinned in this gate (single-flight key, hot-cache key, scale-6 normalisation, UUID v7 dependency, encoded-PAN guard) and 20 P1 findings tracked in [design-grill.md §2](design-grill.md). Deferral acceptance recorded in [p1-deferrals-acceptance.md](p1-deferrals-acceptance.md).

## 11. Exit-criteria checklist for Phase 3

| Criterion | Status |
|---|---|
| Architecture docs created/updated (system-context, component-design, data-model, api-contracts, deployment-architecture). | ✅ |
| ADR-0001 records all major decisions with options considered, rationale, consequences, rollback notes. | ✅ |
| Data model reviewed; versioned rate persistence schema present; indexes and constraints listed. | ✅ |
| API contracts reviewed; RFC 9457 + `application/problem+json`; CONV-vs-UPSTREAM decision table present. | ✅ |
| Deployment architecture reviewed; case-study and production-reference both documented; rollback path described. | ✅ |
| Failure modes documented with detection / mitigation / test mapping. | ✅ |
| Phase-2 P0 findings resolved or pinned. | ✅ (G-P0-1..G-P0-5 all closed or pinned.) |
| Phase-3 prototype completed and findings recorded. | ✅ |
| Open design questions enumerated for Phase 4. | ✅ |
| Source-requirements.md untouched. | ✅ |
| `.human-approvals/` untouched. | ✅ |
| No implementation files created. | ✅ |
| Verdict recorded. | ✅ |

## 12. Verdict

**READY FOR PHASE 4 — DESIGN GRILL.**

Phase 3 has produced a complete architecture artefact set, ratified the stack, empirically closed the most dangerous P0 (rate orientation), and pinned the rate-versioning persistence policy. The Phase-4 grill should attack the forward-looking gaps in §10, with priority on the single-flight design, the versioned-rate edge cases, and the PCI defense-in-depth surface.

Phase 4 begins on explicit human "proceed to Phase 4" approval.
