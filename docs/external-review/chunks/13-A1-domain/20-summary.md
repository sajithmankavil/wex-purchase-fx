# 20-summary — Chunk A1 (Phase 13 M1 domain layer)

CLAUDE.md §9 completion summary for sub-chunk A1, copied here as the durable record. Original PR description: https://github.com/sajithmankavil/wex-purchase-fx/pull/2.

```
Summary:
- Chunk A1 implemented per Option (b) of the LOC-cap deviation: M1 domain
  layer only. M2 application services + ports deferred to A2.
- Three commits on feature/chunk-a1-domain (baseline 970bd81 → plant 06240ce
  → revert ff773bf). Plant-and-revert pattern provides durable ArchUnit
  fitness-function evidence in git history.
- Draft PR #2 opened: https://github.com/sajithmankavil/wex-purchase-fx/pull/2
- PR #1 closed as superseded before opening #2.
- DEVIATION SURFACED — see Risks: CI is RED on revert SHA, but the cause is
  pre-existing and unrelated to Chunk A1 code. Diagnosis + four resolution
  options included in the PR description; awaiting reviewer direction before
  any out-of-Chunk-A1-scope change.

Files changed (this PR; 15 files; +1471 LOC):
- pom.xml (NEW, 187 LOC) — Java 21 + JUnit 5 + AssertJ + jqwik + ArchUnit
  + uuid-creator 5.3.3 + Pitest + JaCoCo. No Spring parent yet (A2).
- src/main/java/com/example/purchaseconversion/domain/
  - Money.java, CurrencyDescriptor.java, PurchaseId.java, Purchase.java,
    ExchangeRate.java, RateSelectionPolicy.java (6 files; 398 LOC).
- src/test/java/com/example/purchaseconversion/domain/
  - MoneyTest, RateSelectionPolicyTest, CurrencyDescriptorTest,
    PurchaseIdTest, PurchaseTest, ExchangeRateTest (6 files).
- src/test/java/com/example/purchaseconversion/architecture/
  - ArchitectureTests.java (6 ArchUnit rules per component-design.md §6).
- .gitignore — defensive addition of `.human-approvals/*.txt` to prevent
  the prior PR-#1 regression (markers swept into commit by `git add .`).

Tests run:
- mvn test cannot run under existing CI (no Java compile step; M7 carry-
  forward). Reviewer must run locally on both the plant SHA and revert SHA
  per the PR's "Reviewer action — REQUIRED before approval" section.
- The plant SHA's expected failure mode and the revert SHA's expected pass
  criteria are documented as the deliberate-fail evidence.

Requirement coverage:
- FR-001..FR-003 (domain-layer prerequisites).
- AC-002, AC-003, AC-004, AC-008, AC-014..AC-020, AC-018b, AC-019b,
  AC-024b, AC-025, AC-026 — full AC→test-method map in the PR.
- AC-006 deferred to A2 (application-layer responsibility).
- ADR-0001 D-2 / D-4 / D-5 / D-6 / D-7 / D-10 honoured.
- OQ-002 tie-break implemented and tested.
- NFR-021 (mutation thresholds) — Pitest configured at 85% on
  RateSelectionPolicy + Money.
- NFR-030 (BigDecimal-only) — ArchUnit-enforced (and plant-evidenced).
- Phase-6 grill items G4-P0-4, G4-P1-2, G6-P1-3, G4-P1-9 addressed.

Risks:
- DEVIATION: CI status on the revert SHA is **RED**, not GREEN as the
  Chunk A1 brief had assumed. Root cause is in scripts/quality/
  operational_readiness_check.py (line 24): the gate flips from advisory
  to strict mode the moment `src/` contains files. Chunk A1 is the first
  commit that puts Java under `src/`, so the gate now strict-scans
  pre-existing operational docs (`docs/operations/*` +
  `docs/planning/operational-design-session.md`) and flags occurrences
  of the literal word "placeholder". Those occurrences are intentional
  v1-scope language — the docs themselves say "the case-study build
  ships with placeholders; real names land at pre-production hand-off".
  No Chunk-A1 file is implicated. Hard constraint forbids me from
  editing those files in this PR. **Resolution options (a/b/c/d) are in
  the PR description; please advise which to take.** My recommendation
  is (c) — strengthen the strict-mode trigger to require
  `.human-approvals/implementation-approved.txt` AND `src/`, not just
  `src/`. That defers strict mode to the production-readiness boundary
  where this gate was designed to bite, and closes the parallel issue
  in pci-check at the same time.
- Pre-existing security.yml workflow is a startup_failure on every
  commit on main and on this branch — also M7 carry-forward; not a
  Chunk A1 regression.
- Diff size is ~1 471 LOC — over the brief's 1 000 LOC threshold; already
  surfaced + accepted last turn.

Follow-ups:
- Reviewer chooses (a)/(b)/(c)/(d) for the ops-check deviation.
  → Resolved post-summary: reviewer issued directive selecting option (c)
    with the correct marker (pci-production-approved.txt rather than
    implementation-approved.txt). Tracked as chunk 13-PRE-readiness-check-fix.
- Reviewer pastes local `mvn test` output for plant + revert SHAs on PR #2.
- Chunk A2 kickoff prompt: application services + ports + domain
  exceptions + ConversionResult DTO, against the integration branch.
- PR #2 stays draft until both items above land.
```

## Post-summary developments

After the §9 summary was first posted, the reviewer responded with two prompts that are tracked in their own dossiers:

1. **`13-PRE-dossier-bootstrap`** — scaffold this dossier convention. PR forthcoming on `feature/external-review-dossier-bootstrap`.
2. **`13-PRE-readiness-check-fix`** — implement the ops-check + pci-check trigger fix per [`directives/2026-05-17-ops-check-and-pci-check-strict-flip.md`](../../directives/2026-05-17-ops-check-and-pci-check-strict-flip.md). PR forthcoming on `feature/fix-readiness-check-triggers`.

Once `13-PRE-readiness-check-fix` merges, `feature/chunk-a1-domain` will be rebased on `main`; both `ops-check` and `pci-check` will return to advisory mode; A1's CI is expected to go green; the reviewer then writes `chunks/13-A1-domain/30-review.md`. At that point `manifest.yml` flips `deviations_open: []` and `status: under_review` (or `accepted` depending on the verdict).
