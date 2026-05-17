# Observability Design

> Logs, metrics, traces, dashboards, alerts, synthetic checks, and the correlation rules that hold them together. References [adr-0001-core-architecture.md](../architecture/adr-0001-core-architecture.md) §D-12, [component-design.md](../architecture/component-design.md) §7, [deployment-architecture.md](../architecture/deployment-architecture.md) §7, and the Phase-4 grill's pinned refinements.
> **Status:** Phase 4 (Design Grill), 2026-05-17. Refined in Phase 5 (operational design) — that's where thresholds, alert tuning, and capacity-anchor verification crystallise.

---

## 1. Goals

Make production behaviour **measurable, explainable, debuggable, and actionable**:

- Every request can be traced from inbound HTTP through the database and outbound Treasury client.
- Every business event (purchase created, conversion succeeded/failed, content guard fired, alias drift detected) is a first-class log entry that an operator can grep.
- Every dependency failure (Treasury 5xx / timeout / circuit-open, DB unreachable / pool exhausted) has a metric and an alert.
- Every PII / CHD risk is closed by redaction (NFR-017) — `description` never leaves the trust boundary as plaintext in logs.

This document is the **contract** between the service and its operators. The metric / event / span names below are the source of truth for dashboards and alerts.

---

## 2. Logs

### 2.1 Structured fields (every entry)

| Field | Type | Source | Notes |
|---|---|---|---|
| `timestamp` | ISO-8601 (RFC 3339, UTC, microsecond) | Logback | Server clock. |
| `level` | enum {`TRACE,DEBUG,INFO,WARN,ERROR`} | Logback | `INFO` for events; `WARN` for degradations; `ERROR` for unhandled. |
| `logger` | string | Logback | Java logger name, typically the FQCN. |
| `service` | string | env / config | Always `wex-purchase-fx`. |
| `environment` | string | env | `local`, `test`, `dev`, `staging`, `prod`. |
| `version` | string | build info | e.g. `1.0.3+gitabcd123`. |
| `build_sha` | string | build info | Short git sha. |
| `traceId` | string | OpenTelemetry MDC | W3C trace-id. |
| `spanId` | string | OpenTelemetry MDC | W3C span-id. |
| `correlationId` | string | request header / generated | **Server-bound** value (see §2.3 below). |
| `event` | string | code | Canonical event name (see §2.2). |
| `outcome` | enum {`success,failure,degraded`} | code | Business outcome of the operation, distinct from `level`. |
| `error_code` | string (optional) | code | The RFC-9457 `errorCode` if the request produced one. |
| `latency_ms` | number (optional) | code | Operation duration in milliseconds. |
| `context` | object (bounded) | code | Structured per-event payload; see §2.2 schemas. |

### 2.2 Event taxonomy

Events are emitted at well-known names; dashboards and alerts key on these.

| Event | When | Required `context` | Sensitivity |
|---|---|---|---|
| `service_started` | Process boot completed; readiness UP. | `{ profile, dataDir, treasuryBaseUrl, cacheTtlHours, logHashKeyVersion }` | Operational; safe. |
| `service_stopping` | SIGTERM received. | `{ inFlightHttp, inFlightSingleFlightGates }` | Operational; safe. |
| `purchase_created` | FR-001 success. | `{ purchaseId, transactionDate, amountUsd, descriptionLength, descriptionHash }` | Hashed `description`; never plaintext. |
| `purchase_validation_failed` | FR-001 boundary rejection. | `{ reason }` where `reason ∈ {required,format,length,positive_required,scale_exceeded,future_date,pan_pattern,track1,track2,luhn-encoded}` (Phase-4 expanded set per AC-010d). Rejected payload NOT logged. | Operational. |
| `purchase_retrieved` | FR-002 success. | `{ purchaseId }` | Safe. |
| `purchase_conversion_requested` | FR-003 entry. | `{ purchaseId, currencyInput, currencyResolved }` | Safe. |
| `purchase_conversion_completed` | FR-003 success. | `{ purchaseId, currencyResolved, rateRecordDate, rateValue, convertedAmount }` | Safe. |
| `purchase_conversion_failed` | FR-003 failure. | `{ purchaseId, currencyResolved, reason, errorCode }` where `reason ∈ {not_found,invalid_currency,rate_unavailable,upstream_unavailable,upstream_bad_response,malformed_identifier}` | Safe. |
| `exchange_rate_cache_hit` / `_miss` | Hot-cache hit / miss. | `{ currency, recordDate }` for hits; `{ currency, transactionDate, window }` for misses. | Safe. |
| `exchange_rate_persisted` | Treasury fetch → upsert success. | `{ currency, recordDate, effectiveDate, exchangeRate, source }` | Safe. |
| `exchange_rate_revision_persisted` | Revision row added (max `effective_date` advanced). | `{ currency, recordDate, oldEffectiveDate, newEffectiveDate, oldRate, newRate }` | Safe; used for audit of AC-026b. |
| `treasury_api_request` | Outbound HTTP issued. | `{ currency, windowLower, windowUpper, attempt }` | Safe. |
| `treasury_api_success` | Outbound HTTP 2xx returned. | `{ currency, recordsReturned, latencyMs }` | Safe. |
| `treasury_api_failure` | Treasury error. | `{ currency, reason, attempt, latencyMs, httpStatus? }` where `reason ∈ {timeout,circuit_open,http_5xx,http_4xx,malformed,rate_sanity,orientation_drift}` | Safe. |
| `treasury_circuit_open` / `_half_open` / `_closed` | Resilience4j state transitions. | `{ state, failureRate }` | Safe. |
| `currency_alias_drift_detected` | Daily reconciliation OR per-request Treasury-rejects-alias-known case OR Treasury-returns-renamed-canonical case (AC-021b, AC-021c). | `{ resolvedCanonical, treasuryReturned?, source }` | Safe. |
| `rate_orientation_drift_warn` | `RateOrientationContractCheck` WARN (Phase-3 D-9). | `{ currency, recordDate, expectedFromFixture, observedFromTreasury, delta }` | Safe. |
| `single_flight_loser_polled` | Loser bounded-wait → re-checked DB and succeeded (Phase-4 G4-P1-6). | `{ currency, quarterEnd, waitMs }` | Safe. |
| `single_flight_loser_failed` | Loser bounded-wait → DB still empty → returned same outcome as winner. | `{ currency, quarterEnd, waitMs, outcomeErrorCode }` | Safe. |
| `single_flight_gate_released_on_shutdown` | Phase-4 G4-P1-20 release-on-SIGTERM. | `{ activeGateCount }` | Safe. |
| `readiness_state_changed` | Spring readiness state moves UP↔DOWN. | `{ from, to, reasons }` | Safe. |
| `db_pool_saturated` | Hikari active reaches max for ≥ 5 s (Phase-4 G4-P1-19). | `{ active, max }` | Operational. |
| `log_hash_key_rotation_detected` | First request signed with a `vN+1:` prefix after `vN`. | `{ oldVersion, newVersion }` | Safe. |

### 2.3 `description` redaction (NFR-017 / AC-032 / AC-032b)

- `description` is **never** written to logs in plaintext.
- Every log entry that references a purchase carries `descriptionLength` (integer) and `descriptionHash` (string, format `vN:<lower-hex>`).
- The HMAC-SHA-256 key is loaded from `WEX_LOG_HASH_KEY` (mandatory in `prod`/`staging`; documented no-op fallback prefixed `v0:` for `local`/`test`).
- Rotation changes the prefix (`v1:` → `v2:`). Cross-rotation correlation queries match on the unsalted underlying byte sequence (joining by `descriptionHash` is only valid within a single version tag; log search must filter by tag).
- An ArchUnit / PMD rule (Phase 7 / 13) prevents direct logging of the `description` parameter.

### 2.4 Correlation id (Phase-4 G4-P1-13 refinement)

- Inbound: server reads `X-Correlation-Id` if present.
- Server **binds a server-side prefix**: `<serviceInstance>-<inboundOrGenerated>`. Example: `wex-purchase-fx-abc12-01HXZ7…`.
- The bound id is what is written to logs and echoed in the response `X-Correlation-Id` header.
- This defeats client-controlled correlation re-use as a log-confusion vector. A client replaying an old id still gets a fresh service-instance prefix, so log graphs differentiate.

### 2.5 Redaction summary

- **Never logged:** `description` plaintext; secrets; HMAC key; DB password; full request payloads on validation failures.
- **Logged with controls:** `descriptionHash` + `descriptionLength` (length is a documented residual side-channel, G4-P2-4).
- **Logged plain:** `purchaseId`, `currencyResolved`, `transactionDate`, `amountUsd`, `recordDate`, `effectiveDate`, `exchangeRate`, `convertedAmount`, error codes, error reasons, timing, correlation/trace/span ids.

---

## 3. Metrics

All metrics are emitted via Micrometer with a Prometheus registry, scraped at `GET /actuator/prometheus` on the management port (`MANAGEMENT_PORT=8081` default).

### 3.1 RED (request-rate / errors / duration)

Endpoint-keyed; `outcome` label distinguishes success vs failure. Per Phase-2 / Phase-4 cardinality budget, **`currency` is NOT a label on RED counters**.

| Metric | Type | Labels | Notes |
|---|---|---|---|
| `http.server.requests` | timer (count + duration) | `method`, `uri` (templated), `status`, `outcome` | Spring Boot default. |
| `purchase.create.success.count` | counter | — | Per-event counter. |
| `purchase.create.validation_error.count` | counter | `reason` ∈ {`required,format,length,positive_required,scale_exceeded,future_date,pan_pattern,track1,track2,luhn-encoded`} | Phase-4 expanded set per AC-010d. False-positive feedback loop (G-P1-3). |
| `purchase.conversion.success.count` | counter | — | — |
| `purchase.conversion.failure.count` | counter | `error_code` ∈ {`PURCHASE_NOT_FOUND,INVALID_CURRENCY,CONVERSION_RATE_NOT_AVAILABLE,UPSTREAM_BAD_RESPONSE,UPSTREAM_UNAVAILABLE,MALFORMED_IDENTIFIER`} | Cardinality bounded by the error-code catalogue. |
| `purchase.conversion.unavailable_rate.count` | counter | — | Subset of failure: terminal "no eligible rate" — useful as a business signal distinct from upstream-down. |

### 3.2 Cache and rate-lookup

| Metric | Type | Labels | Notes |
|---|---|---|---|
| `exchange_rate.lookup.success.count` | counter | `source` ∈ {`hot_cache,db,treasury`} | Where the eligible rate ultimately came from. |
| `exchange_rate.lookup.miss.count` | counter | — | Eligible rate not found locally; Treasury fetch attempted. |
| `exchange_rate.cache.hit.count` / `.miss.count` / `.eviction.count` | counter | — | Hot-cache health. |
| `exchange_rate.hot_cache.size` | gauge | — | Live entries (target steady-state ≈ 1 600). |
| `exchange_rate.lookup.duration_by_currency` | timer | `currency` (allow-list from `WEX_METRICS_CURRENCY_ALLOWLIST`, default top-10) | The **only** metric carrying `currency` (NFR-018b cardinality budget). |

### 3.3 Treasury client

| Metric | Type | Labels | Notes |
|---|---|---|---|
| `treasury.client.request.count` | counter | — | Outbound HTTP issued. |
| `treasury.client.duration` | timer | `outcome` ∈ {`success,error,timeout,circuit_open`} | — |
| `treasury.api.failure.count` | counter | `reason` ∈ {`timeout,circuit_open,http_5xx,http_4xx,malformed,rate_sanity,orientation_drift`} | Drives alerts. |
| `treasury.client.circuit_open.count` | counter | — | Increments per state transition into OPEN. |
| `treasury.contract.orientation_drift.count` | counter | `currency` | Phase-3 D-9 contract check; WARN at v1, Phase-5 to ratify fail-closed threshold. |

### 3.4 USE (utilisation / saturation / errors) for pools

| Metric | Type | Labels | Notes |
|---|---|---|---|
| `hikaricp.connections.active` / `.idle` / `.usage` / `.pending` / `.timeout` | gauge / timer | pool name | DB-pool USE; readiness considers `active < max - 1` (Phase-4 G4-P1-19). |
| `jvm.threads.live` / `.daemon` / `.peak` | gauge | — | — |
| `jvm.memory.used` / `.committed` / `.max` | gauge | `area`, `id` | Heap and non-heap. |
| `jvm.gc.pause` | timer | `action`, `cause` | — |
| `tomcat.threads.busy` / `.config.max` | gauge | — | Tomcat connector saturation. |
| `resilience4j.bulkhead.available.concurrent.calls` | gauge | `name` (e.g., `treasury`) | Bulkhead headroom. |
| `resilience4j.circuitbreaker.state` | gauge (0/1/2) | `name`, `state` | CB state per breaker. |

### 3.5 Drift / business signals

| Metric | Type | Labels | Notes |
|---|---|---|---|
| `currency_alias.drift.detected.count` | counter | `kind` ∈ {`reverse_unknown,treasury_renamed`} | AC-021b / AC-021c (Phase-4 G4-P1-8). |
| `treasury.contract.schema_drift.count` | counter | `field` | Nightly schema-pin canary; R-010. |
| `single_flight.loser.polled.count` | counter | `outcome` ∈ {`success,failed,timeout`} | Phase-4 G4-P1-6. |
| `single_flight.gate.released_on_shutdown` | counter | — | Phase-4 G4-P1-20. |
| `description.content_guard.fired.count` | counter | `reason` | Mirrors `purchase.create.validation_error.count{reason=pan_pattern|track1|track2|luhn-encoded}` for security-team-side dashboards. |

### 3.6 Cardinality budget

Combined active series ≤ **5 000** steady-state (NFR-018b). Concrete budget:

| Source | Worst-case cardinality |
|---|---|
| `http.server.requests` × ~ 10 routes × 6 status classes × 2 outcomes | ~ 120 |
| `purchase.create.validation_error.count` × 10 reasons | 10 |
| `purchase.conversion.failure.count` × 6 error_codes | 6 |
| `exchange_rate.lookup.success.count` × 3 sources | 3 |
| `exchange_rate.lookup.duration_by_currency` × ≤ 10 allow-listed currencies × 1 histogram | ≤ 10 series + histogram buckets |
| `treasury.api.failure.count` × 7 reasons | 7 |
| `resilience4j.circuitbreaker.state` × 1 breaker × 3 states | 3 |
| `hikaricp.*` × pool | ~ 10 |
| `jvm.*`, `process.*`, `tomcat.*` | ~ 60 |
| Headroom for histogram buckets and minor labels | ~ 600 |

Total well under 5 000. The budget is enforced as a soft rule in `MetricsCatalog` (component-design.md §7) at v1; Phase 5 promotes to a hard build-time check.

---

## 4. Traces

OpenTelemetry instrumentation. W3C `traceparent` propagation.

| Span | Source | Attributes |
|---|---|---|
| Inbound HTTP | Spring auto-instrumentation | `http.method`, `http.route`, `http.status_code`, `http.user_agent`, `wex.correlation_id` |
| Application service method | Manual `@WithSpan` on `PurchaseService` / `ConversionService` | `wex.purchase.id`, `wex.currency.input`, `wex.currency.resolved` |
| DB statement | OTel JDBC instrumentation | `db.statement` (parameterised), `db.system=postgresql\|h2`, `db.operation` |
| Treasury HTTP | RestClient OTel instrumentation | `http.method`, `http.url`, `http.status_code`, `wex.treasury.attempt`, `wex.treasury.outcome` |
| Single-flight gate | Manual span | `wex.gate.currency`, `wex.gate.quarter_end`, `wex.gate.role` ∈ {`winner,loser`}, `wex.gate.wait_ms` |
| Content-guard pre-pass | Manual span on `ContentGuard.check` | `wex.guard.outcome`, `wex.guard.reason?` |

Sampling: 100 % at v1 (low volume). Phase 5 ratifies tail-based sampling on volume.

---

## 5. Dashboards

Definitions live in `operations/monitoring-alerting.md` (Phase 5 fills the JSON). Required dashboards:

| Dashboard | Primary users | Required panels |
|---|---|---|
| **Service health** | On-call SRE | RED per endpoint; p50 / p95 / p99 latency; 5xx rate; JVM heap; GC pause time; thread-pool saturation; Tomcat busy threads. |
| **Dependency health (Treasury)** | On-call SRE | Treasury request rate; success / 4xx / 5xx / timeout breakdown; CB state timeline; bulkhead concurrent calls; latency histogram; orientation-drift counter; schema-drift counter. |
| **DB & cache health** | On-call SRE | Hikari active / idle / pending / timeout; pool saturation; hot-cache size, hit / miss / eviction rates; rate-lookup source breakdown. |
| **Business workflow** | Product + SRE | Conversions per minute (overall); conversions per minute per allow-listed currency; `CONVERSION_RATE_NOT_AVAILABLE` rate; alias-drift events; content-guard fires by reason. |
| **Rollout health** | On-call SRE | Per-version request rate and error rate during deploys; readiness transitions; release-graceful-shutdown counter. |

Each panel cites a metric from §3 and a recommended alert tying back to §6.

---

## 6. Alerts

Thresholds are **anchors**; Phase 5 ratifies. Severity ladder: P1 (page on-call), P2 (ticket on-call), P3 (informational).

| Alert | Signal | Anchor threshold | Severity |
|---|---|---|---|
| Service 5xx rate | `http.server.requests{status=5xx} / total` | > 1 % over 5 min | P1 |
| p99 latency saturation (`POST /purchases`) | `http.server.requests.duration{uri=/api/v1/purchases,quantile=0.99}` | > 2× NFR-001 anchor for 10 min | P2 |
| p99 latency saturation (`GET /…/conversion`, cache-hit) | percentile from `purchase.conversion.success.count + duration_by_currency` | > 2× NFR-003 cache-hit anchor for 10 min | P2 |
| Multi-window multi-burn-rate availability SLO | Google SRE workbook: 5m@14× + 1h@6× | per workbook | P1 |
| Conversion `UPSTREAM_UNAVAILABLE` rate | `purchase.conversion.failure.count{error_code=UPSTREAM_UNAVAILABLE}` | > 1 % over 5 min | P1 |
| Treasury CB open | `resilience4j.circuitbreaker.state{state=open}` | == 1 sustained > 60 s | P2 |
| Treasury 5xx rate | `treasury.api.failure.count{reason=http_5xx} / treasury.client.request.count` | > 5 % over 10 min | P2 |
| Treasury contract orientation drift | `treasury.contract.orientation_drift.count` | > 0 in last 24 h (Phase-5 promotes to fail-closed per G4-P1-27) | P2 |
| Treasury schema drift | `treasury.contract.schema_drift.count` | > 0 in last 24 h | P2 |
| Hot cache hit rate degraded | `exchange_rate.cache.hit / (hit + miss)` | < 80 % over 30 min | P3 |
| Hikari pool saturated | `hikaricp.connections.pending` | > 0 sustained > 60 s | P1 |
| JVM heap saturation | `jvm.memory.used / max` (heap) | > 80 % sustained 5 min | P2 |
| Readiness flapping | `readiness_state_changed` (count) | > 3 transitions in 5 min | P1 |
| Alias drift detected | `currency_alias.drift.detected.count` | > 0 in last 24 h | P3 |
| Content guard fires | `description.content_guard.fired.count{reason}` | > 10 in 5 min (potential probe) | P2 |
| Bulkhead saturated | `resilience4j.bulkhead.available.concurrent.calls` | == 0 sustained > 30 s | P2 |
| Service restart loop | Container restart count | > 3 in 15 min | P1 |
| Log-hash key rotation observed | `log_hash_key_rotation_detected` | informational | P3 |

Alert routing: P1 → pager; P2 → ticket queue; P3 → daily digest. Concrete routing in Phase 5.

---

## 7. Synthetic checks

Black-box probes to detect end-to-end regressions independent of internal metrics.

| Check | Frequency | What it does | Pass criteria |
|---|---|---|---|
| **Conversion canary** | every 5 min | `POST` a synthetic purchase; `GET /…/conversion?currency=CAD` against canonical Treasury rate (recorded fixture); compare `convertedAmount` to expected. | 200 + amount within ±1 cent of expected. |
| **Liveness probe** | every 30 s (platform) | `GET /actuator/health/liveness` | 200 + `UP`. |
| **Readiness probe** | every 30 s (platform) | `GET /actuator/health/readiness` | 200 + `UP`. |
| **Treasury upstream canary** | every 1 h | `GET https://api.fiscaldata.treasury.gov/services/api/fiscal_service/v1/accounting/od/rates_of_exchange?…` directly (synthetic, not via app) | 2xx + non-empty result. (Separates "Treasury down" from "us down".) |
| **Rate-orientation canary** (Phase 5) | weekly | Fetch CAD / EUR / JPY; assert multiplicative formula against fixture set; alert on drift. | All three within fixture tolerance. |
| **OpenAPI lint** | per release | `spectral lint /v3/api-docs` | No errors. |

---

## 8. Warm-up (Phase-4 G4-P1-21)

On container start, after `readiness=UP`, the warm-up job runs in a single startup task:

1. Read the top-N currency list from config (`WEX_WARMUP_CURRENCIES`, default = top-10 by historical request volume; v1 default: `["Canada-Dollar","Euro Zone-Euro","Japan-Yen","UK-Pound Sterling","Australia-Dollar","Mexico-Peso","China-Yuan Renminbi","India-Rupee","Brazil-Real","Switzerland-Franc"]`).
2. For each currency, issue a `findEligibleRate(currency, today, today.minusMonths(6))` call; if local DB has no eligible rate, trigger a Treasury fetch under the single-flight gate.
3. Emit a `warmup_completed` log event with counts of hits / misses / fetches and duration.

Warm-up is bounded by `WEX_WARMUP_TIMEOUT_SECONDS` (default 30 s); on timeout the service stays UP and emits a WARN. Phase 5 ratifies N and the warmup currency list.

---

## 9. Operator runbook anchors (this section feeds `operations/runbook.md`)

| Symptom | First step | Likely cause | Reference |
|---|---|---|---|
| 5xx rate spike on `POST /purchases` | `tail` JSON logs for `event=purchase_conversion_failed` and `event=db_pool_saturated`. | DB pool exhaustion (G4-P1-19) or recent migration. | rollback-plan.md §Rollback class B (config) / C (schema). |
| 5xx rate spike on `GET /…/conversion` with `error_code=UPSTREAM_UNAVAILABLE` | Check Treasury canary; check CB state. | Treasury down. | Wait for recovery; CB will close automatically. Verify `Retry-After` is being respected by clients. |
| Hot-cache hit rate below 80 % steady-state | Check `exchange_rate.hot_cache.size` and recent restart events. | Recent rolling deploy → cold caches. | Verify warm-up job ran (`warmup_completed` event); if not, investigate. |
| Readiness flapping | Inspect `readiness_state_changed` reasons. | Most commonly: DB pool saturation, or alias table re-load failure. | Stabilise; consider rollback. |
| Treasury orientation drift WARN | Check `rate_orientation_drift_warn` events. | Treasury added / changed a currency's convention. | Investigate manually; the working assumption is currency-specific; ADR-0001 D-4 fixture needs review. |
| Alias drift detected | Check `currency_alias_drift_detected` events. | Treasury renamed a `country_currency_desc`. | Open a PR updating `currency-aliases.json`. |
| Restart loop | Check `service_started` vs `service_stopping`; check `WEX_LOG_HASH_KEY` presence in `prod` profile. | Refuse-to-start on missing env. | Restore the env var; restart. |

---

## 10. Forward-looking gaps (Phase 5 / Phase 7 to close)

- **Alert threshold ratification.** Most alert thresholds above are anchors; Phase 5 (operational design) ratifies against capacity-plan data.
- **`RateOrientationContractCheck` fail-closed threshold.** Phase 5 (G4-P1-27).
- **Tail-based sampling for traces.** Phase 5.
- **CB calibration.** Phase 5 (G4-P1-17).
- **Cardinality enforcement.** Phase 5 promotes the soft policy to a hard build-time check.
- **Audit-log destination + tamper-evident mechanism.** Phase 7 (G4-P1-16).
- **HMAC key sourcing in prod.** Phase 7 (G4-P1-11).
- **Warm-up currency list + N.** Phase 5 (G4-P1-21).

## 11. Linked artefacts

- [docs/architecture/adr-0001-core-architecture.md](../architecture/adr-0001-core-architecture.md) §D-12 — observability decisions.
- [docs/architecture/component-design.md](../architecture/component-design.md) §7 — cross-cutting properties; §3.4 — readiness probe definition.
- [docs/architecture/deployment-architecture.md](../architecture/deployment-architecture.md) §7 — telemetry sinks; §8 — health checks.
- [docs/planning/design-grill.md](../planning/design-grill.md) — Phase-4 findings driving the refinements here.
- [docs/operations/monitoring-alerting.md](monitoring-alerting.md) — Phase-5 deliverable (concrete alert routing, dashboard JSON).
- [docs/operations/slo-sli.md](slo-sli.md) — Phase-5 deliverable (SLO mapping).
- [docs/operations/runbook.md](runbook.md) — Phase-5 deliverable (full runbook narratives).
- [docs/security/threat-model.md](../security/threat-model.md) — STRIDE catalogue; references this doc for telemetry-driven detections.
