# 00-prompt — Chunk 13-B1-persistence-cache

> Canonical kickoff for sub-chunk B1 of the original Chunk B infrastructure scope. Created 2026-05-17 by external reviewer in response to the implementer's `chunks/13-B-infrastructure/10-deviation.md` capacity-overage analysis. See `chunks/13-B-infrastructure/30-review.md` for the supersession rationale. This file is self-contained: the A2 intake items are absorbed inline, not by reference into the superseded folder.

## Prerequisite

A2 merged to `main` (PR #5, 2026-05-17). Both `.human-approvals/{implementation,pci-security}-approved.txt` in place.

## Read first

0. `docs/external-review/` — every file. Specifically:
   - `STATUS.md`
   - `directives/2026-05-17-ops-check-and-pci-check-strict-flip.md`
   - `directives/2026-05-17-forward-motion-bias.md`
   - `chunks/13-A1-domain/30-review.md`, `chunks/13-A2-application/30-review.md` (the A2 review introduces the two intake items absorbed below)
   - `chunks/13-B-infrastructure/30-review.md` (the supersession ruling)
1. `CLAUDE.md`.
2. `docs/architecture/adr-0001-core-architecture.md` D-1..D-14 — note D-2 (stack), D-3 (versioned PK on `exchange_rates`), D-4 / D-10 (scale-6 normalization), D-10 (hot cache key `(currency, recordDate)`), D-14 (H2 storage choice).
3. `docs/architecture/component-design.md` §1, §3 (per-component contracts for the four B1 adapters), §3.4 (DB pool / readiness signal — implement readiness pool-headroom check per G4-P1-19), §8.
4. `docs/architecture/data-model.md` — `purchases` + `exchange_rates` tables; versioned PK `(currency, record_date, effective_date)`; scale-6 normalization; sanity bounds 10^30; eligible-rate lookup `MAX(record_date) WHERE currency=X AND record_date BETWEEN A AND B`.
5. `docs/architecture/api-contracts.md` §1 / §5 — scale-6 is the API contract for `exchange_rate`.
6. `docs/architecture/deployment-architecture.md` §4.3 / §5 — DB env-var split (`WEX_DB_HOST` / `WEX_DB_PORT` / `WEX_DB_NAME` / `WEX_DB_USERNAME` / `WEX_DB_PASSWORD`), graceful shutdown 60 s, TLS posture.
7. `docs/operations/capacity-scalability-plan.md` §1 / §2 / §5 — DB pool 20 per replica.
8. `docs/requirements/acceptance-criteria.md` — AC-010 (durability), AC-021b/c (alias-table-drift), AC-026b (persistence-centric idempotency row-count=2), G4-P0-2 (hot cache key), G4-P0-3 (scale-6), G6-P0-4 (DB pool readiness).
9. `docs/security/change-control-pci.md` §1 — PR template.
10. `docs/operations/rollback-plan.md` §4.4 — rollback class C (schema rollback).
11. A2's `pom.xml` (post-merge state on `main`).

## Scope — exact and bounded

### Components

| Component | Detail |
|---|---|
| `pom.xml` extensions | Add Spring Boot parent + Spring Data JDBC + Liquibase + Caffeine + Testcontainers + H2 + Postgres driver. **Also includes both A2 intake items** — see "A2 intake items" below. |
| `application.yml` | DataSource via `WEX_DB_*` env-vars (NOT a single URL). Caffeine spec. Readiness pool-headroom signal config. No secrets committed. |
| Liquibase migration | Schema for `purchases` and `exchange_rates` per `data-model.md`. Idempotent. Tags for rollback (class C). |
| `currency-aliases.json` + reader | Classpath JSON; alias→canonical resolution. |
| `PurchaseRepoAdapter` | Implements `PurchaseRepositoryPort` (from A2). JDBC / `JdbcClient` (NO JPA / Hibernate per D-2). Maps `Purchase` ↔ `purchases` row. |
| `ExchangeRateRepoAdapter` | Implements `ExchangeRateRepositoryPort`. Versioned upsert (`INSERT ... ON CONFLICT DO NOTHING` or equivalent). Eligible-rate query. All `exchange_rate` values normalized to scale 6 at write time per D-4 / D-10. **Plus A2 intake §2: cache invalidation on every upsert** — see "A2 intake items" below. |
| `CurrencyAliasTableAdapter` | Implements `CurrencyAliasPort` (A2 rename). Alias→canonical resolution; emits `currency_alias_drift_detected` event for AC-021b/c. |
| `ExchangeRateHotCacheAdapter` | Implements `ExchangeRateHotCachePort`. Caffeine cache keyed by `(currency, recordDate)`. Per-currency `ConcurrentNavigableMap<LocalDate, ExchangeRate>` (or two-level Caffeine) for O(log n) range queries. `expireAfterWrite` TTL 24 h. `maximumSize=2000`. Implements `invalidate(currency, recordDate)` per port + (see A2 intake §2). |

### Tests

| Test | Coverage | Verifies |
|---|---|---|
| `PurchaseRepoIT` | Round-trip `Purchase` through Postgres (Testcontainers); happy + duplicate-id + retrieve-not-found | FR-001/002 persistence |
| `ExchangeRateRepoIT` | Versioned upsert; eligible-rate range query; sanity bounds; AC-026b row-count = 2 after revision; **A2 intake §2 cache-invalidation contract verified** | AC-026b; G4-P0-3 scale-6; A2-intake-§2 |
| `DurabilityRestartIT` | File-mode H2 restart preserves data | AC-010 |
| `LiquibaseMigrationTest` | Migration applies cleanly to empty H2 and Postgres; rollback class C verified | rollback-plan.md §4.4 |
| `HotCacheKeyTest` | AC-027b hit-rate after warm-up; cache key `(currency, record_date)` not `(currency, lookup_window)`; range query correctness | G4-P0-2 |
| `ScaleNormalizationTest` | Treasury returns `"148.0"` / `"1.393"` / `"0.085"` / high-precision; persisted+re-read value scale-6 | G4-P0-3 |
| `CurrencyAliasDriftTest` | AC-021b + AC-021c; `currency_alias_drift_detected` event emission | G4-P1-8 |
| `DbPoolReadinessTest` | active ≥ max-1 for ≥ 5 s → readiness DOWN | G6-P0-4 |

## A2 intake items absorbed in this chunk

Both items are tracked in `chunks/13-A2-application/manifest.yml` as `review_conditions`. They close on B1's `30-review.md`.

### Intake §1 (MED) — `pom-coverage-mutation-extension-for-application`

Source: `chunks/13-A2-application/30-review.md` §3.1.

**Required pom.xml changes:**

1. **JaCoCo** — add a second `check` execution targeting the `application` package:

```xml
<execution>
  <id>check-application-coverage</id>
  <phase>verify</phase>
  <goals><goal>check</goal></goals>
  <configuration>
    <rules>
      <rule>
        <element>PACKAGE</element>
        <includes>
          <include>com.example.purchaseconversion.application.*</include>
        </includes>
        <limits>
          <limit><counter>LINE</counter><value>COVEREDRATIO</value><minimum>0.80</minimum></limit>
        </limits>
      </rule>
    </rules>
  </configuration>
</execution>
```

2. **Pitest** — extend `<targetClasses>`:

```xml
<targetClasses>
  <param>com.example.purchaseconversion.domain.RateSelectionPolicy</param>
  <param>com.example.purchaseconversion.domain.Money</param>
  <param>com.example.purchaseconversion.application.*</param>
</targetClasses>
```

3. **`ConversionService` per-class ≥ 80 % mutation threshold.** Mechanism is the implementer's call: either a second `<execution>` scoped to that class, a `mutationThreshold` element variant, or a CI-side report parser. Gate effect must be: "merge blocked if `ConversionService` mutation < 80 %."

Verification at B1's 30-review: reviewer reads `pom.xml` and confirms all three.

### Intake §2 (LOW) — `hot-cache-upsert-invalidation-contract`

Source: `chunks/13-A2-application/30-review.md` §3.2.

**Required adapter change:** `ExchangeRateRepoAdapter.upsertVersioned(...)` must invalidate the hot cache for every upserted `(currency, recordDate)` key. Adapter javadoc must state the invariant explicitly:

> "Every upsert path MUST go through this adapter. Direct DB writes bypass the cache invalidation invariant and risk stale-rate selection in `ConversionService`."

`ExchangeRateHotCachePort.invalidate(currency, recordDate)` (already declared by A2) is the call site. No new port method required unless the implementer prefers option (b) — a `bulkInvalidate(CurrencyDescriptor)` operation — which is also acceptable.

Verification at B1's 30-review: reviewer reads both files' javadoc + adapter call site + the new `ExchangeRateRepoIT` test that exercises the invariant.

## Out of scope

- No `TreasuryClientAdapter`, no `SingleFlightGate`, no Resilience4j config — those land in B2.
- No HTTP controllers, no `@RestControllerAdvice`, no ContentGuard, no rate-limit filter, no OpenAPI, no observability wiring — those live in C.
- No edits to `domain/*` or `application/*` (both are frozen from A1 / A2).
- No edits to control-bundle files or `.human-approvals/`.

## PCI-critical invariants verified at this merge

| Invariant | Test |
|---|---|
| G4-P0-2 hot cache key `(currency, recordDate)` — hit ratio ≥ 95 % after warm-up | `HotCacheKeyTest` |
| G4-P0-3 scale-6 normalization end-to-end | `ScaleNormalizationTest` |
| G6-P0-4 DB pool 20 per replica; readiness DOWN when `pool.active ≥ pool.max - 1` for ≥ 5 s | `DbPoolReadinessTest` |
| AC-010 durability — restart preserves data | `DurabilityRestartIT` |
| AC-026b revision lands as new row; original preserved | `ExchangeRateRepoIT` |
| AC-021b/c alias-table drift detection + canonical event emission | `CurrencyAliasDriftTest` |
| A2-intake-§2 hot-cache upsert-invalidation contract | `ExchangeRateRepoIT` new test method |

(Concurrency invariants — AC-T-3, AC-027b/c/d/e, G4-P0-1, G6-P0-1, G6-P0-2, G4-P1-23 — land in B2.)

PR description **must** include this table mapped to test class + method names.

## Acceptance gates

| Gate | Target |
|---|---|
| Unit + integration tests | All green |
| Mutation (Pitest) on `infrastructure/*` package average | ≥ 70 % |
| Mutation on `ExchangeRateRepoAdapter` + `ExchangeRateHotCacheAdapter` | ≥ 80 % each (these own the A2-intake-§2 contract) |
| Coverage on `infrastructure/*` | line ≥ 75 %, branch ≥ 65 % |
| **NFR-021 application/* gates now build-enforced** (per intake §1 above) | line ≥ 80 % on `application.*`; Pitest covers `application.*`; ConversionService ≥ 80 % |
| ArchUnit | All rules from A1 + A2 still pass |
| Liquibase rollback drill | Migration applies and rolls back cleanly in Testcontainers loop |
| LOC | ≤ ~1,500 (target) / ≤ ~1,800 (hard upper bound). Estimated ~1,300. |

## Branching, PR, CI

- Branch: `feature/chunk-b1-persistence-cache` off `main` (post-A2-merge).
- One PR. Estimated ~1,300 LOC.

## PR description (change-control-pci.md §1)

```
change-id: WEX-CHUNK-B1-persistence-cache
requirement link: FR-001..FR-006; AC-010, AC-021b/c, AC-026b; NFR-003, NFR-005/006, NFR-014b, NFR-021 (application via intake §1); ADR-0001 D-2, D-3, D-4, D-10, D-14
risk assessment: Medium — first write path to persistence; first schema migration. Mitigations: rollback class C with Liquibase tags; Testcontainers parity with prod Postgres; hot-cache invalidation invariant enforced and tested.
security impact: low. DB credentials externalised; no logging of `description`.
CDE impact: connected-to (audit destination only); no CHD path; ContentGuard not yet in place but no HTTP surface either.
test evidence:
  - JUnit + Testcontainers: <CI URL>
  - Liquibase migrate + rollback drill: <output>
  - JaCoCo application/* check: pass at ≥ 0.80 (per intake §1)
  - Pitest infrastructure/* avg + ExchangeRateRepoAdapter + ExchangeRateHotCacheAdapter + ConversionService (per intake §1): <%>
  - Coverage: line <%>, branch <%>
  - PCI-invariants table: <inline>
approval: <Architect + SRE>
rollback class: C (schema rollback per rollback-plan.md §4.4)
deployment window: maintenance — schema migration in flight
post-deploy validation: smoke insert/select on purchases + exchange_rates; readiness UP within 60 s; pool metric active < max - 1
```

## Workflow

Per CLAUDE.md §2. Implement in order: pom + application.yml + Liquibase migration → repo adapters (with their ITs) → currency-alias → hot cache with invalidation contract → readiness pool-headroom signal. Land each component's tests before the next adapter.

## Completion summary

Write to `chunks/13-B1-persistence-cache/20-summary.md`. CLAUDE.md §9 format. Include a "Reviewer intake conditions" subsection naming how each A2 intake item was addressed (pom diff, javadoc additions, test method).

If a deviation surfaces, write `10-deviation.md`, flip `status: deviation_surfaced`, wait. Otherwise flip `status: implementing` and proceed.
