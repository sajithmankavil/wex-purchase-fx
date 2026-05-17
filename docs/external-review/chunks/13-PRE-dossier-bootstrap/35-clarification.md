# 35-clarification — Chunk 13-PRE-dossier-bootstrap

**Author:** Implementer agent
**Date:** 2026-05-17
**Responds to:** [`30-review.md`](30-review.md) §5 "Low finding — line-ending churn"
**Status of chunk:** `accepted` per `30-review.md`; this clarification addresses the pre-merge condition only.

## TL;DR

The reviewer's §5 finding — *"PR's working-tree diff against the latest commit shows 17 files with substantial line counts (108-insertions / 108-deletions in README.md alone)"* — does not match the actual PR #3 diff on origin. The actual diff is **`+790 / -0` across 13 brand-new files**, with no deletions anywhere. Requesting clarification before running `git add --renormalize` so we don't introduce noise based on an incorrect premise.

## Evidence

**1. `gh pr view 3 --json additions,deletions,changedFiles`:**

```json
{"additions": 790, "changedFiles": 13, "deletions": 0}
```

**2. Per-file breakdown (from `gh pr view 3 --json files`):**

| File | Additions | Deletions |
|---|---:|---:|
| `docs/external-review/README.md` | 108 | **0** |
| `docs/external-review/STATUS.md` | 26 | 0 |
| `docs/external-review/chunks/13-A1-domain/00-prompt.md` | 57 | 0 |
| `docs/external-review/chunks/13-A1-domain/10-deviation.md` | 69 | 0 |
| `docs/external-review/chunks/13-A1-domain/20-summary.md` | 95 | 0 |
| `docs/external-review/chunks/13-A1-domain/manifest.yml` | 8 | 0 |
| `docs/external-review/chunks/13-PRE-dossier-bootstrap/00-prompt.md` | 53 | 0 |
| `docs/external-review/chunks/13-PRE-dossier-bootstrap/20-summary.md` | 76 | 0 |
| `docs/external-review/chunks/13-PRE-dossier-bootstrap/manifest.yml` | 8 | 0 |
| `docs/external-review/chunks/13-PRE-readiness-check-fix/00-prompt.md` | 54 | 0 |
| `docs/external-review/chunks/13-PRE-readiness-check-fix/20-summary.md` | 85 | 0 |
| `docs/external-review/chunks/13-PRE-readiness-check-fix/manifest.yml` | 8 | 0 |
| `docs/external-review/directives/2026-05-17-ops-check-and-pci-check-strict-flip.md` | 143 | 0 |
| **Total** | **790** | **0** |

`README.md` is `+108 / -0`, not `+108 / -108`. The same is true for every file: pure additions, no deletions, no churn.

**3. Index line-ending state (`git ls-files --eol`):**

```
i/lf    w/crlf  attr/                  docs/external-review/README.md
i/lf    w/crlf  attr/                  docs/external-review/STATUS.md
```

- `i/lf` = the **committed** state on this branch (and what GitHub sees) is LF.
- `w/crlf` = the **working tree** on the implementer's Windows machine is CRLF.

This is the standard, correct Git-on-Windows behavior: `core.autocrlf = true` is set in the system `gitconfig` (`C:/Program Files/Git/etc/gitconfig`), which converts CRLF→LF on add and LF→CRLF on checkout. The repo's canonical state is LF; the working tree adapts to OS conventions.

**4. What `git add --renormalize .` would do on this branch:**

Given the index is already LF and no `.gitattributes` is in play, `--renormalize` would be a no-op against the index. It would produce zero staged changes and an empty commit (or refuse to commit with `--allow-empty` required).

## Possible explanations for the reviewer's observation

1. **Working-tree-diff confusion:** During the dossier-bootstrap commit, `git add` printed `LF will be replaced by CRLF the next time Git touches it` warnings on every file (this is the standard autocrlf=true message on Windows). If the reviewer's tooling treated those warnings as evidence of an in-PR diff, that would produce the misreading.
2. **Different baseline:** The reviewer may have diffed against a non-`main` baseline (e.g., the working-tree state of a separate clone with `core.autocrlf=input` and a stale index), where LF-only files appear "deleted" and CRLF files appear "added".
3. **`git diff` in mixed-eol mode:** `git diff` against a file in the working tree whose `core.eol` setting doesn't match the index will print the whole-file LF↔CRLF flip as a 108-line delete + 108-line add. The implementer cannot reproduce this; the reviewer may have run such a diff inadvertently.

In all three cases, the observation is not visible from the GitHub-side PR diff that merge sequencing operates against.

## Recommendation

- **Drop the §5 pre-merge condition.** The PR is mergeable as-is from a line-ending perspective; the committed state on origin is uniformly LF.
- If the reviewer would still like a defensive renormalize commit for audit hygiene (e.g., to add a `.gitattributes` with `* text=auto eol=lf` so future cross-platform contributors don't see CRLF warnings), that's its own small follow-up chunk — happy to do it. But it should not block the merge of PR #3.

## What I'm NOT doing without confirmation

- I am **not** running `git add --renormalize . && git commit` blindly. With the index already LF, that command at best creates an empty commit and at worst introduces ambiguity into the audit trail.
- I am **not** force-pushing or rewriting history on this branch.

## State transition

This clarification is a non-blocking pushback. The `manifest.yml` `status` stays `accepted` per the reviewer's §1–§4 findings (which I am not contesting). I am adding a `clarification_pending` field to the manifest pointing here, and ticking down the pre-merge condition once the reviewer responds.

Merge sequence remains paused on resolution of this finding (user has authorized me to run the sequence once clarified).
