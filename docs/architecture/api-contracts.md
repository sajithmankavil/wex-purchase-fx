# API Contracts

> Wire-level interface for the WEX Purchase Currency Conversion Service.
> **Status:** Phase 3 (Architecture & Design Session), 2026-05-17.
> **Phase-4 grill refinements (2026-05-17):** `exchangeRate` normalised to scale 6 on the wire; `Retry-After` default 300 s; AC-021c and AC-027e referenced in the error matrix and decision table. See [design-grill.md](../planning/design-grill.md) §6.
> Canonical: OpenAPI 3.1 served at `/v3/api-docs`; Swagger UI at `/swagger-ui.html`. This document is the human-readable contract and ground truth on edge cases.

---

## 1. API principles

- **Versioned URIs.** All resource paths under `/api/v1/…`. Breaking changes require a new ADR, a deprecation window, and `/api/v2/…`.
- **REST/JSON only.** Single transport. Future media types (event-stream, gRPC) are out-of-scope v1.
- **Validation at the boundary.** Server-side, deny-by-default; strict typing on every field; bounded length.
- **Server-side authorization.** v1 has no app-layer auth (A-007); in production, identity is established at the upstream gateway (OQ-010 BLOCKING-for-prod). The server still treats every request as untrusted at the payload level.
- **RFC 9457 Problem Details for every error,** with `Content-Type: application/problem+json` (AC-T-5) and `errorCode` + `details` extension members.
- **No retroactive mutation.** Once a `convertedAmount` is served, the rate it was computed from is preserved (versioned persistence; D-3 / A-018).
- **Correlation.** Every request/response carries `X-Correlation-Id` (generated if absent). Logs and traces are joined on this id.

---

## 2. Endpoint summary

| Method | Path | Purpose | Auth (v1) | Auth (prod) | Status codes |
|---|---|---|---|---|---|
| POST | `/api/v1/purchases` | Create a purchase transaction (FR-001). | None | Established at gateway | 201, 400, 415, 422 |
| GET | `/api/v1/purchases/{id}` | Retrieve a stored purchase by id (FR-002). | None | Established at gateway | 200, 400, 404 |
| GET | `/api/v1/purchases/{id}/conversion?currency={…}` | Retrieve a stored purchase converted to a target currency (FR-003). | None | Established at gateway | 200, 400, 404, 422, 502, 503 |
| GET | `/actuator/health` | Aggregated health. | None | Platform | 200, 503 |
| GET | `/actuator/health/liveness` | JVM healthy? | None | Platform | 200, 503 |
| GET | `/actuator/health/readiness` | DB reachable, alias table loaded? Treasury degradation does **not** flip this. | None | Platform | 200, 503 |
| GET | `/actuator/info` | Build info, git sha, version. | None | Platform | 200 |
| GET | `/actuator/prometheus` | Metrics scrape. | None (local-only); platform-restricted in prod. | Platform | 200 |
| GET | `/v3/api-docs` | OpenAPI 3.1 JSON. | None | None | 200 |
| GET | `/swagger-ui.html` | Swagger UI. | None | Disabled in prod or restricted by gateway. | 200 |

---

## 3. POST `/api/v1/purchases` — create

### Request

```
POST /api/v1/purchases HTTP/1.1
Content-Type: application/json
X-Correlation-Id: 01HXZ7…  (optional; server echoes back; generated if absent)
Idempotency-Key: 9c1a…     (optional, P1; absence = non-idempotent — AC-001b)

{
  "description": "Office supplies",
  "transactionDate": "2026-05-14",
  "amountUsd": "123.45"
}
```

### Field rules

| Field | Type | Required | Validation |
|---|---|---|---|
| `description` | string | yes | UTF-8 in, UTF-16 code-unit length ≤ 50 (A-013, OQ-005), trim-non-blank. Rejected if PAN-pattern (Luhn 13–19 digits, optional separators — AC-010b) or track-data shape (`%B…?`, `;…?` — AC-010c). |
| `transactionDate` | string (ISO-8601 date) | yes | `YYYY-MM-DD`. No time, no zone. ≤ today (UTC). |
| `amountUsd` | string (decimal) | yes | Numeric, strictly > 0, scale exactly 2. No silent rounding; `"12.345"` → `400 SCALE_EXCEEDED`. No NaN/Infinity. Range bounded to `DECIMAL(19,2)`. |

`Content-Type` MUST be `application/json`. Anything else → `415 Unsupported Media Type`.

### Responses

**`201 Created`** — happy path. `Location: /api/v1/purchases/{id}` header set.

```
HTTP/1.1 201 Created
Content-Type: application/json
Location: /api/v1/purchases/01HXZ7R7R7K7V9R8Z7R5Q7A1BC
X-Correlation-Id: 01HXZ7…

{
  "id": "01HXZ7R7R7K7V9R8Z7R5Q7A1BC",
  "description": "Office supplies",
  "transactionDate": "2026-05-14",
  "amountUsd": "123.45"
}
```

**Error responses** — see §6.

### Acceptance criteria

AC-001..AC-010c. Notable: AC-001b (idempotency-absent semantics), AC-002 (50-char boundary), AC-008 (scale > 2 rejected, no silent rounding), AC-010 (durability across restart), AC-010c (track-data rejection).

---

## 4. GET `/api/v1/purchases/{id}` — retrieve

### Request

```
GET /api/v1/purchases/01HXZ7R7R7K7V9R8Z7R5Q7A1BC HTTP/1.1
X-Correlation-Id: 01HXZ7…
```

`{id}` MUST be a UUID v7 or ULID syntactic shape; otherwise `400 MALFORMED_IDENTIFIER`. Note: the server does not look up the value before checking shape — a malformed id never returns `404`.

### Responses

**`200 OK`**

```
{
  "id": "01HXZ7R7R7K7V9R8Z7R5Q7A1BC",
  "description": "Office supplies",
  "transactionDate": "2026-05-14",
  "amountUsd": "123.45"
}
```

**`400 MALFORMED_IDENTIFIER`** — id is not parseable.
**`404 PURCHASE_NOT_FOUND`** — no purchase with that id.

---

## 5. GET `/api/v1/purchases/{id}/conversion?currency={…}` — retrieve, converted

### Request

```
GET /api/v1/purchases/01HXZ7R7R7K7V9R8Z7R5Q7A1BC/conversion?currency=Canada-Dollar HTTP/1.1
X-Correlation-Id: 01HXZ7…
```

### `currency` resolution

| Input form | Example | Resolution |
|---|---|---|
| Treasury `country_currency_desc` (canonical) | `Canada-Dollar`, `Euro Zone-Euro`, `Japan-Yen` | Case-insensitive exact match against the alias table's canonical set. |
| ISO 4217 three-letter code | `CAD`, `EUR`, `JPY` | Resolves via the alias table to exactly one canonical descriptor. |
| Empty, blank, or junk | `""`, `"   "`, `"X"`, `"Atlantis-Pearl"` | `400 INVALID_CURRENCY`. |

The response always returns `targetCurrency` as the canonical Treasury descriptor regardless of input form (AC-014b, AC-027c).

**Eurozone caveat (Phase-3 prototype P-1):** Treasury's canonical descriptor is `Euro Zone-Euro` — note the **space** between "Euro" and "Zone." Clients submitting `Euro-Zone-Euro` (hyphen-only) would resolve as a Treasury descriptor miss; the alias table includes `Euro-Zone-Euro` as a secondary alias to keep ergonomics intact, while the response carries the canonical `Euro Zone-Euro`.

### Responses

**`200 OK`**

```
{
  "id": "01HXZ7R7R7K7V9R8Z7R5Q7A1BC",
  "description": "Office supplies",
  "transactionDate": "2026-05-14",
  "amountUsd": "123.45",
  "targetCurrency": "Canada-Dollar",
  "exchangeRate": "1.370000",
  "exchangeRateDate": "2026-03-31",
  "convertedAmount": "169.13"
}
```

Field notes:

- `exchangeRate` is a **decimal string normalised to scale 6** (Phase-4 grill G4-P0-3). Treasury's variable-scale publications (`"1.37"`, `"1.393"`, `"148.0"`, `"159.41"`) are normalised to scale 6 on persistence and surfaced at scale 6 on the response (e.g., `"148.000000"`). Clients writing strict equality assertions must use the scale-6 form.
- `exchangeRateDate` is the `record_date` of the selected rate. If a `(currency, record_date)` revision exists, the rate value carried is the max-`effective_date` value, but the response surfaces only `record_date` for stability of client interpretation.
- `convertedAmount = round_half_up(amountUsd × exchangeRate, 2)` (D-4 / D-6).

**Error responses** — see §6, particularly the decision table.

### Acceptance criteria

AC-014..AC-027d. Notable: AC-014b (ISO alias), AC-018b/AC-019b (EOM-clamp boundary), AC-020b/AC-021b/AC-022b (CONV-vs-UPSTREAM intermediate cases), AC-024b (rate sanity), AC-026b (revision behaviour), AC-027b/d (single-flight and concurrent consistency).

---

## 6. Error envelope (RFC 9457) and decision table

### 6.1 Envelope

Every error response has:

```
HTTP/1.1 <status> <reason>
Content-Type: application/problem+json
X-Correlation-Id: <id>

{
  "type": "https://wex.example/problems/<error-slug>",
  "title": "<human-readable title>",
  "status": <int>,
  "detail": "<human-readable detail, never includes a stack trace>",
  "instance": "<the request URI>",
  "errorCode": "<MACHINE_READABLE>",
  "details": { … structured context … }
}
```

`Content-Type: application/problem+json` is asserted by AC-T-5 across the full error-code matrix.

### 6.2 Error-code matrix

| `errorCode` | HTTP | When | `details` shape | AC |
|---|---|---|---|---|
| `VALIDATION_ERROR` | 400 | Syntactic or constraint violation on input. | `{ "errors": [ { "field": "...", "code": "REQUIRED|FORMAT|LENGTH|POSITIVE_REQUIRED|SCALE_EXCEEDED" } ] }` | AC-003..AC-009 |
| `PAN_PATTERN_DETECTED` | 400 | `description` matched PAN-Luhn or track-data shape. Rejected payload is **not** echoed. | `{ "reason": "luhn"|"track1"|"track2" }` | AC-010b, AC-010c |
| `MALFORMED_IDENTIFIER` | 400 | `{id}` not parseable as UUID v7 / ULID. | `{ "id": "..." }` | AC-013 |
| `INVALID_CURRENCY` | 400 | `currency` is empty, malformed, or alias-table-unknown — including the rare case where Treasury rejects an alias-table-known input (AC-021b; emits `currency_alias_drift_detected`). | `{ "currency": "..." }` | AC-021, AC-021b |
| `FUTURE_DATE` | 422 | `transactionDate` > today (UTC). | `{ "transactionDate": "..." }` | AC-006 |
| `PURCHASE_NOT_FOUND` | 404 | `{id}` is well-formed but no purchase exists. | `{ "id": "..." }` | AC-012, AC-027 |
| `CONVERSION_RATE_NOT_AVAILABLE` | 422 | **Terminal.** Rule applied with all data available; no eligible rate in window. | `{ "purchaseDate": "...", "targetCurrency": "...", "windowLower": "...", "windowUpper": "..." }` | AC-020, AC-020b, AC-022b |
| `UPSTREAM_BAD_RESPONSE` | 502 | Treasury payload failed schema validation OR contained a rate outside sanity bounds. | `{ "reason": "schema"|"rate_sanity"|"orientation_drift" }` | AC-024, AC-024b |
| `UPSTREAM_UNAVAILABLE` | 503 | **Inability.** Treasury unreachable beyond retry/CB budget AND no eligible local rate. `Retry-After: <seconds>` header set. | `{ "reason": "timeout"|"circuit_open"|"http_5xx" }` | AC-023 |
| `IDEMPOTENCY_CONFLICT` | 409 | Same `Idempotency-Key` with a different payload (P1). | `{ "key": "..." }` | OQ-009 |
| `INTERNAL_ERROR` | 500 | Last-resort fallthrough. `details` is empty. | `{}` | AC-T-2 |

### 6.3 Decision table — `CONVERSION_RATE_NOT_AVAILABLE` vs `UPSTREAM_UNAVAILABLE`

Reproduced from FR-003 for canonical reference. **Terminal vs inability:**

| Local rate eligible? | Treasury reachable? | Treasury response | Returned |
|---|---|---|---|
| Yes | n/a (not called) | n/a | `200 OK` |
| No | Yes | `200 OK`, ≥ 1 rate in window | `200 OK` |
| No | Yes | `200 OK`, empty result | `422 CONVERSION_RATE_NOT_AVAILABLE` |
| No | Yes | `200 OK`, all rates out of window | `422 CONVERSION_RATE_NOT_AVAILABLE` |
| No | Yes | 4xx "unknown currency" for alias-known input | `400 INVALID_CURRENCY` + `currency_alias_drift_detected` event |
| No | Yes | Schema-invalid payload | `502 UPSTREAM_BAD_RESPONSE` |
| No | Yes | Rate outside sanity bounds | `502 UPSTREAM_BAD_RESPONSE` |
| No | No (timeout / CB open after budgets exhausted) | n/a | `503 UPSTREAM_UNAVAILABLE` |

The principle: **`CONVERSION_RATE_NOT_AVAILABLE`** is the terminal answer when the rule was applied with all data available; **`UPSTREAM_UNAVAILABLE`** is reserved for "could not apply the rule because the data isn't here yet and our budgets are exhausted."

### 6.4 Example error bodies

```json
{
  "type": "https://wex.example/problems/conversion-rate-not-available",
  "title": "Conversion rate not available",
  "status": 422,
  "detail": "No Treasury exchange rate exists within 6 months on or before the purchase date.",
  "instance": "/api/v1/purchases/01HXZ7…/conversion",
  "errorCode": "CONVERSION_RATE_NOT_AVAILABLE",
  "details": {
    "purchaseDate": "2024-01-15",
    "targetCurrency": "Canada-Dollar",
    "windowLower": "2023-07-15",
    "windowUpper": "2024-01-15"
  }
}
```

```json
{
  "type": "https://wex.example/problems/upstream-unavailable",
  "title": "Upstream temporarily unavailable",
  "status": 503,
  "detail": "Treasury Fiscal Data API is unreachable and no eligible local rate exists.",
  "instance": "/api/v1/purchases/01HXZ7…/conversion",
  "errorCode": "UPSTREAM_UNAVAILABLE",
  "details": { "reason": "circuit_open" }
}
```

```json
{
  "type": "https://wex.example/problems/validation-error",
  "title": "Validation error",
  "status": 400,
  "detail": "One or more fields failed validation.",
  "instance": "/api/v1/purchases",
  "errorCode": "VALIDATION_ERROR",
  "details": {
    "errors": [
      { "field": "description", "code": "LENGTH" },
      { "field": "amountUsd", "code": "POSITIVE_REQUIRED" }
    ]
  }
}
```

---

## 7. Headers

| Header | Direction | Required | Purpose |
|---|---|---|---|
| `Content-Type: application/json` | Request (POST) | yes | Else `415`. |
| `Content-Type: application/json` | Response (200/201) | yes | |
| `Content-Type: application/problem+json` | Response (4xx/5xx) | yes (AC-T-5) | RFC 9457 conformance. |
| `Location: /api/v1/purchases/{id}` | Response (201) | yes | RFC 9110. |
| `X-Correlation-Id` | Request/Response | optional in, always out | Generated if absent. Echo in response. |
| `Idempotency-Key` | Request (POST) | optional (P1) | Replay-safe semantics; AC-001b documents the absence consequence. |
| `Retry-After` | Response (503) | yes | Seconds; default **300** (Phase-4 G4-P1-18; raised from 30 to avoid amplifying Treasury outages). Tunable via `WEX_RETRY_AFTER_SECONDS`. |
| `Retry-After` | Response (429) | yes | Seconds; default **1** for inbound rate-limit (Resilience4j refresh period is 1s; clients can retry on the next-second budget). Tunable via `WEX_RATE_LIMIT_RETRY_AFTER_SECONDS`. **C2 30-review §4.8 decision** — kept at 1s rather than 60s after confirming with the api-contracts owner: inbound 429 is a transient overload signal, not an upstream-outage signal, so back-off should be granular. |
| `traceparent` | Request/Response | optional | W3C trace propagation; emitted outbound to Treasury. |

---

## 8. OpenAPI 3.1

The OpenAPI document is generated from controller annotations via springdoc-openapi 3.x and served at `/v3/api-docs`. Notes for v1:

- Each error code is a discrete `responses[…]` entry with the Problem Details schema.
- `currency` is documented as a free string with an `examples` list (`CAD`, `Canada-Dollar`, `Euro Zone-Euro`, `JPY`, `Japan-Yen`). It is **not** an enum, because the canonical universe is the live Treasury catalogue and would drift (OQ-004).
- Decimal monetary fields are typed as `string` (not `number`) to preserve scale and avoid JS-number precision issues. Schema includes a `pattern` regex to enforce decimal-string shape.
- `Schema` extensions: `errorCode` and `details` are defined under `components.schemas.ProblemDetails`.
- Spec is lint-checked in CI against Spectral with the default ruleset plus a project rule asserting `Content-Type: application/problem+json` on every error response (AC-T-5).

---

## 9. Versioning and deprecation

- All resource paths under `/api/v1/`.
- Additive changes (new optional fields, new endpoints) within v1 do not require a version bump.
- Breaking changes (renamed/removed fields, status-code drift, error-code reassignment) require a new ADR, an OAS-diff gate (`oasdiff` in CI; R-018 mitigation), and a `/api/v2/` rollout with a deprecation window.
- Error-code values, once shipped, are append-only.

---

## 10. Examples for quick reading

### Create a purchase and convert it (happy path)

```
$ curl -s -X POST http://localhost:8080/api/v1/purchases \
       -H 'Content-Type: application/json' \
       -d '{"description":"Office supplies","transactionDate":"2026-04-01","amountUsd":"123.45"}' | jq
{
  "id": "01HXZ7R7R7K7V9R8Z7R5Q7A1BC",
  "description": "Office supplies",
  "transactionDate": "2026-04-01",
  "amountUsd": "123.45"
}

$ curl -s 'http://localhost:8080/api/v1/purchases/01HXZ7R7R7K7V9R8Z7R5Q7A1BC/conversion?currency=CAD' | jq
{
  "id": "01HXZ7R7R7K7V9R8Z7R5Q7A1BC",
  "description": "Office supplies",
  "transactionDate": "2026-04-01",
  "amountUsd": "123.45",
  "targetCurrency": "Canada-Dollar",
  "exchangeRate": "1.393000",
  "exchangeRateDate": "2026-03-31",
  "convertedAmount": "171.97"
}
```

(Numbers reflect the 2026-03-31 Treasury rate from the Phase-3 prototype: 123.45 × 1.393 = 171.96585 → 171.97. Treasury publishes `"1.393"`; the service normalises to scale 6 in storage and response per Phase-4 G4-P0-3.)

### Request rejected for content guard

```
$ curl -s -X POST http://localhost:8080/api/v1/purchases \
       -H 'Content-Type: application/json' \
       -d '{"description":"order 4242 4242 4242 4242","transactionDate":"2026-04-01","amountUsd":"10.00"}' | jq
{
  "type": "https://wex.example/problems/pan-pattern-detected",
  "title": "PAN pattern detected in description",
  "status": 400,
  "detail": "The description field contains content matching a PAN-like pattern.",
  "instance": "/api/v1/purchases",
  "errorCode": "PAN_PATTERN_DETECTED",
  "details": { "reason": "luhn" }
}
```

(The audit event `purchase_validation_failed{reason=pan_pattern}` is emitted; the rejected payload is **not** logged.)
