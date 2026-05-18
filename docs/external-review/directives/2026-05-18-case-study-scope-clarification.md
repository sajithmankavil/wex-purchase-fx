# Directive — Case-study scope clarification: production-deployable, not deployed

**Author:** External governance reviewer, recording user (architect / PO) direction.
**Date:** 2026-05-18
**Applies to:** All phases, all chunks, and the HITL-consolidated review. Particularly Phase 10 (Operational Readiness Gate), Phase 11 (PCI Security Readiness Gate), and Phase 12 (Production Approval).
**Companion to:** `directives/2026-05-18-hitl-gate-consolidation-protocol.md` — same effective date; this directive disambiguates the *semantics* of the gates that protocol consolidates.
**Supersedes (in part):** Implicit assumptions in `docs/security/change-control-pci.md §1` and `docs/operations/operational-readiness-gate.md` that a *real* production deployment will occur. For the case-study deliverable, the production-cutover steps are demonstrative artifacts, not operational events.

## 1 — Why this directive exists

Re-read of the project framing (architect / PO direction, 2026-05-18): **this is a case-study deliverable that must demonstrate production-deployability. It is not, and will not be, a real production deployment.**

The case study's audience is a hiring panel / assessor (likely WEX). The bar is "would a competent enterprise engineering organisation accept this design + implementation + operational evidence + security posture as production-ready if they were about to deploy it?" — **not** "did this team actually deploy it and operate it through a real outage?"

This distinction is material because the prior dossier work assumed the latter: Phase 10's bundle has 5 `BLOCKER:` rows that all defer to "Phase 12 staging shakedown / live infrastructure"; Phase 11's PCI procedures reference "the production audit-log destination"; Phase 12 itself is structured as a multi-party signature procedure producing a real `pci-production-approved.txt` marker that ungates `deploy-prod.yml`. None of those Phase 12 outputs will occur for real. The dossier must adjudicate them as **demonstrative artifacts that prove the team would meet the bar if a real deployment were on the table**.

## 2 — The new gate semantics

### 2.1 What "production-deployable" means for this case study

The case study delivers production-deployability evidence in five dimensions. The dossier must demonstrate each:

1. **Architecture** — ADRs, component design, deployment architecture, PCI scope diagrams, threat model. All authored.
2. **Implementation** — Java code passing the test suite, ArchUnit fitness functions, JaCoCo + Pitest gates, idempotent migrations, observability instrumentation. All delivered in Phase 13.
3. **Operations** — runbooks, SLO/SLI definitions, dashboards-as-JSON, drill *templates*, incident-response *process*, rollback *procedures*. Delivered as procedural artifacts in Phase 10. **Execution evidence (drill logs, load-test output, real on-call rotations) is out of scope** because no infrastructure exists to execute against.
4. **Security** — PCI scope analysis, encryption-key-management procedure, audit-log destination *design*, change-control *procedure*, vulnerability scanners *configured*, deploy-prod marker-gate *implemented*. Delivered in Phases 13 + 11. **Live audit-log destination, live secrets backend, live network segmentation are out of scope**.
5. **Change control** — `docs/security/change-control-pci.md §1`'s multi-party signature procedure is *documented and demonstrated* via the dossier's audit trail; the `.human-approvals/pci-production-approved.txt` marker, if produced for the case study, is **demonstrative only** (annotated as case-study artifact, not a real production approval). Real production deploy remains correctly blocked by `deploy-prod.yml`'s marker-gate at all times.

### 2.2 What is OUT of scope (and how the dossier records it)

Items that require real production infrastructure to *execute* are out of scope for case-study completion. The dossier records each such item with:

- Acknowledgement in the relevant phase's `30-review.md` and `manifest.yml`.
- Routing as a "case-study-out-of-scope; would be closed before real production deploy" item.
- An explicit pointer to the documented procedure / template / design that PROVES the team would meet the requirement if real infrastructure existed.

Specifically:

| Item | Phase | Out-of-scope disposition |
|---|---|---|
| Load-test execution against capacity anchors | 10 → 12 punch-list | k6 + WireMock-Treasury procedure documented in `capacity-scalability-plan.md §5`; execution would happen at real staging |
| CB-calibration drill log | 10 → 12 punch-list | Drill template + scenario documented; execution would happen at real staging |
| Class-A / Class-B rollback rehearsal logs | 10 → 12 punch-list | Procedures documented in `rollback-plan.md §4.2 / §4.3`; rehearsal would happen at real staging |
| First quarterly tabletop drill log | 10 → 12 punch-list | Cadence documented in `incident-response.md §5`; first execution at real staging cutover |
| PagerDuty integration provisioned | 10 → 12 punch-list | Routing config + alert conditions documented in `monitoring-alerting.md §3`; live wiring at platform-team provisioning |
| On-call rotation real names populated | 10 → 12 punch-list | Rotation structure + escalation tree documented in `oncall-escalation.md §2`; names populated at real team formation |
| Live audit-log destination | 11 → 12 punch-list | Destination + retention + integrity design in `logging-monitoring-pci.md`; live sink at real infra provisioning |
| oasdiff vs LIVE OAS | 11 → 12 punch-list (M7-paired) | CI workflow shape correct; live OAS regeneration requires Maven-in-runner (M7 carry-forward) |
| Security workflow gating activation (HIGH/CRITICAL blocks merge) | 11 punch-list | Scanners installed; `continue-on-error: true` flip is a single-commit change; would be done at real production cutover when scan-result baseline exists |
| Idempotency-Key (F-15) | 12 signatory adjudication | P1 / BLOCKING-for-prod flag preserved in `failure-modes-and-resilience.md §1 F-15`; case-study assessor weighs whether the documented gap is acceptable for the case-study purposes |
| Deploy workflow marker freshness check | 12 punch-list | Marker-gate logic implemented; "newer than commit" check is a small enhancement; would land at real production-cutover work |

The case study is **complete** when every item in this table has a documented procedure / template / design in the dossier, NOT when every item has been executed.

### 2.3 What Phase 12 looks like for the case study

Phase 12 in `docs/security/change-control-pci.md §1` describes a multi-party signature procedure. For the case study:

- The procedure runs **demonstratively**. The dossier produces an audit-trail entry showing what each signatory would review.
- The `.human-approvals/pci-production-approved.txt` marker, if produced, is annotated `CASE STUDY DEMONSTRATIVE — NOT VALID FOR REAL PRODUCTION DEPLOYMENT` at the top of the file.
- The `deploy-prod.yml` workflow's marker-gate continues to correctly block deploy on the marker's presence + token; **no real production deployment will be performed even if the marker is created**, because no real production infrastructure exists.
- The case-study assessor (the HITL audience) reads the `HITL-CONSOLIDATED-REVIEW.md` + the dossier and adjudicates whether the team has demonstrated production-deployability.

### 2.4 The HITL-CONSOLIDATED-REVIEW.md verdict enum, refined

The verdict enum in `directives/2026-05-18-hitl-gate-consolidation-protocol.md` Step 3.3 is refined for case-study purposes:

| Verdict | Meaning |
|---|---|
| **READY FOR CASE-STUDY HITL** | Every item in §2.2 has a documented procedure / template / design; no defects escape the dossier; case-study assessor may proceed with their evaluation. |
| **READY FOR CASE-STUDY HITL WITH FINDINGS** | Same as above but with non-blocking findings the assessor should weigh (e.g., a documented procedure has known gaps; an architectural choice has trade-offs the assessor should consider). |
| **NOT READY FOR CASE-STUDY HITL** | One or more dossier items are missing / contradictory / overstate closure. Specific gaps enumerated; dev must address before assessor evaluation. |

The phrase "production deployment remains blocked" continues to appear in the dossier, but its meaning is now: **the `deploy-prod.yml` marker-gate is correctly enforcing the gate; real production deploy was never the case-study's goal.**

## 3 — What this directive does NOT change

- **The marker-gate logic in `deploy-prod.yml`** stays as-is. It correctly enforces presence + token of `.human-approvals/pci-production-approved.txt` plus strict ops-check + pci-check + production-checklist files. That logic IS the case-study's "production-deployable" evidence for the deploy gate.
- **The directive `2026-05-17-ops-check-and-pci-check-strict-flip.md`** stays as-is. Strict mode flips on production-approval marker presence; this is correct gate logic regardless of whether the deploy is real or demonstrative.
- **All Phase 13 chunk reviews (A1 / A2 / B1 / B2 / C / C2 / C3)** stay as-is. Those reviewed implementation correctness against the documented requirements; the case-study framing does not retroactively change those verdicts.
- **The HITL-consolidation directive** stays as-is. Same trigger conditions, same consolidated-review structure. This directive only refines the *semantics* of the verdict, not the *procedure* for producing it.

## 4 — Effect on in-flight items

- **Phase 10 `30-review.md`** (dev-authored, provisional). Conditions routed to "Phase 12" are re-tagged as "case-study-out-of-scope (Phase-12-equivalent)" by the consolidated review. No rewrite of Phase 10 `30-review.md` is required; the consolidated review applies this directive when interpreting it.
- **Phase 11 `30-review.md`** (dev-authored, provisional). Same re-tagging at consolidated-review time.
- **F-15 Idempotency-Key.** Phase 10 30-review's option (b) — preserve BLOCKING-for-prod flag for Phase 12 signatory adjudication — is **ratified under case-study scope**. The case-study assessor is the signatory equivalent. Option (a) (open `13-POST-idempotency-key` and implement 600 LOC) is **not required for case-study completion** and the dev is free to skip it. If the dev does choose to implement (e.g., to demonstrate the implementation pattern), it's a value-add rather than a gate item.
- **Any other `13-POST-*` chunk** dev surfaces under this directive: same logic — if the chunk closes a real-production-only gap, it's value-add; if it closes a design / procedure gap, it's gate-relevant.

## 5 — Activation

Effective **2026-05-18 immediately**. The next HITL-CONSOLIDATED-REVIEW.md (when triggered) applies this directive's semantics. The dev agent reads this directive when authoring further provisional 30-review files and adjudicates BLOCKER routing accordingly.

The scheduled `wex-external-review-tick` cron's behaviour does not change — it reads governing directives on every tick per the HITL-consolidation directive §5. This directive is now in that read set.

## 6 — Revising this directive

If at consolidated-review time the reviewer judges that the case-study scope clarification has been over-applied (e.g., something that genuinely IS a design gap is being waived as "production-cutover-out-of-scope"), the reviewer authors `directives/YYYY-MM-DD-case-study-scope-tightening.md` and amends the consolidated review accordingly. The user adjudicates.

End of directive.
