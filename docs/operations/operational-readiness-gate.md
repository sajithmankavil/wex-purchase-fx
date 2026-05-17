# Operational Readiness Gate

> **Status:** **CONDITIONAL_PASS** — 2026-05-17 (end of Phase 5).
> **Go / No-Go for Phase 6:** **GO.**
> **Go / No-Go for Phase 10 (operational-readiness gate for production deployment):** **NO-GO** — Phase 6 (Reliability & Scalability Grill) must ratify the anchors, and Phase 7/8 (PCI Security Design + Grill) must close the audit-log and HMAC-key-sourcing items first.
>
> This gate is the Phase-5 exit verdict. The Phase-10 readiness gate is a different decision made after Phases 6-11 land.

---

## 1. Evidence checklist

| Area | Evidence | Status | Notes |
|---|---|---|---|
| Service catalog and owner | [service-catalog.md](service-catalog.md) | ✅ | Tier-2 prod / Tier-3 case study; placeholder names; structure durable. |
| SLIs / SLOs / error budget | [slo-sli.md](slo-sli.md), [error-budget-policy.md](error-budget-policy.md) | ✅ | SLO-C bounded by Treasury × cache-hit; multi-window multi-burn-rate alerts defined. |
| Observability design | [observability.md](observability.md) | ✅ | Event taxonomy, metric catalogue, cardinality budget, correlation-id server-binding, HMAC `vN:` prefix. |
| Monitoring and alerting | [monitoring-alerting.md](monitoring-alerting.md) | ✅ | 28 catalogued alerts + 6 burn-rate alerts; runbook anchors on every entry. |
| Dashboards | [monitoring-alerting.md §5](monitoring-alerting.md#5-dashboards) | ✅ (specified) | Five dashboards; JSON authoring in Phase 13. |
| Health and synthetic checks | [observability.md §7](observability.md#7-synthetic-checks) + [component-design.md §3.4](../architecture/component-design.md#34-startup--readiness-sequence) | ✅ | Pool-capacity in readiness (G4-P1-19); five synthetic checks. |
| Capacity and scalability | [capacity-scalability-plan.md](capacity-scalability-plan.md) | ✅ | Anchors documented; load-test scenarios for Phase 6/13 manual execution. |
| Failure modes and resilience | [failure-modes-and-resilience.md](failure-modes-and-resilience.md) | ✅ | 27 modes catalogued with detection / mitigation / recovery / test. |
| Load / performance testing | [capacity-scalability-plan.md §5](capacity-scalability-plan.md#5-load-test-plan-closes-g4-p1-25) | ✅ (documented) | Non-CI per source rule; 7 scenarios; tool: k6. |
| Incident / on-call process | [oncall-escalation.md](oncall-escalation.md) | ✅ | SEV1/2/3 ladder; escalation paths; six-phase incident loop; PIR template. |
| Runbook | [runbook.md](runbook.md) | ✅ | 25 alert-specific playbooks; smoke test; quick-action cheat sheet. |
| Rollback | [rollback-plan.md](rollback-plan.md) | ✅ (Phase 4) | Six rollback classes A–F; rehearsal cadence. |

## 2. P0 operational blockers

| ID | Blocker | Required fix | Owner | Status |
|---|---|---|---|---|
| — | None at Phase-5 exit. | — | — | — |

The Phase-2 + Phase-4 P0s were closed during Phase 3 / 4 / hardening. No new P0 was discovered during Phase 5.

## 3. P1 operational risks (open; tracked into later gates)

| ID | Risk | Mitigation | Owner | Status |
|---|---|---|---|---|
| **G4-P1-25** | Capacity anchors unverified by load test | Phase 6 ratifies | SRE | Open → Phase 6 |
| **G4-P1-17** | CB calibration (100 calls / 5 min open) untested at scale | Phase 6 chaos test | SRE | Open → Phase 6 |
| **G4-P1-2** | `effective_date >= record_date` empirically unverified beyond 24-record sample | Phase 6 10-year-window Treasury fetch | SRE / Architect | Open → Phase 6 |
| **G-P1-2** | Treasury effective-uptime baseline 99 % is conservative; needs 90-day passive observation | 90 days canary + monthly review | SRE | Open → ongoing |
| **G4-P1-11** | HMAC key sourcing in prod is plain env at v1 | Phase 7 picks platform retrieval (Vault Agent / CSI driver) | SecArch | Open → Phase 7 |
| **G4-P1-15** | App-layer rate-limiting decision | Phase 7 picks (yes/no on Resilience4j RateLimiter at controller) | SecArch | Open → Phase 7 |
| **G4-P1-16** | Audit-log destination unnamed | Phase 7 picks (S3 object-lock / managed audit service) | SecArch | Open → Phase 7 |
| **G4-P1-26** | Vulnerability-management cadence + SLA | Phase 7 documents | SecArch | Open → Phase 7 |
| **G4-P1-1** | ArchUnit cannot enforce "no `description` in log calls" — PMD/Checkstyle alternative | Phase 7 + Phase 13 ruleset | SecArch + Architect | Open → Phase 7/13 |
| **G4-P1-5** | `@Transactional` boundary expression in `ConversionService` | Phase 13 (`TransactionTemplate`) | Architect | Open → Phase 13 |
| **G4-P1-10** | OpenAPI consumer-usability for dual-mode currency | Phase 13 (`OpenApiSchemaIT`) | Architect | Open → Phase 13 |
| **G4-P1-23** | Graceful-shutdown test | Phase 13 (`GracefulShutdownIT`) | QA Lead | Open → Phase 13 |
| **G4-P1-24** | Idempotency-Key test set | Phase 13 (when feature lands) | QA Lead | Open → Phase 13 |
| **G4-P1-27** | RateOrientationContractCheck implementation | Phase 13 (weekly canary task) | SRE | Open → Phase 13 |

All P1 risks have explicit owners and target gates per the [p1-deferrals-acceptance.md](../planning/p1-deferrals-acceptance.md) acceptance ledger. The Phase-10 readiness gate (when it runs) will re-inspect each.

## 4. Decision

**CONDITIONAL_PASS for Phase 6.**

Phase 5 has produced:
- 8 operational deliverables (this set) plus Phase-4 rollback plan and observability spec.
- Closed P1s: G-P1-2, G4-P1-9, G4-P1-17, G4-P1-21, G4-P1-25 (anchor), G4-P1-27; plus OQ-014.
- Closed E1, E3, E4, E8 RACI shortfalls (structure ratified; named owners pending pre-production hand-off).

Phase 5 has *not* produced:
- Production-ready CI workflows (Phase 13).
- Load-test execution evidence (Phase 6 / 13).
- PCI evidence chain (Phase 7).

**Phase-10 readiness (the *real* operational-readiness gate before production deployment) cannot be GO until Phases 6, 7, 8, 9, and 13 close.** This Phase-5 gate is a "design ready" marker only.

## 5. Linked artefacts

- [operational-design-session.md](../planning/operational-design-session.md) — Phase-5 meta document.
- [requirements-grill.md](../planning/requirements-grill.md), [design-grill.md](../planning/design-grill.md) — Phases 2/4 grill records.
- [p1-deferrals-acceptance.md](../planning/p1-deferrals-acceptance.md) — explicit acceptance of P1 deferrals.
- [adr-0001-core-architecture.md](../architecture/adr-0001-core-architecture.md) — D-1..D-14 foundational decisions.
