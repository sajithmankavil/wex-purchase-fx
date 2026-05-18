# Operating Model — WEX Purchase FX

**Purpose:** single-page index of how this project was authored. Five cross-cutting protocol directives accumulated over the project's 24-hour active window; this document consolidates them with evolution notes so an assessor can read the operating model in one place rather than reconstruct it from five separate files.

The original directives remain in [`directives/`](directives/) for full audit-trail provenance. This document is the cross-reference; conflicts between this document and an original directive resolve in favour of the original (newer-supersedes-older).

---

## 1. The operating rules at the end-state

These are the rules in force when the project reached terminal posture (Phase 12 case-study demonstrative ceremony closed, HITL consolidated review canonical).

### 1.1 Forward-motion bias

When the developer encounters ambiguity in a prompt or a borderline scope question, **make the most-defensible call, document the call in the bundle's `Decisions` section, and proceed**. The reviewer ratifies or amends; the developer does not block on round-trips for borderline judgments.

This was the foundational rule and never required revision. Originally authored as [`2026-05-17-forward-motion-bias.md`](directives/2026-05-17-forward-motion-bias.md).

### 1.2 Ops-check / PCI-check strict-mode deferral

The Python helpers `make ops-check` and `make pci-check` enforce documentation discipline in two modes:
- **Default (deferred):** warn on missing artifacts; do not fail the build.
- **Strict (production):** fail the build on any gap.

Strict mode flips on when `.human-approvals/pci-production-approved.txt` exists (case-study demonstrative or real). Pre-cutover, the checks remain deferred so iterative dossier work is not blocked. Originally authored as [`2026-05-17-ops-check-and-pci-check-strict-flip.md`](directives/2026-05-17-ops-check-and-pci-check-strict-flip.md); implemented by chunk `13-PRE-readiness-check-fix`.

### 1.3 Bulk-pass evidence-gate protocol (Phases 10 & 11)

Phases 10 (Operational Readiness) and 11 (PCI Security Readiness) are **evidence gates, not implementation phases**. Rather than per-artefact reviewer round-trips, the developer compiles all evidence into a single `20-bundle.md` per phase; the reviewer does one deep pass with one `30-review.md`.

Bundle structure is fixed: coverage-map table (source-doc requirement → delivered artefact → status), then Decisions, Risks, Open Questions, Phase-12 hand-off list. Originally authored as [`2026-05-18-phase-10-11-bulk-pass-protocol.md`](directives/2026-05-18-phase-10-11-bulk-pass-protocol.md).

### 1.4 HITL-gate review consolidation (Phases 10 / 11 / 13-POST)

The bulk-pass protocol's per-phase reviewer verdict round-trip was further consolidated into a **single reviewer pass at the HITL boundary** — when the developer believes the cumulative state is gate-ready, they create `HITL-READY.md` to trigger one reviewer-authored `HITL-CONSOLIDATED-REVIEW.md`. Per-phase `30-review.md` files become dev-authored *provisional* verdicts that the consolidated review ratifies or amends.

Phase 10's reviewer-authored `30-review.md` (landed before this directive) is grandfathered as the Phase 10 provisional verdict. Phase 11's `30-review.md` is dev-authored under this directive. Originally authored as [`2026-05-18-hitl-gate-consolidation-protocol.md`](directives/2026-05-18-hitl-gate-consolidation-protocol.md).

### 1.5 Case-study scope clarification

Architect direction received 2026-05-18: this project is a **case-study terminal deliverable that demonstrates production-deployability**, not a real production deployment. Phase-12 "execution" items (load-test runs, real-staging rollback rehearsals, ASV scans, audit-log destination provisioning, on-call rotation real names) are **case-study-out-of-scope**; the documented procedure / template / design is the gate-passing evidence.

`deploy-prod.yml`'s marker-gate continues to correctly block real deploys. The `.human-approvals/pci-production-approved.txt` marker, when created for the case-study, is annotated `CASE STUDY DEMONSTRATIVE — NOT VALID FOR REAL PRODUCTION DEPLOYMENT`. Originally authored as [`2026-05-18-case-study-scope-clarification.md`](directives/2026-05-18-case-study-scope-clarification.md).

---

## 2. Evolution notes (why each rule was needed)

Five directives in 24 hours is a lot. The honest narrative of why each appeared:

| Date / time | Directive | Trigger | Reflection |
|---|---|---|---|
| 2026-05-17 (early) | Forward-motion bias | Project's first chunk (`13-PRE-dossier-bootstrap`) raised the question: *if the reviewer is asynchronous, how does the developer not block?* | This was foundational. Should have been in CLAUDE.md from day one. Has not needed revision since. |
| 2026-05-17 (mid) | Ops/PCI-check strict-flip | The strict-mode failure on missing artefacts blocked the very first PR that tried to add the bundle scaffolding (chicken-and-egg). | Defensible deferral; the strict-mode trigger (production marker) is the right asymmetry. |
| 2026-05-18 (early) | Bulk-pass protocol | Phase 10 + 11 are not Phase 13. Per-artefact reviews would compound round-trip cost without quality gain because the evidence inventory is enumerated upfront in the source docs. | Reasonable protocol. The bundle structure (coverage map / Decisions / Risks / Open Questions) is the load-bearing contribution; the reviewer-pass model was further consolidated by §1.4 below. |
| 2026-05-18 (mid) | HITL-gate consolidation | Through Phase 13 + Phase 10, the reviewer was *ratifying* dev work rather than *discovering* new findings. Per-phase reviewer verdicts were costing O(N) ticks for value that consolidated at the Phase-12 boundary anyway. | Honest assessment: this directive legitimised a pattern the developer was already moving toward (dev-authoring provisional 30-review.md). Better to codify than to leave the pattern unowned. The consolidated reviewer pass at HITL is still a real review with sample-verification. |
| 2026-05-18 (late) | Case-study scope clarification | Phase 12 readiness gate's documented procedure requires real production infrastructure (audit-log destination, on-call paging, ASV vendor engagement). The case-study has none. Without an explicit scope-clarification, every Phase-12 item became a fake-block. | Necessary disambiguation. Codifies what was always true (case-study terminal) so reviewer and assessor share the same expectation about what "complete" means. |

### Honest meta-commentary

Five directives in 24 hours **does reflect process iteration**. Each one solved a real friction; none was decorative. But the volume is also a signal that the operating model was being built **alongside** the product rather than ahead of it. In a longer project this would be a smell — process should stabilise within the first phase or two. Here it stabilised at the HITL-gate consolidation directive and held steady through Phase 12.

For a real engagement (not a case-study), the lesson is: invest 4–8 hours upfront on the operating model rather than letting it grow organically. The cumulative cost of three protocol amendments mid-flight is higher than designing the model once.

---

## 3. The phase + chunk state machine

Used throughout the dossier; values flow:

```
Chunk:  prompt_received → deviation_surfaced? → implementing → summary_posted
                                              ↘ under_review → accepted / rejected
                                                            ↘ superseded (rare)

Phase:  prompt_received → bundling → bundle_posted → under_review → accepted
                                                                   ↘ gaps_returned (cyclic; max 1 revision)
```

`HITL-CONSOLIDATED-REVIEW.md` is the canonical verdict at the HITL boundary, regardless of whether individual phase `30-review.md` files are reviewer- or dev-authored.

---

## 4. Original directive files (audit-trail provenance)

| # | File | Active? |
|---|---|---|
| 1 | [`directives/2026-05-17-forward-motion-bias.md`](directives/2026-05-17-forward-motion-bias.md) | Yes |
| 2 | [`directives/2026-05-17-ops-check-and-pci-check-strict-flip.md`](directives/2026-05-17-ops-check-and-pci-check-strict-flip.md) | Yes (implemented in chunk `13-PRE-readiness-check-fix`) |
| 3 | [`directives/2026-05-18-phase-10-11-bulk-pass-protocol.md`](directives/2026-05-18-phase-10-11-bulk-pass-protocol.md) | Yes; superseded-in-part by §4 below |
| 4 | [`directives/2026-05-18-hitl-gate-consolidation-protocol.md`](directives/2026-05-18-hitl-gate-consolidation-protocol.md) | Yes |
| 5 | [`directives/2026-05-18-case-study-scope-clarification.md`](directives/2026-05-18-case-study-scope-clarification.md) | Yes |

No directives have been superseded outright; supersession-in-part is noted where applicable. The original files are immutable per the dossier convention (`README.md` §"Immutability rule").

---

End of `OPERATING-MODEL.md`.
