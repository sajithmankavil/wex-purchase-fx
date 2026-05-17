# Non-Functional Requirements

> Source is silent on most NFRs beyond "build as if for Production." That silence is itself a risk. Every NFR below is a **proposed target** with rationale; figures marked **(proposed)** are anchor points to be confirmed during Phase 3 design (capacity plan) and Phase 5 operational design. The requirements grill (Phase 2) must challenge each one.
>
> **Phase-2 (Requirements Grill) additions, 2026-05-14:** NFR-014b (CORS), NFR-016b (audit-log retention/integrity), NFR-017 expanded (HMAC key management), NFR-018b (metric cardinality budget), NFR-028 expanded (data-dir override). See `docs/planning/requirements-grill.md` §6.

| ID | Category | Priority |
|---|---|---|
| NFR-001..004 | Performance & latency | P0 |
| NFR-005..007 | Availability & reliability | P0 |
| NFR-008..010 | Scalability | P1 |
| NFR-011..014 | Security (Tier-1 hygiene) | P0 |
| NFR-015..016 | Compliance — PCI scope reduction evidence | P0 |
| NFR-017..020 | Observability | P0 |
| NFR-021..024 | Testability & quality gates | P0 |
| NFR-025..027 | Operability | P0 |
| NFR-028..029 | Portability / packaging | P0 (mandated by source) |
| NFR-030..031 | Data integrity & retention | P0 |
| NFR-032..033 | Maintainability | P1 |
| NFR-034..035 | API governance | P1 |

---

## Performance & latency

- **NFR-001 (P0).** p99 end-to-end latency for `POST /api/v1/purchases` ≤ **150 ms** at steady-state nominal load (proposed). Excludes Treasury API time (only applies to FR-001).
- **NFR-002 (P0).** p99 end-to-end latency for `GET /api/v1/purchases/{id}` ≤ **80 ms** at steady-state nominal load (proposed).
- **NFR-003 (P0).** p99 end-to-end latency for `GET /api/v1/purchases/{id}/conversion` ≤ **300 ms** at steady-state nominal load on **cache hit**; p99 ≤ **1500 ms** on cache miss (calling Treasury API). Cache-hit ratio target ≥ 95 % steady-state (proposed).
- **NFR-004 (P1).** Cold-start time (process boot to readiness=UP) ≤ **15 s** on the reference container image.

## Availability & reliability

- **NFR-005 (P0).** Service availability SLO ≥ **99.5 %** monthly (proposed; "three-nines-ish" matches a single-region, single-binary case-study posture). Error budget 0.5 % = ~3 h 36 min/month.
- **NFR-006 (P0).** Successful-conversion SLO: ≥ **99.0 %** of converted-retrieve requests for currencies present in the Treasury dataset and dates within Treasury's published window return `200 OK` rather than `503 UPSTREAM_UNAVAILABLE`-due-to-our-fault. Treasury-genuine `CONVERSION_RATE_NOT_AVAILABLE` is not counted as a failure.
- **NFR-007 (P0).** Recovery point objective (RPO) ≤ **5 min** for stored transactions; RTO ≤ **30 min** for a single-region restart. (Acknowledges the source's embedded-DB constraint — see NFR-029.)

## Scalability

- **NFR-008 (P1).** Service shall sustain **100 req/s** mixed read/write per instance at p99 NFR-001/002/003 targets (proposed; refined in Phase 5 capacity plan).
- **NFR-009 (P1).** Horizontal scale: stateless app tier; the only stateful component is the database. A single binary instance must function (case-study constraint), but the design must not preclude `n>1` replicas behind a shared DB.
- **NFR-010 (P1).** Data growth: design to absorb **10⁷ transactions** without redesign (proposed); refined in capacity plan.

## Security (Tier-1 hygiene, out-of-CDE)

- **NFR-011 (P0).** All inbound HTTP supports **TLS 1.3 preferred; TLS 1.2 minimum** (Phase-4 G4-P1-12; raised from TLS 1.2+ floor to reflect 2026 expectations) when fronted by ingress; service-local listener can be `127.0.0.1` HTTP only if the ingress terminates TLS (deployment-mode dependent — see `deployment-architecture.md`).
- **NFR-012 (P0).** All outbound calls to the Treasury API are **TLS 1.3 preferred; TLS 1.2 minimum** (Phase-4 G4-P1-12) with full certificate-chain validation, hostname verification, and pinned CA bundle policy.
- **NFR-013 (P0).** Secrets (none required by source, but any future API keys, DB credentials, encryption keys) are loaded from environment or a secrets provider — never from source, never logged, never echoed in error responses.
- **NFR-013b (P0).** **HMAC log-hash key strength (Phase-4 hardening, H7).** `WEX_LOG_HASH_KEY` MUST carry ≥ **256 bits of entropy** (HMAC-SHA-256 with a key shorter than the digest length is weaker than the construction implies). Production generation: `openssl rand -hex 32` or platform-equivalent. The service does not validate key length at startup (the env value is a string of arbitrary length); the operator owns key-strength compliance per NFR-013 + this addendum. Phase 7 records the generation procedure in `docs/security/secrets-policy.md`.
- **NFR-014 (P0).** Input validation is server-side, deny-by-default, with strict typing and bounded length on all string fields. Free-text fields are HTML/JSON-output-encoded.
- **NFR-014b (P1).** **CORS policy.** Default `local`/`test` profile: no CORS. `prod` profile: CORS is disabled unless `WEX_CORS_ALLOWED_ORIGINS` is set to a comma-separated origin allow-list, in which case those origins (and only those) are permitted with `GET, POST, OPTIONS` and credentials disallowed. Wildcard (`*`) is not supported. (Phase-2 grill G-P1-5; A-022; OQ-020.)

## Compliance — PCI DSS scope reduction (per project decision)

- **NFR-015 (P0).** The service shall not store, process, or transmit PAN, SAD, CVV/CVC, track data, PIN/PIN block, or other PCI account data. The `description` free-text field shall be governed by a **PAN-pattern content guard** at the API boundary that rejects strings containing Luhn-valid 13–19-digit sequences with optional separators, returns `400 PAN_PATTERN_DETECTED`, emits an audit event, and does not log the rejected payload. Allow-list / mask-mode trade-offs are tracked in OQ-011.
- **NFR-016 (P0).** Evidence of out-of-CDE scope shall be maintained in `docs/security/pci-scope-and-cde.md`, with a data-flow diagram in `cardholder-data-flow.md` showing the absence of CHD ingress, and shall be adversarially attacked in `pci-security-grill.md` (Phase 8). The **primary** PCI control is the API contract's prohibition on payment data; the PAN-pattern guard (A-016) and track-data shape guard (AC-010c) are defense-in-depth (A-017; Phase-2 grill G-P0-5).
- **NFR-016b (P1).** **Audit-log retention and integrity.** Audit events (`purchase_validation_failed{reason=pan_pattern|track_data}`, `currency_alias_drift_detected`, `treasury_circuit_open`, `treasury_api_failure{reason=rate_sanity}`, and any future security-relevant events) are retained ≥ **1 year**, with the most recent **3 months** online and searchable. Production deployments use a write-once / append-only / signed log destination (WORM or hash-chained). Read access to the audit log is itself audit-logged and reviewed quarterly. (Phase-2 grill G-P1-6; OQ-022.)

## Observability

- **NFR-017 (P0).** Structured JSON logs with `timestamp` (RFC 3339, UTC), `level`, `logger`, `traceId`, `spanId`, `correlationId`, `event`, and bounded `context` map. No payload logging of the `description` field; only `description.length` and `description.hash` (HMAC-SHA-256 with a per-environment key) for correlation. **HMAC key management (Phase-2 grill G-P1-4; A-020):** the key is loaded from `WEX_LOG_HASH_KEY` env var; mandatory in `prod` / `staging` profiles (refuse-to-start on absence); documented no-op fallback in `local` / `test`. Digest output is prefixed with a key version (`v1:<hex>`, `v0:` for the no-op fallback) so pre- and post-rotation digests are distinguishable. The key value is never committed to source, never logged, and never echoed in error responses. Rotation policy in `docs/security/secrets-policy.md` (Phase 7).
- **NFR-018 (P0).** RED metrics (rate, errors, duration) per endpoint; USE metrics for thread pools and DB connection pool; cache hit/miss/eviction counters for the Treasury rate cache. Additional counters required by Phase-2 grill: `purchase.create.validation_error.count{reason}` (with `reason` ∈ `length|format|positive_required|scale_exceeded|future_date|pan_pattern|track_data|required`) for false-positive feedback loop on the content guard (G-P1-3); `treasury.api.failure.count{reason}` (with `reason` ∈ `timeout|circuit_open|http_5xx|http_4xx|malformed|rate_sanity`); `currency_alias.drift.detected.count`.
- **NFR-018b (P1).** **Metric cardinality budget (Phase-2 grill G-P1-7).** Default RED counters (`http.server.requests`, conversion-outcome counters) do **not** carry the `currency` label; per-currency latency is exposed only on a separately-named histogram `exchange_rate.lookup.duration_by_currency` with a configurable allow-list (default: top-10 by request volume). Combined cardinality budget for the service ≤ **5 000** active series steady-state.
- **NFR-019 (P0).** Distributed tracing across (a) inbound HTTP, (b) DB query, (c) outbound Treasury API call, with parent/child spans correctly propagated. W3C Trace Context headers.
- **NFR-020 (P0).** Dashboards: service health, dependency (Treasury) health, business-criticality (conversions per minute per currency, rate-unavailable rate). Alerts: SLO burn-rate (multi-window multi-burn-rate per Google SRE workbook), Treasury 5xx rate, Treasury latency saturation, p99 latency saturation, DB pool saturation, JVM heap saturation, container restart loop.

## Testability & quality gates

- **NFR-021 (P0).** Line coverage ≥ **85 %** and branch coverage ≥ **75 %** on production code; coverage *not* the only quality signal — mutation-test score (Pitest) ≥ **70 %** on changed packages.
- **NFR-022 (P0).** All FRs map to ≥ 1 unit test and ≥ 1 integration test. Negative-path tests are explicit and named.
- **NFR-023 (P0).** Treasury API is contract-tested with WireMock fixtures pinned to the API's published schema; fixtures cover happy path, empty result, malformed response, 5xx, slow response, and timeout.
- **NFR-024 (P0).** CI is the only place that produces release artifacts; tests are deterministic and run < 5 minutes wall-clock for the unit suite.

## Operability

- **NFR-025 (P0).** Single command (`./mvnw spring-boot:run` or `java -jar`) starts the service with sensible defaults; no external dependencies required.
- **NFR-026 (P0).** Configuration via 12-factor environment variables with documented defaults; secrets never via CLI flags.
- **NFR-027 (P0).** Runbook covers: liveness/readiness, log inspection, common errors, Treasury outage handling, rate-cache reset, DB reset.

## Portability / packaging (mandated)

- **NFR-028 (P0).** Repository must be buildable and runnable without installing a separate database, web server, or servlet container. Maven wrapper (`./mvnw`) is provided; embedded servlet container (Tomcat embedded in Spring Boot); embedded relational DB (H2 file mode by default, optional in-memory for tests). **Data directory (Phase-2 grill G-P1-1; A-021):** the H2 file path is configurable via env var `WEX_DATA_DIR` with default `${user.home}/.wex-purchase-fx/data` to avoid corruption on cloud-sync drives. Startup emits a `WARN` log if the resolved data directory is under a known cloud-sync prefix (`OneDrive`, `Dropbox`, `iCloud Drive`).
- **NFR-029 (P0).** Container image (Distroless or Eclipse Temurin JRE 21) is the **production** packaging; the embedded DB is acceptable for case study but the data model must be DB-engine-agnostic to allow swap to Postgres in a real deployment.

## Data integrity & retention

- **NFR-030 (P0).** All monetary fields are `BigDecimal` scale 2; rate fields are `BigDecimal` with native Treasury precision; arithmetic uses **`HALF_UP`** (per `AGENT_PROJECT_INSTRUCTIONS.md` §5; trade-off vs HALF_EVEN recorded in ADR-0001). No `double`/`float` for money or rates anywhere.
- **NFR-031 (P0).** Default data retention for transactions is **indefinite** for v1 (no PCI/PII retention clock). Soft-delete and retention policy are P2 follow-ups documented in OQ-008.

## Maintainability

- **NFR-032 (P1).** Cyclomatic complexity ≤ 10 per method, ≤ 30 per class; enforced by Checkstyle/PMD/Spotless.
- **NFR-033 (P1).** No `SUPPRESS WARNINGS` without comment-justified rationale; no `TODO` without a tracked issue link.

## API governance

- **NFR-034 (P1).** OpenAPI 3.1 spec generated from code, versioned (`/api/v1/...`); breaking changes require an ADR and a deprecation window.
- **NFR-035 (P1).** All error responses follow [RFC 9457 — Problem Details for HTTP APIs].

