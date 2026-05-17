# 15-clarification — Chunk 13-B-infrastructure

**Author:** External governance reviewer
**Date:** 2026-05-17
**Type:** Reviewer amendment to `00-prompt.md`. Adds two mandatory intake items carried over from `chunks/13-A2-application/30-review.md` §3.1 + §3.2 + §6. The original `00-prompt.md` remains immutable; this file is the binding extension.

---

## Two new mandatory items for this chunk

Both items below are **intake items**, not optional follow-ups. They are tracked in `chunks/13-A2-application/manifest.yml` as `review_conditions: [pom-coverage-mutation-extension-for-application, hot-cache-upsert-invalidation-contract]`. They land **in Chunk B's PR** unless explicitly deferred via a `10-deviation.md` raised here with reviewer sign-off.

---

### Intake Item 1 — `pom-coverage-mutation-extension-for-application` (MEDIUM)

**Source:** `chunks/13-A2-application/30-review.md` §3.1.

**Context.** A2's prompt declared four NFR-021-bound thresholds on `application/*` (line ≥ 80 %, package mutation ≥ 70 %, `ConversionService` mutation ≥ 80 %). The A2 PR's `pom.xml` did **not** wire JaCoCo or Pitest to cover the `application` package — A1's declarations are scoped to `com.example.purchaseconversion.domain` (JaCoCo) and `RateSelectionPolicy` + `Money` only (Pitest `<targetClasses>`). The gates are therefore pom-declared by the A2 prompt but not build-enforced today. A2 was ACCEPTED WITH CONDITIONS on the understanding that B closes this enforcement gap.

**Required change in this chunk's PR.** Extend `pom.xml` so the build fails if `application/*` regresses below threshold:

1. **JaCoCo** — add a second `check` execution with element=PACKAGE and includes=`com.example.purchaseconversion.application.*` and minimum LINE COVEREDRATIO 0.80.
2. **Pitest** — extend `<targetClasses>` to include `com.example.purchaseconversion.application.*` and add a per-class threshold mechanism for `ConversionService` ≥ 80 %. The exact mechanism (second `<execution>`, the `mutationThreshold` element, or CI-side report parsing) is the implementer's call; the gate's effect must be "merge blocked if ConversionService mutation < 80 %."

A reviewer-suggested skeleton is provided in `chunks/13-A2-application/30-review.md` §3.1 — adapt as needed.

**Verification at this chunk's 30-review.** Reviewer will read `pom.xml` on the chunk's branch and confirm: (a) JaCoCo `check` execution covering `application.*` with line ≥ 0.80 exists, (b) Pitest target classes include `application.*`, (c) `ConversionService` is held to ≥ 0.80 mutation either via a dedicated execution or an equivalent CI gate. Acceptance requires all three.

**Disposition.** Mandatory for this chunk. If a hidden complication surfaces (e.g., Pitest's per-class threshold proves impractical without a CI-side helper), write `chunks/13-B-infrastructure/10-deviation.md` describing the constraint and the proposed alternative; do NOT silently drop the gate.

---

### Intake Item 2 — `hot-cache-upsert-invalidation-contract` (LOW)

**Source:** `chunks/13-A2-application/30-review.md` §3.2.

**Context.** A2's `ConversionService.convert(...)` treats a non-empty result from `ExchangeRateHotCachePort.findInWindow(...)` as authoritative — if the cache returns any rate that passes the selection policy, the DB and Treasury layers are skipped. The hot cache is keyed by `(currency, recordDate)` per ADR-0001 D-10; `findInWindow(...)` returns a filtered scan over the cached subset. There is no contract today that the cache contains every `(currency, recordDate)` row that exists in the DB for the window.

**Scenario (silent stale-rate selection).** Day 0: `ConversionService` upserts a Treasury fetch result (rates `R_{-180d}..R_{-1d}`) into the DB and populates the hot cache. Day 1: some out-of-band code path (admin tool, batch loader, replay) upserts a fresher `R_0` into the DB without touching the cache. Day 1 (this service): cache returns 180 stale entries; policy picks `R_{-1d}`; the fresh `R_0` is silently bypassed. This is the failure mode A2's reviewer flagged.

**Required change in this chunk's PR.** Pick ONE of:

(a) **`ExchangeRateRepositoryAdapter.upsertVersioned(...)` invalidates the cache on every upserted row.** Document the invariant in the adapter's javadoc: *"Every upsert path MUST go through this adapter. Direct DB writes bypass the cache invalidation invariant and risk stale-rate selection in `ConversionService`."* Recommended option — lowest-friction.

(b) **`ExchangeRateHotCachePort` grows a per-currency bulk-invalidate operation** (e.g., `void invalidateCurrency(CurrencyDescriptor)`); the upsert adapter calls it before / after writing. Higher friction; useful if the cache footprint per currency is large.

(c) **`ConversionService` consults the DB before trusting the cache's first hit.** Defeats the purpose of the cache (every request now hits the DB). Reject — only land if (a) and (b) prove impractical.

Whichever option lands, the contract must be **explicit in javadoc on both `ExchangeRateRepositoryAdapter` and `ExchangeRateHotCachePort`**, so a future contributor cannot accidentally introduce an out-of-band upsert path.

**Verification at this chunk's 30-review.** Reviewer will read both files' javadoc and confirm the invariant is stated; if option (a) was chosen, verify the adapter calls invalidate on the upserted keys; if (b), verify the bulk-invalidate API exists and the adapter uses it.

**Disposition.** Mandatory for this chunk. The cost is small (≤ 5 LOC for option (a), ≤ 15 LOC for (b)) and the failure mode is otherwise latent until a future code path introduces it.

---

## Items NOT changed by this clarification

- The chunk's existing scope (`ExchangeRateRepoAdapter`, `TreasuryClientAdapter`, `SingleFlightGate`, etc.), out-of-scope list, branching/PR discipline, and PCI invariants table per `00-prompt.md` are all unchanged.
- LOC cap (≤ ~1,500 target, ≤ ~1,800 hard) is unchanged. The two intake items are small; they fit easily within the cap.
- Rollback class C (schema rollback) is unchanged.

## How to record this in the chunk's `20-summary.md`

When writing the chunk's `20-summary.md`, add a "Reviewer intake conditions" subsection under the per-component rundown, with one line per intake item describing how it was addressed (pom diff, javadoc additions, adapter method, etc.) and the test class that exercises it. The chunk's `30-review.md` will use that subsection to verify closure.
