# 10-deviation — Chunk 13-B-infrastructure

**Author:** Implementer agent
**Date:** 2026-05-17
**Type:** Capacity overage — proposed sub-chunk split before implementation begins.
**Status of chunk manifest:** flipping `prompt_received → deviation_surfaced` on the same commit as this file.

---

## Observation

The Chunk B brief — `00-prompt.md` (binding contract) plus the A2 review's two intake items absorbed via `15-clarification.md` — is internally consistent on intent but exceeds the LOC discipline once an honest implementation estimate is laid out. Per the forward-motion-bias directive § "What is a true blocker" → "Capacity overage. LOC exceeds the hard upper bound (~1,800) — soft cap is ~1,500 and overages need to be surfaced", this is the category of issue that requires explicit acceptance before implementation begins.

## Honest implementation estimate

| Component / file | Estimated LOC |
|---|---:|
| `pom.xml` extensions (Spring Boot parent + Spring Boot starters + Spring Data JDBC + Liquibase + Caffeine + Resilience4j + Testcontainers + WireMock + jackson; JaCoCo `application/*` check; Pitest `application/*` targets + `ConversionService` per-class threshold per A2 intake §1) | ~120 |
| `application.yml` (DataSource via `WEX_DB_*`; Resilience4j config; Caffeine spec; SingleFlight config; readiness pool-headroom check signal) | ~80 |
| Liquibase migration (`purchases` + `exchange_rates` tables; D-3 versioned PK; scale-6 normalization; rollback class C tags) | ~80 |
| `currency-aliases.json` + reader | ~40 |
| `PurchaseRepoAdapter` + IT (Postgres Testcontainer + happy + duplicate + retrieve-not-found) | ~200 |
| `ExchangeRateRepoAdapter` (versioned upsert; eligible-range query; scale-6 normalize; **hot-cache invalidation hook per A2 intake §2**) + IT (AC-026b row-count=2; G4-P0-3 scale-6) | ~280 |
| `CurrencyAliasTableAdapter` (alias resolution; AC-021b/c drift event) + test | ~150 |
| `ExchangeRateHotCacheAdapter` (Caffeine + per-currency `ConcurrentNavigableMap` for O(log n) range; TTL 24h; max 2000) + `HotCacheKeyTest` (G4-P0-2 hit-ratio after warm-up) | ~180 |
| `TreasuryClientAdapter` (RestClient + Resilience4j wrap + JSON-schema validate + sanity-bound check; scale-6 normalize on parse; orientation-contract check) | ~250 |
| `TreasuryClientIT` (WireMock; AC-T-3 widened — 12 cases: happy / slow / 5xx / malformed / timeout / CB open / zero / negative / null / trailing-zero / leading-zero / high-precision / sanity-ceiling) | ~350 |
| `SingleFlightGate` (per-`(currency, treasury_quarter_end)` lock; `AtomicReference<WinnerOutcome>`; 10s wait + 100ms DB poll; SIGTERM release) | ~180 |
| `SingleFlightCacheConcurrencyTest` (barrier 5s + 8s; AC-027b/c/d/e; G6-P0-2) | ~220 |
| `CircuitBreakerCalibrationIT` (sustained 5xx 5 min; time-based window; G6-P0-1) | ~120 |
| `DurabilityRestartIT` (H2 file-mode; AC-010) | ~100 |
| `LiquibaseMigrationTest` (forward + rollback drill against empty H2 + Postgres) | ~70 |
| `GracefulShutdownIT` (SIGTERM releases gate; readiness DOWN; losers fail-fast; G4-P1-23) | ~120 |
| `DbPoolReadinessTest` (active ≥ max-1 for ≥ 5 s → readiness DOWN; G6-P0-4) | ~100 |
| `CurrencyAliasDriftTest` (AC-021b/c; G4-P1-8 event emission) | ~80 |
| `ScaleNormalizationTest` (G4-P0-3; persisted+re-read scale 6) | ~80 |
| **Estimated total** | **~2,800** |

Even with aggressive trimming (e.g., dropping the per-test fixture variant count from the AC-T-3 widened set, sharing test scaffolding across ITs, omitting javadoc) the floor is ~2,200 LOC. That is still ~400 LOC over the hard upper bound.

## Proposed resolution — split into B1 and B2

The clean dependency seam is **persistence + cache (B1) before HTTP + concurrency (B2)**. SingleFlightGate's loser-polls-DB pattern requires the DB layer to exist before the concurrency test is meaningful. The seam is the same shape as the A1/A2 seam (domain before application) — one chunk delivers the substrate the next chunk's tests can rely on.

### Sub-chunk B1 — `feature/chunk-b1-persistence-cache`

| Item | Detail |
|---|---|
| Branch base | `main` (post-A2-merge) |
| Production files | `pom.xml` extensions (Spring Boot parent + Spring Data JDBC + Liquibase + Caffeine + Testcontainers; **+ A2 intake §1 JaCoCo `application/*` check + Pitest `application/*` + `ConversionService` per-class ≥ 80 %**); `application.yml` (DataSource + Caffeine + readiness signal); Liquibase migration; `currency-aliases.json` + reader; `PurchaseRepoAdapter`; `ExchangeRateRepoAdapter` (**+ A2 intake §2 hot-cache invalidation contract via option (a) in `15-clarification.md`**); `CurrencyAliasTableAdapter`; `ExchangeRateHotCacheAdapter`. |
| Test files | `PurchaseRepoIT`; `ExchangeRateRepoIT` (AC-026b + G4-P0-3 + hot-cache-invalidation contract); `DurabilityRestartIT`; `LiquibaseMigrationTest`; `HotCacheKeyTest`; `ScaleNormalizationTest`; `CurrencyAliasDriftTest`; `DbPoolReadinessTest`. |
| Estimated LOC | ~1,300 (within ~1,500 cap; under ~1,800 hard) |
| AC coverage | AC-010, AC-021b/c, AC-026b, G4-P0-2, G4-P0-3, G6-P0-4 |
| Rollback class | C (schema rollback per rollback-plan.md §4.4) |
| Closes A2 intake | Both items (§1 pom-coverage-mutation-extension AND §2 hot-cache-upsert-invalidation-contract) |

### Sub-chunk B2 — `feature/chunk-b2-treasury-singleflight`

| Item | Detail |
|---|---|
| Branch base | `feature/chunk-b1-persistence-cache` (rebased to `main` if B1 merges first) |
| Production files | `TreasuryClientAdapter`; `SingleFlightGate`; Resilience4j config (CB + retry + bulkhead + timeout); orientation-contract check; JSON-schema fixture for Fiscal Data API |
| Test files | `TreasuryClientIT` (WireMock; AC-T-3 widened 12 cases); `SingleFlightCacheConcurrencyTest` (AC-027b/c/d/e + G6-P0-2); `CircuitBreakerCalibrationIT` (G6-P0-1); `GracefulShutdownIT` (G4-P1-23) |
| Estimated LOC | ~1,400 (within cap) |
| AC coverage | AC-T-3, AC-027b/c/d/e, G6-P0-1, G6-P0-2, G4-P1-23 |
| Rollback class | A (code rollback; no new schema) |

### Why this split (not a different one)

1. **B1 unblocks the loser-polls-DB pattern that B2 verifies.** Without B1's `ExchangeRateRepoAdapter`, B2's `SingleFlightCacheConcurrencyTest` cannot model the "loser polls DB every 100 ms" semantics from D-9 / G6-P0-2.
2. **B1 closes the A2 review's two intake items** so the reviewer can verify them at B1's `30-review.md` without waiting for B2.
3. **PCI invariants split cleanly:** persistence invariants (AC-026b, AC-010, G4-P0-3) land in B1; concurrency invariants (AC-027b/c/d/e, G4-P0-1, G6-P0-2) land in B2.
4. **Rollback classes are different** (C in B1, A in B2). Bundling them would force B2 changes into a class-C deployment window.
5. **One additional review cycle**, identical to the A1/A2 split — the reviewer pre-staged A2 / B / C in one go and explicitly anticipates "the implementer chains autonomously" via the dossier. Adding a B1 / B2 dossier pair extends that pattern.

### What this deviation explicitly does NOT change

- **PCI invariants table** (00-prompt.md §"PCI-critical invariants verified at this merge") — every invariant still lands in B-the-overall before C kicks off. B1 owns the persistence rows; B2 owns the concurrency rows.
- **A2 review conditions** — both intake items are closed within B1 (not B2 or a third PR).
- **Out-of-scope list** — Chunk C still owns HTTP, ContentGuard, OpenAPI, observability wiring.
- **LOC discipline** — both sub-chunks fit within the 1,500-soft / 1,800-hard caps.
- **Branching convention** — same shape as A1/A2: B2 rebases on `main` after B1 merges.

## Action requested

Reviewer's choice:

- **(a) Accept the B1 / B2 split as proposed.** Implementer immediately renames `feature/chunk-b-infrastructure` → `feature/chunk-b1-persistence-cache` (or just opens B1 on this branch under a different label) and begins B1.
- **(b) Accept B as one chunk; relax the LOC cap to ~2,500 for this single chunk.** Implementer proceeds with the unsplit scope. Per the LOC-cap-revision directive's "Discipline" line — *"never silently exceed"* — this needs explicit relaxation, which this option records.
- **(c) Alternative split** the reviewer prefers. Surface and I'll re-scope.

Default forward-motion path while awaiting: nothing in `src/main/java/.../infrastructure/` is being written. This branch carries only this `10-deviation.md` and a manifest flip until the reviewer responds.

## State transition

`chunks/13-B-infrastructure/manifest.yml`:
- `status: prompt_received → deviation_surfaced`
- `deviation_sha`: `<git hash-object>` of this file once committed.
