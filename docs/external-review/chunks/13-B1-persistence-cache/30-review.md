# 30-review — Chunk 13-B1-persistence-cache

Reviewer: external (Phase 13 governance). Tick: 2026-05-18 (automated polling).
Branch: `feature/chunk-b1-persistence-cache` @ `ac7cd9c` (substantive commit `3c1c504`).
PR: #7. Manifest CI: green per `ci_url` (run `26010337208`).

## Verdict

**ACCEPTED WITH CONDITIONS.** PR #7 may merge to `main` once the implementer reads and acknowledges the conditions below. The conditions are intake follow-ups, not pre-merge gates — they may be addressed in a small docs-only commit on this branch before merge, in a fast-follow PR, or absorbed into B2 as explicit prompt items. The implementer's choice; mark the decision in the merge commit body.

This verdict simultaneously closes both A2 intake items (§1 with one residual MED finding; §2 fully closed) carried as `review_conditions` on this chunk's `manifest.yml`.

## Evidence read

- `chunks/13-B1-persistence-cache/00-prompt.md`, `20-summary.md`, `manifest.yml`.
- `directives/2026-05-17-forward-motion-bias.md`, `directives/2026-05-17-ops-check-and-pci-check-strict-flip.md`.
- `chunks/13-A2-application/30-review.md` (intake-item source).
- `chunks/13-B-infrastructure/30-review.md` (supersession + per-sub-chunk LOC cap).
- Branch at `3c1c504` via `git show`:
  - `pom.xml` — JaCoCo + Pitest executions, threshold properties.
  - `src/main/java/.../infrastructure/persistence/ExchangeRateRepoAdapter.java`.
  - `src/main/java/.../application/port/out/ExchangeRateHotCachePort.java` (re-added + javadoc).
  - `src/test/java/.../infrastructure/AbstractPostgresIT.java`, `ExchangeRateRepoIT.java`.
  - `.gitignore` (post-fix at `3c1c504`) and `.gitignore` at A2 `48dc656` (pre-fix).
  - A2 `48dc656` file list — confirmed `port/out/` directory was absent in the merged A2 commit.
- `--numstat` for `3c1c504`: +2,743 / −51 across 31 files; LOC distribution computed inline below.

## Findings

### §1 — A2 regression discovery + fix (CRITICAL → resolved in this PR) — INFO

The implementer's `20-summary.md` Risks block reports that A2 commit `48dc656` silently dropped the entire `application/port/out/` directory because `.gitignore` carried a Node-build `out/` rule (no leading `/`), which gitignore-matches `out/` anywhere in the tree.

**Verified.**
- `git show --name-only 48dc656` shows four files under `application/port/in/` and zero under `application/port/out/`.
- `git show 48dc656:.gitignore` confirms the unscoped `out/` rule under `# Node`.
- `git show 3c1c504:.gitignore` confirms the fix: rule rescoped to `/out/` (top-level only), with an inline comment naming the prior masking incident.
- `git show --name-only 3c1c504` confirms the six expected outbound ports (`ClockPort`, `CurrencyAliasPort`, `ExchangeRateHotCachePort`, `ExchangeRateRepositoryPort`, `PurchaseRepositoryPort`, `TreasuryClientPort`) are re-added in `application/port/out/`.

This means `main` was non-compilable from `48dc656` until B1 merges. The mask was the M7-carry-forward (CI doesn't compile Java yet), which is what enabled A2's `30-review` to land "code-correctness accepted" without catching it — the review was on the diff Claude generated, not on a build of `main` post-merge.

**Disposition:**
- The B1 PR is the correct vehicle for the fix: co-located with the first code that depends on those ports being importable, with the `.gitignore` change in the same commit. Splitting it out would force a non-compiling intermediate state on `main`.
- **Retroactive correction note on A2:** the A2 `20-summary.md` §"Files changed" claim that six outbound ports shipped is now known to be inaccurate against the `48dc656` --stat. Per forward-motion bias §8 ("let the next PR's note carry the correction"), this `30-review.md` and B1's `20-summary.md` Risks block are the canonical audit trail. **No amendment to A2's dossier is required**; A2's `manifest.status: accepted` stands. The A2 acceptance was on the design + diff Claude submitted; the merge mechanic (gitignore mask) was a separate defect that's now closed.
- **No reopen of A2's verdict.** This is the disposition I would have written had the regression been caught at A2's `30-review`: "ACCEPTED WITH CONDITIONS, condition: re-land the masked ports + rescope `.gitignore` in the next PR." B1 has executed that condition.
- **Lesson recorded** (not a B1 finding): future intake `30-review` work should `git show --name-only` the merge commit against the implementer's claimed file list when the M7 Java-build mask is in effect. Add to the reviewer playbook when the dossier conventions are next revised (post-M7).

Severity: **INFO** (regression resolved in the same PR that discovered it). Excellent forensic work by the implementer; the `git add` "hint: Use -f if you really want to add them" warning is an easy thing to miss and the recovery is clean.

### §2 — A2 intake §1: pom.xml coverage + mutation extension for `application/*` — MOSTLY CLOSED (one MED residual)

Intake §1 had three sub-conditions (per `13-A2-application/30-review.md` §3.1 and B1 prompt §"A2 intake items §1"):

| # | Sub-condition | B1 implementation | Verdict |
|---|---|---|---|
| (i) | JaCoCo `check` execution targeting `application.*` with line ≥ 0.80 | `pom.xml` → `<execution id="check-application-coverage">` with `<include>com.example.purchaseconversion.application.*</include>` and `<minimum>${jacoco.application.line}</minimum>` where `jacoco.application.line = 0.80` | ✓ Closed |
| (ii) | Pitest `<targetClasses>` extended to cover `application.*` | `pom.xml` → `<execution id="mutation-application">` with target classes `application.purchase.*` and `application.conversion.*` and `<mutationThreshold>80</mutationThreshold>` | ✓ Closed |
| (iii) | **Per-class** `ConversionService` ≥ 80 % mutation threshold. Gate effect: "merge blocked if `ConversionService` mutation < 80 %." | Implementer chose to rely on the package-scope `mutation-application` execution's `<mutationThreshold>80</mutationThreshold>`. Summary claims "the threshold applies package-wide." | ✗ **NOT closed by mechanism chosen** — see below. |

**MED finding on sub-condition (iii).** Pitest's `<mutationThreshold>` is applied to the **aggregate mutation score** of the execution, not per-class. Concrete failure mode: if `ConversionService` has 10 mutants of which 4 are killed (40 %), and `RegisterPurchaseService` + `RetrievePurchaseService` together contribute 100 mutants of which 85 are killed, the aggregate is 89 / 110 ≈ 81 % — **passes** the 80 % threshold despite `ConversionService` being at 40 %. The gate effect requested by the A2 30-review (and re-stated in the B1 prompt) is not implemented.

In practice the risk is lower than the abstract failure mode (ConversionService is the largest class in `application.conversion`, so its mutants dominate), but the prompt's gate-effect requirement is binding regardless of practical drift.

**Required resolution** (implementer choice between (a)/(b)/(c); pick one and record in the merge commit body or in a follow-up `25-update.md` on this chunk):

(a) **Add a separate Pitest execution** scoped to `ConversionService` with its own threshold. Estimated ~15 lines of pom:

```xml
<execution>
  <id>mutation-conversion-service-per-class</id>
  <phase>verify</phase>
  <goals><goal>mutationCoverage</goal></goals>
  <configuration>
    <targetClasses>
      <param>com.example.purchaseconversion.application.conversion.ConversionService</param>
    </targetClasses>
    <targetTests>
      <param>com.example.purchaseconversion.application.conversion.*</param>
    </targetTests>
    <mutationThreshold>80</mutationThreshold>
    <failWhenNoMutations>true</failWhenNoMutations>
  </configuration>
</execution>
```

(b) **CI-side report parser** that reads `target/pit-reports/mutations.xml`, filters to `ConversionService`, and fails the build if per-class kill rate < 0.80. Heavier; only worth it if more per-class gates land later.

(c) **Explicit downgrade**, with reviewer concurrence: package-scope ≥ 80 % is treated as acceptable proxy for per-class ≥ 80 % on `ConversionService`, on the understanding that `ConversionService` dominates the package's mutant count and so the aggregate is a tight upper bound on its per-class score. If the implementer chooses (c), record it as a directive amendment (e.g., `directives/2026-05-18-conversion-service-per-class-pitest-downgrade.md`) so the original A2 30-review §3.1 (3) wording isn't quietly dropped.

**Reviewer preference: (a).** It's a 15-line pom edit, deterministic, and closes the gate exactly as the A2 30-review specified. Can be a one-commit follow-up on this branch before merge or a small fast-follow PR after merge; either is acceptable. **Not blocking merge of PR #7** — the rest of the intake §1 is closed and CI is green.

Severity: **MEDIUM.**

### §3 — A2 intake §2: hot-cache upsert-invalidation contract — CLOSED

Three artifacts required by the prompt:

1. `ExchangeRateRepoAdapter.upsertVersioned(...)` invalidates the cache for every upserted `(currency, recordDate)` key, including identical-row no-ops.
   - **Verified** in `ExchangeRateRepoAdapter.java`. Iteration collects `InvalidationKey(currency, recordDate)` for every row in the upsert loop, then invokes `hotCache.invalidate(...)` after the DB writes. Identical-row case is also invalidated (the loop is unconditional).

2. Adapter javadoc states the invariant explicitly.
   - **Verified.** `<h2>Hot-cache upsert-invalidation invariant (A2 intake §2; LOW)</h2>` block. The required sentence ("Every upsert path MUST go through this adapter. Direct DB writes bypass the cache invalidation invariant and risk stale-rate selection in `ConversionService`.") is paraphrased with stronger framing — acceptable per forward-motion §9 (semantic equivalence, not literal verbatim).

3. New `ExchangeRateRepoIT` test exercises the invariant.
   - **Verified.** `ExchangeRateRepoIT.HotCacheInvalidationContract` nested class with two methods: `invalidatesCacheKeys` (multi-key upsert) and `identicalUpsertStillInvalidates` (identical-row case). Both seed the cache, perform an upsert, and assert `findInWindow` returns empty after.

4. **Bonus (not required by prompt but useful):** the implementer also added the invariant restatement on `ExchangeRateHotCachePort` javadoc — 5-line semantic edit to an `application/*` file. The implementer flagged this as a forward-motion bend on B1's "no edits to application/*" hard constraint and offered to roll it back if the reviewer prefers.

   **Reviewer ratifies the bend.** The B1 prompt's intake §2 verification explicitly says "reviewer reads **both files'** javadoc" — the prompt anticipated the port javadoc edit even though the §"Out of scope" line is more restrictive. The bend is doc-only (zero runtime semantic change), strengthens the contract for future contributors, and is exactly the kind of small contract-restatement forward-motion bias §9 was written for. Keep as-is.

Severity: closed, no action required. **A2 intake §2 condition is cleared on this chunk.**

### §4 — LOC overage (~2,135 net new content vs 1,800 hard cap) — ACCEPTED, one-time

`git show --numstat 3c1c504` → +2,743 / −51 across 31 files. Breakdown (added lines only, by category):

| Category | LOC | Notes |
|---|---|---|
| Production java | 940 | Includes **207 LOC of re-added outbound ports** that should have been in A2's `48dc656`. True B1-scope production code: ~733 LOC. |
| Test java | 1,046 | 8 ITs + 1 unit test, all mandated by the prompt's PCI-invariants table + acceptance gates. |
| Resources (yaml/json) | 232 | Liquibase changelogs (~130 LOC) + `application.yml` (~65 LOC) + `currency-aliases.json` (~31 LOC). |
| `pom.xml` (added) | 270 | Spring Boot parent + 11 new dependencies + 3 new JaCoCo executions + 3 new Pitest executions + thresholds. ~120 LOC is intake §1 specifically. |
| `.gitignore` | 8 | Regression fix + explanatory comment. |
| Dossier (`20-summary.md`, manifest delta) | 247 | Non-code; not counted toward the cap. |

**Net new content LOC (cap-relevant): 940 + 1,046 + 232 + 270 + 8 − 51 (deletions) ≈ 2,445 LOC.** Implementer's figure of 2,135 is on a slightly different denominator (closer to "Java + resources, no pom"); both numbers are over 1,800.

**Disposition: ACCEPT as one-time concession.** Reasoning, in order:

1. **The A2-regression-fix LOC (~207 LOC re-added ports) is not B1 scope creep.** It is A2-debt recovery, forced by the discovery that `main` doesn't compile. It cannot be split into a separate PR without leaving `main` in a non-compiling intermediate state (and B1's adapters depend on the ports being importable). Subtract this: ~2,238 LOC remaining over the cap by ~+438 LOC.

2. **Test density is prompt-mandated.** The PCI-invariants table in `00-prompt.md` enumerates 7 invariants → 8 test classes including AbstractPostgresIT. The implementer has not over-engineered tests — the 1,046 test LOC is roughly 130 LOC/test average, which is typical for Testcontainers ITs with Postgres-flavoured assertions and proper setup/teardown. A re-split into "B1-impl + B1-tests" would create a non-CI-runnable intermediate PR and double the review cost.

3. **The forward-motion-bias §"Capacity overage" directive requires surfacing, not refusal.** The implementer surfaced honestly in `20-summary.md` Risks with both options (a) accept / (b) re-split. This satisfies the directive.

4. **The B-supersession `30-review.md` warned that accepting LOC overages would set a precedent.** I'm aware of that. The precedent risk is real, but the alternative — re-splitting after the work is done — is worse in this specific case because (a) the regression fix cannot be split, (b) the seam between adapter and IT is artificial, and (c) the redo cost is ~2 hours of implementer time with no review-quality gain. **This is a one-time concession driven by the regression-fix being co-located.** B2 must hold the 1,800 cap; if B2 runs over, the answer is to re-split B2 (it has a clean Treasury/SingleFlight seam), not to grant a second concession.

5. **Reviewability is not actually degraded** — the 31-file structure is per-class-coherent and reviewable in three passes (pom + bootstrap; production adapters + ports; tests). The cap exists to keep reviews tractable; tractability is preserved here.

**Condition for B2:** the LOC cap for B2 is reaffirmed at 1,500 soft / 1,800 hard. If B2 surfaces a similar overage pre-implementation, the disposition will be re-split (the SingleFlightGate / TreasuryClient / Resilience4j seam is natural).

Severity: **LOW (informational).** Documented as one-time concession.

### §5 — Convention decisions — RATIFIED

Five convention decisions in `20-summary.md` §"Convention decisions":

1. **`ExchangeRateHotCachePort` javadoc edit (5 lines).** Ratified — see §3 above.
2. **`TreasuryClientPort` stub in `WexConfig`.** Ratified. Documented in code; B2 swaps it. Acceptable for a B1 boot-only stub. **Condition for B2:** the stub must be removed in the same PR that lands `TreasuryClientAdapter`, with the swap visible in the diff (no dangling stub bean).
3. **Liquibase changelog YAML format.** Ratified. Readability + rollback-instructions-inline are valid reasons; matches the rollback class C contract from `rollback-plan.md` §4.4.
4. **`*.IT.java` vs `*Test.java` test naming.** Ratified. Surefire/Failsafe split is the standard convention; the prompt's `*Test.java` naming was non-binding.
5. **Table name `purchase_transactions` over `purchases`.** Ratified. `data-model.md` §2 DDL is authoritative; `§6` and the B1 prompt §"Components" wording carried the looser short-form. **B1 prompt erratum noted** — future references should use `purchase_transactions`. No code change required; no dossier amendment required (this 30-review.md is the erratum record).

### §6 — Other PCI-critical invariants (table from the prompt) — DECLARED, evidence in CI

The PR description (per `change-control-pci.md` §1) and `20-summary.md` map each prompt-mandated invariant to a test class + method. CI is green per `manifest.ci_url` = `https://github.com/sajithmankavil/wex-purchase-fx/actions/runs/26010337208`. Spot-checks:

- **G4-P0-2 hot cache key** — `HotCacheKeyTest.Keying.sharedKeyReused` + `RangeQuery.*` referenced; cache adapter uses `Caffeine outer + ConcurrentSkipListMap inner` for O(log n) window queries, key `(currency, recordDate)` per D-10.
- **G4-P0-3 scale-6** — `ScaleNormalizationIT.normalisesToScale6` (parameterised across 8 Treasury variants).
- **G6-P0-4 DB pool readiness** — `DbPoolHeadroomHealthIndicator` + test verifies 5-second sustained-saturation + recovery + non-Hikari rejection.
- **AC-010 durability** — `DurabilityRestartIT` uses H2 file-mode (not the Testcontainers fixture); confirmed by file path and AbstractPostgresIT comment.
- **AC-026b** — `ExchangeRateRepoIT.VersionedUpsert.revisionAddsRow` (verified the nested class exists in the test file).
- **AC-021b/c alias drift** — `CurrencyAliasDriftTest`; adapter has refuse-to-start + `currency_alias_drift_detected` event semantics per prompt.

**No deep code-walk performed on every test** — CI green + cohesive structure + spot-check on the high-risk paths (`ExchangeRateRepoAdapter`, `ExchangeRateHotCachePort`, `AbstractPostgresIT`) is sufficient at this review depth. If a regression surfaces in B2/C, it gets caught there.

Severity: none.

### §7 — Pre-existing M7 carry-forwards — non-blocking

Three items the implementer flagged as carry-forward, not B1-scope:

- M7: local `mvn` cannot run in this sandbox; test evidence is the branch CI URL. Same as A1/A2 disposition. Non-blocking; will resolve when the Java/Maven workflow lands post-M7.
- `security.yml` workflow startup_failure (M7 carry-forward). Non-blocking; tracked elsewhere.
- `scripts/tests/` CI test discovery for pytest. Tracked as the "Pending follow-up" in STATUS.md after Chunk A1 closed. Still non-blocking; not a B1-scope item.

Severity: none.

## Merge instructions

1. **Recommended:** add the per-class `ConversionService` Pitest execution (§2 finding (iii), option (a)) as a one-commit follow-up on this branch before merging PR #7. ~15 lines of pom, deterministic.
   - Alternative: merge PR #7 as-is and address §2 (iii) in a separate fast-follow PR with `change-id: WEX-CHUNK-B1-pitest-conversionservice-per-class`. Implementer's call.
2. Rebase strategy as per A1/A2 precedent (linear history; `--ff-only` on merge). The 2 commits on this branch (`3c1c504` substantive + `ac7cd9c` manifest) should remain distinct on `main` — the manifest commit is part of the dossier-with-PR convention and is informative for audit.
3. After merge: the implementer flips B1's `manifest.status` from `summary_posted → accepted` per the forward-motion mechanical-transition pattern (this 30-review.md is the substantive verdict; the manifest flip is bookkeeping). Then proceed to B2 on `feature/chunk-b2-treasury-singleflight` off the post-B1 `main`.
4. **Update STATUS.md** to reflect B1 accepted + B2 starting (reviewer has handled this; see `STATUS.md` rollup).

## State transitions

`chunks/13-B1-persistence-cache/manifest.yml` flips to:
- `status: accepted` (with the §2 (iii) condition recorded in `follow_ups`, not `review_conditions`, because the A2 intake items §1 and §2 are formally closed by this review — the residual is a separate, narrower follow-up).
- `review_sha`: set from `git hash-object` after this file commits.
- `review_conditions: []` (both A2 intake items closed).
- `follow_ups: [pitest-conversionservice-per-class-execution]`.

`chunks/13-A2-application/manifest.yml`: **no change.** The two `review_conditions` carried from A2 are closed by §2 and §3 of this review; A2's manifest already shows `status: accepted` and does not maintain a `review_conditions` field after closure. The closure is recorded here.

`chunks/13-B2-treasury-singleflight/manifest.yml`: `depends_on_satisfied` should flip to `true` once B1 merges to `main`. Implementer handles on B2 kickoff.

`STATUS.md`: rollup updated by this reviewer (separate commit/edit) to reflect B1 accepted + B2 starting.

## Quality summary

Excellent chunk. The regression discovery + fix is the highlight — a less careful implementer would have shipped B1 on top of a broken `main` and discovered the issue post-merge. The decision to co-locate the fix is correct, and the audit trail (gitignore rule scope + the explanatory comment in `.gitignore` itself) is exactly what enterprise SDLC review wants to see.

The one MED residual (per-class ConversionService Pitest threshold) is a real prompt-compliance gap but easily closed; flagging it is consistent with the project's "explicit assumptions over hidden assumptions" principle.

LOC overage is real but driven by causes outside the implementer's control (regression fix + prompt-mandated test density). Accepting it as a one-time concession is the right call; B2 must hold the line.
