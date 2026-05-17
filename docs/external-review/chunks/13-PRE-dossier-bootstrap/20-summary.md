# 20-summary — Chunk 13-PRE-dossier-bootstrap

CLAUDE.md §9 completion summary for the dossier-bootstrap chunk.

```
Summary:
- Scaffolded docs/external-review/ as a bidirectional, in-repo, auditable
  channel between reviewer and implementer for Phase 13+. Replaced the
  loose-file convention with a chunks/<id>/ folder convention plus a
  cross-cutting directives/ folder. README.md fully rewritten to spec the
  layout, authorial limits, immutability rule, state-machine enum, and
  archive policy. STATUS.md added as a hand-maintained rollup until
  `make status` is wired post-M7.
- Three chunks scaffolded retrospectively:
  - 13-A1-domain (under_review; PR #2 open) — full 00/10/20/manifest.
    10-deviation.md preserves both deviations (LOC-cap revision and
    ops-check-strict-flip). The pre-dossier file
    2026-05-17-chunkA-loc-cap-revision.md is removed; its content lives
    in this chunk's 10-deviation.md.
  - 13-PRE-readiness-check-fix (prompt_received) — 00/manifest. PR forthcoming
    on feature/fix-readiness-check-triggers (separate review surface per
    the directive). 20-summary.md lands later as a follow-up commit on
    this same dossier branch (PR1) after that fix PR is open.
  - 13-PRE-dossier-bootstrap (this chunk) — 00/20/manifest.

Files changed (this PR):
- docs/external-review/README.md (rewrite)
- docs/external-review/STATUS.md (NEW)
- docs/external-review/chunks/13-A1-domain/00-prompt.md (NEW)
- docs/external-review/chunks/13-A1-domain/10-deviation.md (NEW)
- docs/external-review/chunks/13-A1-domain/20-summary.md (NEW)
- docs/external-review/chunks/13-A1-domain/manifest.yml (NEW)
- docs/external-review/chunks/13-PRE-readiness-check-fix/00-prompt.md (NEW)
- docs/external-review/chunks/13-PRE-readiness-check-fix/manifest.yml (NEW)
- docs/external-review/chunks/13-PRE-dossier-bootstrap/00-prompt.md (NEW)
- docs/external-review/chunks/13-PRE-dossier-bootstrap/20-summary.md (NEW)
- docs/external-review/chunks/13-PRE-dossier-bootstrap/manifest.yml (NEW)
- docs/external-review/directives/2026-05-17-ops-check-and-pci-check-strict-flip.md
    (git-track existing reviewer-authored file; content unchanged)
- docs/external-review/2026-05-17-chunkA-loc-cap-revision.md (DELETED; content
  absorbed into chunks/13-A1-domain/10-deviation.md)

Tests run:
- N/A — documentation-only change. CI gates: docs-check + ops-check +
  pci-check + lint + typecheck + test + security. All gates expected
  green: this branch does NOT introduce src/, so the readiness-check
  strict-flip does not fire. The deviation that affects PR #2 is
  unrelated to PR #1.

Requirement coverage:
- N/A — process artifact. References Phase-13 chunked-implementation
  contract from CLAUDE.md §2.

Risks:
- The 00-prompt.md for 13-A1-domain is a reconstruction of the original
  Chunk-A1 kickoff (the verbatim text was relayed in chat and not
  archived to disk at the time). Provenance is acknowledged in the file's
  header; the operator's session JSONL is the authoritative source.
- Authorial limits are CONVENTION-ENFORCED, not hook-enforced. A future
  hook policy could harden them (post-M7 work).
- Work item 2's §9 summary (chunks/13-PRE-readiness-check-fix/20-summary.md)
  is NOT in this PR. It will land via a follow-up commit on this same
  dossier branch after the fix PR is open, because the fix PR's scope is
  strictly scripts-only (no chunk-folder edits). PR1 stays in draft until
  that follow-up commit lands.

Follow-ups:
- Land PR2 (feature/fix-readiness-check-triggers) on its own branch.
- Backfill chunks/13-PRE-readiness-check-fix/20-summary.md to PR1
  (this branch) once PR2 is open.
- Mark both PRs ready for review.
- After PR2 merges, rebase feature/chunk-a1-domain on main; verify CI
  green; reviewer writes chunks/13-A1-domain/30-review.md.
- Post-M7: implement `make status` to auto-generate STATUS.md from manifests;
  implement `make ci` gate on 30-review.md presence.
```
