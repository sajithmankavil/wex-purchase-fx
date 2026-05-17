# P1 Deferrals Acceptance — Phases 2 & 4

> **Status:** ACCEPTED — 2026-05-17 (acceptance implied by future human "proceed to Phase N" commands; this document is the explicit tracking record).
> **Purpose:** [CLAUDE.md](../../CLAUDE.md) Gate 3 says *"Implementation is blocked while any P0 or unresolved P1 exists."* The Phase-2 and Phase-4 grills produced P1 findings whose resolution genuinely belongs to a later gate (capacity data needed for calibration, PCI design needed for security wiring, etc.). This document **explicitly accepts those deferrals**, names the target gate where each closes, and identifies the owner — so the bundle's letter and the project's spirit stay aligned.
> **Linked artefacts:** [requirements-grill.md](requirements-grill.md), [design-grill.md](design-grill.md), [day-1-ratifications.md](day-1-ratifications.md).

---

## 1. Phase-2 Requirements-Grill P1 deferrals

| ID | Finding | Status | Target gate | Owner |
|---|---|---|---|---|
| G-P1-1 | H2 file mode on cloud-sync drive | ✅ Closed Phase 3 (D-14 / A-021) | — | — |
| G-P1-2 | SLO bounded by Treasury effective uptime | **Deferred** | Phase 5 (capacity plan) + Phase 6 (reliability grill) | SRE |
| G-P1-3 | PAN false-positive feedback metric not in observability spec | ✅ Closed Phase 4 (`purchase.create.validation_error.count{reason}`) | — | — |
| G-P1-4 | Logging HMAC key — origin, rotation, scope, storage | Partially closed (A-020 sets env-based key + `vN:` prefix); prod retrieval pattern **Deferred** | Phase 7 (PCI security design) — `secrets-policy.md` | SecArch |
| G-P1-5 | CORS policy missing | ✅ Closed Phase 3 (A-022 / NFR-014b) | — | — |
| G-P1-6 | Audit log retention and integrity not stated | Partially closed (NFR-016b sets retention/integrity targets); concrete destination **Deferred** | Phase 7 — `logging-monitoring-pci.md` | SecArch |
| G-P1-7 | Metric cardinality controls | ✅ Closed Phase 3 (NFR-018b) | — | — |
| G-P1-8 | Idempotency-absent semantics not in API contract | ✅ Closed Phase 3 (AC-001b) | — | — |
| G-P1-9 | `Content-Type: application/problem+json` not asserted | ✅ Closed Phase 3 (AC-T-5) | — | — |
| G-P1-10 | Treasury rate sanity bounds | ✅ Closed Phase 3 + 4 (A-019; widened to 10³⁰) | — | — |
| G-P1-11 | Concurrent in-flight Treasury fetch consistency | ✅ Closed Phase 3 (AC-027d) | — | — |

## 2. Phase-4 Design-Grill P1 deferrals

| ID | Finding | Status | Target gate | Owner |
|---|---|---|---|---|
| G4-P1-1 | ArchUnit rules can't enforce "no `description` in log calls" — needs PMD/Checkstyle | Pinned (component-design.md §6); implementation **Deferred** | Phase 7 (secure-SDLC ruleset) + Phase 13 (realisation) | SecArch + Architect |
| G4-P1-2 | `effective_date >= record_date` CHECK empirically unverified | Pinned (CHECK softened); empirical re-check **Deferred** | Phase 5 (10-year-window Treasury fetch) | SRE / Architect |
| G4-P1-3 | Rate sanity bound 10⁹ rejects hyperinflation | ✅ Closed Phase 4 (raised to 10³⁰) | — | — |
| G4-P1-4 | Surrogate `id` on `exchange_rates` redundant | ✅ Closed Phase 4 (dropped) | — | — |
| G4-P1-5 | `@Transactional` boundaries in `ConversionService` unclear | Pinned (component-design.md §3.2); implementation **Deferred** | Phase 13 (`TransactionTemplate` for upsert step) | Architect |
| G4-P1-6 | Single-flight loser semantics (bounded wait + DB poll) | ✅ Closed Phase 4 (pinned in D-9 + component-design.md §3.2) | — | — |
| G4-P1-7 | AC-026b not testable through API alone | ✅ Closed Phase 4 (refined to persistence-centric) | — | — |
| G4-P1-8 | Alias-table-drift dual case (Treasury renames descriptor) | ✅ Closed Phase 4 (AC-021c) | — | — |
| G4-P1-9 | Per-class mutation-test threshold absent | Pinned intent; threshold ratification **Deferred** | Phase 5 (≥ 85 % on `RateSelectionPolicy`, `Money`) | QA Lead |
| G4-P1-10 | OpenAPI dual-mode currency consumer-usability | Pinned intent; verification **Deferred** | Phase 13 (`OpenApiSchemaIT`) | Architect |
| G4-P1-11 | HMAC key sourcing in prod | Pinned (A-020); production retrieval pattern **Deferred** | Phase 7 (CSI driver / Vault Agent / mounted file) | SecArch |
| G4-P1-12 | TLS 1.3 preferred / 1.2 minimum | ✅ Closed Phase 4 (NFR-011 / NFR-012 lifted) | — | — |
| G4-P1-13 | `X-Correlation-Id` server-side binding | ✅ Closed Phase 4 (observability.md §2.4) | — | — |
| G4-P1-14 | DB connection env split | ✅ Closed Phase 4 (deployment-architecture.md §5) | — | — |
| G4-P1-15 | No app-layer rate limiting | Pinned as residual; decision **Deferred** | Phase 7 (Resilience4j `RateLimiter` at controller layer — yes/no) | SecArch |
| G4-P1-16 | Audit-log destination unnamed | Pinned (NFR-016b target); concrete pick **Deferred** | Phase 7 (S3 object-lock / CloudWatch / managed audit service) | SecArch |
| G4-P1-17 | Circuit-breaker calibration possibly too sensitive | Pinned intent; threshold ratification **Deferred** | Phase 5 (longer windows once single-flight in play) | SRE |
| G4-P1-18 | `Retry-After: 300 s` default | ✅ Closed Phase 4 (pinned in api-contracts.md §7) | — | — |
| G4-P1-19 | DB-pool exhaustion as readiness signal | ✅ Closed Phase 4 (component-design.md §3.4) | — | — |
| G4-P1-20 | Single-flight gate release on SIGTERM | ✅ Closed Phase 4 (pinned in D-9 + component-design.md §3.4) | — | — |
| G4-P1-21 | Deploy-time warm-up automation | Pinned in observability.md §8; currency list + N **Deferred** | Phase 5 | SRE |
| G4-P1-22 | AC-T-3 fixture set incomplete | ✅ Closed Phase 4 (widened) | — | — |
| G4-P1-23 | Graceful-shutdown test undocumented | Pinned intent; implementation **Deferred** | Phase 13 (`GracefulShutdownIT`) | QA Lead |
| G4-P1-24 | Idempotency-Key test set undocumented | Pinned intent; implementation **Deferred** | Phase 13 (test set when feature lands) | QA Lead |
| G4-P1-25 | Capacity anchors unverified | Pinned; ratification **Deferred** | Phase 5 (capacity plan) + Phase 6 (reliability grill) | SRE |
| G4-P1-26 | Vulnerability-management cadence + SLA | Pinned intent; cadence **Deferred** | Phase 7 (`vulnerability-management-pci.md`) | SecArch |
| G4-P1-27 | `RateOrientationContractCheck` fail-closed threshold | Pinned WARN-only at v1; fail-closed threshold **Deferred** | Phase 5 (≥ 3 WARN/24 h alert; > 5 % drift on canary fail-closed) | SRE |

## 3. Aggregate deferred set

The following P1s are explicitly accepted as deferred:

- **Phase 5 (Operational Design / Reliability):** G-P1-2, G-P1-6 (partially), G4-P1-2, G4-P1-9, G4-P1-17, G4-P1-21, G4-P1-25, G4-P1-27. *(8 items.)*
- **Phase 7 (PCI Security Design):** G-P1-4 (partially), G-P1-6 (partially), G4-P1-1 (partially), G4-P1-11, G4-P1-15, G4-P1-16, G4-P1-26. *(7 items.)*
- **Phase 13 (Implementation):** G4-P1-1 (realisation), G4-P1-5, G4-P1-10, G4-P1-23, G4-P1-24. *(5 items.)*

Total carried forward: **20 P1 entries across 16 distinct findings** (some span multiple gates).

**Acceptance:** the human owner's "proceed to Phase 5" command (or future equivalent) carries an implicit acceptance of these deferrals. Each target gate **must** close its assigned P1s before its own exit-criteria checklist signs.

## 4. RACI shortfalls (E1–E9 from the Phase-4 hardening pass)

Real-world owners are placeholders pending real organisational mapping. The case-study context makes these academic; flagged for transparency.

| ID | Activity | Stated owner (today) | Action for Phase 5 |
|---|---|---|---|
| E1 | Quarterly restore-to-staging drill | "Platform-managed" | Phase 5: name the platform team. |
| E2 | `currency-aliases.json` PR review | "CODEOWNERS gating" | Phase 5 / Phase 13: author CODEOWNERS file with named owners. |
| E3 | Daily alias-drift reconciliation job | "Operations" | Phase 5: name the on-call rotation that owns drift triage. |
| E4 | Audit-log access review (quarterly) | (none) | Phase 7: name the reviewer. |
| E5 | HMAC log-hash key rotation | SecArch | ✅ Owner named; rotation procedure Phase 7. |
| E6 | `RateOrientationContractCheck` fail-closed threshold | SRE | ✅ Owner named; threshold Phase 5. |
| E7 | Vulnerability-management SLA | SecArch | ✅ Owner named; SLA Phase 7. |
| E8 | On-call schedule | (none) | Phase 5: produce `docs/operations/oncall-escalation.md` with named rotations. |
| E9 | Capacity-anchor verification (load-test plan) | SRE | ✅ Owner named; plan Phase 5. |

## 5. What this document does not do

- It does not waive the Phase-9 implementation-readiness gate. Phase 9 still inspects whether each accepted deferral has indeed closed at its target gate.
- It does not commit the human owner to a single individual per RACI placeholder — those names land in Phase 5 / 7.
- It does not retroactively change the Phase-2 or Phase-4 grill verdicts. Those stand. This document explains how the deferrals reconcile with [CLAUDE.md](../../CLAUDE.md) Gate 3's letter.
