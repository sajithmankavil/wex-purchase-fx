# 20-summary — Chunk 13-A2-application

CLAUDE.md §9 completion summary for sub-chunk A2 (M2 application layer).

```
Summary:
- M2 application layer implemented per component-design.md §1 / §3 and
  the A2 00-prompt.md file mandate. No Spring annotations; no I/O;
  pure POJOs. Two services implement three inbound use-case interfaces;
  six outbound ports abstract the infrastructure for Chunk B.
- ArchitectureTests EXTENDED: the previously-vacuous
  applicationOnlyDependsOnDomainAndJdk rule now actively enforces
  (the application package exists); new rule noSpringStereotypesInApplication
  added per the A2 prompt's clause (b).
- 7 application exceptions added under application.exception.* —
  DomainException base + 6 service-level concretes (FutureDate,
  PurchaseNotFound, InvalidCurrency, ConversionRateNotAvailable,
  UpstreamUnavailable, UpstreamBadResponse). Controller-level
  exceptions (ValidationException, PanPatternDetectedException,
  MalformedIdentifierException) deferred to Chunk C per A2 scope.
- LOC ~1,411 (production 736 / tests 675). Below the 1,500 sub-chunk
  cap and the 1,800 hard upper bound (LOC-cap-revision directive).

Files changed (this PR; +1,411 LOC across 22 Java files):

Production (20 files; 736 LOC):
- application/port/in/:
  - RegisterPurchaseUseCase.java
  - RetrievePurchaseUseCase.java
  - ConvertPurchaseUseCase.java
  - RegisterPurchaseCommand.java
- application/port/out/:
  - PurchaseRepositoryPort.java
  - ExchangeRateRepositoryPort.java     (findInWindow + upsertVersioned;
    eligibility selection delegated to ConversionService via the
    domain.RateSelectionPolicy from A1 — see "Convention decisions" below)
  - TreasuryClientPort.java
  - CurrencyAliasPort.java
  - ExchangeRateHotCachePort.java        (findInWindow + putAll +
    invalidate; keyed by (currency, recordDate) per ADR-0001 D-10)
  - ClockPort.java                        (today())
- application/purchase/:
  - PurchaseService.java                  (RegisterPurchaseUseCase +
    RetrievePurchaseUseCase; AC-001..AC-009)
- application/conversion/:
  - ConversionResult.java                 (immutable record;
    purchase + rate + convertedAmount)
  - ConversionService.java                (ConvertPurchaseUseCase;
    hot cache → DB → Treasury → DB re-read fallback; AC-014..AC-026b)
- application/exception/:
  - DomainException.java                  (abstract base)
  - FutureDateException.java              (AC-006 → 422 FUTURE_DATE)
  - PurchaseNotFoundException.java        (AC-008 → 404)
  - InvalidCurrencyException.java         (AC-021b/c → 400)
  - ConversionRateNotAvailableException.java
                                          (AC-020 → 422)
  - UpstreamUnavailableException.java     (AC-023 → 503)
  - UpstreamBadResponseException.java     (AC-024/AC-024b → 502)

Tests (2 files; 675 LOC):
- application/purchase/PurchaseServiceTest.java
- application/conversion/ConversionServiceTest.java

ArchUnit extension (1 file modified):
- architecture/ArchitectureTests.java
  - applicationOnlyDependsOnDomainAndJdk: comment updated "Activated in
    Chunk A2"; rule otherwise unchanged.
  - noSpringStereotypesInApplication: NEW rule. Forbids @Component,
    @Service, @Repository, @Controller, @RestController,
    @RestControllerAdvice, @ControllerAdvice, @Configuration,
    @Autowired in the application package.

Convention decisions (forward-motion bias § 8):
1. application.exception location. component-design.md §1 puts exceptions
   in top-level `exception/`. We put them in `application.exception.`
   instead so the ArchUnit rule `applicationOnlyDependsOnDomainAndJdk`
   continues to pass without modification (it already permits `..application..`).
   Refactor to top-level later if desired; this is mechanical.
2. RateSelectionPolicy stays in `domain` where A1 placed it (also accepted
   by the A1 30-review.md §1). component-design.md §1 shows it under
   `application.conversion`; we treat A1's placement as canonical and
   note the design-doc divergence here.
3. ExchangeRateRepositoryPort returns `List<ExchangeRate>` from
   findInWindow(...) rather than the design doc's `findEligible(...) ->
   Optional<ExchangeRate>`. Selection happens in ConversionService via
   domain.RateSelectionPolicy. This keeps the policy in the application
   layer (where the design doc places it) while the port stays pure
   data-access (no business logic in adapters). Tests at the service
   level can mock the port returning specific lists to exercise
   AC-014..AC-020 at the service layer per the A2 prompt's mandate
   "rate-selection re-exercised via ConversionService".
4. ExchangeRateHotCachePort has the same `findInWindow(...) ->
   List<ExchangeRate>` shape and a `putAll(...)` + per-key
   `invalidate(...)` for D-10's cache-invalidation-on-revision semantics.
   ConversionService populates the cache after successful reads; the
   Chunk B adapter can choose to also invalidate on upsert (D-10's
   "cache invalidation on Treasury revision" clause).
5. Inbound vs outbound port packaging: application/port/in/ for
   use-cases, application/port/out/ for outbound ports. The design doc
   §1 shows only outbound ports under application/port/; the split
   in/out is a standard clean-architecture convention and is name-only.

Tests run:
- Local mvn test cannot run (no Java/Maven in this sandbox, same as A1).
- The 22 Java files compile cleanly to my read (every {@link} target
  exists, every imported type is on the classpath including A1 domain
  types + JDK + AssertJ/Mockito/JUnit5 from the existing pom dependency
  declarations).
- CI on this branch is the test-evidence channel per the chunkA LOC-cap
  revision §"Operational notes" §2. Pitest mutation thresholds for
  application (≥ 70 % package average; ≥ 80 % on ConversionService) are
  declared in pom.xml from A1 — they run on CI as part of `mvn verify`
  once the Java compile step is wired in (post-M7).

Requirement coverage:
- FR-001 + AC-001..AC-006: PurchaseService.register + FutureDateException
  (PurchaseServiceTest §"register").
- FR-002 + AC-007..AC-009: PurchaseService.retrieve + PurchaseNotFoundException
  (PurchaseServiceTest §"retrieve").
- FR-003 + AC-014..AC-027 (subset per A2 scope):
  - AC-014 exact-date, AC-015 most-recent, AC-018 6mo-before,
    AC-018b EOM-clamp eligible, AC-019 just-outside, AC-019b
    EOM-clamp ineligible, AC-020 no-rate, OQ-002 tie-break:
    ConversionServiceTest §"RateSelection" — all via the service
    (mock port returns list; ConversionService applies the domain
    policy from A1).
  - AC-021b/c alias-miss: ConversionServiceTest §"AliasResolution".
  - AC-023 / AC-024 / AC-024b upstream errors: ConversionServiceTest
    §"TreasuryFailures" (exceptions propagate untouched).
  - AC-025 HALF_UP rounding: ConversionServiceTest §"Monetary correctness".
  - AC-026b persistence-centric idempotency at service level: covered
    by the layer-ordering tests that always re-read from the DB after
    upsert before deciding eligibility (so the canonical state is the
    DB, not the fetched list).
- ADR-0001 D-1..D-14: D-1 layered structure (application sub-packages
  per design); D-2 ratified stack (no new deps); D-4 multiplicative
  conversion via Money.multiply; D-5 6-month window with EOM clamp via
  LocalDate.minusMonths; D-6 HALF_UP at scale 2; D-7 PurchaseId.next();
  D-8 dual-mode currency input via CurrencyAliasPort; D-9 single-flight
  gate is invisible to ConversionService (encapsulated in
  TreasuryClientAdapter; Chunk B); D-10 hot cache port keyed (currency,
  recordDate); D-13 detection-and-alert is API-layer (Chunk C); D-14
  H2 data-dir is config (Chunk B/C).
- NFR-021 mutation thresholds: pom declarations carry through from A1.
- NFR-030 (no double/float in application): ArchUnit's
  noDoubleOrFloatFieldsInDomainOrApplication +
  noDoubleOrFloatMethodReturnTypesInDomainOrApplication now activate
  on application as well — ports + services + DTOs + exceptions use
  Money / BigDecimal / LocalDate only.

Risks:
- ConversionService delegates rate-selection to the domain policy from
  A1 directly. If a future change to the policy's signature (e.g.,
  accepting a List instead of a Collection) breaks the service call,
  it would surface at compile time in ConversionService. Mitigated by
  Pitest mutation tests on the service that should exercise the
  selection path (will be verified once CI is wired).
- The hot-cache invalidation strategy is shared between
  ConversionService (positive population after reads) and the future
  Chunk B adapter (negative invalidation on upsert). If both happen
  on the same key in different orders, the steady-state is correct
  (the cache will be repopulated on next read) but transient
  thrashing is possible. Not a cascading risk — Chunk B can choose
  to drop the negative invalidation if benchmarks show thrashing.
- No A2 prompt deviation surfaced; the LOC budget is comfortably
  within the cap and no hard-constraint clash was hit.

Follow-ups (not blocking; logged for later chunks):
- [B] Implement the 6 outbound port adapters in infrastructure/.
  Each adapter goes in its design-doc-specified sub-package
  (persistence/ , treasury/ , currency/ , cache/) and references
  this PR's port interfaces.
- [B] SingleFlightGate inside TreasuryClientAdapter; key by
  (currency, treasury_quarter_end) per ADR-0001 D-9; 10s wait +
  100ms DB poll + winner-outcome-ref per Phase-6 G6-P0-2.
- [B] Cache-invalidation-on-upsert in
  ExchangeRateRepositoryAdapter calling hotCache.invalidate(...)
  per ADR-0001 D-10.
- [C] Bean wiring in @Configuration classes (kept OUT of application
  per the ArchUnit rule added here).
- [C] @RestControllerAdvice mapping the 6 application exceptions to
  RFC 9457 problem-details responses per component-design.md §4.
- [C] Controller-level exceptions: ValidationException,
  PanPatternDetectedException, MalformedIdentifierException — and
  ContentGuard with NFKC normalisation per design doc §3.5.

Convention extension to flag to the reviewer:
- DomainException base is in `application.exception.*`. The design
  doc places it at top-level `exception/`. If the reviewer prefers the
  top-level placement, the move is a one-package-rename refactor that
  needs the ArchUnit rule allow-list to include `..exception..` — a
  small one-line change. Not surfaced as a deviation because both
  placements are defensible; surfacing here per forward-motion §1
  "pick the most defensible interpretation, document it".
```

## Branch + PR

- Branch: `feature/chunk-a2-application` off `main` (post-A1-merge).
- Builds on commits: `f8cbd83` (A1 baseline), `3e03380` (A1 plant), `04f19aa` (A1 revert), all now on `main`; plus `fdfe8e9` (PR #4 fix) and `b762bec` (PR #3 dossier).
- PR will be opened after this summary commits.

## State transition

`chunks/13-A2-application/manifest.yml`:
- `status`: `implementing → summary_posted` on the same commit as this file.
- `summary_sha`: set after `git hash-object`.
- `pr`: set after `gh pr create`.
- `ci_url`: set after CI runs.
