# Requirements Analysis

> Executive consolidated analysis of the WEX Purchase Currency Conversion Service requirement (`source-requirements.md`). Cites and summarises the normative documents — does not duplicate them. This document satisfies `AGENT_PROJECT_INSTRUCTIONS.md` §First-action; the bundle's structured FR/NFR/AC/traceability files remain the authoritative source.
>
> **Change log:**
> - 2026-05-14 — initial analysis, reconciled with `AGENT_PROJECT_INSTRUCTIONS.md`.

## 1. Problem statement

Build a Java service that:

1. **Stores** purchase transactions (description, transaction date, USD amount) and assigns each a unique server-generated identifier.
2. **Retrieves** any stored transaction converted to a target currency using the most recent Treasury Reporting Rates of Exchange rate whose `record_date` is `≤ purchaseDate` and within the prior 6 months.
3. Returns `CONVERSION_RATE_NOT_AVAILABLE` (RFC 9457, status 422) if no eligible rate exists.
4. Is fully runnable locally without separately installed databases, web servers, or servlet containers — i.e., a single-jar, single-command experience using H2 file-mode and Spring Boot's embedded Tomcat.

## 2. Functional summary

Three P0 functional requirements:

- **FR-001** Create purchase — `POST /api/v1/purchases`. Validations enumerated in `functional-requirements.md`. Idempotency-Key optional (P1, OQ-009). PAN-pattern content guard applied to `description`.
- **FR-002** Retrieve by id — `GET /api/v1/purchases/{id}`.
- **FR-003** Retrieve converted — `GET /api/v1/purchases/{id}/conversion?currency={…}`. Currency accepted as Treasury `country_currency_desc` or ISO 4217 alias. HALF_UP rounding to scale 2. Rate-selection rule formalised in FR-003.

Three P1/P2 implicit functional requirements: operability endpoints (FR-004), observability instrumentation (FR-005), API discoverability via OpenAPI (FR-006).

## 3. Non-functional summary

NFRs cover performance (p99 latency anchors per endpoint), availability/SLOs (99.5 % anchor; subject to grill), scalability (stateless app tier; 100 rps/instance anchor), security hygiene (TLS, secrets, validation, dependency risk), PCI scope-reduction evidence as a deliverable, observability (RED/USE metrics, structured logs, distributed tracing, multi-burn-rate SLO alerts), testability (≥ 85 % line, ≥ 75 % branch, Pitest ≥ 70 %), operability (`./mvnw spring-boot:run` runnable), portability (mandated by source), data integrity (BigDecimal-only), maintainability (Checkstyle/PMD/Spotless), API governance (RFC 9457). Full register in `non-functional-requirements.md`.

## 4. Java implementation assumptions

Java 21, Spring Boot 3.x, Spring Web/Validation/Data-JPA, Resilience4j, Micrometer + Actuator, springdoc-openapi. Tests: JUnit 5, AssertJ, Mockito, WireMock, Pitest, ArchUnit, jqwik (property-based for rounding). Migrations: Flyway. Build: Maven (`./mvnw`). Single executable jar with embedded Tomcat and H2 file-mode for the runnable assignment. PostgreSQL-compatible production profile. Package layout per `AGENT_PROJECT_INSTRUCTIONS.md` §4 (`com.example.purchaseconversion.{api,application,domain,infrastructure,config,observability,exception}`). Final stack ratified in Phase 3 ADR-0001.

## 5. API summary

REST/JSON, versioned at `/api/v1`. Endpoints:

- `POST /api/v1/purchases` → `201 Created` + `Location`.
- `GET /api/v1/purchases/{id}` → `200 OK` or `404 PURCHASE_NOT_FOUND`.
- `GET /api/v1/purchases/{id}/conversion?currency=…` → `200 OK` or `404`/`400`/`422`/`502`/`503`.
- `/actuator/health/{liveness,readiness}`, `/actuator/info`, `/actuator/prometheus`.

Errors: RFC 9457 Problem Details with `errorCode` and `details` extension members.

## 6. Persistence summary

H2 file-mode locally; PostgreSQL-compatible production profile. Two tables: `purchase_transactions` and `exchange_rates`. Indexes per `AGENT_PROJECT_INSTRUCTIONS.md` §7 — notably `exchange_rates(country_currency_desc, record_date DESC)` and the `unique(country_currency_desc, record_date)` constraint. Converted amount is never persisted; it is computed at retrieval time. Flyway migrations.

## 7. Treasury API integration summary

Read-only consumer of `https://api.fiscaldata.treasury.gov/services/api/fiscal_service/v1/accounting/od/rates_of_exchange`. Local DB is the primary lookup surface; Treasury is called on cache/DB miss with single-flight de-duplication, bounded retries, bounded timeouts, circuit breaker, schema-validated response, persisted rates. Treasury outage with an eligible local rate must succeed; Treasury outage with no eligible local rate returns `503 UPSTREAM_UNAVAILABLE` (distinguished from `CONVERSION_RATE_NOT_AVAILABLE`).

## 8. Exchange-rate selection rule

```
Among rates for the resolved country_currency_desc:
  Select rates where transactionDate.minusMonths(6) ≤ record_date ≤ transactionDate
                                                                (inclusive both ends, calendar months, EOM-clamped)
  Choose the rate with max(record_date)
  Tie-breaker on duplicate record_date: stable (record_date desc, id asc) — see OQ-002
  If empty → 422 CONVERSION_RATE_NOT_AVAILABLE
```

## 9. Rounding & money handling

`BigDecimal` only. `amountUsd` validated scale exactly 2 (rejected, not rounded, if scale > 2 inbound). `exchangeRate` preserved at full Treasury precision (string in JSON). `convertedAmount = amountUsd × exchangeRate`, intermediate scale ≥ 12, final `HALF_UP` to scale 2. ArchUnit rule forbids `double`/`float` in `domain` and `application` packages.

## 10. Validation rules

Server-side, deny-by-default, RFC 9457 error envelope. Specific rules in `functional-requirements.md` FR-001 input table and `acceptance-criteria.md` AC-002..AC-010b.

## 11. Error-handling requirements

Single error envelope (Problem Details). Errors carry `errorCode` (machine-readable) and `details` (structured context). No stack traces in API responses. Distinct codes for the six expected failure modes: validation (`VALIDATION_ERROR` / `PAN_PATTERN_DETECTED` / `FUTURE_DATE`), not-found (`PURCHASE_NOT_FOUND`), unknown currency (`INVALID_CURRENCY`), conversion-rule failure (`CONVERSION_RATE_NOT_AVAILABLE`), upstream malformed (`UPSTREAM_BAD_RESPONSE`), upstream unavailable (`UPSTREAM_UNAVAILABLE`), idempotency conflict (`IDEMPOTENCY_CONFLICT`), malformed identifier (`MALFORMED_IDENTIFIER`).

## 12. Observability requirements

Structured JSON logs with correlation, log events per `AGENT_PROJECT_INSTRUCTIONS.md` §11, RED/USE metrics, dependency-health metrics for Treasury client and DB pool, OpenTelemetry traces propagating W3C `traceparent` to the Treasury client. SLOs anchored in NFR-005/006 and refined in `operations/slo-sli.md`. Multi-window multi-burn-rate alerts per Google SRE workbook.

## 13. Reliability & resilience requirements

Bounded timeouts, bounded retries with exponential backoff and jitter, circuit breaker on Treasury client, single-flight de-duplication, bulkhead/concurrency protection on outbound thread pool, durable rate persistence so Treasury outages do not break conversions when an eligible rate exists, graceful shutdown that completes in-flight conversions within a bounded drain window. Failure modes enumerated in §9 of `AGENT_PROJECT_INSTRUCTIONS.md` and addressed in `operations/failure-modes-and-resilience.md`.

## 14. Scalability requirements

Stateless app tier; `n>1` replica capability behind shared DB; connection pooling tuned to DB capacity; pagination-aware Treasury client; in-memory cache for hot-path rate lookups *in addition to* DB persistence; design target 100 rps/instance steady-state (proposed). Capacity plan in `operations/capacity-scalability-plan.md`.

## 15. Security & PCI-aware considerations

Service is out-of-CDE; absence of PAN/SAD/CVV/PIN/track data is enforced by design and by the PAN-pattern content guard on `description`. Evidence-driven scope reduction is itself a deliverable (`security/pci-scope-and-cde.md`, `cardholder-data-flow.md`, `cardholder-data-classification.md`, adversarially attacked in `security/pci-security-grill.md`). Tier-1 hygiene applies: TLS-terminating ingress, secrets via env or vault never source, server-side validation, RFC 9457 errors with no stack leakage, dependency-vulnerability scanning, least-privilege DB access. Authn/authz at the app layer is deferred (A-007); identity origin for non-case-study deployments is BLOCKING (OQ-010).

## 16. Automated testing requirements

Unit, integration, contract (WireMock Treasury), resilience (failure-mode tests with simulated faults), API tests, property-based tests for rounding, mutation tests (Pitest). Coverage thresholds enforced in CI: ≥ 85 % line, ≥ 75 % branch, Pitest ≥ 70 % on `domain` and `application`. Performance test automation explicitly excluded by source; load-test plan documented for manual/non-CI execution.

## 17. Production-readiness expectations

Per the bundle's gate sequence: requirements grill → design → design grill → operational design → reliability/scalability grill → PCI security design → PCI security grill → implementation readiness gate → operational readiness gate → PCI security readiness gate → human approval. Each gate has explicit exit criteria recorded in its document. No "production-ready" claim is made unless every relevant gate passes.

## 18. Local runnable assignment constraints

Single command (`./mvnw spring-boot:run` or `java -jar target/wex-purchase-fx.jar`) on a stock JDK 21; no external DB, web server, or servlet container required. H2 file-mode default; in-memory mode reserved for tests. README documents run/test/curl examples.

## 19. Scope reductions & follow-ups

Out-of-scope v1: app-layer authn/authz, multi-tenant isolation, update/delete of purchases, bulk import, webhooks/event publishing, list/pagination endpoint, non-USD source amounts, performance-test automation. Follow-ups tracked in `open-questions.md` and `risk-register.md`.

## 20. Readiness for Requirements Grill (Phase 2)

The artifact set is internally consistent, traceable end-to-end, and contains explicit boundary conditions, rounding/precision rules, error taxonomies, and operational/security postures. **Recommendation: proceed to Phase 2 (Requirements Grill).**

Open items to surface adversarially in Phase 2:

- A-002 / OQ-001 future-date policy (semantic vs operational).
- A-003 / OQ-003 calendar-month vs day-count semantics for the 6-month window (boundary tests differ by ±1 day).
- A-004 HALF_UP vs HALF_EVEN (rounding-bias trade-off; HALF_UP locked but the grill should record the counter-argument).
- A-001 / OQ-016 dual-input alias-table maintenance and drift detection.
- OQ-002 same-date duplicate-record tie-breaker against the live Treasury dataset.
- NFR latency/availability anchors — unproven and subject to grill challenge.
- A-016 / OQ-011 PAN-pattern guard false-positive policy on legitimate digit-heavy descriptions.
- A-007 / OQ-010 no-auth v1 vs prod-deployment identity origin.

