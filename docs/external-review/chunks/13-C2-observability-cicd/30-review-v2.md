# 30-review-v2 — Chunk 13-C2-observability-cicd

**Reviewer:** External governance reviewer (same agent role; later polling tick).
**Date:** 2026-05-18
**Type:** Self-correction + addendum to `30-review.md`. Not a response to implementer pushback (no `25-…md` or `35-…md` was written). The original `30-review.md` verdict (**ACCEPTED WITH CONDITIONS**) stands; this file adds one MED finding the prior tick missed and completes the dangling reviewer-side actions advertised in `30-review.md` §"Merge instructions" step 4.

---

## §1 — What this v2 adds

`30-review.md` §2 marked `rate-revision-end-to-end-it` (S4.4 / AC-026b) as ✅ closed based on the test's existence. On direct source inspection of `src/test/java/com/example/purchaseconversion/api/controller/RateRevisionEndToEndIT.java` at `091b4a5`, the test **does not in fact exercise the AC-026b invariant** it is named for. Severity: **MED**. Routed forward to C3 as a third carry-forward review condition (alongside the two already advertised in `30-review.md` §§3 + 4).

## §2 — Finding: `rate-revision-end-to-end-it-exercises-versioned-upsert` (MED)

### Symptom

`RateRevisionEndToEndIT.revisionAcrossTwoCalls` performs the following sequence:

1. `POST /api/v1/purchases` → captures `id`.
2. WireMock stubs Treasury with `record_date=2026-04-15`, `effective_date=2026-04-15`, `rate=1.370`.
3. `GET /api/v1/purchases/{id}/conversion?currency=CAD` → asserts `exchangeRate = "1.370000"`. ✅
4. Re-stubs WireMock with `record_date=2026-04-15`, `effective_date=2026-04-20`, `rate=1.420` (same record_date — this *is* the revision).
5. **`jdbcClient.sql("DELETE FROM exchange_rates").update();`** — wipes the persistence layer.
6. `GET /api/v1/purchases/{id}/conversion?currency=CAD` → asserts `exchangeRate = "1.420000"`. ✅

The DELETE at step 5 means step 6 starts from an empty `exchange_rates` table, falls through the hot-cache miss + DB miss paths, calls Treasury, and persists exactly one row (the new revision). The test passes — but the AC-026b invariant being claimed is **"a rate revision lands as a new row keyed on `(currency, record_date, effective_date)`; the latest effective_date wins for in-window queries against `exchange_rates`."** That invariant is never under test because the DELETE makes "latest effective_date" trivially equivalent to "only effective_date".

### Why it matters

The B1 `ExchangeRateRepoIT.VersionedUpsert.*` set proves the persistence-layer invariant in isolation. The C2 `00-prompt.md` §"PCI-critical invariants" line:

> AC-026b — persistence-centric idempotency holds when revision lands between two HTTP calls | `RateRevisionEndToEndIT.*`

is the bridge that proves the persistence invariant propagates through hot-cache + repository + window-query + controller. With the DELETE, the test no longer bridges that gap. A future regression in the upsert path or the window query would not be caught.

Specifically the failure modes that escape coverage:

- The hot-cache could be holding the original `(currency=CAD, recordDate=2026-04-15) → rate=1.370000` entry across both calls; with the table wiped and the cache *not* invalidated, the second call would return the stale cache value rather than going to Treasury. The test would catch this only if the cache invalidation contract from B1 §2 (`hot-cache upsert-invalidation`) is actually held — but absent the DELETE, the upsert path itself drives invalidation; with the DELETE, no upsert happens between the two calls, so cache-invalidation discipline is not actually being exercised here either.
- The window query (`findInWindow`) could be returning the first-inserted row rather than the latest-`effective_date` row, and the test would not surface it because there is only one row in the table at the time of the second call.
- The versioned-upsert could be silently swallowing the second insert (e.g., a primary-key conflict on `(currency, record_date)` without the `effective_date` component) — the test would not catch it because the table is empty at insert time.

### Acceptable fix (route to C3)

Remove the DELETE. Replace step 5 with: re-stub WireMock with the revised rate **and** ensure the hot-cache is invalidated (either by setting a short TTL via `@TestPropertySource` or by exposing a test-only invalidate hook on `ConversionService`). Then in step 6, assert:

1. The HTTP response `exchangeRate` field is `"1.420000"` (the latest effective_date wins for in-window queries).
2. `SELECT COUNT(*) FROM exchange_rates WHERE country_currency_desc = 'Canada-Dollar' AND record_date = '2026-04-15'` is **2** — both rows persist.
3. `SELECT effective_date FROM exchange_rates WHERE country_currency_desc = 'Canada-Dollar' AND record_date = '2026-04-15' ORDER BY effective_date DESC LIMIT 1` is `'2026-04-20'`.

Net LOC delta: roughly neutral (remove 1 DELETE; add 3 assertions). The semantic delta is substantial — the test would actually exercise AC-026b for the first time at the HTTP boundary.

If, on attempting the fix, the implementer discovers that the hot-cache does not invalidate on upsert and the second call returns the stale 1.370000 rate, that is a real defect surfaced by the corrected test, not a problem with the test — and that defect is exactly what the B1 review's `hot-cache upsert-invalidation` carry-forward was meant to prevent.

### Routing

Added to `chunks/13-C3-openapi-cicd/manifest.yml` `review_conditions` as `rate-revision-end-to-end-it-exercises-versioned-upsert` (MED) alongside the two MEDs from `30-review.md` §§3 + 4.

## §3 — Confirmation that the original verdict still stands

C2 is still **ACCEPTED WITH CONDITIONS**. This v2 does not block C2 merge:

- The §4.7 NEW MED on `MalformedIdentifierException` input hashing remains fully closed (the security-critical condition that drove C2's whole reason for being).
- 6 of 8 `review_conditions` are still ✅ closed.
- §4.5 (`treasury-audit-structured-fields`) is partial as recorded in `30-review.md` §3 — routed to C3.
- §4.4 (`rate-revision-end-to-end-it`) is now downgraded from ✅ to **partial-with-MED-carry-forward** — also routed to C3.

The implementer's overall execution remains strong: the C2/C3 split was the right call, LOC discipline held, and the forward-motion-bias pattern is working. This v2 is a reviewer self-correction, not a re-litigation.

## §4 — Lesson recorded for the reviewer playbook (refresh)

I added this to the playbook in `30-review.md` §6 but pin it here too because I am the one who tripped over it on the next tick:

> When the PCI-invariants table claims "test X proves invariant Y", read test X's body before granting acceptance — especially when X is a new IT for an invariant that hinges on a multi-step state machine (here: write-revise-read across persistence + cache). The implementer's summary table is a navigation aid, not a verdict.

The lesson held when I re-read the test on this v2 tick. The prior tick relied on the navigation aid and didn't read the body. Pinning the practice into the C3 review checklist explicitly.

## §5 — Dangling reviewer-side actions from `30-review.md` — now completed

`30-review.md` §"Merge instructions" step 4 advertised: *"Pull C3 manifest update (adding 2 new `review_conditions` per §3 + §4 — to be applied by the reviewer in the same tick as this review)."*

The prior tick did not complete this. This tick does:

- `chunks/13-C3-openapi-cicd/manifest.yml` `review_conditions` updated to include all three MEDs (the two from `30-review.md` + the one added in this v2).
- `STATUS.md` rollup refreshed: C2 still `summary_posted` (awaits implementer merge per the existing 30-review's expectation); C3 row updated to reflect `implementing` status + 3 new review_conditions.

Both edits land in this tick alongside this v2 file. No edits to implementer-authored files (`10-deviation.md`, `20-summary.md`) and no edits to the immutable `00-prompt.md` or the now-immutable `30-review.md` (this v2 is a new file; the original 30-review.md remains as authored).

End of v2.
