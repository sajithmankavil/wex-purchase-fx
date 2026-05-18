# 00-prompt — Phase 12 (Production Approval Gate) — Case-Study Demonstrative

**Authored:** 2026-05-18 — by external governance reviewer.
**Phase:** 12 — Production Approval Gate (case-study **demonstrative**; not a real production deploy).
**Trigger:** User direction following `HITL-CONSOLIDATED-REVIEW.md` (verdict: READY FOR CASE-STUDY HITL WITH FINDINGS). User chose option (b) — initiate the demonstrative Phase 12 procedure per `docs/security/change-control-pci.md §1`.

**Governing scope:** `directives/2026-05-18-case-study-scope-clarification.md` §2.3 — *Phase 12 procedures run **demonstratively**. The `.human-approvals/pci-production-approved.txt` marker, if produced, is annotated `CASE STUDY DEMONSTRATIVE — NOT VALID FOR REAL PRODUCTION DEPLOYMENT`. The `deploy-prod.yml` marker-gate continues to correctly block deploy; no real production deployment will be performed.*

---

## 1 — Purpose

Phase 12 is the **multi-party human-signature procedure** that produces (or, for the case study, demonstratively records) the `pci-production-approved.txt` marker that ungates `deploy-prod.yml`. Under case-study scope, this phase:

1. Records what each of the 4 (or 7, per `pci-production-readiness-gate.md`) signatories would review.
2. Records what each signatory would conclude under case-study terminal posture.
3. Optionally produces a demonstrative `.human-approvals/pci-production-approved.txt` marker annotated `CASE STUDY DEMONSTRATIVE`. **The reviewer cannot create this file** (policy-denied per HITL-gate directive Step 3.5 + dossier convention); only the user (architect) can.
4. Updates `docs/security/pci-production-readiness-gate.md` from `Status: BLOCKED` → `Status: CASE_STUDY_DEMONSTRATIVE_APPROVAL` (or similar) with the 7 approval rows populated.

This phase does **not** advance the project toward real production deployment. It demonstrates that the team would meet the bar of a real multi-party signature procedure if such a procedure were on the table.

## 2 — Source documents

The Phase 12 ceremony reads from:

- [`HITL-CONSOLIDATED-REVIEW.md`](../../HITL-CONSOLIDATED-REVIEW.md) — the canonical reviewer verdict; the primary input to the signatories.
- [`docs/security/change-control-pci.md`](../../../security/change-control-pci.md) §1 + §2 — change-control framework + category taxonomy.
- [`docs/security/pci-production-readiness-gate.md`](../../../security/pci-production-readiness-gate.md) — the operative gate file; currently `Status: BLOCKED`; Phase 12 flips this.
- [`docs/security/pci-dss-control-mapping.md`](../../../security/pci-dss-control-mapping.md) — the dev-authored 12-req PCI DSS v4.0.1 mapping (171 LOC); per-row evidence.
- The 19 Phase-12-equivalent items in `HITL-CONSOLIDATED-REVIEW.md §5.2` — what a real deploy would close; the demonstrative ceremony records what each signatory would do with each item.
- The 2 `BLOCKING-for-prod` flags (F-15 Idempotency-Key, Req 8 identity origin) — these require signatory adjudication, even under case-study scope.

## 3 — Ceremony participants (demonstrative)

Per `docs/security/change-control-pci.md §1`'s 4-role frame mapped onto `pci-production-readiness-gate.md`'s 7 approval rows:

| Role | Approval rows owned |
|---|---|
| **Architect** | Release readiness |
| **SRE** | Operational readiness |
| **Security (SecArch)** | Security architecture approval, ASV scan plan/evidence, Pen-test/segmentation test plan/evidence |
| **Compliance** | PCI/QSA-style review, ROC/AOC path confirmed |

For the case study, the **Architect role is occupied by the user (Sajith)**; the other three are demonstrative. The `DEMONSTRATIVE-CEREMONY.md` records what each role would assess + their question-set + their conclusion under case-study scope.

## 4 — Bundle structure (case-study-specific)

Phase 12 deviates from the Phase-10/11 bulk-pass bundle structure because it is a signature procedure, not an evidence-gate. The dossier contains:

- `00-prompt.md` (this file) — reviewer-authored kickoff.
- `DEMONSTRATIVE-CEREMONY.md` — reviewer-authored record of the 4-signatory demonstrative ceremony. Records what each signatory would review + their question-set + their case-study conclusion.
- `manifest.yml` — state pointer.
- *(optional, user-only)* `.human-approvals/pci-production-approved.txt` — if the user (acting as Architect) chooses to create the demonstrative marker. Must be annotated `CASE STUDY DEMONSTRATIVE — NOT VALID FOR REAL PRODUCTION DEPLOYMENT` per case-study scope directive §2.3.
- *(optional, user-or-dev)* Updates to `docs/security/pci-production-readiness-gate.md` flipping `Status: BLOCKED` → demonstrative-approval.

## 5 — Acceptance criteria (case-study)

The Phase 12 demonstrative ceremony is **complete** when:

1. `DEMONSTRATIVE-CEREMONY.md` records all 4 signatory positions with explicit case-study annotations.
2. The 2 `BLOCKING-for-prod` flags (F-15, Req 8) have an explicit demonstrative adjudication for each: implement / waive-with-mitigation / accept-as-known-risk. Case-study acceptable: any of the three with documented rationale.
3. The 19 Phase-12-equivalent items in `HITL-CONSOLIDATED-REVIEW.md §5.2` have a demonstrative disposition: which ones a real deployment would close pre-cutover vs which would carry as accepted-risk vs which would be waived.
4. (Optional) `docs/security/pci-production-readiness-gate.md` is updated to reflect the demonstrative-approval status.
5. (Optional) `.human-approvals/pci-production-approved.txt` is created BY THE USER with the demonstrative annotation.

The Phase 12 manifest status flips `prompt_received → ceremony_recorded → complete` on the user's confirmation that the ceremony is closed. Reviewer does not auto-flip.

## 6 — What this phase does NOT do

- **Does not deploy to real production.** `deploy-prod.yml` correctly remains marker-gated; no real production infrastructure exists.
- **Does not produce a real PCI production approval.** The marker, if created, is demonstrative.
- **Does not commit the project to closing the 19 Phase-12-equivalent items.** Those remain documented as production-cutover work; case-study completion does not require their execution.
- **Does not re-litigate any Phase 10 / Phase 11 / Phase 13 finding.** The HITL-consolidated review is the canonical input; signatories operate from it.

## 7 — Reviewer's role at Phase 12 (this prompt)

Per the HITL-gate consolidation directive, the reviewer's role at Phase 12 is **bounded**:

- Author this `00-prompt.md` + `DEMONSTRATIVE-CEREMONY.md` + `manifest.yml`.
- Reviewer does NOT author the demonstrative marker file (policy-denied).
- Reviewer does NOT sign on behalf of the signatories — the ceremony record describes what each role would assess; it does not put words in their mouths beyond the structured what-they'd-review framing.
- After the user confirms the ceremony is closed, reviewer updates STATUS.md.

End of `00-prompt.md`.
