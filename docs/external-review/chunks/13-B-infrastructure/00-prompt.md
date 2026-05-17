# 00-prompt — Chunk 13-B-infrastructure

> Canonical kickoff for M3 — infrastructure adapters. Authored by external reviewer 2026-05-17 in the same pre-staging commit as A2 and C.

## Prerequisite

A2 merged to `main`. Both `.human-approvals/{implementation,pci-security}-approved.txt` in place.

## Read first

0. `docs/external-review/` — every file. Specifically:
   - `STATUS.md`
   - `directives/2026-05-17-ops-check-and-pci-check-strict-flip.md`
   - `chunks/13-A1-domain/30-review.md` and `chunks/13-A2-application/30-review.md`
1. `CLAUDE.md`.
2. `docs/architecture/adr-0001-core-architecture.md` — especially D-2 (stack), D-4 / D-10 (scale-6 normalization), D-9 (single-flight gate keyed by `(currency, treasury_quarter_end)`; loser semantics 10 s wait + outcome-ref + 100 ms DB poll), D-10 (hot cache keyed by `(currency, record_date)`), D-13 (detection-and-alert guards — placeholder hook only in M3; full design in M4/Chunk C), D-14 (H2 storage choice).
3. `docs/architecture/component-design.md` §1, §3 (especially §3.2 single-flight loser pattern), §3.4 (DB pool / readiness signal — implement readiness pool-headroom check per G4-P1-19), §8.
4. `docs/architecture/data-model.md` — two tables (`purchases`, `exchange_rates`); versioned PK `(currency, record_date, effective_date)`; scale-6 normalization; sanity bounds 10^30; eligible-rate lookup `MAX(record_date) WHERE currency=X AND record_date BETWEEN A AND B`.
5. `docs/architecture/api-contracts.md` §1 / §5 — scale-6 is the API contract for `exchange_rate`.
6. `docs/architecture/deployment-architecture.md` §4.3 / §5 — DB env-var split (`WEX_DB_HOST` / `WEX_DB_PORT` / `WEX_DB_NAME` / `WEX_DB_USERNAME` / `WEX_DB_PASSWORD`), graceful shutdown 60 s, TLS posture.
7. `docs/operations/capacity-scalability-plan.md` §1 / §2 / §5 — DB pool 20 per replica; bulkhead 10 permits; 60/30/10 mix.
8. `docs/operations/failure-modes-and-resilience.md` §3 — CB calibration TIME_BASED, sliding-window 300 s, min-calls 5, wait-duration 5 min, half-open trials 3.
9. `docs/requirements/acceptance-criteria.md` — AC-T-3 (widened Treasury fixtures), AC-026b (persistence row-count 2 after revision), AC-027b/c/d/e (single-flight dedup and consistent outcomes), AC-010 (durability).
10. `docs/security/change-control-pci.md` §1 — PR template.
11. `docs/security/logging-monitoring-pci.md` §5 — redaction (relevant for Treasury client and repo logs).
12. `docs/operations/rollback-plan.md` §4 — Chunk B introduces schema, so rollback class is **C (schema rollback)**.

## Scope

### Components

| Component | Detail |
|---|---|
| `PurchaseRepoAdapter` | Implements `PurchaseRepoPort`. JDBC or `JdbcClient` (NO JPA / Hibernate — per ADR-0001 D-2). Maps `Purchase` ↔ `purchases` row. |
| `ExchangeRateRepoAdapter` | Implements `ExchangeRateRepoPort`. Versioned upsert (`INSERT ... ON CONFLICT DO NOTHING` or equivalent). Eligible-rate query (`SELECT ... WHERE currency=? AND record_date BETWEEN ? AND ? ORDER BY record_date DESC LIMIT 1`). All `exchange_rate` values normalized to scale 6 at write time per D-4 / D-10. |
| `TreasuryClientAdapter` | Implements `TreasuryClientPort`. Resilience4j wrapping: time-based CB (300 s window / min-calls 5 / wait-duration 5 min / half-open 3), retry (3 attempts × 2 s timeout + jitter), bulkhead (10 permits), SingleFlightGate keyed by `(currency, treasury_quarter_end)`. Loser semantics: 10 s wait + 100 ms DB poll + `AtomicReference<WinnerOutcome>` mirroring winner's exact exception. SIGTERM releases all locks; losers fail-fast with `UPSTREAM_UNAVAILABLE`. JSON schema validation against Fiscal Data API shape. User-Agent `wex-purchase-fx/<version> (contact:<email>)`. |
| `CurrencyAliasTableAdapter` | Implements `CurrencyAliasTablePort`. Reads `currency-aliases.json` from classpath; resolves alias → canonical descriptor; handles AC-021b + AC-021c via the `currency_alias_drift_detected` event emission. |
| `ExchangeRateHotCacheAdapter` | Implements `ExchangeRateHotCachePort`. Caffeine cache keyed by `(currency, record_date)`. Maintain a per-currency `ConcurrentNavigableMap<LocalDate, ExchangeRate>` (or two-level Caffeine) for O(log n) range queries. `expireAfterWrite` TTL 24 h. `maximumSize=2000`. Populate after every successful Treasury fetch. |
| Liquibase migration | Schema for `purchases` and `exchange_rates` per data-model.md. Idempotent. Tags for rollback (class C). |
| `application.yml` | Resilience4j config, DataSource config, Caffeine spec, single-flight gate config. Env-var-based — no secrets committed. |

### Tests

| Test | Coverage | Verifies |
|---|---|---|
| `PurchaseRepoIT` | Round-trip `Purchase` through Postgres (Testcontainers); happy + duplicate-id + retrieve-not-found | FR-001/002 persistence |
| `ExchangeRateRepoIT` | Versioned upsert; eligible-rate range query; sanity bounds; AC-026b row-count = 2 after revision | AC-026b; G4-P0-3 scale-6 |
| `DurabilityRestartIT` | File-mode H2 restart preserves data | AC-010 |
| `TreasuryClientIT` (WireMock) | AC-T-3 widened: happy / slow / 5xx / malformed / timeout / CB open / zero / negative / null / trailing-zero / leading-zero / high-precision / sanity-ceiling | AC-T-3 |
| `SingleFlightCacheConcurrencyTest` | Barrier-coordinated WireMock holds winner 5 s and 8 s; losers see consistent outcomes (success or mirrored exception) | G6-P0-2; AC-027b/c/d/e |
| `CircuitBreakerCalibrationIT` | Sustained 5xx for 5 min opens CB; time-based window opens after min-calls=5 | G6-P0-1 |
| `HotCacheKeyTest` | AC-027b hit-rate after warm-up; cache key `(currency, record_date)` not `(currency, lookup_window)` | G4-P0-2 |
| `ScaleNormalizationTest` | Treasury returns `"148.0"` / `"1.393"` / `"0.085"` / high-precision; persisted+re-read value scale-6 | G4-P0-3 |
| `CurrencyAliasDriftTest` | AC-021b + AC-021c; `currency_alias_drift_detected` event emission | G4-P1-8 |
| `GracefulShutdownIT` | Single-flight lock release on SIGTERM; in-flight losers fail-fast; readiness DOWN at SIGTERM | G4-P1-23 |
| `LiquibaseMigrationTest` | Migration applies cleanly to empty H2 and Postgres; rollback class C verified | rollback-plan.md §4.4 |

## PCI-critical invariants verified at this merge

| Invariant | Test |
|---|---|
| G6-P0-2 single-flight loser waits 10 s, polls DB every 100 ms, mirrors winner's exact exception | `SingleFlightCacheConcurrencyTest` |
| G4-P0-1 single-flight key `(currency, treasury_quarter_end)` | same |
| G4-P0-2 hot cache key `(currency, record_date)` — hit ratio ≥ 95 % after warm-up | `HotCacheKeyTest` |
| G4-P0-3 scale-6 normalization end-to-end | `ScaleNormalizationTest` |
| G6-P0-1 time-based CB opens within 5 min of sustained outage | `CircuitBreakerCalibrationIT` |
| G6-P0-4 DB pool 20 per replica; readiness DOWN when `pool.active ≥ pool.max - 1` for ≥ 5 s | `DbPoolReadinessTest` (additional) |
| AC-026b revision lands as new row; original preserved | `ExchangeRateRepoIT` |
| AC-010 durability — restart preserves data | `DurabilityRestartIT` |

PR description **must** include this invariants table mapped to test class + method names.

## Out of scope

- No HTTP controllers, no `@RestControllerAdvice`. Chunk C.
- No ContentGuard, no rate-limit filter, no NFKC normalization. Chunk C.
- No OpenAPI surface. Chunk C.
- No observability wiring (OTel, structured logs). Chunk C — but emit placeholder logs at INFO so M5 can wire OTel later.
- No CI workflow edits.
- No Spring annotations in `domain` or `application`.
- No edits to control-bundle files or `.human-approvals/`.

## Acceptance gates

| Gate | Target |
|---|---|
| Unit + integration tests | All green |
| Mutation (Pitest) on `infrastructure/*` package average | ≥ 70 % |
| Mutation on `SingleFlightGate` + `TreasuryClientAdapter` | ≥ 80 % |
| Coverage on `infrastructure/*` | line ≥ 75 %, branch ≥ 65 % |
| ArchUnit | All rules pass; no Spring import in `domain` or `application` |
| Liquibase rollback drill | Migration applies and rolls back cleanly in Testcontainers loop |
| LOC | ≤ ~1,500 (target) / ≤ ~1,800 (hard upper bound) |

## Branching, PR, CI

- Branch: `feature/chunk-b-infrastructure` off `main` (post-A2-merge).
- Target ~1,000 LOC; stop and re-scope if you exceed ~1,800.

## PR description

```
change-id: WEX-CHUNK-B-infrastructure
requirement link: FR-001..FR-006; AC-010, AC-021b/c, AC-026b, AC-027b/c/d/e, AC-T-3; NFR-003, NFR-005/006, NFR-014b; ADR-0001 D-2, D-4, D-9, D-10, D-14
risk assessment: Medium — first write path to persistence; first concurrency contract realized; first upstream HTTP. Mitigations: rollback class C with Liquibase tags; AtomicReference<WinnerOutcome> pattern tested; CB calibration verified.
security impact: low. DB credentials externalised; no logging of `description`; Treasury client logs HMAC-hashed correlation id only (when M5 lands; placeholder OK in B).
CDE impact: connected-to (audit destination only); no CHD path; ContentGuard not yet in place but no HTTP surface either.
test evidence:
  - JUnit + Testcontainers: <CI URL>
  - WireMock contract suite: <link>
  - SingleFlightCacheConcurrencyTest barrier 5s + 8s: <output>
  - CircuitBreakerCalibrationIT 5-min sustained: <output>
  - Liquibase migrate + rollback drill: <output>
  - Pitest infrastructure/* avg + SingleFlightGate + TreasuryClientAdapter: <%>
  - Coverage: line <%>, branch <%>
  - PCI-invariants table: <inline>
approval: <Architect + SRE>
rollback class: C (schema rollback per rollback-plan.md §4.4)
deployment window: maintenance — schema migration in flight
post-deploy validation: smoke insert/select on purchases + exchange_rates; readiness UP within 60 s; pool metric active < max - 1
```

## Workflow

Same shape as A1/A2. Implement in order: Liquibase migration → application.yml skeleton → repo adapters with ITs → currency-alias → hot cache → TreasuryClient with WireMock-tested IT → SingleFlight integration with WinnerOutcome + concurrency test → readiness DB-pool signal. Land each component's tests before the next adapter.

## Completion summary

Write to `chunks/13-B-infrastructure/20-summary.md`. CLAUDE.md §9 format. Flip `manifest.status` accordingly.

If a deviation surfaces, write `10-deviation.md`, flip `status: deviation_surfaced`, wait for reviewer 30-review or revision file.
