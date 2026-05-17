# 20-summary — Chunk 13-PRE-readiness-check-fix

CLAUDE.md §9 completion summary for the readiness-check trigger fix. Original PR description: https://github.com/sajithmankavil/wex-purchase-fx/pull/4.

```
Summary:
- Both readiness-check scripts now flip to strict mode iff
  .human-approvals/pci-production-approved.txt exists, per the
  directive's correction (the implementer's first proposal of
  implementation-approved.txt was functionally identical to the existing
  trigger; the reviewer pointed out the correct production-readiness
  marker).
- Branch feature/fix-readiness-check-triggers; commit 4671bed.
- Draft PR #4 opened: https://github.com/sajithmankavil/wex-purchase-fx/pull/4
- CI on the fix branch is GREEN:
  https://github.com/sajithmankavil/wex-purchase-fx/actions/runs/26000104744
- Both gates now exit 0 in-workspace with the advisory message; both
  return to strict only when the Phase-12 PROD marker is created.

Files changed (3 files; +140 / -30):
- scripts/quality/operational_readiness_check.py
  - Replaced APPROVAL + CODE_MARKERS with PROD_APPROVAL.
  - Strict trigger: `strict = PROD_APPROVAL.exists()`.
  - Removed unused has_non_placeholder_files helper.
  - Docstring updated to describe the production-marker contract.
- scripts/security/pci_readiness_check.py
  - Removed SECURITY_APPROVAL and CODE_MARKERS constants.
  - Strict trigger: `strict = PROD_APPROVAL.exists()` (after the
    PROFILE-not-tier1 short-circuit).
  - Removed unused has_non_placeholder_files helper.
  - Advisory message updated; docstring updated.
- scripts/tests/test_readiness_checks.py (NEW, 7 tests):
  1. test_ops_check_advisory_when_no_prod_marker
  2. test_ops_check_advisory_when_only_implementation_marker_present
     (REGRESSION test — the old code would have flipped strict here)
  3. test_pci_check_advisory_when_no_prod_marker
  4. test_pci_check_advisory_when_only_security_marker_present
     (REGRESSION test — same as above for the security marker)
  5. test_pci_check_skipped_when_profile_not_tier1
  6. test_ops_check_strict_fails_on_missing_required_files
  7. test_pci_check_strict_fails_on_missing_required_files

Tests run:
- Local pytest 9.0.3 on Python 3.14.2: 7 passed in 0.91s.
- CI: ci.yml run 26000104744 GREEN.
  - docs-check, ops-check (now advisory), pci-check (now advisory),
    lint, typecheck, test (no python tests discovered — see "CI test
    discovery" risk below), security — all green.

Requirement coverage:
- docs/external-review/directives/2026-05-17-ops-check-and-pci-check-strict-flip.md
  fully implemented per the directive's NEW code blocks.
- The strict-flip deviation tracked at chunks/13-A1-domain/10-deviation.md
  is now resolved by this fix; once feature/chunk-a1-domain rebases on
  main, its CI is expected to go green.

Risks:
- CI test discovery: the existing CI workflow does NOT run the new
  pytest. `scripts/quality/test.sh` only invokes pytest if a top-level
  `tests/` dir exists, and there is no `requirements.txt` so pytest
  is not installed in CI. The strict three-file scope of this PR per
  the directive prohibits adding `requirements.txt`, editing
  `test.sh`, or editing `.github/workflows/ci.yml`. Local pytest output
  is cited in the PR description as the evidence. This is the same
  M7 carry-forward situation as the no-Java-CI gap.
- Recommendation: a follow-up PR (NOT bundled here) should add
  `requirements.txt` with pytest and extend test.sh to discover
  scripts/tests/ — that turns the test suite into actual CI evidence
  rather than local-only. Awaiting reviewer direction.

Follow-ups:
- After PR #4 merges to main:
  1. Rebase feature/chunk-a1-domain on main.
  2. Confirm A1's CI run goes green (ops-check + pci-check both
     advisory; no other gates affected).
  3. Update chunks/13-A1-domain/manifest.yml:
     deviations_open: [] and status: under_review (or accepted if the
     reviewer has issued 30-review.md by then).
- Reviewer writes chunks/13-PRE-readiness-check-fix/30-review.md.
- Optional follow-up PR to wire scripts/tests/ into CI (see Risks).
```

## Note on packaging

This summary is committed on `feature/external-review-dossier-bootstrap` (PR #3), not on `feature/fix-readiness-check-triggers` (PR #4). The directive's strict three-file scope for the fix PR forbids edits to chunk folders. Authoring the §9 summary file via the dossier-bootstrap branch keeps PR #4's diff strictly to the three named files while still landing the durable evidence inside the dossier convention.
