# External-Review Dossier

Bidirectional, in-repo, auditable channel between the external governance **reviewer** and the implementer **agent**. Every chunk of work in Phase 13+ has a dossier folder under `chunks/` with a fixed naming convention; cross-cutting directives that span chunks live under `directives/`. Both sides write, with authorial limits enforced by convention (not by hook policy).

## Layout

```
docs/external-review/
├── README.md                              ← this file (convention spec)
├── STATUS.md                              ← hand-authored rollup; one line per chunk
├── directives/                            ← cross-cutting, dated, span multiple chunks
│   └── YYYY-MM-DD-<short-topic>.md
└── chunks/
    └── <chunk-id>/
        ├── 00-prompt.md                   ← reviewer-authored canonical kickoff
        ├── 10-deviation.md                ← OPTIONAL; implementer-authored if brief has internal tension
        ├── 15-clarification.md            ← OPTIONAL revision (immutability rule, see below)
        ├── 20-summary.md                  ← implementer-authored CLAUDE.md §9 chunk handoff
        ├── 30-review.md                   ← reviewer-authored verdict + next-step pointer
        └── manifest.yml                   ← state machine; the ONLY mutable file in the folder
```

## Chunk-id convention

`<phase>-<sub-id>-<slug>` — examples: `13-A1-domain`, `13-PRE-readiness-check-fix`, `13-PRE-dossier-bootstrap`, `13-B-infrastructure`. `PRE-` prefix denotes pre-chunk infrastructure/process work that unblocks a numbered chunk; sortable alphabetically before numbered sub-chunks.

## Authorial limits (convention)

| File | Reviewer writes | Implementer writes |
|---|---|---|
| `00-prompt.md` | ✅ | — |
| `10-deviation.md` | — | ✅ |
| `15-clarification.md`, `25-...`, etc. | revisions of `00`/`30` | revisions of `10`/`20` |
| `20-summary.md` | — | ✅ |
| `30-review.md` | ✅ | — |
| `manifest.yml` | flip `under_review → accepted/rejected` | flip `prompt_received → implementing → summary_posted` |
| `STATUS.md` | ✅ | — (until `make status` is wired post-M7) |
| `directives/*.md` | ✅ | — |

Both sides can **read** everything.

## Immutability rule

The four numbered markdown files (`00`, `10`, `20`, `30`) are **immutable once authored**. Revisions are *new files* with the next free numeric prefix:

- Reviewer wants to amend a kickoff → write `15-clarification.md`, not edit `00`.
- Implementer wants to amend a summary → write `25-update.md`, not edit `20`.
- Reviewer issues a v2 verdict → write `30-review-v2.md`, not edit `30-review.md`.

`manifest.yml` is the **single mutable** file per chunk. Treat it as the state pointer; treat the numbered files as the conversation transcript.

## State machine (in `manifest.yml`)

```yaml
chunk_id: <chunk-id>
status: <enum below>
prompt_sha: <git hash-object of 00-prompt.md at author time>
summary_sha: <git hash-object of 20-summary.md at commit time; null until written>
pr: <PR number; null until opened>
ci_url: <CI run URL for the head commit; null until run>
deviations_open: [<short ids>]
next_chunk: <chunk-id-of-the-chunk-this-unblocks>
```

Status enum:

| Status | Set by | Meaning |
|---|---|---|
| `prompt_received` | reviewer (at chunk creation) | `00-prompt.md` exists; implementer hasn't started |
| `deviation_surfaced` | implementer | `10-deviation.md` written; awaiting reviewer response |
| `implementing` | implementer | Brief clean (or deviation resolved); work in progress |
| `summary_posted` | implementer | `20-summary.md` written; PR open; awaiting review |
| `under_review` | implementer (after PR ready-for-review) | Reviewer is reading |
| `accepted` | reviewer | `30-review.md` says approved; PR mergeable |
| `rejected` | reviewer | `30-review.md` says blocked or returned |
| `superseded` | either | This chunk replaced by another (record the successor in `next_chunk`) |

## Cross-cutting directives vs chunk dossiers

- **Chunk dossier** (`chunks/<id>/`) — work that is bounded by a single PR and a single review cycle.
- **Directive** (`directives/YYYY-MM-DD-<topic>.md`) — reviewer ruling that applies to **multiple** current or future chunks. Examples: a stack revision; a CI gate change; an LOC-cap revision affecting many chunks. The directive is the source of truth; a chunk dossier may *reference* a directive but should not duplicate its content.

If a directive originates from a chunk-specific deviation (e.g., the `2026-05-17-ops-check-and-pci-check-strict-flip` directive originated in `13-A1-domain`'s `10-deviation.md`), the chunk's `10-deviation.md` records the observation and the next state; the directive captures the cross-cutting authoritative ruling.

## Archive policy

Keep `chunks/<id>/` forever. After a parent phase fully closes, move all chunks of that phase into `chunks/archive/<phase>/`. No deletes; this is the audit trail.

## Authoring + ping protocol

1. **Reviewer** creates `chunks/<id>/{00-prompt.md, manifest.yml}` with `status: prompt_received`. Commits. Pushes. (Today: a chat ping signals "go"; post-M7: a GitHub Action posts a comment.)
2. **Implementer** reads `STATUS.md` and the chunk's `00-prompt.md`. If the brief has internal tension (LOC vs scope, ratified-stack clash, hook conflict, missing artefact), the implementer writes `10-deviation.md`, flips `status: deviation_surfaced`, and waits. Otherwise flips `status: implementing` and proceeds.
3. **Implementer** completes the work, opens the PR, writes `20-summary.md` (= CLAUDE.md §9 handoff), flips `status: summary_posted` (or `under_review` after marking the PR ready). PR description should link back to `20-summary.md`. Two short chat lines (`"<chunk-id> PR #N up"`) are the ping.
4. **Reviewer** reads `20-summary.md` and the PR, writes `30-review.md`, flips `status: accepted` or `rejected`. If accepted and a follow-on chunk exists, writes the next chunk's `00-prompt.md` in the same commit.

## What the implementer must NOT do

- Do **not** edit `00-prompt.md`, `30-review.md`, `STATUS.md`, or any file under `directives/`.
- Do **not** delete chunk folders (use `superseded` status + `next_chunk` pointer instead).
- Do **not** bundle dossier writes into unrelated code PRs; dossier maintenance is its own PR or its own commit on a process-only branch.

## What the reviewer must NOT do

- Do **not** edit `10-deviation.md` or `20-summary.md` to "correct" the implementer's record; issue a revision file or a `30-review.md` with the correction.

## Notes on history

The pre-dossier files `2026-05-17-chunkA-loc-cap-revision.md` (LOC-cap revision originally authored loose in this folder) have been absorbed into `chunks/13-A1-domain/10-deviation.md`. The pre-dossier directive `directives/2026-05-17-ops-check-and-pci-check-strict-flip.md` predates the dossier convention and is preserved as a cross-cutting directive (the dossier folder for `13-PRE-readiness-check-fix` references it rather than duplicating it).
