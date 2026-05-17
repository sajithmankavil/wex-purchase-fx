# 00-prompt — Chunk 13-PRE-dossier-bootstrap

> Canonical kickoff for the dossier-bootstrap chunk. This is the "Work item 1" body of the reviewer's 2026-05-17 dual-prompt (which also commissioned the readiness-check trigger fix as `13-PRE-readiness-check-fix`).

## Title

External-review dossier scaffold (Phase 13 process).

## Branch

`feature/external-review-dossier-bootstrap` off `main`. Documentation-only change; scripts and `src/` untouched.

## Reviewer rulings (applied verbatim from the kickoff)

1. **Dossier design — implementer proposal accepted with four refinements:**
   - The four numbered markdown files (`00-prompt.md`, `10-deviation.md`, `20-summary.md`, `30-review.md`) are **immutable once authored**. Revisions = new files (e.g., `15-clarification.md`, `30-review-v2.md`). `manifest.yml` is the **only mutable** file per chunk.
   - **Authorial limits enforced by convention** (not hook policy): reviewer writes `00`, `30`, `STATUS.md`, and may flip `manifest.status: under_review → accepted/rejected`. Implementer writes `10`, `20`, and may flip `prompt_received → implementing → summary_posted`. Both can read everything.
   - **Cross-cutting directives** that span multiple chunks live in `docs/external-review/directives/YYYY-MM-DD-<topic>.md` — not inside a chunk folder.
   - **`STATUS.md` is hand-authored for now**, generated later via `make status` once Java/Make is live (post-M7).
2. **Open-question answers from the implementer's design proposal:**
   - GitHub Action trigger → defer until M7 (no Java CI yet); human-relayed pings work today.
   - `docs/external-review/` scope-protected? → No; both sides write, with authorial limits by convention.
   - `30-review.md` gates `make ci`? → Defer until post-M7; then yes (process-as-fitness-function).
   - Sweep policy? → Keep `chunks/<id>/` forever; move to `chunks/archive/<phase>/` after the parent phase fully closes.

## Scope (file mandate)

On a fresh branch off `main`, scaffold:

```
docs/external-review/
├── README.md                          ← REWRITTEN per the rules above
├── STATUS.md                          ← NEW; one-line-per-chunk rollup
├── directives/
│   └── 2026-05-17-ops-check-and-pci-check-strict-flip.md   ← KEEP (reviewer-authored; git-track)
├── chunks/
│   ├── 13-A1-domain/{00-prompt.md, 10-deviation.md, 20-summary.md, manifest.yml}
│   ├── 13-PRE-readiness-check-fix/{00-prompt.md, manifest.yml}
│   └── 13-PRE-dossier-bootstrap/{00-prompt.md, 20-summary.md, manifest.yml}
```

Delete the existing loose file `docs/external-review/2026-05-17-chunkA-loc-cap-revision.md` — its content is absorbed into `chunks/13-A1-domain/10-deviation.md`.

## Hard constraints

- Only writes inside `docs/external-review/`. No edits anywhere else.
- One PR: `feature/external-review-dossier-bootstrap` → `main`. Title: `External-review dossier bootstrap (Phase 13 process)`.
- Documentation-only change; CI should be green (no Java touched; no scripts touched; the readiness-check strict-flip does NOT trigger on docs-only branches because `src/` is not introduced).
- PR description carries: `change-id: WEX-PROCESS-dossier-bootstrap`, `rollback class: A`, `risk: low (documentation-only)`.

## §9 completion summary

Write the §9 summary to `chunks/13-PRE-dossier-bootstrap/20-summary.md` (in this PR's diff).
