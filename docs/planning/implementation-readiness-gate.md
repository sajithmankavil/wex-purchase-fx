# Implementation Readiness Gate — Phase 9

> **Status: READY_FOR_HUMAN_APPROVAL** (with explicit Phase-12 hand-off conditions; see §11).
> **Date:** 2026-05-17 (end-of-session continuation).
> **Owner of the recommendation:** Architect + Service owner. **The approval itself is human-only** — Claude Code cannot create `.human-approvals/implementation-approved.txt`.
>
> Phase 9 inspects whether implementation work (Phase 13) may begin. It does **not** approve production deployment — that is Phase 12 after Phases 10 and 11 close.

---

## 0. Recommendation

**Recommendation: READY_FOR_HUMAN_APPROVAL — implementation may begin once the human creates the marker file.**

Rationale: every P0 across Phases 2/4/6/8 is closed or pinned at gate exit (18 of 18). Every P1 is either closed in-phase (~30) or tracked in [p1-deferrals-acceptance.md](p1-deferrals-acceptance.md) with a named owner and a target gate (~20). The Phase-13 implementation surface is fully designed; tests are catalogued; CI gates are specified. **OQ-010** (identity origin for production) is correctly tracked as **BLOCKING-for-prod** — it does not block implementation, only Phase-12 production cutover.

**This gate does not approve production deployment.** Phase 10 (Operational Readiness Gate) and Phase 11 (PCI Security Readiness Gate) must close, then Phase 12 produces the human approval markers for both implementation *and* PCI security. The case-study build can proceed past this gate; production deployment requires the full Phase 10/11/12 chain.

---

## 1. Required evidence — checklist with evidence pointers

### 1.1 Requirements

| Criterion | Status | Evidence |
|---|---|---|
| Source requirements present | ✅ | [docs/requirements/source-requirements.md](../requirements/source-requirements.md) (verbatim from WEX-Requirement.docx; never overwritten) |
| Functional requirements complete | ✅ | [functional-requirements.md](../requirements/functional-requirements.md) FR-001..FR-006 with decision tables; Phase-4 refinements (idempotency-absent semantics; CONV-vs-UPSTREAM table; rate-orientation note) |
| Non-functional requirements complete | ✅ | [non-functional-requirements.md](../requirements/non-functional-requirements.md) NFR-001..NFR-035 + NFR-013b/014b/016b/018b; Phase-4/6 anchor revisions in-place |
| Acceptance criteria complete | ✅ | [acceptance-criteria.md](../requirements/acceptance-criteria.md): 49 ACs (AC-001..AC-036; 12 Phase-2 additions; AC-010d/021c/027e/026b refined Phase 4; AC-010e + AC-T-6 Phase 8); AC-T-1..AC-T-6 cross-cutting |
| Traceability matrix complete + bidirectional + per-gate finding maps | ✅ | [traceability-matrix.md](../requirements/traceability-matrix.md) — source ↔ FR ↔ AC ↔ tests ↔ NFRs ↔ risks; G-P*-*, G4-P*-*, G6-P*-*, G8-P*-*, F1–F5, H1–H10 maps |
| Requirements grill complete | ✅ | [requirements-grill.md](requirements-grill.md) Phase 2: 5 P0 + 11 P1 + 10 P2; all P0s closed; deferrals tracked |
| Assumptions + open questions register | ✅ | [assumptions-and-open-questions.md](../requirements/assumptions-and-open-questions.md) A-001..A-022; [open-questions.md](../requirements/open-questions.md) OQ-001..OQ-023; 14 closed; 1 carried (OQ-010 BLOCKING-for-prod); rest tracked |
| Risk register | ✅ | [risk-register.md](../requirements/risk-register.md) R-001..R-041 + W-001..W-005; all mitigated or accepted-residual |

### 1.2 Design

| Criterion | Status | Evidence |
|---|---|---|
| Design session complete | ✅ | [design-session.md](design-session.md) Phase 3: 14 decisions D-1..D-14; options considered; rejected alternatives; verdict READY_FOR_PHASE_4 |
| System context complete | ✅ | [system-context.md](../architecture/system-context.md) C4 Level 1: actors, external systems, trust boundaries, data ownership, out-of-scope |
| Component design complete | ✅ | [component-design.md](../architecture/component-design.md) C4 Level 2/3: package layout, component table, request flows, error handling, ArchUnit rules, ContentGuard §3.5 (NFKC + decoder + Luhn/track; rate-limiter Filter ordering) |
| Data model complete | ✅ | [data-model.md](../architecture/data-model.md): two tables; engine-agnostic DDL; versioned `exchange_rates` keyed by `(country_currency_desc, record_date, effective_date)`; lookup index; sanity bounds 10³⁰ |
| API contracts complete | ✅ | [api-contracts.md](../architecture/api-contracts.md): 3 endpoints + actuator; RFC 9457 + `application/problem+json` (AC-T-5); CONV-vs-UPSTREAM decision table; scale-6 normalisation pinned; Retry-After 300 s |
| Deployment architecture complete | ✅ | [deployment-architecture.md](../architecture/deployment-architecture.md): case-study + production-reference; env vars; rolling deploy; graceful shutdown 60 s; rollback class table |
| ADR-0001 for major decisions | ✅ | [adr-0001-core-architecture.md](../architecture/adr-0001-core-architecture.md): 14 decisions with rationale, options considered, consequences, rollback. Phase-4 + Phase-6 follow-on notes in-place |

### 1.3 Review

| Criterion | Status | Evidence |
|---|---|---|
| Design grill complete | ✅ | [design-grill.md](design-grill.md) Phase 4: 5 P0 + 20 P1 + 10 P2; all P0s pinned; deferrals named |
| Operational design session complete | ✅ | [operational-design-session.md](operational-design-session.md) Phase 5: 10 deliverables; SLO-C bounded by Treasury × cache-hit; CB calibration |
| Reliability & scalability grill complete | ✅ | [reliability-scalability-grill.md](reliability-scalability-grill.md) Phase 6: 4 P0 + 12 P1 + 10 P2; all P0s pinned; anchor math verified |
| PCI security design session complete | ✅ | [pci-security-design-session.md](pci-security-design-session.md) Phase 7: 12 PCI requirements designed; D-15..D-20 |
| PCI adversarial security grill complete | ✅ | [pci-security-grill.md](../security/pci-security-grill.md) Phase 8: 4 P0 + 12 P1 + 8 P2; all P0s pinned |
| **No unresolved P0 findings** | ✅ | **18 of 18 P0s** across Phases 2/4/6/8 closed or pinned at gate exit |
| **P1 findings resolved or explicitly accepted** | ✅ | ~30 closed in-phase; ~20 tracked in [p1-deferrals-acceptance.md](p1-deferrals-acceptance.md) with named owners + target gates (Phase 12 / 13) |
| Security model reviewed | ✅ | [threat-model.md](../security/threat-model.md) + 14 docs in `docs/security/` + Phase-8 adversarial grill |
| Operations model reviewed | ✅ | 10 docs in `docs/operations/` + Phase-6 adversarial grill + [operational-readiness-gate.md](../operations/operational-readiness-gate.md) verdict CONDITIONAL_PASS |
| Test strategy reviewed | ✅ | AC-T-1..AC-T-6 + per-component test plan in [component-design.md §8](../architecture/component-design.md); per-class mutation thresholds (≥ 85 % on `RateSelectionPolicy`, `Money`); WireMock fixtures specified |

### 1.4 Implementation plan

| Criterion | Status | Evidence |
|---|---|---|
| Milestones defined | ✅ (high-level) | Phase 13 milestones map to clean-architecture layers: M1 domain + Money + RateSelectionPolicy; M2 application services + ports; M3 infrastructure adapters (PurchaseRepo, ExchangeRateRepo, TreasuryClient, CurrencyAliasTable, Caffeine); M4 API controllers + ProblemDetails handler + ContentGuard advice; M5 observability wiring; M6 OpenAPI + Swagger UI; M7 CI/CD gates. Phase-13 work breaks each into PRs. |
| First implementation slice is small and testable | ✅ | M1 = `domain` package + `Money` value object + `RateSelectionPolicy` pure function + property tests (jqwik) + ArchUnit "no double/float" rule. ~500 LOC; pure unit tests; no DB / no HTTP / no infrastructure. |
| Files expected to change are listed | ✅ | Per-milestone scope in [component-design.md §1](../architecture/component-design.md#1-package-layout-clean-architecture-single-deployable). Each PR cites change-ID + risk-assessment + rollback class per [change-control-pci.md §1](../security/change-control-pci.md) |
| Tests required are listed | ✅ | Per-component test plan in [component-design.md §8](../architecture/component-design.md#8-test-plan-by-component): unit, integration, contract (WireMock), property-based (jqwik), mutation (Pitest ≥ 70 % package / ≥ 85 % critical classes), architectural (ArchUnit) |
| Rollback plan exists for risky changes | ✅ | [rollback-plan.md](../operations/rollback-plan.md) — 6 classes (A code / B config / C schema / D data PITR / E orientation flip / F security incident); migration class table in [change-control-pci.md §5](../security/change-control-pci.md) |

---

## 2. P0 closure verification (18 of 18)

| Grill | P0 ID | Status | Where closed |
|---|---|---|---|
| Phase 2 | G-P0-1 Treasury rate orientation unverified | ✅ Closed | Phase-3 prototype ([phase-3-prototype-log.md](phase-3-prototype-log.md)); ADR-0001 D-4 |
| Phase 2 | G-P0-2 6-month window EOM-clamp asymmetry | ✅ Closed | A-003 ratified; AC-018b / AC-019b |
| Phase 2 | G-P0-3 CONV-vs-UPSTREAM boundary | ✅ Closed | FR-003 decision table + AC-020b/021b/022b |
| Phase 2 | G-P0-4 Same-`record_date` revision policy | ✅ Closed | A-018 versioned persistence; AC-026b refined Phase 4 |
| Phase 2 | G-P0-5 PCI scope on a single regex | ✅ Closed (direction); Phase-7/8 implements | A-017; AC-010c/d/e; D-19 |
| Phase 4 | G4-P0-1 Single-flight key too narrow | ✅ Closed | ADR-0001 D-9 re-keyed to `(currency, treasury_quarter_end)`; AC-027e |
| Phase 4 | G4-P0-2 Hot-cache key fragmentation | ✅ Closed | ADR-0001 D-10 re-keyed to `(currency, record_date)` |
| Phase 4 | G4-P0-3 `exchange_rate` scale contradiction | ✅ Closed | Scale-6 normalised end-to-end; NFR-003 + SLO-G refined |
| Phase 4 | G4-P0-4 UUID v7 generator unstated | ✅ Closed | D-2 adds `uuid-creator:5.x` |
| Phase 4 | G4-P0-5 Encoded-PAN bypass | ✅ Closed (direction); Phase-7 D-19 + Phase-8 NFKC | AC-010d + AC-010e |
| Phase 6 | G6-P0-1 CB sliding-window count-based | ✅ Closed | TIME_BASED + min-calls=5 + 5 min open |
| Phase 6 | G6-P0-2 Single-flight loser semantics violate AC-027d | ✅ Closed | 10 s loser wait + outcome-ref |
| Phase 6 | G6-P0-3 p99 cache-miss anchor inconsistent | ✅ Closed | NFR-003 + SLO-G raised to 3000 ms |
| Phase 6 | G6-P0-4 DB pool at 80 % capacity | ✅ Closed | Pool 10 → 20; replica-ceiling note |
| Phase 8 | G8-P0-1 Decoder pipeline runs before rate-limiter | ✅ Closed (design); Phase 13 servlet Filter | component-design.md §3.5 + AC-T-6 |
| Phase 8 | G8-P0-2 Audit destination categorisation | ✅ Closed | Re-categorised connected-to + mitigating argument |
| Phase 8 | G8-P0-3 Unicode NFKC normalisation | ✅ Closed (design); Phase 13 implements | component-design.md §3.5 + AC-010e |
| Phase 8 | G8-P0-4 Treasury TPSP applicability | ✅ Closed | Formal applicability decision in third-party-service-provider-pci.md §1a |

**Zero unresolved P0s.** ✅

---

## 3. P1 closure / deferral verification

Total P1s across Phases 2/4/6/8: ~50.

- **~30 closed in-phase** (Phase 4 hardening + Phases 5/6/7/8 in-place pins).
- **~20 deferred** to Phase 12 (pre-prod platform-binding) and Phase 13 (implementation realisation), each with a named owner and target gate per [p1-deferrals-acceptance.md](p1-deferrals-acceptance.md) §4a.

**Phase-13 carry-forwards (12 items):**

| ID | Title | Owner |
|---|---|---|
| G4-P1-1 | PMD/Checkstyle ruleset realisation | SecArch + Architect |
| G4-P1-5 | `@Transactional` boundaries in `ConversionService` | Architect |
| G4-P1-10 | OpenAPI consumer-usability verification | Architect |
| G4-P1-23 | Graceful-shutdown integration test | QA Lead |
| G4-P1-24 | Idempotency-Key test set (when feature lands) | QA Lead |
| G4-P1-27 | RateOrientationContractCheck implementation | SRE |
| G6-P1-1 | Cold-currency cliff: deferred all-currencies warm-up (optional) | SRE |
| G6-P1-3 | Hot cache range-query (two-level Caffeine) | Architect |
| G6-P1-11 | JVM heap stress-test in load tests | SRE |
| G6-P1-12 | Tail-based trace sampling | SRE |
| G8-P0-1 | Servlet-Filter rate-limiter wiring | Architect + SecArch |
| G8-P0-3 | NFKC normalisation in ContentGuard | SecArch |
| G8-P1-10 | Local-mode rejected-payload invariant test | QA + Architect |
| G8-P1-12 | `security-profile.yml` startup check | SecArch |

**Phase-12 carry-forwards (8 items):**

| ID | Title | Owner |
|---|---|---|
| OQ-010 | Identity origin (gateway: mTLS / OIDC / SPIFFE) — **BLOCKING-for-prod** | Platform security |
| G4-P1-11 | HMAC key sourcing platform-binding (CSI driver vendor pick) | SecArch + Platform |
| G4-P1-16 | Audit-log destination platform pick (S3 Object Lock / GCP Log Buckets / Azure Immutable Blob) | SecArch + Platform |
| G6-P1-7 | Multi-region DR trigger concrete (re-evaluate) | Product owner |
| G6-P1-10 | Postgres `max_connections=200` if scaling beyond 5 replicas | Platform / DBA |
| G8-P1-4 | Audit-of-audit destination separately controlled | SecArch + Platform |
| G8-P1-5 | Image-signing trust chain (cosign + admission control) | SecArch + SRE |
| G8-P1-6 | GitHub SOC 2 freshness check (annual) | Compliance |
| G8-P1-7 | PAN-guard 0.1 % false-positive baseline (after production data) | SecArch + Product |
| G8-P1-8 | Pen-test vendor scope finalisation | SecArch |
| E1, E3, E4, E8 | Named-individual placeholder closures | Service owner |

**Zero P1s without a named owner.** ✅

---

## 4. Test-plan completeness

| Test category | Coverage check | Where |
|---|---|---|
| Unit tests | Per FR + per domain type | component-design.md §8 |
| Integration tests | Per FR + per failure mode + per AC error code | component-design.md §8 |
| Contract tests (WireMock against Treasury) | AC-T-3 widened (zero / negative / null / trailing zero / leading zero / high-precision / sanity-ceiling) | acceptance-criteria.md AC-T-3 |
| Property-based tests (jqwik) | `RoundingPropertyTest` for HALF_UP; `Money` invariants | component-design.md §8 |
| Mutation testing (Pitest) | ≥ 70 % package average; ≥ 85 % on `RateSelectionPolicy` + `Money` (G4-P1-9 / Phase-5 ratified) | NFR-021; component-design.md §8 |
| Architectural fitness (ArchUnit) | Layer dependencies; no `double`/`float` in `domain`/`application`; controller-package locality | component-design.md §6 |
| Boundary / segmentation tests | AC-010b/c/d/e — CHD-shaped injection set | pci-scope-and-cde.md §4.1 |
| Durability (restart) | AC-010 — DurabilityRestartIT in file-mode H2 | acceptance-criteria.md |
| Concurrency | AC-027b/c/d/e — SingleFlightCacheConcurrencyTest + barrier-coordinated WireMock | component-design.md §8 |
| Content-type conformance | AC-T-5 — `application/problem+json` on every error response | acceptance-criteria.md |
| Rate-limit ordering | AC-T-6 — ContentGuard not invoked under rate-limit saturation | acceptance-criteria.md |
| Resilience / failure-injection | AC-T-3 (Treasury slow / 5xx / malformed / timeout / CB open) + capacity-plan §5 chaos scenarios | failure-modes-and-resilience.md §1 |
| Load tests | 7 scenarios documented; non-CI (per source rule); k6 tool | capacity-scalability-plan.md §5 |
| Logging hygiene | `LoggingPiiGuardTest` (AC-032) + `LoggingHashKeyTest` (AC-032b); PMD/Checkstyle rule policy | logging-monitoring-pci.md §5 |
| OpenAPI lint | `oasdiff` gate; Spectral lint | api-contracts.md §8 |
| SAST + SCA + secret + IaC + container scan | Per release gates | secure-sdlc-pci.md §1 |
| DAST | Per release in `staging` | secure-sdlc-pci.md §1 |

**Test plan is complete at the design level.** Phase 13 writes the test classes.

---

## 5. SDLC-gate completeness

| Gate | Designed | Tool recommended | Phase 13 wires |
|---|---|---|---|
| Threat-model review | ✅ | Phase 4 + Phase 8 process | n/a (process gate) |
| Secure design review (CODEOWNERS) | ✅ | CODEOWNERS file (exists) | Phase 13 augments |
| Code review (PR) | ✅ | GitHub PR + branch protection | Phase 13 wires required-status-checks |
| SAST | ✅ | SonarQube Cloud OR Semgrep | Phase 13 picks one |
| SCA / dependency scan | ✅ | OWASP Dependency-Check + Snyk | Phase 13 |
| Secret scan | ✅ | GitLeaks + TruffleHog | Phase 13 |
| IaC scan | ✅ (when IaC lands) | Checkov OR tfsec | Phase 13 |
| Container scan | ✅ | Trivy | Phase 13 |
| Image signing | ✅ | cosign / sigstore | Phase 13 + Phase 12 platform-binding |
| SBOM | ✅ | CycloneDX Maven plugin | Phase 13 |
| DAST | ✅ | OWASP ZAP | Phase 13 + Phase 12 staging |
| Change control | ✅ | Per change-control-pci.md | Phase 13 PR template |

Reference: [secure-sdlc-pci.md](../security/secure-sdlc-pci.md). All gates designed; tool picks recommended; Phase 13 implements.

---

## 6. Evidence-register completeness

| EVD ID | Designed | Collection cadence | Retention |
|---|---|---|---|
| EVD-001 Req 1 (network controls) | ✅ | Monthly | 1 year |
| EVD-002 Req 2 (secure config) | ✅ | Per release | 1 year |
| EVD-003 Req 3 (stored CHD — n/a justification) | ✅ | Quarterly + per release | 1 year |
| EVD-004 Req 4 (TLS / transmission) | ✅ | Per release + quarterly | 1 year |
| EVD-005 Req 5 (malware) | ✅ | Per release | 1 year |
| EVD-006 Req 6 (secure SDLC) | ✅ | Per PR + per release | 1 year (scans); per-repo (PRs) |
| EVD-007 Req 7 (access reviews) | ✅ | Quarterly | 1 year |
| EVD-008 Req 8 (identity + authn) | ✅ | Quarterly + per change | 1 year |
| EVD-009 Req 9 (physical) | ✅ | Annual (cloud AOC) | Per contract |
| EVD-010 Req 10 (audit logs) | ✅ | Monthly sample + continuous | 1 year (audit); 3 years (pen-test) |
| EVD-011 Req 11 (security tests) | ✅ | Quarterly + annual | 1 year |
| EVD-012 Req 12 (security program) | ✅ | Annual + per incident | 3 years |

All 12 evidence rows are **designed** in Phase 7. Status moves to **collected** in Phase 12 / 13 as evidence accumulates. Reference: [evidence-register.md](../security/evidence-register.md).

---

## 7. OQ-010 — identity origin (BLOCKING-for-prod tracking)

This is the **single largest open item** at Phase-9 close. It does not block implementation; it blocks Phase-12 production cutover.

| Aspect | State |
|---|---|
| Working position v1 | No app-layer authn (A-007); service runs behind a trusted gateway in real deployments |
| Documented direction (Phase-12 closure) | mTLS at gateway / OIDC / JWT / SPIFFE — three options recorded in [access-control-pci.md §5](../security/access-control-pci.md#5-application-layer-access-the-oq-010-closure-direction) |
| Phase-7 working position | Service accepts an opaque `X-Request-Identity` header from the gateway; v1 does not parse for authz; logged as part of every request for audit traceability |
| Required at Phase 12 | Concrete pattern picked; gateway-side enforcement validated; service-side identity-aware authorisation logic added (if multi-tenant or RBAC is required) |
| Blocking for | Phase 12 (pre-prod hand-off → `.human-approvals/pci-production-approved.txt`); **not** Phase 9 implementation approval |
| Risk if unaddressed | A mis-deployed service without a gateway exposes the API to untrusted networks. R-008 mitigation: readiness probe refuses UP if `WEX_GATEWAY_REQUIRED=true` env var is unset in non-local profiles (Phase-7 control). |

---

## 8. Phase-10 / Phase-11 / Phase-12 preview (not this gate's responsibility)

Phase 9 says "ready to implement." Phases 10 / 11 / 12 say "ready to deploy."

| Gate | Document | Re-inspects |
|---|---|---|
| Phase 10 — Operational Readiness Gate | [operational-readiness-gate.md](../operations/operational-readiness-gate.md) | Capacity load-tests **executed** (not just planned); CB calibration tested under chaos; SLO baseline measured; alert routing wired; on-call rotation named with real individuals |
| Phase 11 — PCI Security Readiness Gate | [pci-production-readiness-gate.md](../security/pci-production-readiness-gate.md) (Phase-11 deliverable) | Evidence-register rows **collected** (not just designed); QSA walk-through scheduled; ASV scan passed; pen-test report received; AOCs current |
| Phase 12 — Human approval markers | `.human-approvals/implementation-approved.txt` + `pci-security-approved.txt` (and later `pci-production-approved.txt`) | Outside Claude Code; human-only |

The case-study posture can ship without Phases 10/11/12 because the case study is not a production deployment. Production deployment requires the full chain.

---

## 9. Remaining risks (informational)

All risks R-001..R-041 are tracked in [risk-register.md](../requirements/risk-register.md):

- **Mitigated:** R-021, R-022, R-023, R-024, R-025, R-026, R-027, R-028, R-029, R-030, R-031, R-032, R-033, R-034, R-035, R-037, R-038, R-039 (Phase-3/4/6/8 mitigations in place).
- **Open accepted-residual:** R-036 (cold-currency cliff), R-040 (HMAC dictionary-attack residual), R-041 (GitHub SOC 2 monitoring).
- **Open watched:** W-001..W-005 (Treasury rate-limit; Java 21 LTS; Spring Boot cadence; H2 CVE; springdoc churn).

No high or critical risks are unmitigated at Phase 9 exit.

---

## 10. First implementation milestone — concrete proposal

For the human approver: this is what the first implementation slice looks like.

**Milestone M1 — Domain layer + Money + RateSelectionPolicy.** Estimated 1 day of focused implementation.

| Item | Detail |
|---|---|
| **Goal** | Land `com.example.purchaseconversion.domain.*` with full unit-test coverage; ArchUnit rules in place; no Spring; no DB; no HTTP |
| **Files created** | `src/main/java/com/example/purchaseconversion/domain/{Purchase,ExchangeRate,Money,CurrencyDescriptor,PurchaseId,RateSelectionPolicy}.java`; `src/test/java/.../domain/*Test.java`; `src/test/java/.../ArchitectureTests.java`; `pom.xml` |
| **Tests** | `MoneyTest` (jqwik property-based: HALF_UP across signs/scales/magnitudes; rejects double/float); `RateSelectionPolicyTest` (table-driven boundary tests for AC-014..AC-020 + AC-018b + AC-019b); `ArchitectureTests` (layer rules; no `double`/`float` in `domain`/`application`) |
| **Acceptance** | All `domain/*Test.java` green; ArchUnit green; coverage on `domain/*` ≥ 85 % line + ≥ 75 % branch; Pitest mutation score ≥ 85 % on `RateSelectionPolicy` + `Money` (G4-P1-9) |
| **Rollback class** | A (code rollback) per [rollback-plan.md §4.2](../operations/rollback-plan.md#42-class-a--code-rollback) |
| **Risks** | None material — pure unit-testable code with no external dependencies |
| **PR template** | Per [change-control-pci.md §1](../security/change-control-pci.md#1-required-change-fields-pr--change-ticket-template) — `change-id`, `requirement link`, `risk assessment`, `security impact: none`, `CDE impact: none`, `test evidence`, `approval`, `rollback class: A`, `deployment window: continuous`, `post-deploy validation: unit tests` |

This is **deliberately minimal**: ~500 LOC; no infrastructure; no integration tests yet; passes through every CI gate; demonstrates the discipline; provides the foundational types that every subsequent milestone uses.

Milestones M2..M7 are scoped in [pci-security-design-session.md §1.4](pci-security-design-session.md) and [component-design.md §1](../architecture/component-design.md#1-package-layout-clean-architecture-single-deployable).

---

## 11. Decision

```
Status: READY_FOR_HUMAN_APPROVAL
```

**Conditions** (these are explicit expectations on the human-approval process; not bugs in the design):

1. **OQ-010 must be closed before production cutover (Phase 12).** Implementation can proceed; production deployment cannot.
2. **Phase-12 platform-binding picks must happen at pre-prod.** Audit-log destination concrete vendor; CSI driver vendor; image-signing chain; pen-test vendor; ASV vendor. ~8 items in [p1-deferrals-acceptance.md](p1-deferrals-acceptance.md).
3. **Named-individual placeholders (E1, E3, E4, E8) must be replaced** before Phase 12 hand-off.

**What human approval enables (after `.human-approvals/implementation-approved.txt` is created with `APPROVED_FOR_IMPLEMENTATION`):**

- Phase-13 implementation work begins.
- Hooks now allow edits to `src/`, `pom.xml`, `infra/`, `.github/workflows/deploy-*.yml`.
- Each PR follows the change-control template; CI gates run.
- Milestones M1..M7 are picked up in branch-based PRs.

**What human approval does NOT enable:**

- Production deployment. That requires `.human-approvals/pci-production-approved.txt` with `APPROVED_FOR_PCI_PRODUCTION_RELEASE`, which itself requires Phases 10 + 11 to close.
- Bypassing the change-control / security-review / vulnerability-management SLAs.
- Modifying source-requirements.md (still locked).
- Modifying `.human-approvals/` (still hook-blocked and policy-blocked).

---

## 12. Human-approval action — what the human needs to do

To enable implementation, the human owner manually creates:

```
.human-approvals/implementation-approved.txt
```

with **exactly** this content (one line, no trailing whitespace, no quotes):

```
APPROVED_FOR_IMPLEMENTATION
```

For PCI security implementation approval (also required because of `security-profile.yml` = `pci_dss_tier1`), additionally create:

```
.human-approvals/pci-security-approved.txt
```

with **exactly**:

```
APPROVED_FOR_PCI_SECURITY_IMPLEMENTATION
```

Both files are hook-blocked from Claude-Code creation. The human owner must use a separate path (file manager, IDE save-outside-Claude, shell command). Claude Code's role ends at producing this recommendation.

Per [.human-approvals/README.md](../../.human-approvals/README.md):
- `.human-approvals/pci-production-approved.txt` is a **third** marker required for production deployment; it is **not** part of Phase 9 approval. It's created only after Phases 10 + 11 close.

---

## 13. Exit-criteria checklist

| Criterion | Status |
|---|---|
| All Phase-2/4/6/8 P0s closed or pinned at gate exit | ✅ (18 of 18) |
| All P1s either resolved or in p1-deferrals-acceptance.md with named owner + target gate | ✅ |
| Source-requirements.md vs design contradictions inspected | ✅ (none found) |
| Test-plan completeness verified | ✅ |
| SDLC-gate completeness verified | ✅ |
| Evidence-register completeness verified | ✅ (designed; collection in Phase 12/13) |
| OQ-010 (identity origin) marked BLOCKING-for-prod with Phase-12 hand-off path | ✅ |
| Phase-5/6 operational readiness anchors verifiable | ✅ (anchors math-verified Phase 6; load-test execution in Phase 12/13) |
| Phase-7/8 PCI evidence chain collectible | ✅ (designed; collection in Phase 12) |
| First implementation milestone defined and small | ✅ (§10) |
| Source-requirements.md untouched | ✅ |
| `.human-approvals/` untouched | ✅ |
| No implementation files created | ✅ |
| Recommendation recorded (READY_FOR_HUMAN_APPROVAL with conditions) | ✅ |
| Human-approval action documented | ✅ |

---

## 14. Linked artefacts

- [requirements-grill.md](requirements-grill.md) Phase 2
- [design-session.md](design-session.md) + [design-grill.md](design-grill.md) Phases 3 + 4
- [phase-3-prototype-log.md](phase-3-prototype-log.md) — Treasury rate-orientation empirical verification
- [day-1-ratifications.md](day-1-ratifications.md) + Day-2 ratifications (F1..F5)
- [operational-design-session.md](operational-design-session.md) + [reliability-scalability-grill.md](reliability-scalability-grill.md) Phases 5 + 6
- [pci-security-design-session.md](pci-security-design-session.md) + [pci-security-grill.md](../security/pci-security-grill.md) Phases 7 + 8
- [p1-deferrals-acceptance.md](p1-deferrals-acceptance.md) — definitive deferral acceptance ledger
- All `docs/requirements/`, `docs/architecture/`, `docs/operations/`, `docs/security/` artefacts
- [.human-approvals/README.md](../../.human-approvals/README.md) — human-approval marker requirements
- [CLAUDE.md](../../CLAUDE.md) §3 + §6A — implementation-blocking rules
