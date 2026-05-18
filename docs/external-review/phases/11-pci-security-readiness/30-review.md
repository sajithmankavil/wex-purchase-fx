# 30-review — Phase 11 (PCI Security Readiness Gate) — PROVISIONAL (dev-authored)

**Author:** Dev agent (provisional verdict per `directives/2026-05-18-hitl-gate-consolidation-protocol.md` §2.1).
**Date:** 2026-05-18.
**Reviewed against:** branch `feature/phase-11-pci-security-readiness` HEAD (this commit) and the bundle at [`20-bundle.md`](20-bundle.md).
**Convention:** under the HITL-gate consolidation directive, the per-phase reviewer pass is replaced by a single consolidated reviewer pass at the HITL gate. The dev authors a **provisional** verdict that names the conditions the dev expects the consolidated review will ratify. Provisional verdicts overstating closure will be amended at the HITL gate and recorded as findings against the dev.
**Provisional verdict:** **ACCEPTED WITH CONDITIONS.**

This document is honest self-criticism, not advocacy. Where the bundle is weak or speculative, this review says so.

---

## 1 — Verification summary (dev-self)

Sample of bundle claims spot-checked against the branch:

| Bundle claim | Verification |
|---|---|
| [`pci-dss-control-mapping.md`](../../../security/pci-dss-control-mapping.md) authored as NEW deliverable per directive §6 | File exists on branch; 171 LOC; covers 12 PCI DSS v4.0.1 requirement groups with sub-req-level mapping. ✅ |
| Closure summary 18 CLOSED / 23 Phase-12-deferred / 7 n/a-evidenced / 1 BLOCKING-for-prod | Manually re-counted from the mapping doc. Counts approximate; some rows are dual-status (e.g., Req 6.4.1 CI scans = CLOSED but gating-flip = F1 carry-forward). Reviewer should not treat the counts as audited — they are dev's best-effort tally. ⚠️ |
| C2 closure: `MalformedIdentifierException.getMessage()` no longer embeds raw input | `git show HEAD:src/main/java/com/example/purchaseconversion/application/exception/MalformedIdentifierException.java` — `buildMessage()` produces `"malformed purchase identifier (length=" + input.length() + ")"`. Test in [`ProblemDetailExceptionHandlerTest.malformedId()`](../../../../src/test/java/com/example/purchaseconversion/api/advice/ProblemDetailExceptionHandlerTest.java) asserts `getMessage().doesNotContain(pan)` and `.doesNotContain("4242")`. ✅ |
| F1 gating flip NOT executed in this PR; documented for Phase 12 | `git diff HEAD..main -- .github/workflows/security.yml` shows no change. Bundle Decisions §2.2 explains the deferral. ✅ (claim consistent with disposition) |
| `oasdiff-vs-live-oas` Phase-12-deferred with M7 dependency | `.github/workflows/ci.yml` oasdiff step still uses `oasdiff diff infra/openapi/baseline.yaml infra/openapi/baseline.yaml` (self-diff tautology); no Maven invocation; bundle Decisions §2.3 names this honestly. ✅ |
| Audit-log destination is a Phase 12 BLOCKER with spec-side completion in `logging-monitoring-pci.md` | `docs/security/logging-monitoring-pci.md` exists at 151 LOC; sink-selection deferred consistent with §3 audit-destination spec. ✅ |
| Incident-response-pci.md is a 27-line stub | `wc -l docs/security/incident-response-pci.md` — confirmed 27 LOC. Bundle §1.2 + §2.6 + §3.5 surface this honestly. ✅ |

**No fabricated claims observed.** Where the bundle marks ⚠️ it explicitly names the gap.

---

## 2 — Open-questions adjudication (dev-provisional)

Per `20-bundle.md §4`, the bundle listed 5 open questions. Dev's provisional adjudication of each — the **reviewer at HITL-gate may amend any of these**.

### Q1 — Phase 11 prompt pre-stage ratification

**Provisional decision: RATIFY.** Content materially matches `directives/2026-05-18-phase-10-11-bulk-pass-protocol.md §6` (Phase 11 source-doc list). Bundle declares the bend explicitly in Decisions §2.1.

The Phase 10 30-review §F-INFO-3 lesson ("future phases should follow nominal flow") was authored before the HITL-gate consolidation directive; the new directive supersedes it for Phases 10–12 anyway. So the pre-stage is no longer a deformation — it's the **default** under the new directive.

### Q2 — F1 gating-flip deferral

**Provisional decision: ACCEPT THE DEFERRAL.** Per Decisions §2.2, the safer protocol is baseline-scan + triage + flip + validate, which requires Phase 12 infrastructure.

**Risk acknowledgement:** until Phase 12 lands the flip, HIGH/CRITICAL CVEs in transitive deps could ship to prod without CI blocking. This is the failure mode the consolidated HITL-gate reviewer should weigh. Mitigation: the Phase 12 PROMPT-AUTHOR'S hand-off list explicitly carries `security-workflow-gating-activation`; the human-signatory chain reviews it before marker creation.

### Q3 — `incident-response-pci.md` stub

**Provisional decision: DEFER TO PHASE 12 PREP** (Decisions §2.6 position). Rationale: the operational `incident-response.md` is the canonical IR; the PCI-specific diff should be small and focused (~50-80 LOC). Phase 12 tabletop prep is the natural surface for that authoring.

**Alternative the HITL reviewer should consider:** expand it now (~80 LOC). I judged the marginal value vs context budget and chose deferral; the reviewer may disagree.

### Q4 — Closure-summary counts defensibility

**Provisional decision: COUNTS ARE A BEST-EFFORT TALLY, NOT AN AUDIT.** Reviewer should re-count from the mapping doc rather than trust the bundle's claim. Some rows are dual-status (Req 6.4.1 is CLOSED for scans but the gating flip is its own carry-forward); the tally simplification may understate the residual work.

**Recommendation:** the consolidated HITL-gate review re-counts and amends if needed.

### Q5 — Audit-log destination C4

**Provisional decision: BLOCKER-with-Phase-12-deferral IS the correct posture.** Choosing a sink today (e.g., "CloudWatch Logs with object-lock retention") would be a speculative commitment that the platform team may revise. The spec side is complete in `logging-monitoring-pci.md`; the implementation choice is platform-team's decision at provisioning.

**Compensating mitigation already in place:** the audit-event taxonomy (`observability.md §2.2`) is sink-agnostic — any compliant destination will accept the structured events.

---

## 3 — Findings

### F-LOW-1 — `pci-dss-control-mapping.md` is dev-authored, not QSA-validated

**Severity:** LOW (process risk).

**Observation:** the 12-req mapping is detailed and traceable, but it has not been validated against a QSA's reading of PCI DSS v4.0.1. A QSA could:
- Disagree on n/a-evidence sufficiency for Req 3 (Account-Data Storage) — the boundary `ContentGuard` is presented as defense; a QSA might want stronger evidence that no PAN ever crosses the boundary at any layer.
- Disagree on Phase-12-deferred items they consider implementable today (e.g., Req 8 identity origin could be argued as solvable now).
- Find sub-requirements the dev missed (e.g., 12.10.5 incident-response testing cadence).

**Disposition:** the document is the dev's reading. Phase 12 QSA engagement is the canonical adjudication. Reviewer should not block on this; it gets the QSA pass at Phase 12.

### F-LOW-2 — Closure-summary counts uncertain

**Severity:** LOW (audit-trail clarity).

**Observation:** the bundle and the mapping both cite "18 CLOSED / 23 Phase-12-deferred / 7 n/a-evidenced / 1 BLOCKING-for-prod". Dev re-counted and found dual-status rows that complicate the tally (e.g., a row that's CLOSED for CI scans but ⚠️ for gating). The single-number summary obscures these.

**Disposition:** carry into HITL consolidated review for re-count. Non-blocking.

### F-INFO-3 — HITL-gate consolidation directive activation

**Severity:** INFO.

**Observation:** this is the **first** activation of `directives/2026-05-18-hitl-gate-consolidation-protocol.md`. The dev pre-staged 00-prompt.md + authored 20-bundle.md + authored this provisional 30-review.md + will set manifest status. Phase 10's 30-review.md (reviewer-authored at commit `4e0ead2`) is grandfathered as the provisional Phase 10 verdict per the directive §5 activation clause.

**Playbook entry pinned for the HITL-gate consolidated reviewer:** when consolidating, sample-verify the dev's provisional 30-review.md claims against the actual source-doc + delivered-artifact state on `main` at HITL trigger time. The dev's verdict carries the same audit weight as if reviewer-authored, but the consolidated reviewer's verification step is what makes it canonical.

### F-INFO-4 — Phase 11 PR DOES carry source code (C2 closure)

**Severity:** INFO.

**Observation:** Phase 10's 30-review §C2 routed `malformed-identifier-exception-message-redaction` to Phase 11 with the reasoning "Phase 10 is explicitly an evidence-only gate; a 3-LOC source change is *code*, not evidence, and belongs on a phase boundary that admits source modifications." This Phase 11 PR carries that 3-LOC change + 1 test assertion update. The PCI evidence-gate framing of Phase 11 IS consistent with admitting code changes (PCI is regulatory; security-relevant code lives here naturally).

The dev judges this acceptable. Reviewer ratifies or amends.

### F-INFO-5 — Forward-motion-bias mid-review branching pattern (third instance)

**Severity:** INFO.

**Observation:** Phase 11 was parallel-started off `feature/phase-10-operational-readiness` (HEAD `4e0ead2`) while Phase 10 PR #13 was still open. Phase 10 had not yet merged to main when Phase 11 work began. Same pattern as:
- C3 off C2 branch (Phase-13 first instance; Phase 10 30-review §F6 INFO recorded this).
- C2 off C branch (Phase-13 zero-th instance; Phase 13 C 30-review §1 INFO recorded this).
- (this) Phase 11 off Phase 10 branch (Phase-bulk-pass first instance).

The pattern repeats. The Phase 10 30-review §F6 flagged "if branching mid-review continues into Phase 10, escalate for a directive amendment." That was authored before the HITL-gate consolidation directive. The new directive's §2 effectively legitimises this pattern (dev authors all dossier files; flips happen at the dev's discretion until HITL gate). The mid-review branching is now **expected behaviour**, not deformation.

---

## 4 — Conditions (consolidated)

| # | Condition | Severity | Routing |
|---|---|---|---|
| C1 | `idempotency-key-blocking-for-prod` | HIGH (compliance posture) | Phase 12 multi-party human-signature procedure (no change from Phase 10 §C1) |
| C2 | `malformed-identifier-exception-message-redaction` | — | **CLOSED IN THIS PR** (source + test) |
| C3 | `oasdiff-vs-live-oas-pending-mvn` | MED | Phase 12 (paired with M7 Maven-in-runner provisioning) |
| C4 | `audit-log-destination-provisioning` | MED | Phase 12 (platform-team provisioning task) |
| F1 | `security-workflow-gating-activation` | MED | Phase 12 (baseline-scan + triage + flip + validate, per Decisions §2.2) |
| F-LOW-1 | `pci-dss-control-mapping-qsa-validation` | LOW (process) | Phase 12 QSA engagement |
| F-LOW-2 | `closure-summary-recount-at-hitl` | LOW (audit-clarity) | HITL-gate consolidated review |
| F-INFO-3 | `hitl-consolidation-first-activation-playbook` | INFO | Pinned for HITL-gate reviewer |
| F-INFO-4 | `phase-11-admits-code-changes-rationale` | INFO | Documented; reviewer ratifies |
| F-INFO-5 | `forward-motion-mid-review-branching-now-default` | INFO | New directive legitimises the pattern |
| **Hand-off** | 13 items inherited from Phase 10 §5 + Phase 11 §5 hand-off | mixed | Phase 12 prompt-author surfaces all 19 (6 PCI-specific + 13 ops-readiness) to human-signatory chain |

---

## 5 — Pre-merge protocol

Phase 11 PR is acceptable to open + merge under:

1. Dev opens PR from `feature/phase-11-pci-security-readiness` to `main`.
2. PR description references this provisional `30-review.md` + the `20-bundle.md §5` Phase-12 hand-off list.
3. CI green is the only pre-merge gate.
4. Merge under rebase strategy (consistent with prior).
5. **On merge:** dev flips `phases/11-pci-security-readiness/manifest.yml::status: under_review → accepted` per new directive §2.1 mechanical-transition authority. PR# and merge SHA recorded. **This is NOT the canonical "accepted" verdict — that comes from the HITL-gate consolidated review.** The manifest status reflects the dossier state-machine; the canonical verdict lives in `HITL-CONSOLIDATED-REVIEW.md` when the HITL gate fires.

---

## 6 — Forward-motion summary

- **Phase 10:** under_review on `main` until PR #13 merges; then accepted via dev mechanical flip (PR #12 dossier-flip handles this for C2 + C3 + future).
- **Phase 11:** bundle_posted → under_review on this commit; → accepted on PR merge per §5 step 5.
- **13-POST chunks:** none open. If one is opened between now and HITL gate, dev authors all four files per new directive §2.1.
- **HITL gate triggers (per new directive §2.3):**
  1. `docs/external-review/HITL-READY.md` created by dev.
  2. `.human-approvals/pci-production-approved.txt` request artifact created.
  3. User explicitly requests consolidated review.
- **Phase 12 hand-off:** 19 items total (6 Phase-11-specific + 13 inherited from Phase 10) — see [`20-bundle.md §5`](20-bundle.md).

End of provisional `30-review.md`.
