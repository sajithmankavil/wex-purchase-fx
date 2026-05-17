# Component Design

> C4 Level 2/3 — internal structure of `wex-purchase-fx`. Builds on [system-context.md](system-context.md) (outside-the-process view) and is constrained by [adr-0001-core-architecture.md](adr-0001-core-architecture.md) (decision rationale).
> **Status:** Phase 3 (Architecture & Design Session), 2026-05-17.
> **Phase-4 grill refinements (2026-05-17):** Single-flight gate re-keyed; hot cache re-keyed; loser-polls-DB pattern documented; gate-release-on-shutdown documented; encoded-input pre-pass in `ContentGuard`. See [design-grill.md](../planning/design-grill.md) §6 and the §3.2 / §3.4 updates below.

---

## 1. Package layout (clean architecture, single deployable)

```
com.example.purchaseconversion
├── api                        ← controllers, request/response DTOs, RFC-9457 advice
│   ├── controller
│   │   ├── PurchaseController              (POST /api/v1/purchases, GET /api/v1/purchases/{id})
│   │   └── ConversionController            (GET /api/v1/purchases/{id}/conversion)
│   ├── dto
│   │   ├── PurchaseRequest                 (validated DTO)
│   │   ├── PurchaseResponse
│   │   └── ConversionResponse
│   └── advice
│       ├── ProblemDetailsExceptionHandler  (RFC 9457; Content-Type: application/problem+json)
│       └── ContentGuard                    (PAN-pattern + track-data on `description`)
│
├── application                ← orchestration, no Spring annotations on domain pure rules
│   ├── purchase
│   │   └── PurchaseService                 (create, find by id)
│   ├── conversion
│   │   ├── ConversionService               (rate selection, single-flight orchestration)
│   │   ├── RateSelectionPolicy             (6-month + EOM clamp + max(record_date))
│   │   └── ConversionResult                (immutable application-layer record)
│   └── port                                ← outbound ports the application calls
│       ├── PurchaseRepositoryPort          (interface)
│       ├── ExchangeRateRepositoryPort      (interface)
│       ├── TreasuryClientPort              (interface)
│       └── CurrencyAliasPort               (interface)
│
├── domain                     ← framework-free POJOs; no Spring/JPA imports
│   ├── Purchase                            (id, description, transactionDate, amountUsd)
│   ├── ExchangeRate                        (currencyDesc, recordDate, effectiveDate, rate)
│   ├── Money                               (BigDecimal; HALF_UP scale 2 invariants)
│   ├── CurrencyDescriptor                  (canonical "Country-Currency" string)
│   └── PurchaseId                          (UUID v7 value object)
│
├── infrastructure             ← adapters that implement application ports
│   ├── persistence
│   │   ├── PurchaseRepositoryAdapter       (Spring Data JPA; maps DTOs ↔ domain)
│   │   ├── ExchangeRateRepositoryAdapter   (Spring Data JPA; versioned `effective_date`)
│   │   └── jpa                             (PurchaseEntity, ExchangeRateEntity, Spring repos)
│   ├── treasury
│   │   ├── TreasuryClientAdapter           (RestClient + Resilience4j + schema validator)
│   │   ├── TreasuryResponse                (DTO mirroring Fiscal Data API shape)
│   │   ├── RateOrientationContractCheck    (asserts published convention on every parse)
│   │   └── SingleFlightGate                (per-(currency, window) lock, Caffeine-backed)
│   ├── currency
│   │   ├── CurrencyAliasTableAdapter       (loads currency-aliases.json; readiness probe)
│   │   └── alias.json                      (resources/)
│   └── cache
│       └── ExchangeRateHotCache            (Caffeine, in-memory; TTL 24h default / 7d prod)
│
├── config                     ← Spring beans, profiles, env binding
│   ├── WexProperties                       (@ConfigurationProperties; all env vars)
│   ├── ResilienceConfig                    (Resilience4j: timeout, retry, CB, bulkhead)
│   ├── RestClientConfig                    (TreasuryClient HTTP client + User-Agent + TLS)
│   ├── JpaConfig
│   └── ObservabilityConfig                 (Micrometer, OTel exporter, log hashing)
│
├── observability              ← cross-cutting telemetry
│   ├── DescriptionHasher                   (HMAC-SHA-256, vN: version prefix)
│   ├── MetricsCatalog                      (constants; cardinality budget enforcement)
│   └── CorrelationFilter                   (X-Correlation-Id; trace propagation)
│
└── exception                  ← domain → HTTP mapping
    ├── DomainException                     (abstract)
    ├── ValidationException
    ├── PanPatternDetectedException
    ├── FutureDateException
    ├── PurchaseNotFoundException
    ├── MalformedIdentifierException
    ├── InvalidCurrencyException
    ├── ConversionRateNotAvailableException
    ├── UpstreamUnavailableException
    └── UpstreamBadResponseException
```

**Dependency direction.** Domain depends on nothing. Application depends on domain + ports (interfaces in `application.port`). Infrastructure depends on domain and implements application ports. API depends on application. Cross-cutting: observability, exception, config.

Enforced by ArchUnit (see §6).

---

## 2. Components — responsibilities, interfaces, data owned, failure modes

| Component | Responsibility | Interfaces (in) | Data owned | Key failure modes |
|---|---|---|---|---|
| `PurchaseController` | HTTP boundary for FR-001 / FR-002. Validation, content-type negotiation. | `POST /api/v1/purchases`, `GET /api/v1/purchases/{id}` | None | 400 (validation), 415 (content type), 422 (future date), 415/400 to client. |
| `ConversionController` | HTTP boundary for FR-003. | `GET /api/v1/purchases/{id}/conversion?currency=…` | None | 200, 400, 404, 422, 502, 503 per [api-contracts.md](api-contracts.md). |
| `ContentGuard` (advice) | PAN-Luhn and track-data shape rejection on `description`. Audit-event emission. | Servlet filter / `@RestControllerAdvice` | None | Rejects payload before controller; emits `purchase_validation_failed{reason=pan_pattern|track_data}`. |
| `ProblemDetailsExceptionHandler` | Maps `DomainException` subclasses → RFC 9457 body + `application/problem+json`. | Spring `@RestControllerAdvice` | None | Last-resort fallthrough returns `500 INTERNAL_ERROR` with empty `details`. |
| `PurchaseService` | Orchestrates create + read for purchases. | API; persistence port | Transient | Wraps repository exceptions; never leaks JPA types. |
| `ConversionService` | Orchestrates FR-003: alias resolution, eligible-rate lookup, Treasury fetch on miss, single-flight, persist returned rates, re-evaluate, HALF_UP rounding. | API; all four ports | Transient | Returns domain exception types; never throws JPA / HTTP exceptions across the API boundary. |
| `RateSelectionPolicy` | Pure function: given `transactionDate` and a set of `ExchangeRate` rows, return the eligible rate or empty. Calendar-month minus, EOM clamp, max(record_date), max(effective_date) tie-break. | Domain only | None | None (pure). |
| `Money` | BigDecimal value object with scale-2 invariant, HALF_UP multiplication for conversion. | Domain only | None | Throws `IllegalArgumentException` on negative, non-finite, or scale > 2 input. |
| `PurchaseRepositoryAdapter` | JPA-backed persistence of `Purchase`. | Implements `PurchaseRepositoryPort` | Owns the `purchase_transactions` table. | DB unreachable → readiness DOWN; integrity violation → mapped to `409` (Idempotency-Key conflict only). |
| `ExchangeRateRepositoryAdapter` | Versioned persistence of `ExchangeRate` keyed by `(country_currency_desc, record_date, effective_date)`. | Implements `ExchangeRateRepositoryPort` | Owns the `exchange_rates` table. | Same as above; duplicate-key on identical `(currency, record_date, effective_date, exchange_rate)` is a no-op (idempotent insert). |
| `TreasuryClientAdapter` | Outbound HTTP to Treasury Fiscal Data API. Bounded timeout, retry+backoff, circuit breaker, bulkhead. Parses JSON. | Implements `TreasuryClientPort` | None | Timeout / 5xx / CB open → maps to upstream-error domain type; schema-invalid / sanity-failed → maps to upstream-bad-response domain type. |
| `RateOrientationContractCheck` | Sanity-asserts the multiplicative-formula invariant against a small fixture set on every fresh fetch (defence against a future currency landing with inverted convention). | Internal to TreasuryClientAdapter | None | Logs WARN + increments `treasury.contract.orientation_drift.count`; does not fail the request. (Phase 5 promotes to fail-closed.) |
| `SingleFlightGate` | Per-`(country_currency_desc, treasury_quarter_end)` lock (Phase-4 G4-P0-1) so concurrent cache misses fan in to one upstream call. `treasury_quarter_end = ceil(transactionDate, quarter-end)`. | Internal | Transient | **Bounded wait 10 s** (Phase-6 G6-P0-2; raised from 200 ms to match Treasury client's worst-case retry budget). Loser polls DB every 100 ms during the wait; watches an `AtomicReference<WinnerOutcome>` on the gate's per-key state. Returns when (a) DB has the persisted result or (b) winner-outcome-ref shows failure (loser mirrors winner's exception type to satisfy AC-027d). On 10 s timeout: `UpstreamUnavailableException("loser_timeout")`. On SIGTERM the gate releases all locks; in-flight losers fail-fast with `503 UPSTREAM_UNAVAILABLE`. |
| `CurrencyAliasTableAdapter` | Loads `currency-aliases.json` at startup; resolves ISO-4217 or case-insensitive Treasury descriptor → canonical `country_currency_desc`. | Implements `CurrencyAliasPort` | Read-only resource | Refuse-to-start on missing/parse-error file (readiness DOWN). |
| `ExchangeRateHotCache` | Caffeine cache for hot-path rate lookups, keyed by `(country_currency_desc, record_date)` (Phase-4 G4-P0-2). TTL 24 h (default) / 7 d (prod override). Cache invalidated on `upsertVersioned()` for the affected key (Phase-4 cache-invalidation note in ADR-0001 D-10). | Internal | Transient | Eviction is silent; correctness fallback is the DB. |
| `DescriptionHasher` | HMAC-SHA-256 with versioned key prefix. | Internal | None | Refuse-to-start in prod/staging if `WEX_LOG_HASH_KEY` is unset (NFR-017). |
| `CorrelationFilter` | Per-request `X-Correlation-Id`; populates MDC, attaches to response header. | Servlet filter | Transient | Generates a UUID if header absent. |

---

## 3. Request / event flows

### 3.1 `POST /api/v1/purchases` — create purchase

```
Client → PurchaseController
  validate content-type = application/json
  validate body shape (Spring @Valid + Bean Validation)
  → ContentGuard.check(description)
       Luhn(13–19 digits, separators allowed) → reject (400 PAN_PATTERN_DETECTED)
       Track 1/2 shape match            → reject (400 PAN_PATTERN_DETECTED)
  validate transactionDate ≤ today (UTC) → else 422 FUTURE_DATE
  validate amountUsd: positive, scale exactly 2 → else 400 VALIDATION_ERROR
→ PurchaseService.create(request)
  → PurchaseRepositoryAdapter.save(domain.Purchase{ id=UUIDv7.now(), ... })
→ PurchaseController returns 201 Created
  body = PurchaseResponse(id, description, transactionDate, amountUsd)
  headers: Location=/api/v1/purchases/{id}, X-Correlation-Id=...
```

Idempotency-absent semantics (AC-001b): two identical POSTs produce two distinct purchases. Idempotency-Key is a P1/prod-blocking follow-up (OQ-009).

### 3.2 `GET /api/v1/purchases/{id}/conversion?currency=…` — convert and retrieve

```
Client → ConversionController
  validate {id} is UUID v7 (or ULID) shape → else 400 MALFORMED_IDENTIFIER
→ ConversionService.convert(id, currencyInput)
  → CurrencyAliasPort.resolve(currencyInput)
       case-insensitive Treasury descriptor → canonical descriptor
       ISO 4217 code                        → canonical descriptor
       no match                             → 400 INVALID_CURRENCY
  → PurchaseRepositoryPort.findById(id)
       not found → 404 PURCHASE_NOT_FOUND
  → ExchangeRateHotCache.get(currency, window=[txDate-6mo(EOM), txDate])
       hit  → return rate
       miss → continue
  → ExchangeRateRepositoryPort.findEligible(currency, window)
       (sorted by record_date desc, effective_date desc; first row wins)
       hit  → populate hot cache, return rate
       miss → continue
  → SingleFlightGate.runOnce(currency, window, () ->
       TreasuryClientPort.fetchRates(currency, windowLower, windowUpper)
         RestClient → Resilience4j(timeout=2s, retry=3, CB) → Treasury API
         on success:
           schema-validate; sanity-check each rate (> 0, ≤ 1e9)
           reject offending rates with treasury_api_failure{reason=rate_sanity}
           ExchangeRateRepositoryPort.upsertVersioned(rates)
             (key = currency + record_date + effective_date)
           hot-cache the latest eligible rate (if any)
         on timeout / CB open / 5xx after retry budget:
           throw UpstreamUnavailableException
         on schema-invalid / rate-sanity-failed:
           throw UpstreamBadResponseException
       )
  → ExchangeRateRepositoryPort.findEligible(currency, window)   (re-evaluate after upsert)
       hit  → continue
       miss → 422 CONVERSION_RATE_NOT_AVAILABLE
  → Money.convert(purchase.amountUsd, rate.exchangeRate)
       = round_half_up(amountUsd × exchangeRate, 2)   intermediate scale ≥ 12
  → return ConversionResult{ purchase, rate, convertedAmount }
→ ConversionController returns 200 OK
  body = ConversionResponse(..., targetCurrency=canonical, exchangeRate, exchangeRateDate, convertedAmount)
```

Error-code routing (decision table — terminal vs inability):
| State | Code |
|---|---|
| Eligible local rate or Treasury 200 + eligible rate after upsert | `200 OK` |
| Treasury 200 + empty list, no eligible local | `422 CONVERSION_RATE_NOT_AVAILABLE` (AC-020b) |
| Treasury 200 + rates all out of window, no eligible local | `422 CONVERSION_RATE_NOT_AVAILABLE` (AC-022b) |
| Treasury 4xx unknown currency for alias-known input | `400 INVALID_CURRENCY` + drift event (AC-021b) |
| Treasury schema-invalid OR rate outside sanity bounds | `502 UPSTREAM_BAD_RESPONSE` (AC-024 / AC-024b) |
| Treasury timeout / CB open / 5xx after budgets, no eligible local | `503 UPSTREAM_UNAVAILABLE` + `Retry-After` (AC-023) |

### 3.3 Failure-mode flow detail

```
TreasuryClientAdapter
  ├─ Resilience4j Timeout       (2s connect+read) → UpstreamUnavailableException
  ├─ Resilience4j Retry         (3 attempts, 100ms*2^n + jitter, only on 5xx/IOException)
  ├─ Resilience4j CircuitBreaker
  │     50% failure over 20 calls → open 30s
  │     half-open: 5 trial calls
  │     open + miss + no local rate → UpstreamUnavailableException
  ├─ Resilience4j Bulkhead       (50 concurrent permits → upstream backpressure)
  ├─ JsonSchemaValidator         (schema pinned in src/main/resources/treasury-schema.json)
  │     failure → UpstreamBadResponseException
  ├─ RateSanity                  (exchange_rate > 0 && ≤ 1e9 per record)
  │     failure → UpstreamBadResponseException + treasury_api_failure{reason=rate_sanity}
  └─ RateOrientationContractCheck (compares parsed rate against fixture set; WARN on drift)
```

### 3.4 Startup / readiness sequence

```
Bean wiring
  ├─ Load WexProperties from env (fail-fast on prod profile if WEX_LOG_HASH_KEY absent)
  ├─ Construct DescriptionHasher (v0: fallback in local/test; vN: from env elsewhere)
  ├─ Flyway migrate DB
  ├─ Load CurrencyAliasTable from classpath resource
  │     parse-fail → ApplicationContextException → process exit
  ├─ Construct Caffeine ExchangeRateHotCache (TTL from config)
  └─ Construct TreasuryClient (RestClient + Resilience4j wrappers)

Readiness probe (Phase-4 refinement, G4-P1-19)
  ├─ DB reachable (SELECT 1)                                                → required UP
  ├─ DB pool has spare capacity (active < max - 1) sustained ≥ 5 s          → required UP
  ├─ Alias table loaded                                                     → required UP
  ├─ Treasury reachable                                                     → NOT required (degraded mode)
  └─ Hot cache initialised                                                  → required UP

Liveness probe
  └─ JVM heap not exhausted; thread pool not deadlocked → UP

Graceful shutdown (Phase-4 refinement, G4-P1-20)
  ├─ Readiness flips DOWN on SIGTERM (LB stops routing)
  ├─ SingleFlightGate releases all locks; in-flight losers fail-fast 503
  ├─ Treasury bulkhead closes (no new outbound calls)
  └─ In-flight HTTP drains within 30 s grace window
```

Treasury degradation alone does **not** flip readiness DOWN: FR-001 and FR-002 remain serviceable, and FR-003 continues to serve from local cache (AC-022).

### 3.5 ContentGuard (Phase-4 refinement G4-P0-5; Phase-8 refinement G8-P0-1 + G8-P0-3)

Phase-3 D-13 framed the guards as defense-in-depth. Phase-4 upgraded them to **detection-and-alert** with an encoded-input pre-pass. Phase-8 grill added **Unicode NFKC normalisation** (G8-P0-3) and **rate-limiter ordering** (G8-P0-1).

```
ContentGuard.check(description):
  // Phase-8 G8-P0-3: normalise to NFKC first (catches fullwidth digits, homoglyphs)
  normalised = Normalizer.normalize(description, Form.NFKC)

  // Phase-4 G4-P0-5: build candidate set including decoded variants
  candidates = [ normalised ]
  for decoder in [ base64-standard, base64-urlsafe, hex, urlEncoded ]:
      decoded = decoder.tryDecode(normalised)
      if decoded is not None: candidates.append(decoded)

  // Apply guards to each candidate
  for candidate in candidates:
      if matchesLuhn(candidate):      reject "luhn" (or "luhn-encoded" if candidate != normalised)
      if matchesTrack1(candidate):    reject "track1"
      if matchesTrack2(candidate):    reject "track2"
```

The *stored* `description` remains the original (un-normalised) input; NFKC is applied only for the guard check. AC-010d covers encoded variants; AC-010e (Phase 8) covers Unicode-confusable variants. Audit event `purchase_validation_failed{reason=…}` is emitted; rejected payload is **never logged**.

**Order of operations (Phase-8 G8-P0-1).** Rate-limiting must execute **before** the decoder pipeline to avoid CPU-cost DoS:

```
HTTP Filter chain
  └─ WexRateLimiterFilter (servlet Filter, @Order(HIGHEST_PRECEDENCE + 100))
       Returns 429 here if limit exceeded → ContentGuard never runs
  └─ Spring DispatcherServlet
       └─ Bean Validation (@Valid: bounded length, format)
           └─ ContentGuard advice (NFKC + decoder + Luhn/track guards)
               └─ Controller method
                   └─ Application service
```

The earlier Phase-7 `@RateLimiter` controller-method annotation is **removed**; rate-limiting moves entirely to the servlet Filter. AC-T-6 (Phase 8) asserts that `contentguard.invocations.count` increments only for requests that pass the rate-limit.

---

## 4. Error handling

Centralised in `ProblemDetailsExceptionHandler`. Every domain exception subclass maps to a stable `errorCode`, HTTP status, `type` URI, `title`, and `details` shape.

```
DomainException
  ├─ ValidationException          → 400, errorCode=VALIDATION_ERROR,           details.errors[{field, code}]
  ├─ PanPatternDetectedException  → 400, errorCode=PAN_PATTERN_DETECTED,       details={reason}
  ├─ FutureDateException          → 422, errorCode=FUTURE_DATE,                details={transactionDate}
  ├─ MalformedIdentifierException → 400, errorCode=MALFORMED_IDENTIFIER,       details={id}
  ├─ PurchaseNotFoundException    → 404, errorCode=PURCHASE_NOT_FOUND,         details={id}
  ├─ InvalidCurrencyException     → 400, errorCode=INVALID_CURRENCY,           details={currency}
  ├─ ConversionRateNotAvailableException
  │                               → 422, errorCode=CONVERSION_RATE_NOT_AVAILABLE,
  │                                 details={purchaseDate, targetCurrency, windowLower, windowUpper}
  ├─ UpstreamBadResponseException → 502, errorCode=UPSTREAM_BAD_RESPONSE,      details={reason}
  └─ UpstreamUnavailableException → 503, errorCode=UPSTREAM_UNAVAILABLE,       details={reason}, header Retry-After
```

Last-resort `Throwable` → `500 INTERNAL_ERROR`, `details={}`. Stack traces never appear in the response body. All errors carry `Content-Type: application/problem+json` (AC-T-5).

---

## 5. Concurrency / idempotency

| Concern | Decision |
|---|---|
| Threading model | Spring Boot's default servlet container, one HTTP thread per request. Domain code is stateless. |
| Single-flight per `(currency, window)` | Caffeine `LoadingCache` with a per-key lock, plus an explicit `SingleFlightGate` that wraps the load function so all losers see the winner's result (AC-027b). |
| Two concurrent requests for the same purchase id while Treasury fetch is in flight | Same gate; either both see the freshly fetched eligible rate or both see the original outcome — never a split (AC-027d). |
| `POST /purchases` idempotency | No `Idempotency-Key` in v1; two POSTs produce two purchases (AC-001b). Idempotency-Key is P1 for v1, BLOCKING for prod (OQ-009). |
| Versioned-rate upsert race | UPSERT on `(currency, record_date, effective_date)`; identical rows are no-ops, conflicting rows raise integrity-violation → re-read after conflict and pick latest. |
| Graceful shutdown | Spring Boot graceful shutdown enabled; readiness flips DOWN on SIGTERM; in-flight requests drain on a 30 s grace window; Treasury bulkhead is closed first to stop new outbound. |

---

## 6. ArchUnit rules (architectural fitness functions)

These are enforced as JUnit tests in `src/test/java/.../ArchitectureTests.java`. They prevent layer leakage and money-arithmetic mistakes.

```java
// Layer dependencies
classes().that().resideInAPackage("..domain..")
    .should().onlyDependOnClassesThat()
    .resideInAnyPackage("..domain..", "java..")
    .as("domain must be framework-free");

classes().that().resideInAPackage("..application..")
    .should().onlyDependOnClassesThat()
    .resideInAnyPackage("..application..", "..domain..", "java..");

noClasses().that().resideInAPackage("..domain..")
    .should().dependOnClassesThat()
    .resideInAnyPackage("org.springframework..", "jakarta.persistence..");

// Money safety
noClasses().that().resideInAnyPackage("..domain..", "..application..")
    .should().haveFieldOfType(Double.class).orShould().haveFieldOfType(Float.class)
    .orShould().haveFieldOfType(double.class).orShould().haveFieldOfType(float.class)
    .as("no double/float in domain or application; BigDecimal only");

// Logging hygiene
noClasses().should().callMethod(Logger.class, "info", String.class, Object.class)
    .with(arg -> argReferences("description"))
    .as("description must not be logged in plain text — use DescriptionHasher");

// Controllers
classes().that().areAnnotatedWith(RestController.class)
    .should().resideInAPackage("..api.controller..")
    .andShould().notDependOnClassesThat().resideInAPackage("..infrastructure..")
    .as("controllers depend on application, not infrastructure");
```

(Final test text in Phase 13; rule intent is fixed.)

---

## 7. Cross-cutting properties

| Property | Implementation |
|---|---|
| Correlation | `X-Correlation-Id` header in/out; UUID-generated if absent; populates SLF4J MDC; propagated to Treasury client as a custom header. |
| Distributed tracing | OpenTelemetry instrumentation on inbound HTTP, DB, and Treasury RestClient. W3C `traceparent` propagated outbound. |
| Logs | JSON via Logback (`logstash-logback-encoder`). Event taxonomy in [docs/operations/observability.md](../operations/observability.md). |
| Metrics | Micrometer + Prometheus registry on `/actuator/prometheus`. Cardinality budget ≤ 5 000 series. |
| Configuration | `@ConfigurationProperties("wex")` bound to env vars; nested by concern (`wex.treasury.timeout`, `wex.cache.ttl`, `wex.data-dir`, `wex.log.hash-key`, ...). |
| Secrets | Env-only; never logged; refuse-to-start in prod if required env vars absent. |
| Time | `Clock` bean injected everywhere; UTC; tests use `Clock.fixed(...)`. |
| Validation | Jakarta Bean Validation (`@Valid`, custom `@PositiveScaleTwo`, `@FutureDateForbidden`). |
| Content guard | `@RestControllerAdvice` runs before controller method invocation; emits the validation failure event with `reason` label. |

---

## 8. Test plan (by component)

| Component | Test types | Notable cases |
|---|---|---|
| `Money` | unit (jqwik property) | HALF_UP correctness across scale, sign, magnitude; rejects double/float; intermediate scale ≥ 12. |
| `RateSelectionPolicy` | unit (table-driven) | AC-014..AC-020, plus AC-018b (EOM clamp eligible) and AC-019b (one-day-past-clamp ineligible). |
| `ContentGuard` | unit + integration | Luhn-valid / Luhn-invalid; track-1, track-2 shapes; legitimate digit-heavy descriptions (false-positive baseline). |
| `ProblemDetailsExceptionHandler` | integration (MockMvc) | Every error code → status, body shape, `Content-Type: application/problem+json` (AC-T-5). |
| `PurchaseController` | integration | AC-001..AC-010c. |
| `ConversionController` | integration (WireMock Treasury) | AC-014..AC-027d; the full error-code decision table. |
| `TreasuryClientAdapter` | contract + resilience | AC-T-3: happy path, empty result, malformed payload, 5xx, slow response, timeout, rate-with-zero-decimals, rate-out-of-sanity-bounds (AC-024b), rate revision (AC-026b). |
| `SingleFlightGate` | concurrency | Coordinated barrier with WireMock; assert exactly-one upstream call under N concurrent demand (AC-027b/d). |
| `CurrencyAliasTableAdapter` | unit + startup | Refuses to start on missing/parse-error; resolves `CAD`, `Canada-Dollar`, `canada-dollar`, `Euro Zone-Euro` (the space-bearing descriptor); rejects unknown. |
| `DescriptionHasher` | unit | `vN:` prefix; key-version distinguishability; refuse-to-start in prod when env absent (AC-032b). |
| `ExchangeRateRepositoryAdapter` | integration (file-mode H2) | Versioned upsert; max(effective_date) selection; identical-row insert is a no-op (idempotent). |
| Persistence durability | integration (DurabilityRestartIT) | AC-010 (restart with file mode preserves data). |
| Architectural fitness | ArchUnit | The §6 rules. |
| Cardinality | unit | `MetricsCatalog` enforces label allow-list; deny tests for `currency`-labelled RED counters. |

---

## 9. Forward-looking gaps (called out for Phase 4 grill)

- **Idempotency-Key implementation** is P1 for v1; Phase-4 grill should confirm the v1 scope is genuinely acceptable for the case study and that the prod-only blocker stays clearly tracked.
- **OpenAPI 3.1** generated from controllers via springdoc; the generator does not surface the dual-mode currency input perfectly (path-parameter alias). Phase-4 should verify the OAS document is consumer-usable.
- **Cardinality budget enforcement** is currently a soft policy in `MetricsCatalog`. A hard enforcement (build-time check on Micrometer registrations) is a Phase-13 follow-up.
- **`RateOrientationContractCheck`** is WARN-only at v1 launch. Phase 5 should ratify the alert threshold and the path to fail-closed.
- **Hot cache eviction policy** — Caffeine `expireAfterWrite` vs `expireAfterAccess` is not yet pinned; Phase 4 grill should challenge.
