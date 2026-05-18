# Directive — HITL-gate consolidation of reviewer verdicts

**Author:** External governance reviewer, recording user (architect / PO) decision.
**Date:** 2026-05-18
**Applies to:** Phase 10 onward (Operational Readiness, PCI Security Readiness, and any `chunks/13-POST-*/` items that surface between Phase 10 and Phase 12).
**Does NOT apply to:** Phase 12 (production approval) itself — the HITL gate is the consumer of this consolidation, not its subject.
**Supersedes (in part):** `directives/2026-05-18-phase-10-11-bulk-pass-protocol.md` §4 (the per-phase reviewer-verdict round-trip). The bulk-pass artifact structure (`00-prompt.md` / `20-bundle.md` / `30-review.md` / `manifest.yml`) still applies. The change is to **who writes `30-review.md` and when the substantive reviewer pass runs**.

## 1 — Why

Per-phase reviewer ticks have produced compounding friction:

- The dev has demonstrated through Phases 13-A1 / A2 / B1 / B2 / C / C2 / C3 + Phase 10 that they read the source docs and the directives faithfully, self-identify findings, and route conditions forward in the manifest accurately. The per-phase reviewer verdict is consistently ratifying rather than discovering.
- The reviewer's per-phase deep pass costs O(N) reviewer ticks across N phases, but the cumulative state is what the human signatory cares about at the Phase 12 sign-off. Verdicts can be consolidated without quality loss.
- "Just before HITL" is when the architect / PO needs the audit trail anyway. Spreading the audit work across N phases delays it and produces N verdict documents that get superseded by the Phase 12 consolidated read.

This directive codifies a single reviewer pass at the HITL boundary, with the dev moving autonomously through Phases 10 + 11 + 13-POST in the meantime.

## 2 — The new protocol

### 2.1 Dev's authority through Phases 10 + 11 + 13-POST

The dev may author **all four** dossier files for any phase or 13-POST chunk between Phase 10 and Phase 12, including:

- `00-prompt.md` (formerly reviewer-territory per the bulk-pass directive §4 Step 1).
- `20-bundle.md` / `20-summary.md` (already implementer-territory).
- `30-review.md` (formerly strictly reviewer-territory).
- `manifest.yml` (already dual-authored).

The dev's `30-review.md` is **provisional** until the consolidated HITL-gate review ratifies or amends it. It carries the verdict the dev expects the reviewer will reach, with the same structure as a reviewer-authored 30-review (verdict, findings, conditions matrix, routing). The dev is expected to be honest and self-critical — provisional verdicts that overstate closure will be amended at the HITL-gate review and recorded as findings against the dev.

Status flips (`bundle_posted → under_review → accepted` for bulk-pass phases; `summary_posted → under_review → accepted` for 13-POST chunks) may be performed by the dev as mechanical transitions. The dev should NOT use `rejected` — that verdict is reserved for the consolidated reviewer.

### 2.2 Reviewer's authority through Phases 10 + 11 + 13-POST

The scheduled `wex-external-review-tick` continues to poll but its behaviour changes:

- **Default action:** read `STATUS.md` + every active phase / 13-POST manifest. If no HITL-trigger condition is met (see §3 below), report "no consolidated review needed" and exit.
- **Do NOT** write `30-review.md` for phases or 13-POST chunks between Phase 10 and Phase 12. Those are dev-authored under this directive.
- **Do NOT** flip manifest status. Dev owns mechanical transitions.
- **Do** write a `25-review-feedback.md` in a phase / chunk folder ONLY if the dev's provisional `30-review.md` overstates closure to a degree that would mislead the Phase 12 signatories. Use sparingly; default is to wait for the HITL-gate consolidated review.
- **Do** amend or write directives if a cross-cutting pattern emerges that needs codification before the HITL gate.

### 2.3 The HITL-gate consolidated review

Triggered by **any** of:

1. The dev creates a marker file `docs/external-review/HITL-READY.md` indicating they believe all upstream work is complete and the HITL gate is ready for the reviewer pass.
2. The dev creates `.human-approvals/pci-production-approved.txt` request artifact (the file that the multi-party signatories will sign) — even if not yet signed.
3. The user (architect / PO) explicitly requests the consolidated review.

The consolidated review:

- Reads **all** active phase / chunk manifests + dev-authored `30-review.md` files in scope (Phase 10, Phase 11, every `13-POST-*` chunk between them).
- Reads the cumulative `STATUS.md` history.
- Verifies a representative sample of each dev-provisional verdict against the actual source-doc + delivered-artifact state on `main`.
- Produces **one** reviewer-authored consolidated verdict at `docs/external-review/HITL-CONSOLIDATED-REVIEW.md`.
- The consolidated verdict is the audit trail for the Phase 12 multi-party signatory procedure per `docs/security/change-control-pci.md §1`. It is **not** a substitute for the human signatures — it is the input to them.

### 2.4 What stays the same

- The dossier file naming convention (`00-prompt.md` / `20-bundle.md` / `30-review.md` / `manifest.yml`).
- The immutability rule (numbered files are immutable once authored; revisions are new files with version suffixes).
- The bulk-pass directive §5 bundle structure (coverage map / Decisions / Risks / Open questions).
- The Phase 12 multi-party signature procedure per `docs/security/change-control-pci.md §1`. Phase 12 is **not** consolidated under this directive — it has its own human-signatory path.
- All Phase-13 chunk reviews already written (`chunks/13-A1/30-review.md` through `chunks/13-C3-openapi-cicd/30-review.md` + the v2 self-correction). Those are canonical reviewer-authored and remain so.

## 3 — Why the HITL-gate trigger is asymmetric

The dev triggers the consolidated review by writing `HITL-READY.md` (or by surfacing the `.human-approvals/` request artifact). The reviewer does NOT independently decide "the work is done" — that's the dev's judgment call. The reviewer's pass then verifies that judgment against the dossier state.

Asymmetric trigger means: the reviewer is awakened only when the dev is ready, not on a schedule. This prevents the prior pattern of multiple reviewer passes through partial state.

## 4 — Risks of this protocol

Honest accounting of the risks of consolidating verdicts:

1. **Dev's provisional verdict could overstate closure.** Mitigation: the consolidated review samples + verifies; findings against the dev are recorded.
2. **A real defect could escape multiple phases before the consolidated review surfaces it.** Mitigation: dev is expected to honestly self-identify; if a defect is found at HITL gate that should have been caught earlier, it gets recorded as a learning, not a re-litigation. The Phase 12 signatory adjudicates whether the defect is gate-blocking.
3. **The Phase 12 HITL gate could surface a verdict-amendment that contradicts a dev-provisional `30-review.md`.** Mitigation: the consolidated review writes corrections via amendment files; dev-authored verdicts remain on disk as audit trail.
4. **The polling cron could continue to do per-phase work despite this directive.** Mitigation: the scheduled-task prompt should be updated to read this directive at the top of every tick and short-circuit to "no consolidated review needed" when no trigger condition (§3) is present.

## 5 — Activation

Effective **2026-05-18 immediately**. The dev's already-authored `phases/10-operational-readiness/30-review.md` (commit `4e0ead2`) is **the provisional Phase 10 verdict** under this directive — no reviewer rewrite required. The Phase 11 dossier (when authored) and any `chunks/13-POST-*` follow the same rule.

The scheduled `wex-external-review-tick` cron prompt SHOULD be updated to:

1. Read this directive at the top of every tick.
2. Check for the HITL trigger conditions in §3.
3. If no trigger: report "no consolidated review needed" and exit.
4. If triggered: produce the consolidated review at `HITL-CONSOLIDATED-REVIEW.md`.

The reviewer (operating under the new cron prompt or invoked by the user directly) reads this directive on every action and obeys it.

## 6 — Revising this directive

If the consolidated-review-at-HITL pattern produces a worse Phase 12 outcome than the per-phase pattern would have (e.g., a defect escapes that a per-phase pass would have caught), the reviewer is empowered to author `directives/YYYY-MM-DD-hitl-consolidation-revision.md` proposing a return to per-phase ticks for specific high-risk phases. The user adjudicates the proposal.

End of directive.
