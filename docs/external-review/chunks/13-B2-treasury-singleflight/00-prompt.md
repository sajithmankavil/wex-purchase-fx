# 00-prompt — Chunk 13-B2-treasury-singleflight

> Canonical kickoff for sub-chunk B2 of the original Chunk B infrastructure scope. Created 2026-05-17 by external reviewer alongside B1. See `chunks/13-B-infrastructure/30-review.md` for the supersession rationale.

## Prerequisite

B1 merged to `main`. (`feature/chunk-b1-persistence-cache` → `main` via the standard chunk-merge sequence.)

## Read first

0. `docs/external-review/` — every file. Specifically:
   - `STATUS.md`
   - `directives/2026-05-17-forward-motion-bias.md`
   - `chunks/13-B1-persistence-cache/30-review.md` (just-closed; verifies persistence layer)
1. `CLAUDE.md`.
2. `docs/architecture/adr-0001-core-architecture.md` — especially D-9 single-flight gate keyed by `(currency, treasury_quarter_end)`; loser semantics 10 s wait + outcome-ref + 100 ms DB poll (G6-P0-2 closure); CB calibration TIME_BASED (G6-P0-1).
3. `docs/architecture/component-design.md` §3 (especially §3.2 single-flight loser pattern), §8.
4. `docs/operations/failure-modes-and-resilience.md` §3 — CB calibration TIME_BASED, sliding-window 300 s, min-calls 5, wait-duration 5 min, half-open trials 3.
5. `docs/requirements/acceptance-criteria.md` — AC-T-3 (widened Treasury fixtures), AC-027b/c/d/e (single-flight dedup and consistent outcomes), AC-T-3 12 cases.
6. `docs/security/change-control-pci.md` §1 — PR template.
7. `docs/operations/rollback-plan.md` §4.2 — rollback class A (code; no schema).
8. B1's `ExchangeRateRepoAdapter` (post-merge state) — needed because the single-flight loser pattern polls the DB during the 10 s wait.

## Scope — exact and bounded

### Components

| Component | Detail |
|---|---|
| `TreasuryClientAdapter` | Implements `TreasuryClientPort` (from A2). Resilience4j wrapping: time-based CB (300 s window / min-calls 5 / wait-duration 5 min / half-open 3), retry (3 attempts × 2 s timeout + jitter), bulkhead (10 permits). JSON schema validation against Fiscal Data API shape. Sanity-bound check on rates. Scale-6 normalize on parse. User-Agent `wex-purchase-fx/<version> (contact:<email>)`. |
| `SingleFlightGate` | Per-`(currency, treasury_quarter_end)` lock. `AtomicReference<WinnerOutcome>` for loser-mirror semantics. 10 s loser wait + 100 ms DB poll. SIGTERM releases all locks; losers fail-fast with `UPSTREAM_UNAVAILABLE`. |
| Resilience4j config | Externalised in B1's `application.yml`. B2 adds the runtime wiring + the CB / retry / bulkhead annotations or programmatic config. |
| Orientation-contract check | Treasury orientation per ADR-0001 D-12 verified at startup or per-fetch (implementer's call). Failures emit `treasury_orientation_check_failed` event. |

### Tests

| Test | Coverage | Verifies |
|---|---|---|
| `TreasuryClientIT` (WireMock) | AC-T-3 widened 12 cases: happy / slow / 5xx / malformed / timeout / CB open / zero / negative / null / trailing-zero / leading-zero / high-precision / sanity-ceiling | AC-T-3 |
| `SingleFlightCacheConcurrencyTest` | Barrier-coordinated WireMock holds winner 5 s and 8 s; losers see consistent outcomes (success or mirrored exception); 10 s wait + 100 ms DB poll pattern verified | G6-P0-2; AC-027b/c/d/e |
| `CircuitBreakerCalibrationIT` | Sustained 5xx for 5 min opens CB; time-based window opens after min-calls=5 | G6-P0-1 |
| `GracefulShutdownIT` | Single-flight lock release on SIGTERM; in-flight losers fail-fast; readiness DOWN at SIGTERM | G4-P1-23 |

## Out of scope

- No persistence changes (B1 owns `purchases`, `exchange_rates`, Liquibase, schema). B2 reads via B1's `ExchangeRateRepoAdapter` but does not modify it.
- No HTTP / no controllers / no ContentGuard / no rate-limit filter — C.
- No OpenAPI / no observability wiring — C.
- No CI workflow edits — C / M7.
- No edits to control-bundle files or `.human-approvals/`.

## PCI-critical invariants verified at this merge

| Invariant | Test |
|---|---|
| G6-P0-2 single-flight loser waits 10 s, polls DB every 100 ms, mirrors winner's exact exception | `SingleFlightCacheConcurrencyTest` |
| G4-P0-1 single-flight key `(currency, treasury_quarter_end)` — three purchases in same quarter dedupe | same |
| G6-P0-1 time-based CB opens within 5 min of sustained outage | `CircuitBreakerCalibrationIT` |
| AC-T-3 widened Treasury fixtures (12 cases) | `TreasuryClientIT` |
| G4-P1-23 SIGTERM releases single-flight locks; in-flight losers fail-fast | `GracefulShutdownIT` |

PR description **must** include this table mapped to test class + method names.

## Acceptance gates

| Gate | Target |
|---|---|
| Unit + integration tests | All green |
| Mutation (Pitest) on `infrastructure/*` extensions added in this chunk | ≥ 70 % package |
| Mutation on `SingleFlightGate` + `TreasuryClientAdapter` | ≥ 80 % each |
| Coverage on B2's new code | line ≥ 75 %, branch ≥ 65 % |
| ArchUnit | All rules from A1 + A2 still pass |
| `application/*` JaCoCo + Pitest gates (from A2 intake §1, landed in B1) | Still pass on this branch |
| LOC | ≤ ~1,500 (target) / ≤ ~1,800 (hard upper bound). Estimated ~1,400. |

## Branching, PR, CI

- Branch: `feature/chunk-b2-treasury-singleflight` off `main` (post-B1-merge).
- One PR. Estimated ~1,400 LOC.

## PR description (change-control-pci.md §1)

```
change-id: WEX-CHUNK-B2-treasury-singleflight
requirement link: FR-003; AC-T-3, AC-027b/c/d/e; G4-P0-1, G6-P0-1, G6-P0-2, G4-P1-23; ADR-0001 D-9, D-12
risk assessment: Medium — first upstream HTTP path; first realized concurrency contract. Mitigations: AtomicReference<WinnerOutcome> pattern unit-tested with barrier-coordinated WireMock; CB calibration verified under 5-min sustained outage; SIGTERM release tested.
security impact: low. Treasury client logs HMAC-hashed correlation id only.
CDE impact: connected-to (audit destination only); no CHD path.
test evidence:
  - JUnit + Testcontainers + WireMock: <CI URL>
  - SingleFlightCacheConcurrencyTest barrier 5s + 8s: <output>
  - CircuitBreakerCalibrationIT 5-min sustained: <output>
  - GracefulShutdownIT: <output>
  - Pitest SingleFlightGate + TreasuryClientAdapter: <%>
  - Coverage: line <%>, branch <%>
  - PCI-invariants table: <inline>
approval: <Architect + SRE>
rollback class: A (code rollback per rollback-plan.md §4.2)
deployment window: continuous
post-deploy validation: smoke a conversion request; verify CB metric exposes state; verify single-flight dedup metric increments under concurrent requests
```

## Workflow

Per CLAUDE.md §2. Implement in order: Resilience4j wiring config → TreasuryClientAdapter with WireMock-tested IT → SingleFlightGate with AtomicReference<WinnerOutcome> + concurrency test → CircuitBreakerCalibrationIT → GracefulShutdownIT. Land each component's tests before the next.

## Completion summary

Write to `chunks/13-B2-treasury-singleflight/20-summary.md`. CLAUDE.md §9 format. Flip `manifest.status` accordingly.
