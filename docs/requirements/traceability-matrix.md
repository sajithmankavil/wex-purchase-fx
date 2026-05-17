# Traceability Matrix

> Bidirectional traceability: every source clause → FR/NFR → AC → planned test artifact. Updated at the end of each phase. Test-artifact paths are forward-looking and become concrete in Phase 3 (component design) and Phase 13 (implementation).
>
> **Phase-2 (Requirements Grill) update, 2026-05-14:** new section §Grill findings → IDs maps each G-P*-* finding to the FRs, NFRs, ACs, OQs, and risks it touches. See `docs/planning/requirements-grill.md`.
>
> **Phase-4 (Design Grill) update, 2026-05-17:** new section §Design grill findings → IDs maps each G4-P*-* finding to the FRs, NFRs, ACs, OQs, and risks it touches. See `docs/planning/design-grill.md`.

## Source → FR/NFR

| Source clause | FR / NFR | Notes |
|---|---|---|
| Req #1 — store purchase with description, date, USD amount, server-assigned id | FR-001 | Validations enumerated in source map directly to FR-001 input rules |
| Req #1 — Description ≤ 50 chars | FR-001 / AC-002, AC-003 | Char-counting policy in A-013, OQ-005 |
| Req #1 — Transaction date must be valid date format | FR-001 / AC-005 | A-014 fixes UTC date semantics |
| Req #1 — Positive amount rounded to nearest cent | FR-001 / AC-007, AC-008 | Inbound: rejected if scale > 2 (AC-008); conversion output: rounding HALF_UP (A-004) |
| Req #1 — Unique identifier | FR-001 / A-005 | UUID v7 working assumption |
| Req #2 — retrieve converted to supported currency from Treasury API | FR-003 | Treasury dataset universe = currency support universe (OQ-004) |
| Req #2 — response fields: id, description, date, USD amount, exchange rate, converted amount | FR-003 / AC-014 | Added `exchangeRateEffectiveDate` and `targetCurrency` for unambiguous client interpretation |
| Req #2 — rate selection: ≤ purchase date, within last 6 months | FR-003 / AC-015..AC-019 | A-003 fixes calendar-month semantics |
| Req #2 — error if no rate in window | FR-003 / AC-020 | `422 CONVERSION_RATE_NOT_AVAILABLE` per RFC 9457 (Problem Details with `errorCode` extension) |
| Req #2 — converted amount rounded to 2 decimals | FR-003 / AC-024, AC-025 | HALF_UP per A-004 |
| Technical — Java language | NFR-028, NFR-029 | Java 21 (proposed, ratified in ADR-0001) |
| Technical — "as if for Production" | NFR-005..NFR-035 | Source's enabling clause for every NFR added |
| Technical — functional automated tests | NFR-021, NFR-022, NFR-023, AC-T-1..AC-T-4 | Performance tests explicitly excluded by source |
| Technical — no external DB/web server/servlet container | NFR-028, NFR-029, A-008 | H2 file mode + embedded Tomcat |
| (implicit) operability | FR-004, NFR-025, NFR-026, NFR-027 | Production-grade implies operability |
| (implicit) observability | FR-005, NFR-017..NFR-020, NFR-032 | Production-grade implies observability |
| (implicit) discoverability | FR-006, NFR-034 | OpenAPI |
| (project-decision) PCI scope reduction | NFR-015, NFR-016 | Phase 7/8 will produce the formal evidence |

## FR → Acceptance Criteria → planned tests

| FR | Acceptance criteria | Planned test artifacts |
|---|---|---|
| FR-001 | AC-001..AC-010 | `TransactionControllerCreateTest` (unit, MockMvc), `TransactionPersistenceIT` (integration, file-mode H2), `ValidationParametricTest` (boundary table-driven), `DurabilityRestartIT` |
| FR-002 | AC-011..AC-013 | `TransactionControllerGetTest`, `NotFoundIT`, `MalformedIdTest` |
| FR-001 (Phase-2 additions) | AC-001b, AC-010c | `IdempotencyAbsentSemanticsTest`, `TrackDataGuardTest` |
| FR-003 | AC-014..AC-027 | `ConversionServiceTest` (unit, mock Treasury), `RateSelectionUnitTest` (boundary), `ConversionEndpointIT` (WireMock Treasury), `TreasuryClientContractTest` (recorded fixtures), `RoundingPropertyTest` (jqwik property-based), `SingleFlightCacheConcurrencyTest`, `CircuitBreakerIT`, `UpstreamMalformedResponseIT` |
| FR-003 (Phase-2 additions) | AC-018b, AC-019b, AC-020b, AC-021b, AC-022b, AC-024b, AC-026b, AC-027d | `EomClampBoundaryTest`, `ConversionRateUnavailableDecisionTableIT`, `AliasDriftIT`, `RateSanityRejectionTest`, `TreasuryRateRevisionVersioningIT`, `ConcurrentConversionConsistencyIT` |
| FR-004 | AC-028..AC-030 | `ActuatorEndpointsIT` |
| FR-005 | AC-031..AC-034 | `LoggingPiiGuardTest`, `MetricsExposureIT`, `TraceContextPropagationIT` |
| FR-005 (Phase-2 additions) | AC-032b | `LoggingHashKeyTest` |
| FR-006 | AC-035..AC-036 | `OpenApiSchemaIT`, `SwaggerUiIT` |
| Cross-cutting | AC-T-5 | `ProblemDetailsContentTypeTest` |

## NFR → verification method

| NFR | Verification |
|---|---|
| NFR-001..004 (latency) | Not in CI (source excludes perf-test automation); documented load-test plan in `release/test-plan.md`; latency observed via Micrometer p99 histograms in non-CI smoke. |
| NFR-005..007 (availability/SLO) | SLO defined in `operations/slo-sli.md`; verified via burn-rate alerts in `operations/monitoring-alerting.md`. |
| NFR-008..010 (scalability) | Documented in `operations/capacity-scalability-plan.md`; load-test plan exists; not automated in CI. |
| NFR-011..014 (security hygiene) | `security/threat-model.md`; SAST + dependency scan in `.github/workflows/security.yml`; integration test asserting TLS verification not disabled in any profile. |
| NFR-015..016 (PCI scope reduction) | `security/pci-scope-and-cde.md` + `cardholder-data-flow.md`; CHD/PII guard unit test on `description` field. |
| NFR-017..020 (observability) | `LoggingPiiGuardTest`, `MetricsExposureIT`, `TraceContextPropagationIT`, dashboard JSON in `operations/monitoring-alerting.md`. |
| NFR-021..024 (test coverage) | JaCoCo gate ≥ 85% line / 75% branch; Pitest gate ≥ 70% on changed packages. |
| NFR-025..027 (operability) | `operations/runbook.md`; smoke `./mvnw verify` produces a green-checked runnable artifact in CI. |
| NFR-028..029 (portability) | `make smoke` test: fresh checkout → `./mvnw spring-boot:run` → service answers `/actuator/health/readiness`. |
| NFR-030..031 (data integrity) | `MoneyTypeTest` (asserts no `double`/`float` in domain via ArchUnit). |
| NFR-032..033 (maintainability) | Checkstyle/PMD/Spotless gates in CI. |
| NFR-034..035 (API governance) | OpenAPI lint gate (`spectral`) in CI; problem-details body shape integration test. |

## Open question → resolution gate

| OQ | Must be resolved before |
|---|---|
| OQ-001 | **CLOSED Phase 2** (ratified A-002 — reject future-dated) |
| OQ-002 | Phase 3 (design session, blocked until verified against live Treasury API) |
| OQ-003 | **CLOSED Phase 2** (ratified A-003 — calendar months + EOM clamp) |
| OQ-004 | Phase 3 (design session) |
| OQ-005 | Phase 3 (design session) |
| OQ-006 | **CLOSED Phase 1** (HALF_UP per A-004) |
| OQ-007 | Phase 9 (impl readiness) — currently out of scope |
| OQ-008 | Phase 5 (operational design session) |
| OQ-009 | Phase 3 (design session); BLOCKING for prod (G-P1-8) |
| OQ-010 | Phase 7 (PCI security design session); BLOCKING for prod deploy only |
| OQ-011 | Phase 7 (PCI security design session) |
| OQ-012 | Phase 5 (operational design session) |
| OQ-013 | Phase 3 (design session) |
| OQ-014 | Phase 5 (operational design session) + Phase 6 (reliability grill) |
| OQ-015 | Phase 3 (design session) |
| OQ-016 | Phase 3 (design session) |
| OQ-017 | **BLOCKING for Phase-3 exit** (G-P0-1) |
| OQ-018 | Phase 3 (api-contracts decision table) |
| OQ-019 | Phase 7 (secrets policy) |
| OQ-020 | Phase 3 (design session) |
| OQ-021 | Phase 3 (data-model) |
| OQ-022 | Phase 7 (PCI logging-monitoring) |
| OQ-023 | Phase 3 (deployment architecture) |

## Risk → mitigation owner

| Risk | Source | Phase to address | Owner |
|---|---|---|---|
| Treasury API availability / latency | External dependency | Phase 3 (CB, retry, cache) + Phase 5 (alerts) + Phase 6 (chaos plan) | Architect + SRE |
| Treasury rate semantics (which date field, ties) | OQ-002 | Phase 3 prototype | Architect |
| Floating-point money error | NFR-030 | Phase 3 (BigDecimal + ArchUnit guard) | Architect |
| Time-zone ambiguity at boundary | A-014 | Phase 3 (UTC policy + ADR) | Architect |
| Data loss on restart with in-memory DB | NFR-028, A-008 | Phase 3 (file-mode H2 default) | Architect |
| PII/CHD in free-text `description` | NFR-015, OQ-011 | Phase 7 (content guard policy) | SecArch |
| No authn for v1 vs deploy without auth | A-007, OQ-010 | Phase 7 (security design) | SecArch |
| Latency under cache miss | NFR-003 | Phase 5 (cache strategy + warmup) | SRE |
| Treasury schema change breaks integration | A-009, A-010, A-011 | Phase 3 (contract test) + Phase 5 (alert on contract drift) | Architect + SRE |
| SLO targets unproven | NFR-001..006 | Phase 5 (capacity plan) + Phase 6 (reliability grill) | SRE |
| Treasury rate orientation mis-applied (R-021) | OQ-017, G-P0-1 | Phase 3 (empirical verification) | Architect |
| H2 file corruption on cloud-sync drive (R-022) | A-021, G-P1-1 | Phase 3 (deployment architecture) | Architect / SRE |
| Logging HMAC key mis-managed (R-023) | A-020, G-P1-4 | Phase 7 (secrets-policy) | SecArch |
| Metric cardinality explosion (R-024) | NFR-018b, G-P1-7 | Phase 5 (operations design) | SRE |
| CORS misconfiguration (R-025) | NFR-014b, G-P1-5 | Phase 3 / 7 | Architect / SecArch |
| CONV-vs-UPSTREAM mis-routing (R-026) | FR-003 decision table, G-P0-3 | Phase 3 (api-contracts) | Architect |
| Treasury rate revision drift (R-027) | A-018, G-P0-4 | Phase 3 (data-model) | Architect |

## Grill findings → IDs (Phase 2)

| Grill finding | New / touched IDs | Phase to close |
|---|---|---|
| G-P0-1 Treasury rate orientation unverified | OQ-017 (escalated to BLOCKING-for-Phase-3-exit), R-021 (new), A-009/A-010/A-011 | Phase 3 (ADR-0001) |
| G-P0-2 6-month window EOM-clamp asymmetry | A-003 (ratified), AC-018b (new), AC-019b (new), OQ-003 (closed), R-005 | Phase 3 (ADR-0001) |
| G-P0-3 CONV-vs-UPSTREAM boundary under-specified | FR-003 decision table (new), AC-020b/021b/022b (new), OQ-018 (new), R-026 (new) | Phase 3 (api-contracts) |
| G-P0-4 Same-record_date Treasury conflict policy missing | A-018 (new), AC-026b (new), OQ-021 (new), R-027 (new) | Phase 3 (data-model) |
| G-P0-5 PCI control re-framing | A-017 (new), AC-010c (new), NFR-016 (expanded), OQ-011 | Phase 7 (PCI design) |
| G-P1-1 H2 on cloud-sync path | A-021 (new), NFR-028 (expanded), OQ-023 (new), R-022 (new) | Phase 3 (deployment) |
| G-P1-2 SLO bounded by Treasury uptime | NFR-005/006, OQ-014, R-011 | Phase 5/6 |
| G-P1-3 PAN false-positive metric | NFR-018 (expanded), AC-033 (assertion expanded conceptually), R-007 | Phase 5 (operations design) |
| G-P1-4 Logging HMAC key management | A-020 (new), NFR-017 (expanded), AC-032b (new), OQ-019 (new), R-023 (new) | Phase 7 (secrets) |
| G-P1-5 CORS missing | A-022 (new), NFR-014b (new), OQ-020 (new), R-025 (new) | Phase 3 |
| G-P1-6 Audit-log retention | NFR-016b (new), OQ-022 (new) | Phase 7 |
| G-P1-7 Metric cardinality budget | NFR-018b (new), R-024 (new) | Phase 5 |
| G-P1-8 Idempotency absence semantics | AC-001b (new), FR-001 (expanded), OQ-009 (escalated to BLOCKING-for-prod) | Phase 3 |
| G-P1-9 `application/problem+json` content type | AC-T-5 (new), NFR-035 | Phase 3 / 13 |
| G-P1-10 Treasury rate sanity bounds | A-019 (new), AC-024b (new) | Phase 3 |
| G-P1-11 Concurrent in-flight Treasury fetch consistency | AC-027d (new), R-015 | Phase 3 |
| G-P2-1..G-P2-10 | P2/NICE clarifications; tracked in grill §3 | Phase 3 |

## Design grill findings → IDs (Phase 4)

| Grill finding | Touched IDs | Resolution phase |
|---|---|---|
| G4-P0-1 Single-flight key too narrow | AC-027b (refined intent), AC-027e (new), R-029 (new), D-9 (in-place pin) | Pinned this phase; Phase 13 implements |
| G4-P0-2 Hot-cache key fragmentation | NFR-003 (cache-hit anchor), R-028 (new), D-10 (in-place pin) | Pinned this phase; Phase 13 implements |
| G4-P0-3 `exchange_rate` scale contradiction | AC-014 example refreshed, AC-T-3 widened, R-032 (new), D-4/D-10 (in-place pin), data-model.md / api-contracts.md pinned | Pinned this phase; Phase 13 implements |
| G4-P0-4 UUID v7 generator dependency | D-2 (in-place pin), R-030 (new) | Pinned this phase |
| G4-P0-5 Encoded-PAN bypass | AC-010d (new), R-031 (new), D-13 (in-place pin) | Phase 4 direction pinned; Phase 7 implements |
| G4-P1-1 ArchUnit limits for logging hygiene | NFR-018, component-design.md §6 | Phase 7/13 (PMD/Checkstyle) |
| G4-P1-2 `effective_date >= record_date` strictness | data-model.md (pinned) | Phase 5 empirical re-check |
| G4-P1-3 Sanity bound 10⁹ → 10³⁰ | A-019, AC-024b, data-model.md (pinned) | Pinned this phase |
| G4-P1-4 Surrogate id on `exchange_rates` | data-model.md (pinned) | Pinned this phase |
| G4-P1-5 `@Transactional` boundaries | component-design.md §3.2 | Phase 13 |
| G4-P1-6 Single-flight loser semantics | component-design.md §3.2 (pinned) | Pinned this phase; Phase 13 implements |
| G4-P1-7 AC-026b wording refined | AC-026b (refined) | Pinned this phase |
| G4-P1-8 Alias-table drift reverse case | AC-021c (new), OQ-016 | Pinned this phase; Phase 13 implements |
| G4-P1-9 Per-class mutation threshold | NFR-021 | Phase 5 |
| G4-P1-10 OpenAPI consumer-usability for dual-mode currency | AC-035, AC-036 | Phase 13 (OpenApiSchemaIT) |
| G4-P1-11 HMAC key sourcing in prod | A-020, NFR-017 | Phase 7 |
| G4-P1-12 TLS 1.3 preferred | NFR-011, NFR-012 (pinned) | Pinned this phase |
| G4-P1-13 Server-side correlation-id binding | NFR-017, observability.md | Pinned this phase (observability.md) |
| G4-P1-14 DB env split | deployment-architecture.md §5 (pinned) | Pinned this phase |
| G4-P1-15 App-layer rate limiting | OQ-010, security/threat-model.md | Phase 7 |
| G4-P1-16 Audit-log destination + tamper-evidence | NFR-016b, OQ-022 | Phase 7 |
| G4-P1-17 Circuit-breaker calibration | NFR-005/006, OQ-014 | Phase 5/6 |
| G4-P1-18 Retry-After default 300 s | api-contracts.md §7 (pinned), observability.md | Pinned this phase |
| G4-P1-19 DB-pool exhaustion in readiness | component-design.md §3.4 (pinned), observability.md | Pinned this phase |
| G4-P1-20 Single-flight gate release on SIGTERM | D-9, component-design.md §3.4 (pinned) | Pinned this phase; Phase 13 implements |
| G4-P1-21 Deploy-time warm-up automation | operations/observability.md, operations/runbook.md | Phase 5 |
| G4-P1-22 AC-T-3 fixture set widening | AC-T-3 (pinned) | Pinned this phase |
| G4-P1-23 Graceful-shutdown test | component-design.md §8 | Phase 13 |
| G4-P1-24 Idempotency-Key test set | component-design.md §8 | Phase 13 |
| G4-P1-25 Capacity anchors verification | NFR-005..008, OQ-014 | Phase 5/6 |
| G4-P1-26 Vulnerability-management SLA | NFR-011/014, security/vulnerability-management-pci.md | Phase 7 |
| G4-P1-27 `RateOrientationContractCheck` threshold | operations/monitoring-alerting.md | Phase 5 |
| G4-P2-1..G4-P2-10 | P2/NICE clarifications; tracked in design-grill §3 | Mixed |

## Phase-4 hardening pass additions (2026-05-17 — H1..H10)

| ID | Action | Touched IDs | Status |
|---|---|---|---|
| H1 | Loosen hooks for governance-file edits | — | HELD (user requested per-edit confirmation; pending) |
| H2 | Apply seven bundle/overlay edits | AGENT §6/§7/§8/§11/§12, CLAUDE §7/§10 | HELD (depends on H1) |
| H3 | P1 deferrals acceptance | [p1-deferrals-acceptance.md](../planning/p1-deferrals-acceptance.md); G-P1-* / G4-P1-* | ✅ |
| H4a | OQ-002 closed | OQ-002 (now RESOLVED Phase 4) | ✅ |
| H4b | design-session.md §10 refreshed | Phase-3 forward-looking gaps table | ✅ |
| H5 | Day-2 ratifications (F1–F5) | day-1-ratifications.md; AC-008, A-002, A-013, D-4, OQ-009 | ✅ |
| H6 | New threat-model entries | TM-T-008, TM-T-009, TM-D-008, TM-I-009, AB-014 | ✅ |
| H7 | NFR-013b addendum | NFR-013b (HMAC key ≥ 256 bits) | ✅ |
| H8 | Phase-7 carry-forwards | threat-model.md §8 (SBOM, SAST, image signing) | ✅ |
| H9 | Residuals refresh | threat-model.md §7 | ✅ |
| H10 | This row | All Phase-4 hardening IDs | ✅ |

## F1–F5 ratifications (2026-05-17 — Day-2)

| F | Position | Linked IDs |
|---|---|---|
| F1 — reject scale > 2 on `amountUsd` | KEEP | AC-008 |
| F2 — reject future-dated `transactionDate` | KEEP | A-002, AC-006 |
| F3 — "50 characters" = UTF-16 code units | KEEP | A-013, OQ-005 (stays NICE) |
| F4 — `exchangeRate` at scale 6 | KEEP (Phase-4 G4-P0-3) | D-4, AC-014 |
| F5 — `409 IDEMPOTENCY_CONFLICT` | KEEP for v1; revisit Phase 13 | OQ-009 (BLOCKING-for-prod) |

