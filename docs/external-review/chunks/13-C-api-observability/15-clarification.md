# 15-clarification — Chunk 13-C-api-observability

**Author:** External governance reviewer
**Date:** 2026-05-17
**Type:** Reviewer amendment to `00-prompt.md`. Adds one mandatory intake item carried over from `chunks/13-A2-application/30-review.md` §5 + §6. The original `00-prompt.md` remains immutable; this file is the binding extension.

---

## One new mandatory item for this chunk

Tracked in `chunks/13-A2-application/manifest.yml` as `review_conditions: [api-layer-currency-input-hashing-on-emit]`. Lands **in Chunk C's PR** unless explicitly deferred via a `10-deviation.md` raised here with reviewer sign-off.

---

### Intake Item — `api-layer-currency-input-hashing-on-emit` (LOW)

**Source:** `chunks/13-A2-application/30-review.md` §5.

**Context.** A2's `InvalidCurrencyException` carries the raw unresolved currency input string as a constructor parameter (`InvalidCurrencyException.java:23`). The exception's javadoc explicitly flags: *"It is NOT logged in plain text — log emission must use structured event ... with the value passed through DescriptionHasher."* This responsibility was intentionally deferred to the API / observability layer (i.e., this chunk).

The deferral is correct in principle (the application layer should not own log redaction policy), but it creates a contract obligation on Chunk C: the `@RestControllerAdvice` mapping for `InvalidCurrencyException` must not emit the raw currency string in logs, metrics, or response bodies. Without this, a malformed-input attacker can flood logs with their chosen strings (audit-log noise) or, worst case, smuggle CHD-shaped content into the audit destination (which is categorised connected-to per `docs/security/pci-scope-and-cde.md §2` — see also G8-P0-2 in `chunks/13-A1-domain/10-deviation.md`).

**Required change in this chunk's PR.**

1. **`@RestControllerAdvice` mapping for `InvalidCurrencyException`** must:
   - Pass the carried currency string through `DescriptionHasher` (the HMAC-SHA-256 with `vN:` prefix mechanism defined in `docs/security/logging-monitoring-pci.md §5`) before any log or metric emission.
   - Never log the raw value at any level (TRACE / DEBUG / INFO / WARN / ERROR). The advice's structured-event emission carries `currency.hash` + `currency.length` only — the same redaction shape as `descriptionHash` / `descriptionLength` from M5.
   - In the `application/problem+json` response body's `details` map: either echo only the hashed form (`details.currency.hash = "v1:..."`), or omit the value entirely with `details.reason = "unknown-currency"` (response body is a 400 client-facing surface; the response itself is not stored in audit logs but is reflected back to the client).

2. **Test (boundary / PCI guard layer per the existing prompt §2 matrix).** A new test method on the existing `LoggingPiiGuardTest` class (or its API-layer equivalent) that:
   - Submits a POST with a currency value of `"4242 4242 4242 4242"` (a Luhn-valid PAN test fixture — the same shape used by `ContentGuardEncodedTest`).
   - Asserts the response body's `details.currency` field is either absent or hashed.
   - Asserts the application log emission for the request contains no instance of the raw `"4242"` substring at any log level.
   - The fixture should also exercise a Unicode-confusable variant per AC-010e (`"４２４２ ４２４２ ４２４２ ４２４２"`) since `InvalidCurrencyException` is unicode-input-shaped — the `ContentGuard` already handles `description` field NFKC; the currency-input path needs the equivalent.

3. **PCI invariants table extension.** Add a row to the chunk's `00-prompt.md` "PCI-critical invariants verified at this merge" matrix:

   | Invariant | Test |
   |---|---|
   | API-layer redaction: `InvalidCurrencyException` currency input is hashed before any log emission | `LoggingPiiGuardTest.invalidCurrencyExceptionEmitsHashedCurrencyOnly` |

   The chunk's `20-summary.md` records this as an addition to the existing "PCI-critical invariants" table.

**Verification at this chunk's 30-review.** Reviewer will:
- Read the `InvalidCurrencyException` `@RestControllerAdvice` mapping and verify the hash call + no raw-value log statement.
- Read the new test method's assertions.
- Read the `application/problem+json` response shape in the OpenAPI spec (M6) to confirm `details.currency` is the hashed form or omitted.

**Disposition.** Mandatory for this chunk. Cost is small (≤ 10 LOC mapping + ≤ 20 LOC test). The failure mode is real (audit-log poisoning + potential connected-to scope expansion if CHD-shaped input is logged plaintext).

---

## Items NOT changed by this clarification

- The chunk's existing scope (M4 + M5 + M6 + M7 — controllers, `ContentGuard`, rate-limit filter, observability wiring, OpenAPI, CI/CD), the PCI-critical invariants matrix (existing 9 rows), the out-of-scope list, branching/PR discipline, and the prompt's hard constraints are all unchanged.
- The existing observability + logging redaction requirements (no `description` in logs, HMAC `vN:` prefix on hashes, etc.) per `00-prompt.md` §M5 are unchanged — this intake item is **additive**: extends the redaction discipline from the `description` field to the `currency` field on the `InvalidCurrencyException` error path.
- LOC cap (≤ ~1,500 target, ≤ ~1,800 hard) is unchanged. The intake item is small and fits comfortably within the cap.
- Rollback class A (code rollback) is unchanged.

## How to record this in the chunk's `20-summary.md`

Add the new invariant row to the existing "PCI invariants table" subsection. The chunk's `30-review.md` will use that table row + the per-test assertions to verify closure.
