# PCI Production Readiness Gate

Status: CASE_STUDY_DEMONSTRATIVE_APPROVAL
GO/NO-GO: CASE-STUDY GO (demonstrative); NO-GO for real production deploy
Owner: Sajith Mankavil (Architect; case-study)

> Case-study scope per `docs/external-review/directives/2026-05-18-case-study-scope-clarification.md` §2.3.
> The 7 approval rows below record demonstrative positions; real-deployment activation requires the
> 4-role signatory chain to re-engage with current evidence. No real production deploy will occur from
> the case-study repository.

## Required approvals
| Approval | Required? | Owner | Evidence | Status |
|---|---:|---|---|---|
| Security architecture approval | Yes | SecArch (demonstrative) | `docs/external-review/phases/12-production-approval/DEMONSTRATIVE-CEREMONY.md §4` | DEMONSTRATIVE-SIGNED (case-study) |
| PCI/QSA-style review | Yes | Compliance (demonstrative) | `DEMONSTRATIVE-CEREMONY.md §5` + `docs/security/pci-dss-control-mapping.md` | DEMONSTRATIVE-SIGNED (case-study); NOT-READY for real deploy |
| ROC/AOC path confirmed | Yes | Compliance (demonstrative) | `docs/security/qsa-roc-readiness.md` | DEMONSTRATIVE-SIGNED (case-study); execution Phase-12-equivalent |
| ASV scan plan/evidence | Yes | SecArch (demonstrative) | `docs/security/asv-scan-plan.md` | DEMONSTRATIVE-SIGNED (case-study); execution Phase-12-equivalent |
| Pen-test/segmentation test plan/evidence | Yes | SecArch (demonstrative) | `docs/security/penetration-test-plan.md` + `network-segmentation.md` | DEMONSTRATIVE-SIGNED (case-study); execution Phase-12-equivalent |
| Operational readiness | Yes | SRE (demonstrative) | `docs/external-review/phases/10-operational-readiness/30-review.md` + `DEMONSTRATIVE-CEREMONY.md §3` | DEMONSTRATIVE-SIGNED (case-study) |
| Release readiness | Yes | Architect (Sajith Mankavil) | `DEMONSTRATIVE-CEREMONY.md §2.3` + `HITL-CONSOLIDATED-REVIEW.md` | SIGNED (case-study Architect) |

## P0/P1 status
| Priority | Item | Owner | Due date | Status |
|---|---|---|---|---|
| P0 | F-15 Idempotency-Key (BLOCKING-for-prod) | Architect adjudication | Real-deploy activation | RESERVED — `DEMONSTRATIVE-CEREMONY.md §6.1` |
| P0 | Req 8 identity origin (OQ-010 BLOCKING-for-prod) | Architect adjudication | Real-deploy activation | RESERVED — `DEMONSTRATIVE-CEREMONY.md §6.2` |
| P1 | 19 Phase-12-equivalent items | Real-deploy production cutover | Real-deploy activation | CASE-STUDY-OUT-OF-SCOPE — `HITL-CONSOLIDATED-REVIEW.md §5.2` |

## Final decision
**For case study:** READY_FOR_CASE_STUDY_HITL — demonstrative ceremony complete. The dossier is assessor-ready.

**For real production deploy:** STILL BLOCKED — would require:
1. F-15 + Req 8 explicit adjudication by 4-role signatory chain.
2. Execution of the 19 Phase-12-equivalent items.
3. QSA-validated PCI DSS mapping.
4. Real `.human-approvals/pci-production-approved.txt` re-creation by the 4-role chain (not the demonstrative case-study version).

The demonstrative marker file at `.human-approvals/pci-production-approved.txt` is annotated `CASE STUDY DEMONSTRATIVE — NOT VALID FOR REAL PRODUCTION DEPLOYMENT` per the case-study scope directive and would be replaced — not merely re-signed — for any real production activation.
