# External-Review Status

> Auto-target: `make status` once Java/Make wired (post-M7). Hand-maintained until then.

| Chunk ID | Status | PR | Merged | Next |
|---|---|---|---|---|
| 13-PRE-dossier-bootstrap | accepted | #3 (squash) | `b762bec` | — closed |
| 13-PRE-readiness-check-fix | accepted | #4 (squash) | `fdfe8e9` | — closed |
| 13-A1-domain | accepted | #2 (rebase) | `04f19aa` (plant `3e03380` + revert `04f19aa` preserved) | — closed |
| 13-A2-application | implementing | — | — | implementer authoring on `feature/chunk-a2-application` |
| 13-B-infrastructure | prompt_received | — | — | starts after A2 merges |
| 13-C-api-observability | prompt_received | — | — | starts after B merges |

Status enum: `prompt_received | deviation_surfaced | implementing | summary_posted | under_review | accepted | rejected | superseded`.

## Phase 13 chunk plan (live)

```
13-PRE-dossier-bootstrap        ← accepted, merged b762bec
13-PRE-readiness-check-fix      ← accepted, merged fdfe8e9
13-A1-domain                    ← accepted, merged 04f19aa (plant+revert preserved)
13-A2-application               ← implementing (in-flight on feature/chunk-a2-application)
13-B-infrastructure             ← prompt_received (pre-staged)
13-C-api-observability          ← prompt_received (pre-staged) — final chunk
```

## Autonomous-handoff protocol (added 2026-05-17)

All four upcoming chunks (A1 merge, A2, B, C) have their `00-prompt.md` and `manifest.yml` pre-staged. The implementer reads `STATUS.md` + the next-pending chunk's `00-prompt.md` at the start of every session — no reviewer round-trip is required to start the next chunk after the previous one merges. The reviewer's role is per-PR `30-review.md` writing, which is triggered by `manifest.status: summary_posted` on any chunk.

True file-watch ("watch for each other and go") requires a GitHub Action on `docs/external-review/**` push events. Proposed but deferred (post-M7); see `docs/external-review/README.md` §"Authoring + ping protocol".

## Cross-cutting directives in force

- [`directives/2026-05-17-ops-check-and-pci-check-strict-flip.md`](directives/2026-05-17-ops-check-and-pci-check-strict-flip.md) — defers strict mode in `ops-check` + `pci-check` to the production-approval marker. Implemented by `13-PRE-readiness-check-fix` (accepted 2026-05-17).

## Recent reviewer activity

- 2026-05-17 — Withdrew §5 line-ending finding on `13-PRE-dossier-bootstrap` after implementer's `35-clarification.md` showed PR diff is `+790/-0` (pure additions, no churn); my original `git diff` was working-tree-vs-index on Windows-CRLF + LF-index, not the actual PR diff. See `chunks/13-PRE-dossier-bootstrap/30-review-v2.md`. PR #3 now has zero pre-merge conditions.
- 2026-05-17 — Pre-staged `13-A2-application` + `13-B-infrastructure` + `13-C-api-observability` (00-prompt + manifest each). Implementer can chain through all four remaining chunks without further reviewer round-trips for kickoff.
- 2026-05-17 — Code-correctness accepted `13-A1-domain` (PR #2). `chunks/13-A1-domain/30-review.md`. CI-verification awaits rebase-on-main post-PR-#4 merge.
- 2026-05-17 — Accepted `13-PRE-readiness-check-fix` (PR #4). Verdict: `chunks/13-PRE-readiness-check-fix/30-review.md`. Independent pytest 7/7 pass.
- 2026-05-17 — Accepted `13-PRE-dossier-bootstrap` (PR #3) with one pre-merge condition (line-ending normalization). Verdict: `chunks/13-PRE-dossier-bootstrap/30-review.md`.

## Recent implementer activity

- 2026-05-17 — Ran full merge sequence per `30-review-v2.md`: PR #4 → PR #3 → rebase A1 → PR #2. All four merged. A1's `manifest.status` flipped to `accepted` under forward-motion bias (mechanical transition; substantive review was `30-review.md` §1-§4). Started A2 on `feature/chunk-a2-application`; flipped A2 manifest to `implementing`.
- 2026-05-17 — Pushed back on the §5 line-ending finding in `chunks/13-PRE-dossier-bootstrap/30-review.md`. Actual PR #3 diff on origin is `+790 / -0` across 13 new files (no churn). Evidence in `chunks/13-PRE-dossier-bootstrap/35-clarification.md`. Reviewer withdrew the finding in `30-review-v2.md`.

## Pending follow-ups (non-blocking)

- After Chunk A1 closes: open a separate dossier chunk to wire `scripts/tests/` into CI (`requirements.txt` + `scripts/quality/test.sh` discovery + pip-install step in the workflow). Tracked in `chunks/13-PRE-readiness-check-fix/30-review.md` §4.
