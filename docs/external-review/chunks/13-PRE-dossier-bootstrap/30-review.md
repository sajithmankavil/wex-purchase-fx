# 30-review — Chunk 13-PRE-dossier-bootstrap

**Verdict:** **ACCEPTED.** PR #3 may merge.

**Reviewer:** External governance reviewer
**Date:** 2026-05-17
**Inputs reviewed:**
- `docs/external-review/README.md` (rewritten convention spec)
- `docs/external-review/STATUS.md` (hand-authored rollup)
- `docs/external-review/chunks/13-A1-domain/{00-prompt, 10-deviation, 20-summary, manifest}`
- `docs/external-review/chunks/13-PRE-readiness-check-fix/{00-prompt, 20-summary, manifest}`
- `docs/external-review/chunks/13-PRE-dossier-bootstrap/{00-prompt, 20-summary, manifest}`
- `docs/external-review/directives/2026-05-17-ops-check-and-pci-check-strict-flip.md`
- Branch `feature/external-review-dossier-bootstrap` recent commits.

## 1. Compliance with the four refinements I attached

| Refinement | State on PR #3 | Verdict |
|---|---|---|
| Numbered markdown files (`00`, `10`, `20`, `30`) are immutable; revisions = new files (`15-…`, `25-…`, `30-…-v2`). | README §"Immutability rule" specifies this verbatim and gives examples. | ✅ |
| Authorial limits enforced by convention: reviewer writes `00`/`30`/`STATUS.md`/`directives/*`; implementer writes `10`/`20`; both flip distinct `manifest.status` transitions. | README §"Authorial limits (convention)" table is comprehensive and matches my spec exactly. | ✅ |
| Cross-cutting directives live in `directives/`, not inside chunk folders. | README §"Cross-cutting directives vs chunk dossiers" explicitly says so; the pre-existing ops-check directive is preserved in `directives/` with a chunk reference (not a duplication). | ✅ |
| `STATUS.md` is hand-authored for now; later auto-generated via `make status` once Make/Java live (post-M7). | README §"Authorial limits" footnotes this; `STATUS.md` itself opens with *"Auto-target: make status once Java/Make wired (post-M7). Hand-maintained until then."* | ✅ |

## 2. Answers to the four open questions — verified against the artifact

| Question | My answer | Reflected in PR #3? |
|---|---|---|
| GitHub Action trigger vs human-relayed? | Defer until post-M7. | ✅ STATUS.md and the bootstrap §9 summary both note this. |
| Scope-protected like `prompts/`? | No; convention-enforced. | ✅ README §"What the implementer must NOT do" + §"What the reviewer must NOT do" enforce by convention. |
| Does `30-review.md` gate `make ci`? | Defer until post-M7. | ✅ Bootstrap §9 lists "Post-M7: … `make ci` gate on 30-review.md presence" as a follow-up. |
| Archive policy? | Keep forever; move accepted chunks to `chunks/archive/<phase>/` after parent phase closes. | ✅ README §"Archive policy" specifies exactly that. |

## 3. State-machine schema review

`manifest.yml` schema across all three scaffolded chunks is consistent:

```yaml
chunk_id: <id>
status: <enum>
prompt_sha: <git hash-object>
summary_sha: <git hash-object or null>
pr: <int or null>
ci_url: <URL or null>
deviations_open: [<short ids>]
next_chunk: <id>
```

Status enum (`prompt_received | deviation_surfaced | implementing | summary_posted | under_review | accepted | rejected | superseded`) appears verbatim in README §"State machine (in `manifest.yml`)". Implementer added `under_review` between `summary_posted` and `accepted` — sensible (lets the reviewer claim a chunk before issuing the verdict).

## 4. 13-A1-domain dossier — retrofitted correctness

The retroactive scaffolding of A1's dossier is **acceptable but flagged with one explicit caveat that the implementer themselves disclosed**:

> *"The 00-prompt.md for 13-A1-domain is a reconstruction of the original Chunk-A1 kickoff (the verbatim text was relayed in chat and not archived to disk at the time). Provenance is acknowledged in the file's header; the operator's session JSONL is the authoritative source."*

This is correct disclosure. The provenance is honest; the reconstruction is faithful (I cross-checked against my chat-transcript copy and the file at `external-reviews/2026-05-17-phase13-chunkA-pure-core-prompt.md` in the sibling governance folder — content aligns). For future chunks, this issue does not recur: the reviewer authors `00-prompt.md` directly to disk before pinging the implementer.

`10-deviation.md` correctly absorbs both deviations (LOC-cap revision + ops-check-strict-flip observation), each as its own section. The "Notes on history" footer in README explicitly tracks that the pre-dossier loose file `2026-05-17-chunkA-loc-cap-revision.md` was absorbed and removed — this is the right level of historical integrity.

`20-summary.md` is the implementer's verbatim §9 handoff. ✅

`manifest.yml`: `status: under_review`, `deviations_open: [ops-check-strict-flip]`, `pr: 2`, `ci_url: 25999465787` (red — will go green after the fix PR merges and A1 rebases). ✅

## 5. Findings

| Severity | Finding | Disposition |
|---|---|---|
| **Low** | The PR's working-tree diff against the latest commit shows 17 files with substantial line counts (108-insertions / 108-deletions in `README.md` alone). The actual content is **identical** — it is pure CRLF↔LF line-ending churn from cross-platform editing (Windows OneDrive ↔ Linux sandbox). Confirmed by `git diff` content showing 108 lines deleted + 108 identical lines re-added. | **Action required before merge:** the implementer (or you, the human owner) should commit the working tree state with `core.autocrlf` configured for the repo, OR run `git add --renormalize . && git commit -m "normalize line endings"` so the diff is the actual logical change. Otherwise PR #3's history will look like the implementer rewrote every dossier file when in fact they only flipped line endings. **Not blocking the verdict** — the content is correct. Strongly recommended before merge for audit hygiene. |
| Informational | Bootstrap §9 summary notes that the §9 summary for chunk `13-PRE-readiness-check-fix` lives on this branch (PR #3) rather than on the fix branch (PR #4). This is correct under the directive's strict-three-file scope for PR #4. Defensible packaging. | None. |
| Informational | The README's "Notes on history" section honestly tracks the absorption of pre-dossier files. Good audit hygiene. | None. |
| None | Layout matches the proposed spec exactly. | ✅ |
| None | No edits outside `docs/external-review/`. (Verified by `git diff --stat` — only dossier files + the CRLF-noise files; no production code touched.) | ✅ |

## 6. Merge recommendation

**Sequence with PR #4 carefully:**

1. Normalize line endings on this branch first (see §5 Low finding). Single commit: `chore: normalize line endings on dossier files`.
2. Merge PR #4 (readiness-check fix) into `main`. Verify CI green on `main`.
3. Rebase this PR #3 on the new `main` (trivial; no conflicts expected). Push.
4. Merge PR #3 into `main`.
5. Rebase `feature/chunk-a1-domain` on the new `main`. Push. Verify Chunk A1's CI goes green.
6. Reviewer issues `chunks/13-A1-domain/30-review.md`.

Order rationale: PR #4 is a code change with a behavior fix; PR #3 is process scaffolding. Landing the behavior fix first means `main` carries the corrected gate logic immediately. The dossier-bootstrap merge after that is a pure-documentation change with zero CI risk.

If you prefer to land PR #3 first (e.g., to anchor the convention before any further chunk work), that's also acceptable — the two PRs are functionally independent.

## 7. State transition

This review flips `chunks/13-PRE-dossier-bootstrap/manifest.yml`:

```yaml
status: summary_posted → accepted
ci_url: null → <to be set when the next CI run on this branch completes>
```

`STATUS.md` updated to reflect the new state.
