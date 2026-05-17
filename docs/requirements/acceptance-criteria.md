# Acceptance Criteria

> Behaviour-driven, deliberately testable. Reconciled with `AGENT_PROJECT_INSTRUCTIONS.md` on 2026-05-14: resource path is `/api/v1/purchases`, amount field is `amountUsd`, rounding is `HALF_UP`, error code for the source rule is `CONVERSION_RATE_NOT_AVAILABLE`, errors follow RFC 9457 Problem Details with extension members `errorCode` and `details`.
>
> **Phase-2 (Requirements Grill) additions, 2026-05-14:** AC-001b, AC-010c, AC-018b, AC-019b, AC-020b, AC-021b, AC-022b, AC-024b, AC-026b, AC-027d, AC-032b, AC-T-5. See `docs/planning/requirements-grill.md` §7.
>
> **Phase-4 (Design Grill) additions, 2026-05-17:** AC-010d (encoded-PAN guard), AC-021c (alias-table-drift reverse case), AC-027e (single-flight quarter-window deduplication); AC-026b refined to persistence-centric wording; AC-T-3 fixture set widened. See `docs/planning/design-grill.md` §5.
>
> **Phase-8 (PCI Security Grill) additions, 2026-05-17:** AC-010e (Unicode-confusable PAN; NFKC pre-pass) and AC-T-6 (rate-limit before ContentGuard). See `docs/security/pci-security-grill.md`.

---

## FR-001 — Create purchase

### AC-001 — happy path
- **Given** `{"description":"Office supplies","transactionDate":"2026-04-01","amountUsd":"12.34"}`
- **When** `POST /api/v1/purchases`
- **Then** `201 Created` with server-assigned `id`, echoed input fields, `Location: /api/v1/purchases/{id}`, durably persisted.

### AC-002 — description boundary (50 chars accepted)
- Exactly 50 UTF-16 code units → `201`.

### AC-003 — description 51 chars rejected
- `400 Bad Request`, `errorCode=VALIDATION_ERROR`, `details.errors[].field="description"`, `details.errors[].code="LENGTH"`.

### AC-004 — blank description rejected
- `400`, code `REQUIRED`.

### AC-005 — malformed `transactionDate` rejected
- `"2026-13-01"`, `"04/01/2026"`, empty → `400`, code `FORMAT`.

### AC-006 — future-dated transaction
- `transactionDate` > server today (UTC) → `422`, `errorCode=FUTURE_DATE`. (A-002; subject to grill — OQ-001.)

### AC-007 — non-positive amount
- `0.00`, negative, absent → `400`, code `POSITIVE_REQUIRED`/`REQUIRED`.

### AC-008 — amount scale > 2 rejected
- `"12.345"` → `400`, code `SCALE_EXCEEDED`. No silent rounding of inbound payload.

### AC-009 — non-numeric / NaN / Infinity amount
- `"twelve"`, `"NaN"`, `"Infinity"` → `400`, code `FORMAT`.

### AC-010 — durability across restart
- After `201`, the same `id` is retrievable via `GET /api/v1/purchases/{id}` following an in-process restart with file-mode H2.

### AC-010b — PAN-pattern guard
- A `description` containing a Luhn-valid 13–19-digit sequence (with optional spaces/dashes) → `400`, `errorCode=PAN_PATTERN_DETECTED`. Audit event `purchase_validation_failed` emitted with the reason; the rejected payload is **not** logged.

### AC-010c — Track-data shape guard (G-P0-5)
- A `description` containing magnetic-stripe track-data shape (e.g., `%B<digits>^<name>^<expiry>...?`, `;<digits>=<expiry>...?`) → `400`, `errorCode=PAN_PATTERN_DETECTED`, audit-logged; payload not logged. Defense-in-depth alongside the Luhn rule.

### AC-010d — Encoded-PAN guard (G4-P0-5; Phase 4)
- A `description` whose base64- (standard or URL-safe), hex-, or URL-encoded form decodes to a Luhn-valid 13–19-digit sequence is rejected as `400`, `errorCode=PAN_PATTERN_DETECTED`, `details.reason="luhn-encoded"`. The decoder pipeline runs as a pre-pass to the Luhn check (component-design.md §3.5). Audit event `purchase_validation_failed{reason=luhn-encoded}` emitted; payload not logged.

### AC-010e — Unicode-confusable PAN guard (G8-P0-3; Phase 8)
- A `description` containing a Luhn-valid 13–19-digit sequence written in **Unicode-confusable digits** (e.g., fullwidth digits `U+FF10..U+FF19` separated by ideographic spaces `U+3000`) is rejected as `400`, `errorCode=PAN_PATTERN_DETECTED`, `details.reason="luhn"`. The Unicode NFKC normalisation pre-pass in `ContentGuard.check` (component-design.md §3.5) maps the confusable digits to ASCII before the Luhn regex runs. Example: `４２４２　４２４２　４２４２　４２４２` is rejected with `reason="luhn"`. Stored `description` (if it had been valid) retains the original code points; only the guard check uses NFKC.

### AC-001b — POST without `Idempotency-Key` is not idempotent (G-P1-8)
- Two consecutive identical `POST /api/v1/purchases` requests with no `Idempotency-Key` header produce **two** distinct purchases with **two** distinct server-assigned `id`s, each returning `201`. Documented contract; `Idempotency-Key` (when offered, P1 — OQ-009) is the only way to obtain replay-safe semantics.

---

## FR-002 — Retrieve by id

### AC-011 — happy path
- `GET /api/v1/purchases/{id}` for an existing id → `200` with `id`, `description`, `transactionDate`, `amountUsd`.

### AC-012 — unknown id
- `404`, `errorCode=PURCHASE_NOT_FOUND`, RFC 9457 body.

### AC-013 — malformed id
- Non-UUID/non-ULID shaped id → `400`, `errorCode=MALFORMED_IDENTIFIER`. Not `404`.

---

## FR-003 — Retrieve converted

### AC-014 — happy path, exact-date rate exists
- Stored `transactionDate=2026-04-01`; Treasury rate for `Canada-Dollar` with `record_date=2026-04-01` exists.
- `GET /api/v1/purchases/{id}/conversion?currency=Canada-Dollar` → `200`.
- Body includes `exchangeRate`, `exchangeRateDate=2026-04-01`, `targetCurrency="Canada-Dollar"`, `convertedAmount = round_half_up(amountUsd × exchangeRate, 2)`.

### AC-014b — happy path, ISO 4217 alias
- Same as AC-014 but `?currency=CAD` resolves via the alias table to `Canada-Dollar`. Response `targetCurrency` is the canonical `Canada-Dollar`.

### AC-015 — no exact-date rate; most recent within 6 months is used
- `transactionDate=2026-04-15`; rates exist at `record_date=2026-03-31` and `2025-12-31`. Selects `2026-03-31`.

### AC-016 — boundary: rate exactly on purchase date preferred
- Rate `record_date=2026-04-15` is preferred over `2026-03-31` (max `record_date ≤ transactionDate`).

### AC-017 — boundary: 6 months minus 1 day before — eligible
- `transactionDate=2026-04-15`; only rate `record_date=2025-10-16` exists. Eligible (within 6 months) → conversion succeeds.

### AC-018 — boundary: exactly 6 calendar months before — eligible
- `transactionDate=2026-04-15`; only rate `record_date=2025-10-15` exists. `transactionDate.minusMonths(6) = 2025-10-15`; inclusive `≥` check → eligible → conversion succeeds.

### AC-018b — boundary: EOM-clamp lower bound — eligible (G-P0-2)
- `transactionDate=2026-08-31`; `transactionDate.minusMonths(6) = 2026-02-28` (calendar-month subtraction with end-of-month clamp; Feb 2026 has 28 days). Only rate `record_date=2026-02-28` exists. Inclusive `≥` check → eligible → conversion succeeds. The eligible window for this date is 184 days; reviewers must not silently substitute a 183-day rule.

### AC-019 — boundary: just outside 6 months — ineligible
- `transactionDate=2026-04-15`; only rate `record_date=2025-10-14` → `422`, `errorCode=CONVERSION_RATE_NOT_AVAILABLE`.

### AC-019b — boundary: just outside EOM-clamp — ineligible (G-P0-2)
- `transactionDate=2026-08-31`; only rate `record_date=2026-02-27` → `422`, `errorCode=CONVERSION_RATE_NOT_AVAILABLE`. One day below the clamped lower bound is rejected.

### AC-020 — no rate at all for currency in window
- `422`, `errorCode=CONVERSION_RATE_NOT_AVAILABLE`. Message matches the literal source rule.

### AC-020b — Treasury reachable, returns 200 with empty list, no local rate (G-P0-3)
- Treasury endpoint is reachable; local DB has no rate; Treasury returns `200 OK` with an empty result set for the queried currency-and-date window. Service returns `422`, `errorCode=CONVERSION_RATE_NOT_AVAILABLE`. **Must not** be `503 UPSTREAM_UNAVAILABLE`.

### AC-021 — unknown currency identifier
- `currency=ZZZ` or `currency=Atlantis-Pearl` → `400`, `errorCode=INVALID_CURRENCY`. Distinct from `CONVERSION_RATE_NOT_AVAILABLE`.

### AC-021b — Treasury rejects an alias-table-known currency (G-P0-3, OQ-016)
- `currency=CAD` resolves via the alias table to `Canada-Dollar`; Treasury responds 4xx "unknown currency" (alias-table drift case). Service returns `400`, `errorCode=INVALID_CURRENCY` to the client and emits `currency_alias_drift_detected` audit event with the resolved canonical that Treasury rejected.

### AC-021c — Treasury renames a descriptor (G4-P1-8; Phase 4)
- The alias table resolves `currency=EUR` to canonical `Euro Zone-Euro`. Treasury returns rates whose `country_currency_desc` is `Eurozone-Euro` (a renamed descriptor). Persistence is under Treasury's truth (`Eurozone-Euro`); the response carries `targetCurrency=Eurozone-Euro`; `currency_alias_drift_detected` audit event is emitted with `{ resolved=Euro Zone-Euro, treasuryReturned=Eurozone-Euro }`. The alias table is updated by PR in the next release cycle.

### AC-022 — Treasury unreachable with eligible local rate → success
- Treasury client returns timeout/CB open; local DB has an eligible rate. Conversion **succeeds** (no upstream call required). `exchange_rate_cache_hit` event emitted.

### AC-022b — Treasury reachable, all returned rates outside window (G-P0-3)
- Treasury responds `200 OK` with rates whose `record_date` is uniformly `< transactionDate.minusMonths(6)`. Service persists what it can (forward usefulness) and returns `422`, `errorCode=CONVERSION_RATE_NOT_AVAILABLE`. **Must not** be `503 UPSTREAM_UNAVAILABLE`.

### AC-023 — Treasury unreachable with no eligible local rate
- After retries and CB budget exhausted, no eligible local rate exists → `503`, `errorCode=UPSTREAM_UNAVAILABLE`, `Retry-After` header set. **Must not** be `CONVERSION_RATE_NOT_AVAILABLE`.

### AC-024 — Treasury malformed payload
- Schema validation rejects upstream response → `502`, `errorCode=UPSTREAM_BAD_RESPONSE`. Never returns synthetic data.

### AC-024b — Treasury rate sanity rejection (G-P1-10)
- Treasury returns a record where `exchange_rate ≤ 0` **or** `exchange_rate > 10^9` → `502`, `errorCode=UPSTREAM_BAD_RESPONSE`. Persistence does **not** store the offending row. Audit event `treasury_api_failure{reason=rate_sanity}` emitted.

### AC-025 — HALF_UP rounding behaviour
- `amountUsd=1.00`, `exchangeRate=0.875` → `convertedAmount=0.88` (HALF_UP).
- `amountUsd=1.00`, `exchangeRate=0.865` → `convertedAmount=0.87` (HALF_UP).
- `amountUsd=1.00`, `exchangeRate=0.864999` → `convertedAmount=0.86` (no half-tie).

### AC-026 — large amount preserves precision
- `amountUsd=99999999.99`, `exchangeRate=0.918237` → no overflow; intermediate BigDecimal scale ≥ 12; final scale exactly 2.

### AC-026b — Treasury republishes a rate for an existing `record_date` (G-P0-4; Phase-4 refined per G4-P1-7)
- Local DB already has `(Canada-Dollar, 2026-03-31, exchange_rate=1.370000, effective_date=2026-03-31)`. Treasury republishes the same `(country_currency_desc, record_date)` with `exchange_rate=1.371100, effective_date=2026-04-02` (revision). After upsert, the `exchange_rates` table contains **two rows** for `(Canada-Dollar, 2026-03-31)`: the original (preserved, unchanged) and the revision. The eligible-rate query for any subsequent conversion picks the row with max `effective_date`. Assertion is row-count + value-of-original; the API response shape is a downstream consequence (responses are regenerated on each request and naturally reflect the latest revision).

### AC-027 — unknown purchase id on conversion endpoint
- `404`, `errorCode=PURCHASE_NOT_FOUND`. Identical body shape to AC-012.

### AC-027b — concurrent identical conversion requests
- Two concurrent requests for the same `(country_currency_desc, transactionDate)` cache key cause **at most one** Treasury upstream call (single-flight / per-key lock). Verified by integration test with a coordinated barrier.

### AC-027c — case-insensitive currency lookup
- `?currency=canada-dollar`, `?currency=Canada-Dollar`, `?currency=CANADA-DOLLAR` all resolve identically. ISO codes resolved similarly.

### AC-027d — concurrent conversions under in-flight Treasury fetch (G-P1-11)
- Two concurrent `GET /api/v1/purchases/{id}/conversion?currency=X` requests for the same purchase, served while a single-flight Treasury fetch is in flight, **either** both succeed with the *same* `exchangeRate` **or** both fail with the same `errorCode`. Split outcomes (one succeeds, one fails) are forbidden. Verified by integration test with a coordinated barrier on the WireMock-backed Treasury.

### AC-027e — single-flight quarter-window deduplication (G4-P0-1; Phase 4)
- Two concurrent conversion requests for two different purchases (different `id`s) with `transactionDate` values that fall in the **same Treasury quarter** (e.g., `2026-04-01` and `2026-06-30` both target Q2-2026) cause **at most one** Treasury upstream call. The single-flight gate keys on `(country_currency_desc, treasury_quarter_end)`. Verified by integration test with a coordinated barrier on the WireMock-backed Treasury.

---

## FR-004 — Operability

### AC-028 — liveness
- `/actuator/health/liveness` → `200 UP` if JVM healthy regardless of DB or Treasury.

### AC-029 — readiness
- `/actuator/health/readiness` → `200 UP` only when DB reachable. Treasury degradation does **not** flip readiness DOWN.

### AC-030 — version info
- `/actuator/info` → build artifact name, version, git sha, build time.

---

## FR-005 — Observability

### AC-031 — JSON logs with correlation
- Request with `X-Correlation-Id: abc` logs an entry containing that ID. Absent → service generates one and echoes in response header.

### AC-032 — no PII in logs (CHD/PAN/description body)
- For any successful FR-001, no log line contains the verbatim `description` value. Only `description.length` and a non-reversible HMAC digest are permitted. Asserted by `LoggingPiiGuardTest`.

### AC-032b — logging HMAC key sourced from configuration (G-P1-4)
- In `prod` / `staging` profiles, the service refuses to start if `WEX_LOG_HASH_KEY` is unset. The emitted digest carries a `vN:<hex>` version prefix so that pre- and post-rotation digests are distinguishable. In `local` / `test`, a documented no-op fallback key may be used and the digest carries the prefix `v0:`. Asserted by `LoggingHashKeyTest`.

### AC-033 — metrics surface
- `GET /actuator/prometheus` returns RED counters per route, conversion-outcome counters, and cache hit/miss counters per `AGENT_PROJECT_INSTRUCTIONS.md` §11.

### AC-034 — traces propagated
- End-to-end test with WireMock-Treasury shows a single trace with ≥ 3 spans and W3C `traceparent` propagation to the WireMock server.

---

## FR-006 — API discoverability

### AC-035 — OpenAPI served
- `GET /v3/api-docs` → valid OpenAPI 3.1 JSON; schema-lints clean (Spectral).

### AC-036 — Swagger UI
- `GET /swagger-ui.html` loads and lists all endpoints.

---

## Cross-cutting test-suite obligations

- **AC-T-1** Every FR has ≥ 1 unit test and ≥ 1 integration test.
- **AC-T-2** Every error code has at least one negative-path test asserting status, `errorCode`, RFC 9457 `type`/`title`, and `details` shape.
- **AC-T-3** Treasury contract tests cover happy path, empty result, malformed payload, 5xx, slow response, timeout, rate-with-trailing-zero (`148.0` persists and serialises as `148.000000` per Phase-4 G4-P0-3), rate-with-leading-zero (`0.085`), high-precision rate (≥ 6 fractional digits), **zero rate** (rejected as `502 UPSTREAM_BAD_RESPONSE`), **negative rate** (rejected), **null rate** (schema-rejected), and rate-near-sanity-ceiling (≤ 10³⁰; Phase-4 G4-P1-3).
- **AC-T-4** Mutation testing (Pitest) ≥ 70 % on `domain` and `application` packages on each PR build.
- **AC-T-5** (G-P1-9) Every error response carries `Content-Type: application/problem+json`. Asserted in `ProblemDetailsContentTypeTest` as a cross-cutting test over the full error-code matrix.
- **AC-T-6** (G8-P0-1) Rate-limit rejections occur **before** the `ContentGuard` decoder pipeline executes. Asserted via the metric `contentguard.invocations.count` (Phase 13 implementation: increments only inside the advice; load test under sustained rate-limit saturation confirms the counter stays at zero while the rate-limiter rejects). CPU profile of `ContentGuard.check` under rate-limited load shows no calls.

