# External-Review Status

> Auto-target: `make status` once Java/Make wired (post-M7). Hand-maintained until then.

| Chunk ID | Status | PR | Open deviations | Next |
|---|---|---|---|---|
| 13-PRE-dossier-bootstrap | accepted (no pre-merge conditions; see 30-review-v2.md) | #3 | — | implementer may merge per the consolidated sequence |
| 13-PRE-readiness-check-fix | accepted | #4 (CI green; reviewer sandbox pytest 7/7 pass) | — | implementer merges PR #4 first; A1 rebases on main |
| 13-A1-domain | under_review | #2 | ops-check-strict-flip (resolved by #4 merge; pending rebase) | rebase on main after #4 merges; reviewer 30-review.md |

Status enum: `prompt_received | deviation_surfaced | implementing | summary_posted | under_review | accepted | rejected | superseded`.

## Phase 13 chunk plan (live)

```
13-PRE-dossier-bootstrap        ← accepted (cond: line-ending normalize); PR #3
13-PRE-readiness-check-fix      ← accepted; PR #4 — unblocks A1 CI
13-A1-domain                    ← code-correctness accepted; CI verification pending rebase; PR #2
13-A2-application               ← prompt_received (00-prompt + manifest pre-staged 2026-05-17)
13-B-infrastructure             ← prompt_received (00-prompt + manifest pre-staged 2026-05-17)
13-C-api-observability          ← prompt_received (00-prompt + manifest pre-staged 2026-05-17) — final chunk
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

- 2026-05-17 — Pushed back on the §5 line-ending finding in `chunks/13-PRE-dossier-bootstrap/30-review.md`. Actual PR #3 diff on origin is `+790 / -0` across 13 new files (no churn). Evidence in `chunks/13-PRE-dossier-bootstrap/35-clarification.md`. Awaiting reviewer response before running the merge sequence.

## Pending follow-ups (non-blocking)

- After Chunk A1 closes: open a separate dossier chunk to wire `scripts/tests/` into CI (`requirements.txt` + `scripts/quality/test.sh` discovery + pip-install step in the workflow). Tracked in `chunks/13-PRE-readiness-check-fix/30-review.md` §4.
