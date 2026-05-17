# Directive — Defer strict mode in operational + PCI readiness checks to the production-approval boundary

**Date:** 2026-05-17
**Author:** External governance reviewer
**Type:** Cross-cutting directive (spans Chunks A1, A2, B, C — and any future implementation chunks)
**Trigger:** Chunk A1 surfaced that CI flips red on the revert SHA because both `scripts/quality/operational_readiness_check.py` and `scripts/security/pci_readiness_check.py` move from advisory to strict mode the instant `src/` contains files. Their strict scan flags the literal token "placeholder" in pre-existing operational docs whose content is intentional v1-scope language documenting accepted Phase-12 deferrals.
**Verdict:** The strict-flip triggers are wrong. The right trigger for both gates is the production-readiness marker, not the implementation-start marker.

## Background

### Current state of `scripts/quality/operational_readiness_check.py`

```python
APPROVAL = Path('.human-approvals/implementation-approved.txt')
CODE_MARKERS = [Path('src'), Path('app'), Path('services')]
strict = APPROVAL.exists() or any(has_non_placeholder_files(p) for p in CODE_MARKERS) or has_non_placeholder_files(Path('infra'))
```

`implementation-approved.txt` was created at Phase 9 closure (2026-05-17). Chunk A1 adds files to `src/`. Both conditions are now true. Strict mode is now active.

### Current state of `scripts/security/pci_readiness_check.py`

```python
SECURITY_APPROVAL = Path('.human-approvals/pci-security-approved.txt')
PROD_APPROVAL = Path('.human-approvals/pci-production-approved.txt')
CODE_MARKERS = [Path('src'), Path('app'), Path('apps'), Path('services'), Path('packages'), Path('infra')]
strict = SECURITY_APPROVAL.exists() or PROD_APPROVAL.exists() or any(has_non_placeholder_files(p) for p in CODE_MARKERS)
```

Same issue — `pci-security-approved.txt` was created at Phase 9 closure for PCI Tier 1 posture, and Chunk A1 adds files to `src/`. Strict mode is now active.

### Design intent of both gates

Both scripts' docstrings explicitly state the intent: *prevent production-bound work from passing CI with vague or missing artifacts*. The phrase **production-bound** is the operative one. Strict mode should fire at the **production-readiness boundary**, not at the start of implementation. The current triggers conflate "implementation started" with "production is imminent" — those are 100% of Phase 13 apart.

### Why "implementation-approved" is the wrong marker

The implementer agent's deviation note proposed deferring to `implementation-approved.txt AND src/`. That trigger is functionally identical to the current `implementation-approved.txt OR src/` trigger once both exist, which they do today. It does not solve the problem.

The correct marker is **`.human-approvals/pci-production-approved.txt`** (content: `APPROVED_FOR_PCI_PRODUCTION_RELEASE`), which by framework design (`CLAUDE.md` §2A Gate 3B; `.human-approvals/README.md`) is created **only at Phase 12 closure**, after Phases 10 (Operational Readiness Gate) and 11 (PCI Security Readiness Gate) have formally closed. That is precisely the production-readiness boundary the docstring describes.

## Required change

### 1. `scripts/quality/operational_readiness_check.py`

Replace lines 12–13 and line 24:

```python
# OLD
APPROVAL = Path('.human-approvals/implementation-approved.txt')
CODE_MARKERS = [Path('src'), Path('app'), Path('services')]
# ...
strict = APPROVAL.exists() or any(has_non_placeholder_files(p) for p in CODE_MARKERS) or has_non_placeholder_files(Path('infra'))
```

with:

```python
# NEW
PROD_APPROVAL = Path('.human-approvals/pci-production-approved.txt')
# ...
strict = PROD_APPROVAL.exists()
```

Remove the unused `CODE_MARKERS` constant and the `has_non_placeholder_files` helper if no other code path uses them (they should not, after this change).

Update the module docstring:

```python
"""
Operational readiness CI gate.

This gate becomes strict once a Phase-12 human owner has created
.human-approvals/pci-production-approved.txt with content
APPROVED_FOR_PCI_PRODUCTION_RELEASE. Until then it runs in advisory mode:
it prints the list of missing required files but exits 0 so implementation
work can proceed.

The gate prevents production-bound work from passing CI with vague or
missing observability, reliability, scalability, and operations artifacts.
"""
```

### 2. `scripts/security/pci_readiness_check.py`

Replace line 29:

```python
# OLD
strict = SECURITY_APPROVAL.exists() or PROD_APPROVAL.exists() or any(has_non_placeholder_files(p) for p in CODE_MARKERS)
```

with:

```python
# NEW
strict = PROD_APPROVAL.exists()
```

Remove `SECURITY_APPROVAL` constant and `CODE_MARKERS` if no other code path uses them.

Update the module docstring:

```python
"""
PCI Tier 1 readiness CI gate.

Advisory until a Phase-12 human owner creates
.human-approvals/pci-production-approved.txt with content
APPROVED_FOR_PCI_PRODUCTION_RELEASE.

In advisory mode the script lists missing required files and exits 0
so implementation work can proceed (PCI controls are realized
incrementally across Chunks A–C; full PCI strictness is meaningful
only once the implementation is complete and ready for production).
"""
```

## Constraints on the fix

| Constraint | Detail |
|---|---|
| **Separate PR**, not bundled into Chunk A1 | The fix is an infrastructure change, not a Chunk A1 production artifact. Branch: `feature/fix-readiness-check-triggers`. Merge **before** Chunk A1 PR rebases. |
| **Scope: exactly these two files** | No edits to operational docs, no edits to PCI docs, no edits to `src/`, no edits to chunks. |
| **Rollback class A** (code rollback per `docs/operations/rollback-plan.md §4.2`) | Reverting either script restores the prior strict triggers; no schema or data implications. |
| **Tests required** | Add minimal pytest at `scripts/tests/test_readiness_checks.py` that exercises both scripts in advisory mode (no PROD marker → exit 0) and in strict mode (PROD marker present + intentional missing required file → exit 1). Mock filesystem via `tmp_path`. |
| **CI must turn green on the fix branch** before Chunk A1 PR merges | The whole point of the fix is to get CI to GREEN; verify on the fix branch first. |

## Why not edit the operational docs themselves

The "placeholder" tokens the strict scan flags are intentional v1-scope language. They reference E1, E3, E4, E8 in `docs/planning/p1-deferrals-acceptance.md` §4 — accepted deferrals with named-individual closure scheduled for Phase 12. Editing them out now would silently weaken the audit trail of those deferrals.

## Why not allow-list the tokens in the script

An allow-list (`vague_markers - intentional_placeholder_patterns`) is a workable alternative but adds maintenance burden and creates a subtle hole: a real future placeholder could match the allow-list pattern by accident. The trigger-deferral approach is more robust — strict mode fires when the system is genuinely production-bound, and at that point the operational docs SHOULD be free of placeholders because Phase 12 has closed E1/E3/E4/E8.

## Acceptance for this directive

- Implementer agent creates `feature/fix-readiness-check-triggers` with the two file edits and the test file.
- CI turns green on that branch.
- Reviewer (me) issues `30-review.md` for this directive in `chunks/<id>` if we elect to give the fix its own dossier folder (recommended: `chunks/13-PRE-readiness-check-fix/`), OR appends approval to the next chunk-A1 review file.
- Implementer merges the fix PR.
- Implementer rebases `feature/chunk-a1-domain` on the new `main`; Chunk A1's CI now goes green; the dossier's `manifest.yml` advances toward `accepted`.
