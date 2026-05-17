# 00-prompt — Chunk A1 (Phase 13 Pure-Core, M1 domain layer)

> **Provenance.** This file is a faithful reconstruction of the reviewer's original Chunk A1 kickoff prompt. The verbatim text was relayed in chat during a session whose canonical transcript lives at the project's session log on the operator's workstation (Anthropic Claude Code session JSONL). The reconstruction below captures the binding contract; if it ever drifts from the operator's transcript, the transcript is authoritative.
>
> Sub-chunk A1 was carved out of the original "Chunk A — Pure Core (M1 + M2)" kickoff after the implementer surfaced an LOC-cap deviation. The cleaned-up binding contract for A1 — which is the union of the original Chunk-A prompt + the LOC-cap revision — is captured here. See `10-deviation.md` for the deviation record and `20-summary.md` for the completion summary.

## Title

Chunk A1 — Pure-Core Domain Layer (Phase 13, M1).

## Branch

`feature/chunk-a1-domain` off `main`. Sub-chunk A2 (`feature/chunk-a2-application`) ships separately after A1 merges or as a rebased branch when review timing dictates.

## Scope (file mandate)

**Production:**
- `pom.xml` — Java 21 + JUnit 5 + AssertJ + jqwik + ArchUnit + uuid-creator 5.3.3 + Pitest + JaCoCo. No Spring Boot parent yet (A2 introduces it).
- `src/main/java/com/example/purchaseconversion/domain/`:
  - `Money.java` — scale-2 BigDecimal value object; HALF_UP multiply.
  - `CurrencyDescriptor.java` — preserves the space-bearing `Euro Zone-Euro` form per Phase-3 prototype.
  - `PurchaseId.java` — UUID v7 via uuid-creator; strict v7 validation.
  - `Purchase.java` — aggregate; description ≤ 50 chars.
  - `ExchangeRate.java` — composite-key value object; sanity bound 1e30; softened effectiveDate per Phase-6.
  - `RateSelectionPolicy.java` — 6-month rule (FR-003 / D-5); EOM-clamped via `LocalDate.minusMonths(6)`; tie-break per OQ-002.

**Tests:**
- `src/test/java/com/example/purchaseconversion/domain/` — `MoneyTest` (jqwik property-based, HALF_UP across signs/scales/magnitudes); `RateSelectionPolicyTest` (table-driven for AC-014..AC-020 + AC-018b + AC-019b); `PurchaseTest`; `ExchangeRateTest`; `CurrencyDescriptorTest`; `PurchaseIdTest` (UUID v7 timestamp-extraction property).
- `src/test/java/com/example/purchaseconversion/architecture/ArchitectureTests.java` — 6 ArchUnit rules per `component-design.md §6`, including the "no `double`/`float` in `domain` or `application`" rule that anchors the deliberate-fail evidence.

## Requirement coverage

FR-001..FR-003 (domain prerequisites); AC-002, AC-003, AC-004, AC-008, AC-014..AC-020, AC-018b, AC-019b, AC-024b, AC-025, AC-026; ADR-0001 D-2 (stack), D-4 (multiplicative orientation), D-5 (6-month + EOM clamp), D-6 (HALF_UP scale 2), D-7 (UUID v7), D-10 (scale-6 normalisation); OQ-002 (tie-break by effectiveDate); G4-P0-4, G4-P1-2, G6-P1-3, G4-P1-9.

AC-006 (future-date rejected), AC-001, AC-001b, AC-005, AC-007, AC-009, AC-021b/c, AC-026b — all service-level; deferred to A2.

## Hard constraints

- No touch outside the M1 scope (pom + `src/main/java/.../domain/` + `src/test/java/.../{domain,architecture}/` + `.gitignore`).
- No new ADR.
- No edits to `docs/requirements/source-requirements.md`, `prompts/`, `.claude/`, `security-profile.yml`, `CLAUDE.md`, `.human-approvals/`.
- No `double` / `float` in `domain` or `application` on the merged SHA (ArchUnit-enforced).
- All dependencies must come from ADR-0001 D-2's ratified stack.
- LOC discipline: ~1,500 sub-chunk cap, ~1,800 hard upper bound — surface deviations before consuming effort.
- ArchUnit deliberate-fail evidence: plant-and-revert pattern with both commit SHAs + a failing CI run URL cited in the PR. Alternative: a never-merged throwaway branch pushed for CI; cite its run from the real PR.

## Test evidence convention

Local environment lacks Java/Maven; cite CI run URLs in the PR description rather than local output. CI is the authoritative source.

## PR description contract

Use the change-control template per `docs/security/change-control-pci.md §1` (change-id, requirement link, risk assessment, security impact, CDE impact, test evidence, approval, rollback class, deployment window, post-deploy validation).

## §9 completion summary

End the chunk with the CLAUDE.md §9 summary written to `chunks/13-A1-domain/20-summary.md` and linked from the PR description.
