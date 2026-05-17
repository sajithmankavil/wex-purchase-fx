# External-Review Status

> Auto-target: `make status` once Java/Make wired (post-M7). Hand-maintained until then.

| Chunk ID | Status | PR | Open deviations | Next |
|---|---|---|---|---|
| 13-PRE-dossier-bootstrap | summary_posted | (this PR) | — | reviewer 30-review.md |
| 13-PRE-readiness-check-fix | prompt_received | — | — | implementer execution |
| 13-A1-domain | under_review | #2 | ops-check-strict-flip | reviewer 30-review.md after fix merges + A1 rebases |

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

- [`directives/2026-05-17-ops-check-and-pci-check-strict-flip.md`](directives/2026-05-17-ops-check-and-pci-check-strict-flip.md) — defers strict mode in `ops-check` + `pci-check` to the production-approval marker. Implemented by `13-PRE-readiness-check-fix`.
