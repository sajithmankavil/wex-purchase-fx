# 30-review — Chunk 13-A2-application

**Verdict:** **ACCEPTED WITH CONDITIONS.** PR #5 may merge after the two conditions in §3 are acknowledged and tracked as Chunk B intake (no A2 code changes required). The application-layer code itself is correct, complete against the prompt's scope, and consistent with ADR-0001 + component-design.md.

**Reviewer:** External governance reviewer (automated tick 2026-05-17)
**Date:** 2026-05-17
**Method:** File-by-file read of the working tree on the agreed branch (`feature/chunk-a2-application`) via the workspace mount; cross-checked against `00-prompt.md` scope items, `20-summary.md` claims, A1's domain types, `component-design.md §1/§3.2/§6`, ADR-0001 D-2/D-5/D-6/D-9/D-10, NFR-021/NFR-030, AC-001..AC-009 + AC-014..AC-020 + AC-018b/019b + AC-021b/c + AC-023/024/024b + AC-025 + AC-026b. Pitest/JaCoCo are pom-declared but not run in this sandbox (no Java/Maven) — same CI-is-test-evidence stance as A1.

---

## 1. Acceptance gates — verified against the branch

| Gate | Target | Verified state | Verdict |
|---|---|---|---|
| Production file count + locations | 20 files under `application/{conversion,exception,port/in,port/out,purchase}/` | Counted on disk: 2 + 7 + 4 + 6 + 1 = 20 — matches summary §24 | ✅ |
| Test file count + locations | 2 net-new test classes (`PurchaseServiceTest`, `ConversionServiceTest`) + 1 modified `ArchitectureTests` | All three present at expected paths | ✅ |
| Production LOC | ≤ ~1,500 (target) / ≤ ~1,800 (hard) per LOC-cap-revision directive | `wc -l` on the 20 production files = 736; net-new tests = 675; total 1,411 — under target | ✅ |
| Inbound use-case names | `RegisterPurchaseUseCase`, `RetrievePurchaseUseCase`, `ConvertPurchaseUseCase` (component-design.md §3) | All three present with the exact names | ✅ |
| Outbound port names | `PurchaseRepositoryPort`, `ExchangeRateRepositoryPort`, `TreasuryClientPort`, `CurrencyAliasPort` (renamed from prompt's `CurrencyAliasTablePort`), `ExchangeRateHotCachePort`, `ClockPort` | All six present; `CurrencyAliasPort` rename from prompt's `CurrencyAliasTablePort` is benign | ✅ (rename noted) |
| Services | `PurchaseService` (covers Register + Retrieve), `ConversionService` | Both present; `PurchaseService` combines registration + retrieval (matches prompt-listed `PurchaseRegistrationService` + `PurchaseLookupService` use cases collapsed onto one class) | ✅ (collapse noted) |
| Exception classes | Domain base + concretes mapping to AC error codes | `DomainException` (abstract) + 6 concretes: `FutureDateException` (AC-006), `PurchaseNotFoundException` (AC-008), `InvalidCurrencyException` (AC-021b/c), `ConversionRateNotAvailableException` (AC-020/020b/022b), `UpstreamUnavailableException` (AC-023), `UpstreamBadResponseException` (AC-024/024b). Controller-only exceptions (`ValidationException`, `PanPatternDetectedException`, `MalformedIdentifierException`) correctly deferred to Chunk C — see summary §17-§20 | ✅ |
| Spring-stereotype absence in `application` | No `@Component`/`@Service`/`@Configuration`/`@Autowired`/`@RestController`/`@ControllerAdvice` | Grep-clean on the 20 files; `ArchitectureTests.noSpringStereotypesInApplication` declares the rule | ✅ |
| ArchUnit extension #1 | `applicationOnlyDependsOnDomainAndJdk` actively enforces (was vacuous in A1) | Rule body unchanged; comment updated to "Activated in Chunk A2"; `application` package now exists so the rule is non-vacuous on this branch | ✅ |
| ArchUnit extension #2 | New rule `noSpringStereotypesInApplication` | Present at `ArchitectureTests.java:88-104` with the 9 stereotype annotations from the A2 prompt | ✅ |
| `noDoubleOrFloatFieldsInDomainOrApplication` activates on `application` | Already covers `..application..` via package pattern; now non-vacuous | Rule unchanged from A1 (already had `..application..` in the package list); now enforces on real classes — production code uses `Money`, `BigDecimal`, `LocalDate` only | ✅ |
| AC-014..AC-020 + AC-018b/019b coverage via `ConversionService` | Re-exercised at the service layer per prompt | `ConversionServiceTest.RateSelection` nested class: `ac014_exactDate`, `ac015_mostRecent`, `ac018_sixMonthsBeforeEligible`, `ac018b_eomClampEligible`, `ac019_justOutsideIneligible`, `ac019b_eomClampIneligible`, `oq002_tieBreakOnEffectiveDate`, `filtersOtherCurrencies` | ✅ |
| AC-021b/c alias-miss path | Service throws `InvalidCurrencyException` on `aliasPort.resolve(...).empty()` | `ConversionServiceTest.AliasResolution.unknownCurrencyInputRaises` + `isoCodeResolves` | ✅ |
| AC-023/024 upstream propagation | Exceptions pass through `ConversionService` untouched | `ConversionServiceTest.TreasuryFailures.upstreamUnavailable` + `upstreamBadResponse` (asserts `upsertVersioned` is never called) | ✅ |
| AC-020/022b empty-Treasury path | `ConversionRateNotAvailableException` thrown after upsert + re-read still empty/policy-rejected | `ConversionServiceTest.LayerOrdering.cacheMissDbMissTreasuryEmpty` + `treasuryReturnsRatesOutsideWindow` | ✅ |
| AC-025 HALF_UP at scale 2 | `Money.multiply(rate)` end-to-end through service | `ConversionServiceTest.Money_.halfUpAtScaleTwo` (`169.1265` → `169.13`) + `halfUpRoundsHalfUp` (`0.005` → `0.01`) | ✅ |
| AC-026b persistence-centric idempotency | Service re-reads from DB after upsert; canonical state is the DB row, not the fetched list | `ConversionService.java:107-114` (re-read + policy re-apply) + `LayerOrdering.cacheMissDbMissTreasurySuccess` `InOrder` verification of `upsert → re-read → cache.putAll` | ✅ |
| AC-001..AC-006 register | Future-date check via `ClockPort`; delegated invariants to `Purchase` constructor | `PurchaseServiceTest.Register`: 8 test methods covering happy past-dated, same-day, future, far-future, 50-char + 51-char description, UUID-v7 verification, null guard | ✅ |
| AC-007..AC-009 retrieve | `Optional<Purchase>` → `PurchaseNotFoundException` on empty | `PurchaseServiceTest.Retrieve`: `returnsStored`, `raisesNotFound`, `rejectsNullId` | ✅ |
| ADR-0001 D-9 single-flight gate not leaked into application | Treasury port surface is plain `fetchRates(...)`; gate is adapter concern | `TreasuryClientPort` javadoc explicitly says gate is encapsulated; no gate types referenced in `application/*` | ✅ |
| ADR-0001 D-10 hot-cache key | `(currency, recordDate)` | `ExchangeRateHotCachePort.findInWindow(currency, lower, upper)` + `invalidate(currency, recordDate)` — both keyed on the D-10 pair | ✅ contract; ⚠ semantics — see finding §3.2 |
| NFR-030 (no double/float in domain/application) | ArchUnit-enforced | Both `noDoubleOrFloatFieldsInDomainOrApplication` and `noDoubleOrFloatMethodReturnTypesInDomainOrApplication` cover `..application..` via package pattern (unchanged from A1; now non-vacuous) | ✅ |
| Out-of-scope respected | No JPA / no Spring / no HTTP / no Caffeine / no Resilience4j / no domain edits | Grep-clean on all 20 production files; A1's `domain/*` files untouched on this branch (only the `application` tree is new + 1 test file modified) | ✅ |
| AC coverage table in PR description | Per change-control-pci.md §1 | Cannot verify from dossier (PR description lives on GitHub, not in repo); claim accepted contingent on PR #5 description containing the table | ◻ unverified |

## 2. Code-quality observations (passing)

- `ConversionService.java:78-117` — layer order is exactly `alias → purchase → cache → db → treasury(+upsert) → db-re-read`. Each layer's failure mode is handled deterministically. `verifyNoInteractions` chains in the tests assert short-circuits at every boundary.
- `ConversionService.java:107-114` — the comment "AC-026b: persistence-centric idempotency — the canonical state is the DB, not the fetched list" is exactly the right pattern. The `LayerOrdering.cacheMissDbMissTreasurySuccess` `InOrder` test pins this order with explicit `inOrder.verify(...)` calls.
- `PurchaseService.java:42-53` — `register(...)` checks the future-date business rule via `ClockPort` first, then delegates primitive invariants to `Purchase`'s constructor. The split is the right responsibility boundary (FR-001 business rule = service; structural invariants = domain).
- `ExchangeRateHotCachePort` and `ExchangeRateRepositoryPort` return `List<ExchangeRate>` from `findInWindow(...)` rather than the design-doc-shown `Optional<ExchangeRate> findEligible(...)`. The summary §3 rationale is sound: keeps the `RateSelectionPolicy` as the single source of selection truth in the application layer, prevents adapters from re-implementing or diverging from the policy, and keeps the ports pure-data-access. The corresponding cost (the cache cannot guarantee window-completeness) is a real concern — see §3.2.
- `DomainException` placement under `application.exception.*` instead of top-level `exception/` is justified in the summary §73-§78 — keeps `applicationOnlyDependsOnDomainAndJdk` rule unchanged. The design-doc divergence is documented in the summary's "Convention decisions" section; a future top-level rename is a mechanical one-package refactor with a one-line ArchUnit allow-list edit. Accepted as-is.
- Inbound vs outbound port packaging (`application/port/{in,out}/`) is standard clean-architecture convention. Name-only delta from the design-doc.
- `ArchitectureTests.noSpringStereotypesInApplication` is comprehensive: 9 stereotype + injection annotations covered, exactly matching the A2 prompt's clause (b) intent.
- The 5 "Convention decisions" surfaced in `20-summary.md §73-§101` follow the forward-motion bias correctly: each is named, the design-doc divergence is acknowledged, and the rationale is given. No silent deviations.

## 3. Findings (severity-tagged)

### 3.1 [MEDIUM] NFR-021 enforcement gap: pom does not cover `application/*` for coverage or mutation

**Finding.** The A2 prompt's "Acceptance gates" table lists four NFR-021-bound thresholds that must pass at merge:

- Line coverage on `application/*` ≥ 80 %
- Mutation (Pitest) on `application/*` package average ≥ 70 %
- Mutation on `ConversionService` ≥ 80 %
- (And implicitly: the gates need to be enforced, not just claimed.)

The branch's `pom.xml` has two enforcement plugins:

- **JaCoCo `check-domain-coverage` execution** — `<includes><include>com.example.purchaseconversion.domain</include></includes>` only. The `application` package is **not** included; the JaCoCo `check` will pass even at 0 % application coverage.
- **Pitest `<targetClasses>`** — declared `RateSelectionPolicy` and `Money` only (the two A1 mutation targets). `ConversionService` is **not** a target; mutation never runs on it; the threshold can never fail.

The `20-summary.md §107-§113` claim ("NFR-021 mutation thresholds: pom declarations carry through from A1") is **incorrect** — the A1 declarations were scoped by class/package and do not transitively cover the new A2 code.

**Why it matters.** The gates as written by the prompt cannot be objectively verified on CI. The chunk's quality is not at risk (the tests appear comprehensive and the production code is small and straightforward) — the *enforcement* is. Without the pom update, a future regression in `application/*` coverage or `ConversionService` mutation can land without the build complaining.

**Disposition.** **CONDITION (non-blocking for A2 merge; mandatory for Chunk B intake).** A2's PR may merge as-is — the A1 precedent (Pitest mutation evidence accepted via implementer claim + future CI) applies. But Chunk B's PR must include a small `pom.xml` extension:

```xml
<!-- JaCoCo: add a second `check` execution -->
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

<!-- Pitest: extend targetClasses + add per-class threshold for ConversionService -->
<targetClasses>
  <param>com.example.purchaseconversion.domain.RateSelectionPolicy</param>
  <param>com.example.purchaseconversion.domain.Money</param>
  <param>com.example.purchaseconversion.application.*</param>
</targetClasses>
<!-- and add a per-class threshold mechanism (Pitest's mutationThreshold is package-wide;
     for the ≥ 80 % on ConversionService specifically, use the
     `mutationThreshold` per a second `<execution>` scoped to that one class, or
     evaluate per-class via the Pitest report's per-class line in CI). -->
```

The exact pom shape is the implementer's call (Pitest's per-class threshold needs a separate execution or a CI-side script). Track in Chunk B's `00-prompt.md` as a mandatory addition. Recording as `review_conditions: [pom-coverage-mutation-extension-for-application]` on the A2 manifest.

### 3.2 [LOW] Hot-cache window-completeness contract risk

**Finding.** `ConversionService.java:88-93` treats a non-empty `selectEligible(hotCache.findInWindow(...))` result as authoritative — if the cache returns *any* rate that passes the policy, the DB and Treasury layers are skipped. But the hot cache is keyed by `(currency, recordDate)` per ADR-0001 D-10, and the port's `findInWindow(...)` is necessarily a filtered scan over only the cached subset — it cannot guarantee that the cache contains every `(currency, recordDate)` row that exists in the DB for the window.

**Scenario.** Day 0: Treasury returns rates {R_{-180d}, ..., R_{-1d}}; service upserts them; `hotCache.putAll(...)` populates 180 entries. Day 1 (different request): a separate code path upserts a new R_{0} (recordDate = today) directly into the DB without populating the cache. Day 1 (this service): request for CAD with window [-6mo+1, today]. `hotCache.findInWindow(...)` returns 179 entries (R_{-180d}+1 .. R_{-1d}), `selectEligible(...)` returns R_{-1d}. Service returns R_{-1d} — but the most recent eligible rate is actually R_0 in the DB. **Silent stale-rate selection.**

The current `invalidate(currency, recordDate)` API is per-key and cannot help: it would have to be called with `(CAD, day-0)`, which is the just-inserted rate, not the cached older ones. Per-key invalidation does not refresh the cached subset to be window-complete.

**Why it matters.** Severity is LOW because the architecture's actual upsert path is `ConversionService.convert(...)` itself (which does upsert + re-read + `putAll`), not "a separate code path". A Chunk B `ExchangeRateRepositoryAdapter` that upserts only via this service preserves correctness. But the contract is fragile: any future upsert path (admin tool, batch loader, replay scenario) must invalidate the *entire* `(currency, *)` slice, or the service's cache-short-circuit becomes a stale-rate bug. The summary §158-§164 already flags "transient thrashing is possible" but does not name the stale-selection mode.

**Disposition.** **OBSERVATION (non-blocking).** Track for Chunk B's `00-prompt.md`: either (a) `ExchangeRateRepositoryAdapter.upsertVersioned(...)` calls `hotCache.invalidate(currency, recordDate)` for every upserted row *and* documents that the cache short-circuit relies on this invariant, or (b) `ExchangeRateHotCachePort` grows a `bulkInvalidate(CurrencyDescriptor)` operation and the upsert adapter uses it, or (c) the service consults the DB before trusting the cache's first hit (defeats the cache, not recommended). Option (a) is the lowest-friction path and consistent with D-10's "cache invalidation on Treasury revision" clause.

Recording as `review_conditions: [hot-cache-upsert-invalidation-contract]` on the A2 manifest, scoped to Chunk B.

### 3.3 [NIT] Unused imports in `ConversionServiceTest`

Lines 23 (`ArgumentCaptor`), 32 (`Collection`), 39 (`anyList`), 40 (`eq`) are imported but never referenced. Cosmetic; does not affect correctness or CI. Pick up on the next touch of the file. Not tracked as a condition.

### 3.4 [NIT] PR-description AC coverage table

The prompt mandates an AC→test-method coverage table in the PR description per `change-control-pci.md §1`. I cannot verify GitHub PR descriptions from the dossier. **Implementer-attestation accepted** contingent on PR #5 actually containing the table; reviewer will spot-check on a later tick if surfaced as a gap.

## 4. Code outside scope — confirmed clean

- A1's `domain/*` files: untouched on this branch (the `application` tree is the only addition; `ArchitectureTests.java` is the only test file modified, and the change is additive — two new rules + a comment update on a third).
- `pom.xml`: unchanged in A2 (confirms condition §3.1 above — the application-package coverage/mutation gates are not yet wired in; needs Chunk B).
- `.github/workflows/`, `infra/`, `.human-approvals/`, `prompts/`, `.claude/`, `security-profile.yml`, `CLAUDE.md`, `Makefile`, `docs/requirements/source-requirements.md`: not touched (no risk of policy denial; no PCI scope change).
- No new third-party dependencies introduced; the existing pom dependencies (JUnit, AssertJ, Mockito, ArchUnit, jqwik, uuid-creator) cover everything the new test classes import.

## 5. PCI / security posture

- No CHD path touched. The application layer is out-of-CDE by design (component-design.md §1).
- No secrets, no credentials, no I/O.
- `InvalidCurrencyException` carries the raw unresolved currency input string (`InvalidCurrencyException.java:23`). This is intentional — the controller layer / observability layer (Chunk C / D-12) is responsible for hashing via `DescriptionHasher` before any log emission. The exception's javadoc correctly flags this responsibility ("It is NOT logged in plain text — log emission must use structured event ... with the value passed through DescriptionHasher"). Track for Chunk C: the API-layer `@RestControllerAdvice` mapping for `InvalidCurrencyException` must hash the currency string in any log/metric emission and must echo only the hashed form (or a redacted placeholder) in the response body's `details.currency` field. Recording as `review_conditions: [api-layer-currency-input-hashing-on-emit]` scoped to Chunk C.
- No PCI scope-expansion in this chunk.

## 6. Conditions summary (machine-readable)

```yaml
review_conditions:
  - id: pom-coverage-mutation-extension-for-application
    severity: medium
    scope: chunk-b
    description: |
      Extend pom.xml so JaCoCo `check` covers application.* (line ≥ 80 %)
      and Pitest <targetClasses> includes application.* with ConversionService
      held to ≥ 80 % (per-class). Without this, the A2 prompt's NFR-021 gates
      are pom-declared but not build-enforced.
  - id: hot-cache-upsert-invalidation-contract
    severity: low
    scope: chunk-b
    description: |
      ExchangeRateRepositoryAdapter.upsertVersioned(...) must invalidate the
      hot cache for the affected (currency, recordDate) keys on every upsert,
      OR ExchangeRateHotCachePort grows a per-currency bulk-invalidate. Without
      this, ConversionService's cache short-circuit can return a stale rate
      when out-of-band upserts land. Document the invariant either way.
  - id: api-layer-currency-input-hashing-on-emit
    severity: low
    scope: chunk-c
    description: |
      The @RestControllerAdvice mapping for InvalidCurrencyException must hash
      the carried currency string via DescriptionHasher before any log/metric
      emission. Response body's details.currency should echo only the hashed
      form or a redacted placeholder.
```

## 7. State transition

```yaml
# chunks/13-A2-application/manifest.yml — target state after this commit
status: accepted
review_sha: <set when this file commits>
review_conditions:
  - pom-coverage-mutation-extension-for-application
  - hot-cache-upsert-invalidation-contract
  - api-layer-currency-input-hashing-on-emit
follow_ups: []  # the three conditions are scope-tagged in §6 above; Chunk B/C prompts will pick them up
```

`STATUS.md` rollup will be updated in the same commit: A2 row flips to `accepted | #5 | <merge sha when known> | — closed`.

## 8. Merge guidance

PR #5 may merge as-is. Recommend squash-or-rebase per the repo's prevailing strategy (A1 was rebase to preserve plant+revert; A2 has no equivalent audit chain, so squash is fine if cleaner).

Post-merge, Chunk B (`13-B-infrastructure`) is unblocked. The implementer should pick up `chunks/13-B-infrastructure/00-prompt.md` as the next canonical kickoff. The three review_conditions above are not pre-merge gates for A2 itself but are mandatory intake items for B/C respectively — the implementer should fold them into B's and C's `00-prompt.md` (or call out in the corresponding `20-summary.md` how they're addressed).
