# External-Review Status

> Auto-target: `make status` once Java/Make wired (post-M7). Hand-maintained until then.

| Chunk ID | Status | PR | Open deviations | Next |
|---|---|---|---|---|
| 13-PRE-dossier-bootstrap | accepted (clarification pending on §5 line-ending finding) | #3 | — | reviewer responds to 35-clarification.md; merge sequence then proceeds |
| 13-PRE-readiness-check-fix | accepted | #4 (CI green; reviewer sandbox pytest 7/7 pass) | — | implementer merges PR #4 first; A1 rebases on main |
| 13-A1-domain | under_review | #2 | ops-check-strict-flip (resolved by #4 merge; pending rebase) | rebase on main after #4 merges; reviewer 30-review.md |

Status enum: `prompt_received | deviation_surfaced | implementing | summary_posted | under_review | accepted | rejected | superseded`.

## Phase 13 chunk plan (live)

```
13-PRE-dossier-bootstrap        ← process scaffold; this PR
13-PRE-readiness-check-fix      ← unblocks 13-A1-domain CI
13-A1-domain                    ← M1 domain layer (open; awaiting fix + rebase)
13-A2-application               ← M2 application services + ports
13-B-infrastructure             ← M3 persistence + adapters
13-C-api-observability          ← M4-M6 controllers + observability + config
```

## Cross-cutting directives in force

- [`directives/2026-05-17-ops-check-and-pci-check-strict-flip.md`](directives/2026-05-17-ops-check-and-pci-check-strict-flip.md) — defers strict mode in `ops-check` + `pci-check` to the production-approval marker. Implemented by `13-PRE-readiness-check-fix` (accepted 2026-05-17).

## Recent reviewer activity

- 2026-05-17 — Accepted `13-PRE-readiness-check-fix` (PR #4). Verdict: `chunks/13-PRE-readiness-check-fix/30-review.md`. Independent pytest 7/7 pass.
- 2026-05-17 — Accepted `13-PRE-dossier-bootstrap` (PR #3) with one pre-merge condition (line-ending normalization). Verdict: `chunks/13-PRE-dossier-bootstrap/30-review.md`.

## Recent implementer activity

- 2026-05-17 — Pushed back on the §5 line-ending finding in `chunks/13-PRE-dossier-bootstrap/30-review.md`. Actual PR #3 diff on origin is `+790 / -0` across 13 new files (no churn). Evidence in `chunks/13-PRE-dossier-bootstrap/35-clarification.md`. Awaiting reviewer response before running the merge sequence.

## Pending follow-ups (non-blocking)

- After Chunk A1 closes: open a separate dossier chunk to wire `scripts/tests/` into CI (`requirements.txt` + `scripts/quality/test.sh` discovery + pip-install step in the workflow). Tracked in `chunks/13-PRE-readiness-check-fix/30-review.md` §4.
