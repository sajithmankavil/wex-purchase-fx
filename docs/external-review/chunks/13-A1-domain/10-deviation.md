# 10-deviation — Chunk A1

Two deviations were surfaced during Chunk A1. Both are recorded here as the durable chunk-level record; cross-cutting directives that emerged from them live under `docs/external-review/directives/`.

---

## Deviation 1 — LOC-cap inconsistency (surfaced before implementation start)

### Observation (implementer)

The original "Chunk A — Pure Core (M1 + M2)" kickoff carried an LOC cap of ~800 (target) / ~1,000 (stop and re-scope). The same kickoff's file mandate listed M1 + M2 + jqwik property tests on Money + an AC-014..AC-020 + AC-018b + AC-019b boundary table + ArchUnit deliberate-fail evidence. Honest estimate: ~2,400 LOC. The cap and the scope were internally inconsistent.

### Reviewer ruling — LOC-cap revision + sub-chunk split

The reviewer revised the LOC cap and split Chunk A into two sequential sub-chunks along the `domain` → `application` dependency seam:

| Constraint | New value |
|---|---|
| Sub-chunk PR LOC cap (incl. tests) | ~1,500 |
| Hard upper bound (stop and re-scope) | ~1,800 |
| Discipline | Surface deviations before consuming effort — never silently exceed. |

**Sub-chunk A1** = M1 domain (this chunk). Branch `feature/chunk-a1-domain` off `main`. Estimated ~1,300 LOC; actual ~1,471. Production files: pom + 6 domain classes. Tests: 6 domain test classes + ArchUnit. ArchUnit deliberate-fail evidence via plant-and-revert (cite both commit SHAs + a failing CI run URL in the PR description).

**Sub-chunk A2** = M2 application services + ports + exceptions. Branch `feature/chunk-a2-application` off `feature/chunk-a1-domain` (rebase to `main` if A1 merges first). Estimated ~1,100 LOC.

**Operational acceptances** carried along with the ruling:
- PR #1 (the prior standalone M1) is superseded; close with "Superseded by Chunk A under the 3-chunk plan."
- Local environment lacks Java/Maven; cite CI run URLs in PR descriptions rather than local output.
- ArchUnit plant-and-revert: plant on a temporary commit; capture failing CI URL; revert; cite both SHAs + the URL. Alternative: throwaway branch pushed only for CI.

### Status

Resolved before A1 implementation began. A1 was authored within the revised cap. The original reviewer ruling lived in `docs/external-review/2026-05-17-chunkA-loc-cap-revision.md`; that file is now superseded by this dossier entry and removed in the same PR that bootstraps the dossier convention.

---

## Deviation 2 — ops-check + pci-check strict-mode flip on `src/` presence (surfaced after A1 push)

### Observation (implementer)

After pushing the A1 baseline + plant + revert commits to `feature/chunk-a1-domain` and opening PR #2, CI ran **red** on all three commits. Root cause: `scripts/quality/operational_readiness_check.py` line 24 flips from advisory to strict mode the instant any file exists under `src/`, `app/`, or `services/`. Pre-A1, main had no `src/` → check was advisory → CI green. A1's baseline commit introduces `src/main/java/...` for the first time → check is now strict → it greps for the literal token "placeholder" across `docs/operations/*.md` and `docs/planning/operational-design-session.md` and reports failures.

The flagged occurrences are **intentional v1-scope language** — the docs themselves say *"the case-study build ships with placeholders; real names land at pre-production hand-off."* The literal-word scan does not distinguish self-aware case-study language from unresolved scaffolding. Files flagged: `service-catalog.md`, `runbook.md`, `oncall-escalation.md`, `operational-readiness-gate.md`, `operational-design-session.md`. None were touched by A1; the same files passed under main's advisory mode at the prior `M-1 closure` commit.

`scripts/security/pci_readiness_check.py` line 29 has the same flip pattern and would be in strict mode for the same reasons.

### Implementer's proposed resolution options (in PR #2 description)

- (a) Rename "placeholder" → "v1 holder" in the affected docs.
- (b) Tighten the gate's regex to require `<placeholder>` or `TODO: placeholder`.
- (c) Strengthen the strict-mode trigger to require `.human-approvals/implementation-approved.txt` AND `src/`.
- (d) Accept RED CI for A1; address in a follow-up chunk.

Recommendation in PR #2 description: option (c).

### Reviewer ruling — option (c) approved, but with the correct marker

The reviewer issued a separate cross-cutting directive: [`docs/external-review/directives/2026-05-17-ops-check-and-pci-check-strict-flip.md`](../../directives/2026-05-17-ops-check-and-pci-check-strict-flip.md). The substantive ruling:

- Option (c) is correct in spirit, but the trigger marker proposed by the implementer (`implementation-approved.txt`) is functionally identical to the current `OR src/` trigger because the implementation marker already exists from Phase 9.
- The **correct** trigger marker is `.human-approvals/pci-production-approved.txt` (content `APPROVED_FOR_PCI_PRODUCTION_RELEASE`), which by framework design is created only at Phase 12 closure — the actual production-readiness boundary the docstrings reference.
- The fix is a separate PR (`feature/fix-readiness-check-triggers`) authored as chunk `13-PRE-readiness-check-fix`. It must land before `feature/chunk-a1-domain` is rebased.

### Status

Resolution in flight. Tracked as chunk `13-PRE-readiness-check-fix`. After that fix merges, `feature/chunk-a1-domain` rebases on `main` and A1's CI should turn green (both `ops-check` and `pci-check` now in advisory mode). At that point this chunk's `manifest.yml` advances toward `accepted` and the reviewer issues `30-review.md`.

The pre-existing `security.yml` workflow startup_failure (0s runtime, every commit on main and on every branch) is a separate, pre-existing M7 carry-forward — not in scope of either deviation. It is the same nature as the no-Java-CI gap that motivates citing local `mvn test` for ArchUnit evidence.
