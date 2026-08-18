# Cardholder Benefit Eligibility Check — Spec

Source ticket: Priya Krishnan (Sr. PM, Affluent Charter) — "Feature request — cardholder benefit eligibility check."

## 1. Summary

Add a read-only HTTP endpoint that answers "is a cardholder of tier X eligible for benefit Y?" so the cardholder-facing UI can filter benefit offers *before* display, instead of after the cardholder tries to redeem. This directly targets the ~40 tickets/week Support is fielding from tier-mismatched benefit displays (e.g., a Platinum cardholder seeing an Infinite-only benefit).

## 2. API Contract

### Endpoint

```
GET /api/v1/benefits/{benefitId}/eligibility?tier={tier}
```

`benefitId` is a path parameter (identifies a specific resource — the benefit); `tier` is a query parameter (a filter on that resource, not itself a resource). GET because this is a pure lookup with no side effect visible to the caller.

### Request

| Field | Location | Required | Notes |
|---|---|---|---|
| `benefitId` | path | yes | Opaque catalog identifier, e.g. `BEN-1042`. Format TBD by whoever owns the benefit catalog — see Open Questions §5. |
| `tier` | query | yes | One of `PLATINUM`, `SIGNATURE`, `INFINITE` (canonical form — see §3). Case-sensitive; UI is responsible for sending the canonical value. |
| `X-Correlation-Id` | header | no | Optional; echoed in the response and in the audit log line. If absent, the service generates one, matching the existing `CorrelationIdFilter` convention already used elsewhere in this codebase. |

No request body.

### Response — 200 OK

```json
{
  "benefitId": "BEN-1042",
  "tier": "SIGNATURE",
  "eligible": true
}
```

### Response — 400 Bad Request (invalid `tier`)

Follows this repo's existing RFC 9457 Problem Details convention (`application/problem+json`, stable `errorCode` enum).

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "tier 'GOLD' is not a recognized card tier",
  "errorCode": "INVALID_TIER",
  "instance": "/api/v1/benefits/BEN-1042/eligibility"
}
```

Also returned when `tier` is missing entirely (`errorCode: MISSING_TIER`).

### Response — 404 Not Found (unknown `benefitId`)

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "benefit 'BEN-9999' does not exist",
  "errorCode": "BENEFIT_NOT_FOUND",
  "instance": "/api/v1/benefits/BEN-9999/eligibility"
}
```

### Response — 500 Internal Server Error

```json
{
  "type": "about:blank",
  "title": "Internal Server Error",
  "status": 500,
  "detail": "an unexpected error occurred",
  "errorCode": "INTERNAL_ERROR",
  "instance": "/api/v1/benefits/BEN-1042/eligibility"
}
```

No internal exception detail leaked, per this repo's existing safe-error-handling convention.

### Example curl commands

```bash
# Success — eligible
curl -s "http://localhost:8080/api/v1/benefits/BEN-1042/eligibility?tier=SIGNATURE" | jq
# → { "benefitId": "BEN-1042", "tier": "SIGNATURE", "eligible": true }

# Success — not eligible (tier below the benefit's minimum)
curl -s "http://localhost:8080/api/v1/benefits/BEN-1042/eligibility?tier=PLATINUM" | jq
# → { "benefitId": "BEN-1042", "tier": "PLATINUM", "eligible": false }

# 400 — unrecognized tier
curl -s "http://localhost:8080/api/v1/benefits/BEN-1042/eligibility?tier=GOLD" | jq
# → { "errorCode": "INVALID_TIER", ... }

# 400 — missing tier
curl -s "http://localhost:8080/api/v1/benefits/BEN-1042/eligibility" | jq
# → { "errorCode": "MISSING_TIER", ... }

# 404 — unknown benefit
curl -s "http://localhost:8080/api/v1/benefits/BEN-9999/eligibility?tier=SIGNATURE" | jq
# → { "errorCode": "BENEFIT_NOT_FOUND", ... }
```

## 3. Business rules

### 3.1 Tiers — canonical form

Priya wrote "Platinum, Signature, and Infinite" casually. Canonical wire form (uppercase, no separators, matches this repo's existing enum-serialization style for currency descriptors):

```
PLATINUM  <  SIGNATURE  <  INFINITE
```

Ordered ascending by benefit reach — this ordering is itself an assumption; see §5.

### 3.2 Data model — how eligibility is determined

**Decision: minimum-tier model.** Each benefit has exactly one `minimumTier`. A cardholder of tier `T` is eligible for a benefit iff `T >= benefit.minimumTier` under the ordering in §3.1 (i.e., a Signature cardholder is eligible for every Platinum-minimum and Signature-minimum benefit, but not Infinite-minimum ones).

This is simpler than an arbitrary per-tier eligibility set (e.g., a benefit restricted to `{PLATINUM, INFINITE}` but not `SIGNATURE`), and matches how tiered card programs conventionally work — higher tier is a superset of lower-tier benefits plus more. **This is a real assumption, not a confirmed fact** — see §5, item 1.

Storage: a new table, e.g. `benefit_eligibility(benefit_id PK, minimum_tier, updated_at)`, added via Liquibase migration, matching this repo's existing PostgreSQL/Liquibase pattern. v1 has no admin API to manage this table — rows are seeded via migration/config, coordinated offline between Product and Eng. Adding or changing a benefit's tier requirement in v1 requires an engineering change, not a self-service Product action.

### 3.3 Unknown tier vs. unknown benefit

These are deliberately handled differently:

- **Unknown/malformed `tier`** → `400 INVALID_TIER`. `tier` is a bounded enum we control; a value outside it is a client input error, not a missing resource.
- **Unknown `benefitId`** → `404 BENEFIT_NOT_FOUND`. `benefitId` identifies a specific resource in the path; a benefit that doesn't exist in the catalog is a missing resource, standard REST semantics.

Rationale for keeping these distinct rather than collapsing both into `eligible: false`: conflating "not eligible" with "this benefit doesn't exist in our catalog" would hide catalog bugs (e.g., a typo'd benefit ID from the UI) behind a false-looking "not eligible" response — which is exactly the kind of silent wrong-answer this endpoint exists to prevent.

### 3.4 What "auditable" means in v1

The ticket's stated inputs are only `tier` and `benefitId` — there is no cardholder or session identifier anywhere in "what we need." So **true cardholder-level audit ("which cardholder checked what") is not achievable with the contract as specified.**

**Decision for v1:** every call emits one structured log line (not a dedicated audit table, not an event) with: timestamp, correlation ID, calling-service identity (from service auth — see §5), `benefitId`, `tier`, `eligible` result, and latency. This supports "which benefits are being checked, how often, and with what result" analytics — but **not** "which cardholder checked what," which is what Priya's phrasing suggests she actually wants. Flagged explicitly in §5 as something to confirm with her before this ships, since closing it later means adding a cardholder/session identifier to the contract — a breaking change to the endpoint shape.

**When the line fires:** after the response has already been sent to the client, best-effort. Audit logging is observability, not business logic — it must never add latency to, or risk failing, a response the caller is waiting on. Implementation-wise this means the write happens in a post-response hook (a `HandlerInterceptor#afterCompletion`, not inline in the request-handling code path), and any failure in the logging path itself is caught and swallowed (logged at WARN) rather than ever surfacing to the caller. The line only fires for an actual eligibility determination (`eligible: true` or `eligible: false`) — not for the 400/404 error paths, which are already observable via their HTTP response.

## 4. Non-functional requirements

### 4.1 Latency — p99 < 100ms

**Achievable, but not with a naive per-request database read**, and there's a real tension in the ticket worth surfacing: Priya's success criteria demand p99 < 100ms, but her non-goals explicitly rule out "a caching layer." Taken literally, those two constraints conflict — a per-request round trip to Postgres, especially under connection-pool contention, will not reliably hit 100ms p99 at any real load.

**Interpretation I'm proceeding with:** "no caching layer" refers to *response/personalization caching* (the v2 per-cardholder feature), not to holding small, mostly-static reference data in memory. The eligibility table (benefit → minimum tier) is expected to be small (low hundreds of rows) and changes rarely (engineering-mediated, per §3.2). So v1 loads the full table into an in-memory map on startup and on a periodic background refresh, and serves every request from memory — no per-request DB call. This is the same shape as this repo's existing hot-cache pattern for exchange rates, just simpler (no windowing, no versioning).

**Refresh interval — locked down:** configurable via `wex.eligibility.refresh-interval-ms`, **default 5 minutes**. Small, mostly-static reference data doesn't need tighter freshness, and 5 minutes bounds the "I just wrote a row, why isn't it showing up yet" staleness window to something a human debugging it would expect. A **hard floor of 1 minute** is enforced at startup (`IllegalArgumentException` if configured lower) — a misconfigured near-zero value would turn this into an accidental per-request-ish DB hammer, defeating the entire point of the in-memory design above. This fails fast at startup rather than silently clamping, so a bad value is caught in review/CI, not discovered later as unexplained DB load.

Under that design, p99 < 100ms is comfortably achievable — the request path is an in-memory map lookup plus HTTP overhead.

### 4.2 Volume

Not stated in the ticket. Rough estimate, stated as an assumption pending real numbers from Product/Analytics: if the UI checks eligibility once per benefit shown, and a session displays on the order of 5-10 benefits, at an assumed active-session volume in the tens of thousands per day, that's roughly **10-50 requests/second at peak**, trivial load for an in-memory lookup. This needs real traffic numbers before being used for capacity sign-off — it's a placeholder, not a load-test target.

### 4.3 Behavior under high load or dependency failure

Two distinct failure modes given the in-memory-cache design:

- **High request load:** in-memory reads don't fail under load the way a DB-backed endpoint would; the main risk is thread-pool/connection saturation from the HTTP layer itself, handled by the existing rate-limiter filter pattern already in this codebase.
- **Background refresh failure** (DB unreachable when the periodic reload runs): serve the **last known-good in-memory snapshot** and log a WARN — never fail live requests because a background refresh failed. This mirrors the "best-effort, failure-tolerant" warm-up pattern already used elsewhere in this codebase.
- **Cold start with DB unreachable before the first successful load:** the service should not report itself ready (via the standard Spring Boot readiness probe) until the initial load succeeds — fail closed at the health-check level rather than serving an empty/wrong eligibility map. This is a deliberate choice: for a benefit-gating check, a *wrong* answer (e.g., defaulting to "not eligible" from an empty map, or worse "eligible" from an empty map treated as no-restriction) is worse than the endpoint being briefly unready.

## 5. Assumptions and open questions

Grouped by how expensive it is to be wrong: **blocking** (wrong answer forces a rebuild of core logic, data model, or architecture — must resolve before writing code), **scope-affecting** (changes what v1 delivers or how much work it is, but doesn't force redoing what's already built), and **FYI** (good to confirm eventually, low risk either way).

### Blocking — resolve before implementation starts

1. **Is eligibility strictly hierarchical, with a single minimum tier per benefit?** (§3.2) I've assumed a Signature cardholder gets everything a Platinum cardholder gets, plus more — no benefit is restricted to a non-contiguous set of tiers (e.g. Platinum + Infinite but not Signature). If that's wrong, the data model, the comparison logic, and the storage schema all need to change — this isn't a config tweak, it's a different feature.

2. **Is the tier ordering really `PLATINUM < SIGNATURE < INFINITE`, with no other tiers and no exceptions?** This was inferred from the ticket's framing ("Platinum cardholder ... Infinite-only benefits"), never confirmed. It was previously written up as a background assumption, which understated the risk: every eligibility comparison the service makes depends on this ordering being correct. If there's a fourth tier not mentioned, or the order isn't what I've assumed, every existing eligibility result is wrong, not just edge cases. This needs an explicit yes from Priya (or whoever owns the tier taxonomy), not an inference from one sentence in a ticket.

3. **Does "no caching layer" rule out an in-memory reference-data cache, or only response/personalization caching (the v2 concept)?** I've proceeded on the latter reading (§4.1), because taken literally the former makes the stated p99 < 100ms target unachievable against a real database under load. If Priya actually means "no application-level caching of any kind," then the latency success criterion as written needs to be renegotiated — that's a conversation to have now, not a surprise at launch.

### Scope-affecting — resolve before shipping, doesn't block starting the build

1. **Is "auditable" cardholder-level or aggregate/service-level?** The ticket's stated inputs (`tier`, `benefitId`) contain no cardholder or session identifier, so cardholder-level audit isn't achievable with the contract as specified. v1 is built around aggregate call-level logging (§3.4), which is a complete, shippable answer on its own — nothing about v1 is blocked on this. Downgraded from blocking because of that: the risk isn't to v1, it's to whatever integrates against this contract next. If cardholder-level audit turns out to be required later, the endpoint contract needs a cardholder/session identifier added, which is a breaking change to whatever's already calling it by then — so resolve it before other consumers integrate, not before v1 ships.
2. **Is there an existing benefits catalog/admin system this should read from, or are we the source of truth for v1?** Doesn't change the comparison logic (still "does tier meet minimum"), but changes whether this is new storage or an integration, and how much of §3.2's schema is real work.
3. **Service-to-service auth mechanism** for the calling UI backend — assumed necessary (given this repo's PCI-Tier-1 posture, and that responses gate what's shown to cardholders) but not specified. Pluggable independent of the core eligibility logic, so it doesn't block starting the build. Hard milestone: **must be decided and implemented before this endpoint is reachable from any non-test environment** — i.e., it's a release-gate item for the production-readiness review (§7 of this repo's release process), not a "someday" — the endpoint ships with no external auth story otherwise.
4. **No admin/self-service API for managing the tier→benefit mapping in v1** (already captured as a deliberate scope cut in §6) — flagged here because if benefit-tier changes turn out to be frequent, this becomes a recurring engineering-ticket burden Priya should knowingly accept, not discover later.
5. **`benefitId` format is assumed to be an opaque string** (e.g. `BEN-1042`) — if the real catalog uses a different scheme (numeric, UUID), that's a straightforward type change, not a rebuild.

### FYI — low risk, confirm when convenient

1. **Volume estimate (§4.2) is a placeholder**, not a confirmed number from Analytics. Doesn't change the v1 design — the in-memory-cache approach (§4.1) is largely volume-insensitive — but real numbers would firm up the capacity story before launch.

## 6. Explicit out-of-scope

Carried over from the ticket, unchanged:

- No per-cardholder personalization (v2)
- No batch endpoint (v2)
- No caching layer, in the sense of response/personalization caching (v2) — see §4.1 for the narrower in-memory reference-data cache this spec *does* include for v1, and why

Additional items deliberately deferred, not mentioned in the ticket but flagged here so they're a conscious choice rather than an oversight:

- No admin UI/API for managing benefit-tier mappings (engineering-mediated for v1, §3.2)
- No cardholder-level audit trail (only service/call-level logging, §3.4)
- No per-caller rate limiting beyond whatever blanket rate-limiting already exists in this service
- No multi-region/geo-specific behavior
- No localization of error messages (UI is responsible for translating `errorCode` into user-facing copy)

## 7. Testing strategy

Following this repo's existing test-layer conventions (unit / integration / ArchUnit / property-based where it fits):

### Unit tests — eligibility resolution logic

- Exact-tier match: cardholder tier equals benefit's minimum tier → eligible (inclusive boundary).
- Tier above minimum → eligible.
- Tier below minimum → not eligible.
- Full 3×N boundary table across all three tiers against benefits at each minimum-tier value (mirrors the boundary-table style already used for the 6-month rate-selection rule in this codebase).
- Unknown tier value → rejected before reaching the eligibility lookup at all (fails fast in request validation, not treated as "not eligible").

### Controller/API-layer tests

- Correct HTTP status + `errorCode` for every case in §2: 200 (eligible true/false), 400 (`INVALID_TIER`, `MISSING_TIER`), 404 (`BENEFIT_NOT_FOUND`).
- Correlation ID: supplied header is echoed back; absent header results in one being generated.
- Response body shape matches the documented contract exactly (no extra/missing fields) — regression-guarded, since the UI will bind directly to this shape.

### Integration tests

- Full request path against a seeded test database (Testcontainers Postgres, matching this repo's existing IT pattern) — confirms the Liquibase migration, the load-into-memory step, and the HTTP layer all agree.
- Background refresh: verify a change written to the DB is reflected in responses after the next refresh cycle, and *not* before (proves the in-memory model isn't accidentally per-request DB-backed).
- Cold start with DB unavailable: readiness probe stays down until first successful load (§4.3) — verify via the health-indicator pattern already established in this codebase.
- Refresh failure after a successful initial load: service keeps serving the last-good snapshot and logs a WARN, live requests are unaffected.
- Concurrent refresh — exact parameters, not left implicit: **8 reader threads** running for **2 seconds wall-clock**, racing against a refresh loop running unpaced (no sleep, to maximise contention) for the same window. Assert (a) no reader ever observes a torn/garbage value — every read matches one of the two known-valid generations, never a mix; and (b) **p99 single-read latency stays under 10ms** even mid-swap (a generous bound for a pure in-memory lookup with no network/serialization, chosen to tolerate CI-machine noise without becoming flaky while still catching a regression like accidentally introducing a lock).

### Non-functional / resilience tests

- Smoke-level latency check: p99 < 100ms against the in-memory path under a modest concurrent load — not a full load-test suite for v1, but enough to catch a regression that reintroduces a per-request DB call.
- Audit logging: exactly one structured log line per call, containing `benefitId`, `tier`, `eligible`, correlation ID, and latency — and explicitly asserting the *absence* of any cardholder/PII field, since that's a scope boundary worth regression-testing, not just documenting.

### Edge-case matrix (all must be covered, unit or integration as appropriate)

| Input | Expected result |
|---|---|
| Known tier, known benefit, tier ≥ minimum | 200, `eligible: true` |
| Known tier, known benefit, tier < minimum | 200, `eligible: false` |
| Tier exactly equal to benefit's minimum | 200, `eligible: true` (inclusive boundary) |
| Unrecognized tier string (typo, wrong case, garbage) | 400 `INVALID_TIER` |
| Missing `tier` query param | 400 `MISSING_TIER` |
| Unknown `benefitId` | 404 `BENEFIT_NOT_FOUND` |
| DB unreachable during background refresh (post-warm) | Requests still succeed, served from last-good snapshot |
| DB unreachable before first successful load | Readiness probe down; no requests served with an empty/wrong map |
