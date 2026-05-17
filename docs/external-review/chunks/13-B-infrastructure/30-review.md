# 30-review — Chunk 13-B-infrastructure

**Verdict:** **DEVIATION ACCEPTED. Option (a) — split B into B1 + B2. This chunk is `superseded` by `13-B1-persistence-cache` and `13-B2-treasury-singleflight`.**

**Reviewer:** External governance reviewer
**Date:** 2026-05-17
**Responds to:** `chunks/13-B-infrastructure/10-deviation.md` (capacity-overage-proposed-b1-b2-split)

---

## 1. Why accepted

The implementer's LOC estimate of ~2,800 (~2,200 even with aggressive trimming) is honest and ~400+ LOC over the hard upper bound (1,800) set by the `chunkA-loc-cap-revision` directive. Per `directives/2026-05-17-forward-motion-bias.md` § "What is a true blocker" → "Capacity overage", this is a category that requires explicit surfacing before implementation begins. The implementer correctly raised it pre-implementation rather than silently exceeding.

Option (a) — the B1/B2 split — is the right resolution. The reasoning in the deviation §"Why this split (not a different one)" is sound on five counts:

1. **Dependency seam is natural.** B1's `ExchangeRateRepoAdapter` is a prerequisite for B2's `SingleFlightCacheConcurrencyTest` (the loser-polls-DB pattern from D-9 / G6-P0-2 requires the DB layer to exist).
2. **A2 intake items both land in B1.** That means A2's `review_conditions` close at B1's 30-review without waiting for B2 — keeps the audit chain tight.
3. **PCI invariants partition cleanly:** persistence-layer (AC-010, AC-026b, G4-P0-2, G4-P0-3, G6-P0-4) → B1. Concurrency / upstream (AC-T-3, AC-027b/c/d/e, G4-P0-1, G6-P0-1, G6-P0-2, G4-P1-23) → B2. No invariant straddles the seam.
4. **Rollback classes diverge:** B1 introduces schema (class C, maintenance window). B2 is code-only (class A, continuous). Bundling would force B2 into a class-C deployment window unnecessarily.
5. **Process pattern already validated:** the same shape as the A1/A2 split worked. One extra review cycle is the cost; identical to A1/A2 cost.

**Options (b) and (c) rejected.** (b) would have set a precedent that LOC caps are negotiable per-chunk; the cap exists to keep reviews tractable. (c) — no alternative split was preferable; the persistence/concurrency seam is the natural one and the implementer's analysis is correct.

---

## 2. State transitions

### This chunk (`13-B-infrastructure`)

```yaml
status: deviation_surfaced → superseded
superseded_by: [13-B1-persistence-cache, 13-B2-treasury-singleflight]
deviations_open: []   # the capacity-overage deviation is resolved by the split
next_chunk: 13-B1-persistence-cache
```

The folder + its files (`00-prompt.md`, `10-deviation.md`, `15-clarification.md`, `manifest.yml`, this `30-review.md`) are preserved as the audit trail for the supersession. No deletes — per the README's archive policy.

### New chunks created in the same commit

- `chunks/13-B1-persistence-cache/{00-prompt.md, manifest.yml}` — `status: prompt_received`. Absorbs the **two** A2 intake items inline (so B1 is self-contained; no reference into the superseded folder).
- `chunks/13-B2-treasury-singleflight/{00-prompt.md, manifest.yml}` — `status: prompt_received`. Depends on `13-B1-persistence-cache`.

### Downstream chunk update

- `chunks/13-C-api-observability/manifest.yml` — `depends_on: [13-B-infrastructure]` → `depends_on: [13-B2-treasury-singleflight]`. C remains the final chunk in Phase 13; only the dependency edge is rewired.

### Rollup

- `STATUS.md` — supersede the B row, add B1 + B2 rows, update C's "Next" pointer, log under "Recent reviewer activity."

---

## 3. Constraints on B1 and B2

The constraints from the original B `00-prompt.md` propagate to **both** sub-chunks unless the new prompts explicitly override:

- LOC cap: ≤ ~1,500 target / ≤ ~1,800 hard, per sub-chunk (not bundled).
- No JPA / Hibernate. JDBC + `JdbcClient` only (D-2).
- No Spring annotations in `domain` or `application` (already enforced by A2's ArchUnit rules).
- No edits outside the chunk's scope. No HTTP / no controllers / no ContentGuard / no OpenAPI / no observability wiring (those live in C).
- No edits to `.human-approvals/`, `prompts/`, `.claude/`, `security-profile.yml`, `CLAUDE.md`, `Makefile`, `docs/requirements/source-requirements.md`.
- B1's rollback class is C (schema). B2's rollback class is A (code).
- Forward-motion bias applies: surface deviations before consuming effort; default-proceed on non-cascading findings.

The PCI-critical invariants table from the original B `00-prompt.md` is **partitioned** across the two new prompts (see §"Scope" of each new `00-prompt.md`).

---

## 4. A2 intake items — closure path

| A2 intake item | Where it closes | Verification at 30-review |
|---|---|---|
| `pom-coverage-mutation-extension-for-application` (MED, scope: chunk-b) | **B1** — `pom.xml` extension in B1's PR | B1's `30-review.md` will read `pom.xml` and confirm JaCoCo `application/*` check + Pitest `application/*` targets + `ConversionService` ≥ 80 % per-class |
| `hot-cache-upsert-invalidation-contract` (LOW, scope: chunk-b) | **B1** — `ExchangeRateRepoAdapter.upsertVersioned(...)` invalidates the cache + javadoc invariant | B1's `30-review.md` will read the adapter + port javadoc and confirm the contract is explicit |

Both items remain on `chunks/13-A2-application/manifest.yml` as `review_conditions` until B1's `30-review.md` closes them. At that point the reviewer flips them to `closed` and records the SHA.

---

## 5. Process notes

- The implementer's surfacing of this deviation **before** consuming implementation effort is exactly the discipline the forward-motion-bias directive endorses. Saved hours of rework.
- The original `15-clarification.md` in this folder (A2 intake items) is referenced by content (not by path) in B1's new `00-prompt.md`, so B1 is self-contained even after this folder is archived.
- C's `15-clarification.md` (one A2 intake item, API-layer currency hashing) is unaffected — that item is C-scoped and doesn't move.

---

## 6. Merge guidance

No PR exists for this superseded chunk. The next merges are B1's and B2's PRs, in that order. The implementer should:

1. Read `chunks/13-B1-persistence-cache/00-prompt.md` (self-contained — includes both A2 intake items inline).
2. Open `feature/chunk-b1-persistence-cache` off `main`.
3. Standard chunk workflow from there (10-deviation if needed, else implement, then `20-summary.md` + manifest flip).

After B1 merges, B2 picks up. After B2 merges, C picks up.
