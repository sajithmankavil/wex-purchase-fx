# Functional Requirements

> Derived from `source-requirements.md` and reconciled with `AGENT_PROJECT_INSTRUCTIONS.md` on 2026-05-14. Every FR has an ID, owner, priority, source citation, explicit inputs/outputs, validation rules, and acceptance-criteria mapping. Ambiguities are not silently resolved here — they are tracked in `open-questions.md`.

| ID | Priority | Owner | Source |
|---|---|---|---|
| FR-001 | P0 | Backend | Source §Requirement #1 |
| FR-002 | P0 | Backend | Implicit — required to enable FR-003 |
| FR-003 | P0 | Backend | Source §Requirement #2 |
| FR-004 | P1 | Backend | Implicit — production-grade hygiene |
| FR-005 | P1 | Backend | Implicit — operability |
| FR-006 | P2 | Backend | Implicit — discoverability |

Priority: **P0** required for case-study acceptance; **P1** production-grade hygiene strongly implied; **P2** quality enhancement.

---

## FR-001 — Create (store) a purchase

**Trigger.** `POST /api/v1/purchases` (REST/JSON; ratified in ADR-0001).

**Inputs.**

| Field | Type | Required | Validation |
|---|---|---|---|
| `description` | string | yes | non-blank after trim; length ≤ 50 (UTF-16 code units, see OQ-005); must not match the **PAN-pattern guard** (NFR-015) |
| `transactionDate` | date | yes | ISO-8601 `YYYY-MM-DD`; not in the future at request time (assumption A-002; subject to grill — OQ-001) |
| `amountUsd` | decimal (string in JSON) | yes | numeric, strictly > 0; scale exactly 2 (no implicit rounding of inbound payload — AC-008); range bounded to `DECIMAL(19,2)` |

**Outputs (success).** `201 Created`; body with server-assigned `id`; `Location: /api/v1/purchases/{id}`.

`id` is server-generated, globally unique, opaque to clients, not enumerable. Working assumption: **UUID v7** (A-005); ULID is a documented alternative. Final choice in ADR-0002.

**Outputs (failure).**

- `400 Bad Request` for syntactic or constraint violations (validation errors); RFC 9457 Problem Details body with `errorCode` + `details.errors[]`.
- `400 PAN_PATTERN_DETECTED` if the `description` matches a PAN-like pattern (Luhn-valid 13–19-digit sequence with optional separators).
- `415 Unsupported Media Type` if content type ≠ `application/json`.
- `422 Unprocessable Entity` for semantically valid JSON failing a business rule (e.g., `FUTURE_DATE`).

**Persistence.** Durable before `201` returns. System of record. Amount stored as `DECIMAL(19,2)`, never `FLOAT`/`DOUBLE`. `transactionDate` stored as a date (no time, no zone).

**Idempotency (P1).** Optional `Idempotency-Key` header. Replay with same key + same payload returns the original `201`. Same key + different payload returns `409 IDEMPOTENCY_CONFLICT`. See OQ-009.

**Absence-of-key consequence (Phase-2 grill G-P1-8; AC-001b).** Without an `Idempotency-Key` header, two identical `POST /api/v1/purchases` requests produce **two** distinct purchases with **two** distinct server-assigned ids. Clients that want replay-safe semantics must use the header. For non-case-study production deployments, OQ-009 escalates to BLOCKING.

**Acceptance criteria.** AC-001..AC-010.

---

## FR-002 — Retrieve a stored purchase by id

**Trigger.** `GET /api/v1/purchases/{id}`.

**Outputs (success).** `200 OK`; body fields `id`, `description`, `transactionDate`, `amountUsd`.

**Outputs (failure).** `404 PURCHASE_NOT_FOUND` if no purchase exists; `400 MALFORMED_IDENTIFIER` if `id` is not a parseable identifier.

**Acceptance criteria.** AC-011..AC-013.

---

## FR-003 — Retrieve a purchase converted to a target currency

**Trigger.** `GET /api/v1/purchases/{id}/conversion?currency={currencyIdentifier}`.

**Inputs.**

- Path parameter `id` (existing purchase).
- Query parameter `currency` (string) — accepts **either** Treasury `country_currency_desc` (e.g., `Canada-Dollar`) **or** ISO 4217 code (e.g., `CAD`). The service resolves an ISO 4217 input to exactly one canonical `country_currency_desc` via a curated alias table. Unknown/ambiguous identifiers return `400 INVALID_CURRENCY`. (A-001.)

**Rate-selection rule.**

Let `D = transactionDate`. Among locally persisted Treasury rates for the resolved `country_currency_desc`:

1. Select rates with `record_date ≤ D` **and** `record_date ≥ D.minusMonths(6)` (calendar-month subtraction with end-of-month clamp, inclusive both ends — A-003).
2. If non-empty, choose the rate with the maximum `record_date`. Tie-breaker on duplicate `record_date`: see OQ-002 (proposed: stable order by `(record_date desc, id asc)`).
3. If empty, attempt Treasury Fiscal Data API lookup (single-flight per `(country_currency_desc, D)`), persist any returned rates, and re-evaluate locally.
4. If still empty, return `422 CONVERSION_RATE_NOT_AVAILABLE`.

**Outputs (success).** `200 OK`:

```json
{
  "id": "01HXZ7R7R7K7V9R8Z7R5Q7A1BC",
  "description": "Office supplies",
  "transactionDate": "2026-05-14",
  "amountUsd": "123.45",
  "targetCurrency": "Canada-Dollar",
  "exchangeRate": "1.3700",
  "exchangeRateDate": "2026-03-31",
  "convertedAmount": "169.13"
}
```

`exchangeRate` is the decimal value as stored from Treasury (full Treasury precision preserved as string). `targetCurrency` is the canonical `country_currency_desc` regardless of input form.

**Outputs (failure).**

- `404 PURCHASE_NOT_FOUND` — `id` not found.
- `400 INVALID_CURRENCY` — `currency` is empty, malformed, not resolvable to a known currency, or alias-table-known but rejected by Treasury as unknown (AC-021b; OQ-016).
- `422 CONVERSION_RATE_NOT_AVAILABLE` — no eligible rate in window (literal source rule). **Terminal answer.** Returned when the rule has been correctly applied with all required data available; covers (a) no local rate and Treasury returns 200 with empty list (AC-020b), (b) all returned rates fall outside the 6-month window (AC-022b).
- `503 UPSTREAM_UNAVAILABLE` with `Retry-After` — Treasury unreachable beyond retry/CB budget **and** no eligible local rate exists. **Inability-to-apply answer.** Distinct from `CONVERSION_RATE_NOT_AVAILABLE`.
- `502 UPSTREAM_BAD_RESPONSE` — Treasury returned a payload that fails schema validation, or a rate value outside sanity bounds (`≤ 0` or `> 10^9`; AC-024b).

**Error-code decision table (Phase-2 grill G-P0-3; OQ-018).**

| Local rate eligible? | Treasury reachable? | Treasury response | Returned code |
|---|---|---|---|
| Yes | n/a | n/a (not called) | `200 OK` |
| No | Yes | `200 OK`, ≥ 1 rate in window | `200 OK` |
| No | Yes | `200 OK`, empty list | `422 CONVERSION_RATE_NOT_AVAILABLE` (AC-020b) |
| No | Yes | `200 OK`, all rates outside window | `422 CONVERSION_RATE_NOT_AVAILABLE` (AC-022b) |
| No | Yes | 4xx "unknown currency" for alias-known input | `400 INVALID_CURRENCY` + `currency_alias_drift_detected` (AC-021b) |
| No | Yes | Schema-invalid payload | `502 UPSTREAM_BAD_RESPONSE` (AC-024) |
| No | Yes | Rate value outside sanity bounds | `502 UPSTREAM_BAD_RESPONSE` (AC-024b) |
| No | No (timeout / CB open after budgets exhausted) | n/a | `503 UPSTREAM_UNAVAILABLE` (AC-023) |

**Persistence on conflict (Phase-2 grill G-P0-4; A-018; AC-026b).** Storage key is `(country_currency_desc, record_date, effective_date)`. Treasury revisions of an existing `(country_currency_desc, record_date)` are stored as new version rows; previously-persisted rows are not mutated. The eligible-rate query selects max(`effective_date`) for the eligible `record_date`.

**Amount arithmetic.** `convertedAmount = amountUsd × exchangeRate`, computed in `BigDecimal` with intermediate scale ≥ 12, then rounded to scale 2 using **`HALF_UP`** (A-004; chosen per `AGENT_PROJECT_INSTRUCTIONS.md` §5; trade-off vs HALF_EVEN documented in ADR-0001).

**Rate orientation (Phase-2 grill G-P0-1; OQ-017 BLOCKING-for-Phase-3-exit).** The working formula above assumes Treasury's `exchange_rate` is `foreign-currency-units per USD`. This convention must be empirically verified against the live Treasury dataset for at least three currencies of known canonical cross-rate before ADR-0001 is signed. If Treasury documents per-record orientation, the conversion direction follows the published convention.

**Acceptance criteria.** AC-014..AC-027.

---

## FR-004 — Operability: health/readiness/version

`/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/info`. Readiness rule: DB reachable is required; Treasury degradation alone does **not** flip readiness DOWN (FR-001 and FR-002 remain serviceable).

**Acceptance criteria.** AC-028..AC-030.

---

## FR-005 — Observability: logs, metrics, traces

Structured JSON logs with `traceId`, `spanId`, `correlationId`. Standard event names per `AGENT_PROJECT_INSTRUCTIONS.md` §11. Metrics per same §11. OpenTelemetry traces across inbound HTTP, DB, and Treasury client. No PII in log bodies — `description` logged only as `length` + HMAC-SHA-256 digest.

**Acceptance criteria.** AC-031..AC-034.

---

## FR-006 — API discoverability

OpenAPI 3.1 at `/v3/api-docs`; Swagger UI at `/swagger-ui.html`.

**Acceptance criteria.** AC-035..AC-036.

---

## Out-of-scope for v1

- Authentication/authorization at the application layer — service runs behind a trusted gateway in real deployments (A-007, OQ-010).
- Multi-tenant isolation, update/delete of purchases, bulk import, webhooks/event publishing, list/pagination, non-USD source amounts.
- Performance-test automation (explicitly excluded by source).

