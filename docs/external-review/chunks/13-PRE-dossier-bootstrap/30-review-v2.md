# 30-review-v2 — Chunk 13-PRE-dossier-bootstrap

**Author:** External governance reviewer
**Date:** 2026-05-17
**Supersedes:** `30-review.md` §5 (the line-ending finding). All other sections of the original review stand.
**Responds to:** `35-clarification.md` (implementer counter-finding).

## Verdict

**ACCEPTED, no pre-merge conditions.** PR #3 may merge as-is.

## Retraction of §5 finding

The implementer's analysis in `35-clarification.md` is correct. The reviewer's §5 line-ending finding in the original `30-review.md` was based on `git diff` output (working-tree-vs-index) which on a Windows checkout with `core.autocrlf=true` correctly shows CRLF↔LF flips for every line. This is **not** the PR diff and **not** what merges to `main`.

What I should have looked at:
- `gh pr view 3 --json additions,deletions,changedFiles` → `{"additions": 790, "deletions": 0, "changedFiles": 13}` — pure additions, no churn.
- `git ls-files --eol` → `i/lf` for every file (the committed state is uniformly LF).

The implementer's working tree showing CRLF is the **correct** behaviour for Windows with `core.autocrlf=true`: the index/origin state is LF, and Git transparently flips to CRLF on checkout for OS-conventional editing. This is a feature, not a defect.

`git add --renormalize .` on this branch would have been a no-op (and at worst would have introduced an empty commit, polluting the audit trail). The implementer was correct not to run it blindly.

## Updated merge condition list for PR #3

| Condition | Status |
|---|---|
| Code-correctness | ✅ accepted (§1–§4 of `30-review.md`) |
| ~~Line-ending normalize~~ | ❌ **withdrawn** — finding was based on misread working-tree diff |
| Defer GitHub Action trigger to post-M7 | ✅ noted in `20-summary.md` follow-ups |
| Defer `make ci` 30-review-presence gate to post-M7 | ✅ noted in `20-summary.md` follow-ups |

No conditions remain. The PR is mergeable.

## Optional follow-up (NOT a pre-merge condition)

If you (implementer) want to add a `.gitattributes` with `* text=auto eol=lf` so future cross-platform contributors don't see the autocrlf warnings in their console, that's a clean small follow-up chunk (`13-PRE-gitattributes-eol-normalize` or similar). It's audit hygiene, not a defect. **Do not bundle into PR #3 or any other in-flight branch.**

## Updated merge sequence (replaces `30-review.md` §6)

1. **Implementer:** merge PR #4 (readiness-check fix) into `main`. CI green on `main`.
2. **Implementer:** rebase PR #3 on the new `main` (trivial; no conflicts). Merge PR #3.
3. **Implementer:** rebase `feature/chunk-a1-domain` on `main`. Push. Confirm CI green.
4. **Reviewer (me):** flip `chunks/13-A1-domain/manifest.yml` `status` to `accepted` once CI is green (no new `30-review.md`; `30-review.md` of 13-A1 is already authored and stands).
5. **Implementer:** merge PR #2.
6. **Implementer:** immediately read `chunks/13-A2-application/00-prompt.md` and start A2.

## State transition

`chunks/13-PRE-dossier-bootstrap/manifest.yml`:

- `status` stays `accepted`.
- `review_conditions: [normalize-line-endings-before-merge]` → `review_conditions: []`.
- Implementer may now run the merge sequence above.

## Reviewer learning (recorded for audit)

I conflated two different `git diff` invocations:
- `git diff` (no args) on a Windows working tree with `core.autocrlf=true` and LF index → returns a misleading whole-file CRLF↔LF flip, NOT a real content diff.
- `git diff main..feature/external-review-dossier-bootstrap` (or `gh pr view 3 --json`) → returns the real PR diff, which is what the merge will produce.

For future PR reviews where I cannot fetch the PR URL: use `git diff <base>..<feature>` against the actual branch base, never working-tree-vs-index, when the user's working tree might be Windows-CRLF. Or query `gh pr view --json additions,deletions` if available. Recording this as a process correction in my own audit trail; no project-side action needed.
