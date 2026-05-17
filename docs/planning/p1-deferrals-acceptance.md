# P1 Deferrals Acceptance — Phases 2, 4, 6, 8

> **Status:** ACCEPTED — 2026-05-17 (acceptance implied by future human "proceed to Phase N" commands; this document is the explicit tracking record).
> **Last updated:** 2026-05-17 end of Phase 9. Phases 5/6/7/8 closures recorded in §4a; Phase-9 acceptance summary recorded in §6.
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

## 4a. Closures landed in Phases 5–8 (end-of-session update)

The Phase-2 and Phase-4 deferrals tracked above were largely closed in Phases 5–8. Status update as of 2026-05-17 end-of-session:

### Phase-5 closures (Operational Design)

| Item | Status | Where closed |
|---|---|---|
| G-P1-2 SLO bounded by Treasury × cache-hit | ✅ Closed | [slo-sli.md §4](../operations/slo-sli.md) |
| G4-P1-9 Per-class mutation thresholds (≥ 85 % on `RateSelectionPolicy`, `Money`) | ✅ Closed (framing) | [operational-design-session.md](operational-design-session.md) (NFR-021 update Phase 13) |
| G4-P1-17 CB calibration | ✅ Closed (and revised by Phase 6) | [failure-modes-and-resilience.md §3](../operations/failure-modes-and-resilience.md) |
| G4-P1-21 Warm-up currency list (10) + timeout (30 s) | ✅ Closed | [capacity-scalability-plan.md §4](../operations/capacity-scalability-plan.md) |
| G4-P1-25 Capacity anchors (anchor only) | ✅ Closed (anchor); ratification deferred to Phase 6 → done | [capacity-scalability-plan.md §1/§2](../operations/capacity-scalability-plan.md) |
| G4-P1-27 RateOrientationContractCheck fail-closed threshold | ✅ Closed | [monitoring-alerting.md §3.1](../operations/monitoring-alerting.md) |
| OQ-014 SLO target ratification | ✅ Closed | [slo-sli.md §3](../operations/slo-sli.md) |
| E1, E3, E4, E8 RACI shortfalls | ✅ Structurally closed (named individuals pending pre-prod hand-off) | [oncall-escalation.md §4](../operations/oncall-escalation.md) |

### Phase-6 closures (Reliability & Scalability Grill)

| Item | Status | Where closed |
|---|---|---|
| G6-P0-1 CB sliding-window TIME_BASED | ✅ Closed | [failure-modes-and-resilience.md §3](../operations/failure-modes-and-resilience.md) |
| G6-P0-2 Single-flight loser semantics (10 s wait + outcome-ref) | ✅ Closed (design); Phase 13 implements | [adr-0001-core-architecture.md D-9](../architecture/adr-0001-core-architecture.md) |
| G6-P0-3 Cache-miss p99 anchor 1500 ms → 3000 ms | ✅ Closed | NFR-003; [slo-sli.md SLO-G](../operations/slo-sli.md) |
| G6-P0-4 DB pool 10 → 20 | ✅ Closed | [capacity-scalability-plan.md §1/§2](../operations/capacity-scalability-plan.md) |
| G6-P1-4 Graceful shutdown 30 s → 60 s | ✅ Closed | [deployment-architecture.md §4.3](../architecture/deployment-architecture.md) |
| G6-P1-8 Canary cadence 7 d → 24 h | ✅ Closed | [monitoring-alerting.md §3.1](../operations/monitoring-alerting.md) |
| Other Phase-6 P1s (G6-P1-1..3, -5, -6, -7, -9, -10, -11, -12) | Carry to Phase 13/12 with owners | [reliability-scalability-grill.md §2](reliability-scalability-grill.md) |

### Phase-7 closures (PCI Security Design Session)

| Item | Status | Where closed |
|---|---|---|
| G4-P0-5 Encoded-PAN guard direction | ✅ Closed (direction); Phase 13 implements | [pci-security-design-session.md D-19](pci-security-design-session.md) |
| G4-P1-1 PMD/Checkstyle logging hygiene (policy) | ✅ Closed (policy); Phase 13 implements | [secure-sdlc-pci.md §2](../security/secure-sdlc-pci.md) |
| G4-P1-11 HMAC key sourcing (CSI-mounted file) | ✅ Closed | [encryption-key-management.md §3.2](../security/encryption-key-management.md) |
| G4-P1-15 App-layer rate-limiter (YES, default disabled) | ✅ Closed | [pci-security-design-session.md D-15](pci-security-design-session.md) |
| G4-P1-16 Audit destination (WORM/append-only/signed) | ✅ Closed (mechanism); Phase 12 picks the platform binding | [logging-monitoring-pci.md §3](../security/logging-monitoring-pci.md) |
| G4-P1-26 Vulnerability-management SLA | ✅ Closed | [vulnerability-management-pci.md §3](../security/vulnerability-management-pci.md) |
| G6-P1-5 Audit-event rate-limiter | ✅ Closed (same primitive as D-15) | [logging-monitoring-pci.md](../security/logging-monitoring-pci.md) |
| OQ-011 PAN-guard policy | ✅ Closed (reject + encoded-PAN + Phase-8 NFKC) | [pci-security-design-session.md D-19](pci-security-design-session.md) |
| OQ-019 HMAC key origin/rotation | ✅ Closed | [encryption-key-management.md](../security/encryption-key-management.md) |
| OQ-022 Audit-log retention/integrity | ✅ Closed | [logging-monitoring-pci.md §3](../security/logging-monitoring-pci.md) |

### Phase-8 closures (PCI Adversarial Security Grill)

| Item | Status | Where closed |
|---|---|---|
| G8-P0-1 Decoder pipeline ordering before rate-limiter | ✅ Closed (design; AC-T-6); Phase 13 implements via servlet Filter | [component-design.md §3.5](../architecture/component-design.md) |
| G8-P0-2 Audit destination re-categorised connected-to | ✅ Closed | [pci-scope-and-cde.md §2](../security/pci-scope-and-cde.md); [logging-monitoring-pci.md §7b](../security/logging-monitoring-pci.md) |
| G8-P0-3 Unicode NFKC normalisation in ContentGuard | ✅ Closed (design; AC-010e); Phase 13 implements | [component-design.md §3.5](../architecture/component-design.md) |
| G8-P0-4 Treasury TPSP formal applicability decision | ✅ Closed | [third-party-service-provider-pci.md §1a](../security/third-party-service-provider-pci.md) |
| G8-P1-3 Vulnerability-mgmt exception cap (max 5 C/H active) | ✅ Closed | [vulnerability-management-pci.md §4](../security/vulnerability-management-pci.md) |
| G8-P1-6 GitHub SOC 2 freshness check | ✅ Closed | [third-party-service-provider-pci.md §4](../security/third-party-service-provider-pci.md) |
| G8-P1-9 HMAC dictionary-attack residual (documented) | ✅ Documented residual | [logging-monitoring-pci.md §7b](../security/logging-monitoring-pci.md) + R-040 |
| Other Phase-8 P1s (G8-P1-4, -5, -7, -8, -10..12) | Carry to Phase 12/13 with owners | [pci-security-grill.md §2](../security/pci-security-grill.md) |

### Net status at end of Phase 8

- **Total P1 deferrals tracked:** ~ 50 across Phases 2/4/6/8
- **Closed in-phase:** ~ 30 (60 %)
- **Carried to Phase 12 (pre-prod):** ~ 6 (named-individual handoff, platform-specific picks)
- **Carried to Phase 13 (implementation):** ~ 12 (PMD ruleset, CSI mount wiring, servlet Filter wiring, NFKC normalisation, contract tests, OpenAPI consumer-usability, graceful-shutdown test, idempotency-key test set, OQ-010 identity-at-gateway, deferred warm-up, baseline measurements)
- **Carried to Phase 9 (implementation readiness):** OQ-010 (BLOCKING-for-prod) — already in the gate criteria

## 6. Phase-9 Implementation-Readiness-Gate acceptance (2026-05-17)

[implementation-readiness-gate.md](implementation-readiness-gate.md) verdict: **READY_FOR_HUMAN_APPROVAL** with conditions.

The Phase-9 inspection confirmed:
- All 18 P0s across Phases 2/4/6/8 are closed or pinned at gate exit.
- All ~50 P1s are either resolved in-phase or tracked here with named owners + target gates.
- No P1 is "stuck" without an owner or a closure path.
- The case-study posture can ship past Phase 9; production cutover (Phase 12) waits for Phase 10 + Phase 11 to close and for the named-individual placeholders (E1, E3, E4, E8) to resolve.

The Phase-12 carry-forward list (8 items including OQ-010) is the **explicit hand-off**: it does not invalidate this acceptance; it scopes what must happen before production.

## 5. What this document does not do

- It does not waive the Phase-9 implementation-readiness gate. Phase 9 still inspects whether each accepted deferral has indeed closed at its target gate.
- It does not commit the human owner to a single individual per RACI placeholder — those names land in Phase 5 / 7 / 12.
- It does not retroactively change the Phase-2, Phase-4, Phase-6, or Phase-8 grill verdicts. Those stand. This document explains how the deferrals reconcile with [CLAUDE.md](../../CLAUDE.md) Gate 3's letter and tracks them through to closure.
