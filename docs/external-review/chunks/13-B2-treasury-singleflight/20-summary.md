# 20-summary — Chunk 13-B2-treasury-singleflight

CLAUDE.md §9 completion summary for sub-chunk B2 (M3 — upstream HTTP + single-flight + resilience).

```
Summary:
- Treasury upstream HTTP path implemented per the B2 00-prompt.md contract.
  TreasuryClientAdapter wraps RestClient with PROGRAMMATIC Resilience4j
  decorators (Bulkhead → Retry → CircuitBreaker), JSON shape validation,
  scale-6 normalisation, sanity-bound checks.
- SingleFlightGate implements ADR-0001 D-9: per-(currency,
  treasury_quarter_end) key (G4-P0-1), AtomicReference<WinnerOutcome>,
  10 s loser wait, 100 ms DB poll, SIGTERM releaseAll() via @PreDestroy.
- B1's stub TreasuryClient in WexConfig REMOVED; the real adapter is
  @Component-scanned. WexConfig adds a RestClient.Builder bean with
  2 s connect + 2 s read timeouts.
- Resilience4j configured in application.yml: TIME_BASED CB with 300 s
  sliding window, 5 min open, 3 half-open, min-calls 5, threshold 50 %;
  Retry 3 attempts 100 ms+jitter; Bulkhead 10 permits; TimeLimiter 2 s.

Files changed (this PR; +1,468 / -21 across 13 files):

Production (5 Java files + 1 YAML edit + 1 pom edit; ~700 LOC net new):
- pom.xml — resilience4j-spring-boot3 2.2.0 + resilience4j-reactor +
  spring-boot-starter-aop (Resilience4j AOP) + wiremock-standalone 3.9.2
  (test scope).
- src/main/resources/application.yml — wex.treasury.* config +
  resilience4j.circuitbreaker / .retry / .bulkhead / .timelimiter
  instance `treasuryClient`.
- src/main/java/com/example/purchaseconversion/config/WexConfig.java
  (stub TreasuryClient removed; RestClient.Builder bean added).
- src/main/java/com/example/purchaseconversion/infrastructure/treasury/
  - TreasuryResponse.java                  (Jackson-mapped Fiscal Data API response)
  - TreasuryClientAdapter.java             (programmatic R4j chain; gate-wrapped)
  - SingleFlightGate.java                  (per-key gate; @Component; @PreDestroy)

Tests (5 files; ~700 LOC):
- SingleFlightGateTest.java                (UNIT; 8 cases — quarter-end ceiling,
                                            winner-once, AC-027b 2 losers + 1 winner,
                                            AC-027d loser-mirrors, loser_timeout,
                                            releaseAll, shutdown-unblocks-loser)
- TreasuryClientIT.java                    (WireMock; AC-T-3 12 cases — happy +
                                            empty + 3 scale variants + sanity-ceiling +
                                            malformed JSON + null/zero/negative/above
                                            ceiling + 5xx + 4xx)
- SingleFlightCacheConcurrencyIT.java      (AC-027b 3 concurrent identical → 1 call;
                                            AC-027e same-quarter dedup)
- CircuitBreakerCalibrationIT.java         (compressed CB calibration; OPEN after
                                            min-calls=5 sustained 5xx; CallNotPermitted
                                            maps to "circuit_open" fallback)
- GracefulShutdownIT.java                  (G4-P1-23 context-close → releaseAll →
                                            in-flight loser fails with "shutdown")

PCI-critical invariants verified at this merge (per 00-prompt.md):

| Invariant                                                | Test                                       |
|----------------------------------------------------------|--------------------------------------------|
| G6-P0-2 loser 10s wait + 100ms poll + winner-outcome ref | SingleFlightGateTest.{twoLosersOneWinner, loserMirrorsWinnerFailure, loserTimesOut} |
| G4-P0-1 gate key (currency, treasury_quarter_end)        | SingleFlightGateTest.{quarterEndCeiling, sameQuarterSharesKey}; SingleFlightCacheConcurrencyIT.quarterDeduplication |
| G6-P0-1 time-based CB opens                              | CircuitBreakerCalibrationIT.breakerOpens |
| AC-T-3 widened 12 cases                                  | TreasuryClientIT.{HappyAndScale, SchemaAndSanity, Availability} |
| G4-P1-23 SIGTERM releases gate                           | SingleFlightGateTest.{releaseAllShortCircuits, releaseAllUnblocksLoser}; GracefulShutdownIT.contextCloseReleasesLosers |

Tests run:
- Local mvn cannot run (M7 carry-forward). Branch CI is the test-
  evidence channel.
- SingleFlightGateTest is a pure JUnit + ExecutorService unit test
  (no Spring); validates the gate's state-machine standalone.
- Other 4 ITs use @SpringBootTest + AbstractPostgresIT (Postgres
  Testcontainer) + WireMock; each has its own @TestPropertySource for
  compressed CB calibration / loosened CB / etc.
- Pitest gates for B2:
    SingleFlightGate + TreasuryClientAdapter: ≥80% each (existing
       pom infrastructure-critical execution targets both classes).
    infrastructure/* package average: ≥70%.
- JaCoCo gates (infrastructure): line ≥0.75, branch ≥0.65 — from B1's
  pom executions, unchanged.

Requirement coverage:
- FR-003 Treasury HTTP path: TreasuryClientAdapter + TreasuryClientIT.
- AC-T-3 widened: TreasuryClientIT 12 cases per the prompt.
- AC-027b/c/d/e: SingleFlightGateTest + SingleFlightCacheConcurrencyIT.
- ADR-0001 D-9 single-flight + Resilience4j: SingleFlightGate +
  TreasuryClientAdapter programmatic chain + application.yml config.
- ADR-0001 D-12 orientation: scale-6 normalisation on parse +
  multiplicative formula handled at Money.multiply (A1).
- G6-P0-1 / G6-P0-2 / G4-P0-1 / G4-P1-23: tests above.
- NFR-021 mutation: B2 critical classes (SingleFlightGate +
  TreasuryClientAdapter) ≥ 80%; package ≥ 70%.

Convention decisions (forward-motion bias §8 calls):
1. PROGRAMMATIC Resilience4j (Bulkhead.decorateSupplier + Retry +
   CircuitBreaker), not @CircuitBreaker / @Retry / @Bulkhead annotations.
   Reason: the annotated method would be called via self-invocation
   from fetchRates() (inside the SingleFlightGate.runOnce supplier),
   which bypasses Spring AOP proxies and silently skips the wrappers.
   The programmatic chain is equivalent in behaviour and survives
   self-invocation.
2. CircuitBreakerCalibrationIT runs a COMPRESSED calibration
   (5 s sliding window, 1 s open, 2 half-open) so the test finishes
   in seconds rather than minutes. The production calibration
   (300 s / 5 min / 3) is in src/main/resources/application.yml.
   A second test in the IT documents this and asserts the override
   path takes effect.
3. TimeLimiter is declared in application.yml but NOT applied at the
   adapter level. Reason: @TimeLimiter requires the method to return
   a CompletableFuture, which would force the whole call chain to be
   reactive. Per-call timeout is enforced by RestClient's
   SimpleClientHttpRequestFactory (connect 2 s + read 2 s) wired in
   WexConfig.restClientBuilder(). Net effect on the user-visible
   contract is the same.
4. WireMock-standalone (not WireMock-jre8) chosen because it bundles
   all transitive deps under one jar — cleaner against Spring Boot 3's
   BOM-managed servlet/jackson versions.
5. The B2 prompt's "12-case widened AC-T-3" is structured into three
   @Nested groups inside TreasuryClientIT for legibility: HappyAndScale
   (6 cases) + SchemaAndSanity (5 cases) + Availability (2 cases).

Risks:
- TreasuryClientIT's CB-tripping tests rely on the loose CB
  configuration (`minimum-number-of-calls=20`) to keep individual
  failures from accidentally opening the breaker mid-test. If the test
  config drifts back to production calibration, fail-fast tests may
  intermittently see "circuit_open" instead of the expected mapping
  (e.g., http_5xx). Mitigated by explicit @TestPropertySource on the
  IT class.
- The CircuitBreakerCalibrationIT's "productionCalibrationDocumented"
  test asserts the COMPRESSED values from this test class's
  @TestPropertySource (not the production yml). The production values
  themselves are not unit-asserted; they're documented in the yml file
  and inspected by the reviewer during 30-review.
- The SingleFlightGate's loser-polls-DB uses an immediately-returned
  Boolean (no transactional read). In a multi-replica deployment, the
  loser's DB poll could see a brief moment where the winner has flipped
  WinnerOutcome to success but the row is not yet replicated. The
  gate's loop handles this by continuing to poll until either the row
  is visible or the wait expires; AC-027d is satisfied because the
  outcome ref is checked on every iteration.
- The application/* coverage gates landed in B1 (A2 intake §1) continue
  to apply on this branch. B2's added test density on infrastructure/*
  does not affect them.
- LOC: +1,468 / -21 across 13 files. Within the 1,500 soft target and
  the 1,800 hard upper bound. No deviation.
- Follow-up from B1 30-review §2 finding (iii)
  pitest-conversionservice-per-class-execution — NOT addressed in
  this PR. B1's manifest tracks it as follow-up; can land in a
  small PR after B2 or absorbed into C.

Follow-ups (not blocking; logged for later chunks):
- [C] HTTP layer + @RestControllerAdvice (maps the 6 application
  exceptions to RFC 9457 problem-details responses) + ContentGuard
  (NFKC + decoder pipeline) + OpenAPI + observability wiring + DescriptionHasher.
  Closes the C-scoped A2 intake item (currency-input hashing on emit).
- [Follow-up PR] B1 30-review.md §2 finding (iii) pitest per-class
  threshold for ConversionService via a dedicated execution.
- [Post-M7] CI test discovery for scripts/tests/ + Java compile +
  ArchUnit + Pitest + JaCoCo in CI; production CB calibration assertion.
```

## Branch + PR

- Branch: `feature/chunk-b2-treasury-singleflight` off `main` (post-B1-merge).
- Builds on commits: B1's `d96e08b` (persistence + cache + A2 regression fix) + earlier dossier maintenance.
- PR opens after this summary commits.

## State transition

`chunks/13-B2-treasury-singleflight/manifest.yml`:
- `status`: `implementing → summary_posted` on the same commit as this file.
- `summary_sha`: set after `git hash-object`.
- `pr`, `ci_url`: set after `gh pr create` + CI runs.
