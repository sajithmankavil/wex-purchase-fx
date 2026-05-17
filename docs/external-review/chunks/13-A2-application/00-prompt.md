# 00-prompt — Chunk 13-A2-application

> Canonical kickoff for Sub-chunk A2 (M2 application services + ports + exceptions). Authored by external reviewer 2026-05-17 in the same commit as `chunks/13-A1-domain/30-review.md`. Pre-staged so the implementer can begin A2 the moment A1 merges — no reviewer round-trip required.

## Prerequisite

A1 merged to `main`. Both `.human-approvals/{implementation,pci-security}-approved.txt` remain in place (created at Phase 9 closure).

## Read first

0. `docs/external-review/` — every file. Specifically:
   - `STATUS.md`
   - `directives/2026-05-17-ops-check-and-pci-check-strict-flip.md` (in force; `ops-check` + `pci-check` are advisory until production marker exists)
   - `chunks/13-A1-domain/30-review.md` (reviewer code-correctness verdict on the domain layer you build on)
   - `chunks/13-A1-domain/10-deviation.md` (LOC-cap-revision authority: ~1,500 LOC sub-chunk cap; ~1,800 hard upper bound)
1. `CLAUDE.md` (entire file).
2. `docs/architecture/adr-0001-core-architecture.md` D-1..D-14 — note especially D-9 single-flight gate keyed by `(currency, treasury_quarter_end)` with loser semantics 10 s wait + outcome-ref + 100 ms DB poll (G6-P0-2 closure); D-10 hot-cache key `(currency, record_date)`; D-13 detection-and-alert guards (placeholder hook in M2, full design in M4).
3. `docs/architecture/component-design.md` §1 (clean-architecture package layout), §3 (per-component contracts — read all sub-sections, especially `ConversionService` §3.2), §6 (ArchUnit rules — your ArchitectureTests already exists from A1; you extend it), §8 (per-component test plan).
4. `docs/requirements/functional-requirements.md` FR-001..FR-006.
5. `docs/requirements/acceptance-criteria.md` — AC-001..AC-009 (purchase create + retrieve happy + error paths at service level), AC-014..AC-020 + AC-018b + AC-019b (rate-selection re-exercised via `ConversionService`), AC-021b/c (alias-table-drift dual cases), AC-026b (persistence-centric idempotency — service-level expectation), AC-T-1 / AC-T-2 cross-cutting where applicable to pure-application logic.
6. `docs/requirements/non-functional-requirements.md` NFR-021 (mutation thresholds).
7. `docs/security/change-control-pci.md` §1 — PR template required fields.
8. `docs/operations/rollback-plan.md` §4.2 — rollback class A (code).

## Scope — exact and bounded

| Item | Detail |
|---|---|
| Goal | `com.example.purchaseconversion.application.*` orchestrates domain logic via *ports* (interfaces). No Spring annotations anywhere in `application` — ports and services are pure POJOs that will be wired by configuration in M4. Per component-design.md §1 / §3. |
| Production files (services) | Per `component-design.md §3` — at minimum: `PurchaseRegistrationService` (FR-001 purchase create), `PurchaseLookupService` (FR-002 retrieve), `ConversionService` (FR-003 conversion). Match the exact names in the doc. |
| Production files (inbound ports) | Use-case interfaces: `RegisterPurchaseUseCase`, `RetrievePurchaseUseCase`, `ConvertPurchaseUseCase` (or whatever exact names component-design.md §3 specifies). Pure POJOs. |
| Production files (outbound ports) | `PurchaseRepoPort`, `ExchangeRateRepoPort`, `TreasuryClientPort`, `CurrencyAliasTablePort`, `ExchangeRateHotCachePort`, `ClockPort`. Match the exact names in `component-design.md §3`. |
| Production files (DTOs / events as needed) | Per the doc — plain records; no Spring. |
| Exception classes | Domain exception base + concrete subtypes (e.g., `PurchaseNotFoundException`, `ConversionRateUnavailableException`, `RateSelectionAmbiguousException`, etc. per the AC error-code catalogue). |
| Test files | One unit test class per service, Mockito-stubbed ports. Cover happy path + every documented error path + every AC error code. |
| ArchitectureTests | EXTEND the existing `ArchitectureTests.java` from A1 with: (a) `applicationOnlyDependsOnDomainAndJdk` now activates (the vacuous-satisfaction rule from A1 starts checking real classes); (b) no `@RestController` / `@Controller` / Spring bean annotations in `application/`. |

## Out of scope

- No persistence (no JPA, no JDBC, no Liquibase / Flyway). Adapters live in M3 (Chunk B).
- No HTTP, no Spring MVC. Controllers live in M4 (Chunk C).
- No Resilience4j, no WireMock, no Caffeine. They live in M3.
- No ContentGuard, no rate-limit filter, no `@RestControllerAdvice`. M4.
- No observability wiring. M5.
- No OpenAPI. M6.
- No CI workflow edits.
- No edits to `domain/*` (it's frozen — A1's review verified it).
- No edits to `prompts/`, `.claude/`, `security-profile.yml`, `CLAUDE.md`, `docs/requirements/source-requirements.md`, or `.human-approvals/`.

## Acceptance gates (must all pass at merge)

| Gate | Target | Source |
|---|---|---|
| Unit tests | All green | mvn test |
| ArchUnit | All rules pass; the previously-vacuous `applicationOnlyDependsOnDomainAndJdk` is now actively checking real `application/` classes | component-design.md §6 |
| Line coverage on `application/*` | ≥ 80 % | NFR-021 (application is critical path → 80 % vs 70 % package average) |
| Mutation (Pitest) on `application/*` package average | ≥ 70 % | NFR-021 |
| Mutation on `ConversionService` | ≥ 80 % | NFR-021 + per-class threshold convention (ConversionService is the most complex service) |
| AC coverage table | Every AC listed above maps to one or more test methods; show the table in the PR description | acceptance-criteria.md |
| LOC | ≤ ~1,500 (target) / ≤ ~1,800 (hard upper bound). Surface deviation if exceeded. | chunkA LOC-cap-revision directive |

## Branching, PR, CI

- Branch: `feature/chunk-a2-application` off `main` (post-A1-merge).
- One PR. Estimated ~1,100 LOC.
- Run `scripts/quality/lint.sh`, `scripts/quality/test.sh`, `scripts/quality/test-unit.sh`, `scripts/security/local-security-check.sh` locally. All green required.
- CI green on the branch (`.github/workflows/ci.yml` + `.github/workflows/security.yml`); ops-check and pci-check should both be advisory (no production marker).

## PR description (change-control-pci.md §1)

```
change-id: WEX-CHUNK-A2-application
requirement link: FR-001, FR-002, FR-003; AC-001..AC-009, AC-014..AC-020, AC-018b, AC-019b, AC-021b/c, AC-026b; NFR-021; ADR-0001 D-1..D-14
risk assessment: Low — pure-application code, port interfaces only (no real I/O), no PCI surface touched.
security impact: none. No CHD path. No secrets. No I/O.
CDE impact: none. Application layer is out-of-CDE by design.
test evidence:
  - JUnit: <CI URL>
  - ArchUnit (incl. applicationOnlyDependsOnDomainAndJdk now active): <output>
  - Pitest application/* package: <%>; ConversionService: <%>
  - Coverage application/*: line <%>, branch <%>
  - AC coverage table: <inline or link>
approval: <Architect at minimum>
rollback class: A (code rollback per rollback-plan.md §4.2)
deployment window: continuous
post-deploy validation: unit + ArchUnit + Pitest gates green at merge
```

## Step-by-step workflow

1. Restate goal.
2. Identify requirement references.
3. Document assumptions.
4. Propose package layout (read `component-design.md §1` first; do not invent names).
5. List files expected to change with exact paths.
6. Define every test class + ACs it covers.
7. Implement in order: outbound port interfaces → inbound use-case interfaces → exception classes → services (one at a time, test-first) → extend `ArchitectureTests` to activate the `applicationOnlyDependsOnDomainAndJdk` rule.
8. Run all quality scripts. Iterate until green.
9. Summarise per CLAUDE.md §9 to `chunks/13-A2-application/20-summary.md` (NOT chat). Flip `manifest.status: implementing → summary_posted` on the same commit.

## Completion summary

Write to `chunks/13-A2-application/20-summary.md` using the CLAUDE.md §9 format. After writing, the reviewer will inspect via `git show feature/chunk-a2-application` and produce `chunks/13-A2-application/30-review.md`.

If a deviation surfaces (LOC overage, scope tension, hook block), write `chunks/13-A2-application/10-deviation.md` first, flip `manifest.status: prompt_received → deviation_surfaced`, and wait. Otherwise flip `status: implementing` and proceed.
