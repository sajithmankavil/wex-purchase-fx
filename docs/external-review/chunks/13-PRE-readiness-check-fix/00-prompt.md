# 00-prompt — Chunk 13-PRE-readiness-check-fix

> Canonical kickoff for the readiness-check trigger fix. This prompt is the "Work item 2" body of the reviewer's 2026-05-17 dual-prompt that also commissioned the dossier bootstrap. The full technical spec is in [`directives/2026-05-17-ops-check-and-pci-check-strict-flip.md`](../../directives/2026-05-17-ops-check-and-pci-check-strict-flip.md); this file is the chunk-scoped binding contract.

## Title

Readiness-check trigger fix — defer strict mode in `ops-check` and `pci-check` to the production-approval marker.

## Branch

`feature/fix-readiness-check-triggers` off `main`. NOT layered on the dossier-bootstrap branch — separate review surface, separate rollback class.

## Scope (file mandate — exactly these three files)

1. `scripts/quality/operational_readiness_check.py` — replace `APPROVAL` + `CODE_MARKERS` with `PROD_APPROVAL`; replace strict-trigger line; remove now-unused helpers; update module docstring.
2. `scripts/security/pci_readiness_check.py` — replace strict-trigger line; remove unused `SECURITY_APPROVAL` + `CODE_MARKERS` constants; update module docstring.
3. `scripts/tests/test_readiness_checks.py` — NEW. Minimal pytest covering both scripts in advisory mode (no PROD marker → exit 0) and strict mode (PROD marker present + intentional missing required file → exit 1). Use `tmp_path` and `subprocess` or `runpy` to invoke each script.

The full code shape is specified in the directive linked above.

## Acceptance gates

- Both scripts run advisory in the current workspace (no `pci-production-approved.txt` marker) → exit 0 → CI green.
- pytest in `scripts/tests/test_readiness_checks.py` passes (local invocation; the existing CI workflow does not yet discover `scripts/tests/` — same M7 carry-forward as the no-Java-CI gap. Cite local pytest output in the PR description).
- No edits to operational docs, PCI docs, `src/`, or any chunk folder.

## PR description contract (change-control-pci.md §1)

```
change-id: WEX-PRE-fix-readiness-check-triggers
requirement link: docs/external-review/directives/2026-05-17-ops-check-and-pci-check-strict-flip.md
risk assessment: Low — strict triggers deferred to production-readiness boundary per design intent.
security impact: low. PCI readiness check moves to advisory during implementation; full strictness restored at Phase 12 marker.
CDE impact: none.
test evidence:
  - pytest scripts/tests/test_readiness_checks.py: <local output (CI does not yet run pytest)>
  - CI on this branch: <URL> — green
approval: <Architect + SRE>
rollback class: A
deployment window: continuous
post-deploy validation: CI on a downstream branch shows ops-check + pci-check in advisory mode
```

## Workflow

1. Push Work item 1 (dossier bootstrap) first; let CI go green; open its PR.
2. Push this fix on a separate branch; let CI go green; open its PR.
3. After this fix merges to `main`, rebase `feature/chunk-a1-domain` on `main`; A1's CI now goes green (both ops-check + pci-check advisory).
4. Update `chunks/13-A1-domain/manifest.yml`: flip `deviations_open: []` and advance state.
5. Reviewer writes `chunks/13-A1-domain/30-review.md` next.

## §9 completion summary

Write the §9 summary to `chunks/13-PRE-readiness-check-fix/20-summary.md` after the PR is open. (Implementer note: because this chunk's PR is scripts-only and the brief forbids edits to chunk folders within this PR's scope, the 20-summary.md will land via the dossier-bootstrap PR or a small follow-up commit rather than inside this PR.)
