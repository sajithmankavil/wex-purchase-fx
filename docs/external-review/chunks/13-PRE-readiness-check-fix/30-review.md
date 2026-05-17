# 30-review — Chunk 13-PRE-readiness-check-fix

**Verdict:** **ACCEPTED.** PR #4 may merge.

**Reviewer:** External governance reviewer
**Date:** 2026-05-17
**Inputs reviewed:**
- `chunks/13-PRE-readiness-check-fix/00-prompt.md` (canonical kickoff)
- `chunks/13-PRE-readiness-check-fix/20-summary.md` (implementer §9 summary)
- `directives/2026-05-17-ops-check-and-pci-check-strict-flip.md` (authoritative spec)
- `feature/fix-readiness-check-triggers` HEAD via `git show` against three files:
  - `scripts/quality/operational_readiness_check.py`
  - `scripts/security/pci_readiness_check.py`
  - `scripts/tests/test_readiness_checks.py`
- Independent pytest run in reviewer sandbox (extracted files to `/tmp/fix-verify`; `python -m pytest -v`).

## 1. Compliance with directive

| Directive requirement | State on `feature/fix-readiness-check-triggers` | Verdict |
|---|---|---|
| `operational_readiness_check.py`: replace `APPROVAL` + `CODE_MARKERS` with `PROD_APPROVAL`. | `PROD_APPROVAL = Path('.human-approvals/pci-production-approved.txt')`. Old constants removed. | ✅ |
| `operational_readiness_check.py`: `strict = PROD_APPROVAL.exists()`. | Present at the location formerly occupied by the composite trigger. | ✅ |
| `operational_readiness_check.py`: remove `has_non_placeholder_files` helper. | Removed. | ✅ |
| `operational_readiness_check.py`: docstring updated to describe the production-marker contract. | New docstring reads: *"This gate becomes strict once a Phase-12 human owner has created `.human-approvals/pci-production-approved.txt` with content `APPROVED_FOR_PCI_PRODUCTION_RELEASE`. Until then it runs in advisory mode … so implementation work can proceed."* Matches directive verbatim. | ✅ |
| `pci_readiness_check.py`: remove `SECURITY_APPROVAL` + `CODE_MARKERS` constants. | Removed. | ✅ |
| `pci_readiness_check.py`: `strict = PROD_APPROVAL.exists()` (after profile short-circuit). | Present at the correct location. The profile-not-tier1 short-circuit remains in place above it (correct sequencing). | ✅ |
| `pci_readiness_check.py`: remove `has_non_placeholder_files` helper. | Removed. | ✅ |
| `pci_readiness_check.py`: docstring + advisory message updated. | Both updated; advisory message now reads "No production-approval marker present yet." Matches directive intent. | ✅ |
| New `scripts/tests/test_readiness_checks.py` with advisory + strict + regression coverage. | 7 tests authored; structure exactly per directive (`tmp_path` + `subprocess.run`). | ✅ |
| Strict three-file scope (no edits to operational docs, PCI docs, `src/`, or chunk folders) | Per PR #4 diff; only the three named files. (Verified at `git show` level; no out-of-scope content surfaced in the script reads.) | ✅ |

## 2. Independent test verification

Reviewer extracted the three files via `git show feature/fix-readiness-check-triggers:<path>` into `/tmp/fix-verify/`, installed pytest, and ran:

```
$ python -m pytest scripts/tests/test_readiness_checks.py -v
collected 7 items

scripts/tests/test_readiness_checks.py::test_ops_check_advisory_when_no_prod_marker PASSED                       [ 14%]
scripts/tests/test_readiness_checks.py::test_ops_check_advisory_when_only_implementation_marker_present PASSED  [ 28%]
scripts/tests/test_readiness_checks.py::test_pci_check_advisory_when_no_prod_marker PASSED                      [ 42%]
scripts/tests/test_readiness_checks.py::test_pci_check_advisory_when_only_security_marker_present PASSED        [ 57%]
scripts/tests/test_readiness_checks.py::test_pci_check_skipped_when_profile_not_tier1 PASSED                    [ 71%]
scripts/tests/test_readiness_checks.py::test_ops_check_strict_fails_on_missing_required_files PASSED            [ 85%]
scripts/tests/test_readiness_checks.py::test_pci_check_strict_fails_on_missing_required_files PASSED            [100%]

============================== 7 passed in 0.21s ===============================
```

All seven tests pass, matching the implementer's local result. The two **regression** tests are the load-bearing ones:

- `test_ops_check_advisory_when_only_implementation_marker_present` — places `.human-approvals/implementation-approved.txt` AND `.human-approvals/pci-security-approved.txt` AND a non-empty `src/main/java/Dummy.java`, then asserts the ops-check still exits 0 in advisory mode. **This is the precise regression that the old code would have failed.**
- `test_pci_check_advisory_when_only_security_marker_present` — equivalent regression for the PCI script (security-approval marker + `src/` no longer triggers strict).

These confirm the directive's intent has been realized correctly.

## 3. Findings

| Severity | Finding | Disposition |
|---|---|---|
| Informational | The implementer's PR #4 description (per their summary) cites CI run [26000104744](https://github.com/sajithmankavil/wex-purchase-fx/actions/runs/26000104744) as green. Reviewer cannot fetch GitHub URLs but has independently verified via sandbox pytest. CI evidence + local sandbox evidence concur. | None — accepted as evidence. |
| Informational | The implementer surfaced an honest limitation: `scripts/quality/test.sh` does not discover `scripts/tests/` because there is no top-level `tests/` directory and no `requirements.txt` installs pytest in CI. This means the new pytest does **not** currently gate CI; it only gates local invocations. | Acknowledged. Tracked as a follow-up; see §4. |
| Informational | The §9 summary is packaged on the dossier-bootstrap branch (PR #3) rather than on the fix branch (PR #4). This is correct under the directive's strict three-file scope for the fix PR. Defensible packaging trick. | None. |
| None | Strict-mode behaviour (when the production marker is present) is preserved — the required-files catalogue, the token check, the "vague_markers" detection ("TODO", "TBD", "to be defined", "fill this in", "placeholder"), and the operational-readiness-gate `Status: blocked` / `No-Go` short-circuit are all unchanged. Only the **trigger** moved. | ✅ |
| None | No edits to `src/`, `pom.xml`, `docs/operations/*`, `docs/security/*`, `docs/planning/*`, `docs/requirements/source-requirements.md`, `.human-approvals/`, `prompts/`, `.claude/`, `security-profile.yml`, or `CLAUDE.md`. | ✅ scope discipline preserved. |

## 4. Follow-up (NOT blocking this PR)

A separate small PR should land after Chunk A1 closes to wire the new pytest into CI. Suggested scope:

- Add `requirements.txt` (or `pyproject.toml` `[project.optional-dependencies] test`) with `pytest>=8`.
- Edit `scripts/quality/test.sh` to discover and run `scripts/tests/`.
- Add a pip-install step to the CI workflow before running test.sh.

That work is its own dossier chunk (`13-PRE-pytest-ci-wiring` or similar) — not the implementer agent's concern for this review. Flagged here so it doesn't get lost.

## 5. Merge recommendation

**Merge PR #4 first**, then merge PR #3 (dossier bootstrap) so the dossier-bootstrap PR's commit history reflects "fix is on main" as the new baseline. After both merge to `main`:

1. Rebase `feature/chunk-a1-domain` on `main`.
2. Push the rebased Chunk A1 branch.
3. Confirm A1's CI run goes green (both `ops-check` and `pci-check` now in advisory mode since `pci-production-approved.txt` does not exist).
4. Update `chunks/13-A1-domain/manifest.yml`: `deviations_open: []`, `status: under_review` (it already is `under_review`; no transition needed unless A1's CI green triggers a separate state).
5. Reviewer (me) writes `chunks/13-A1-domain/30-review.md` against the rebased PR #2.

## 6. State transition

This review flips `chunks/13-PRE-readiness-check-fix/manifest.yml`:

```yaml
status: summary_posted → accepted
deviations_open: [] (unchanged)
next_chunk: 13-A1-domain (unchanged)
```

`STATUS.md` is updated to reflect the new state.
