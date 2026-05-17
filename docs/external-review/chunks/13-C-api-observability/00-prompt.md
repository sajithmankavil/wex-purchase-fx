# 00-prompt — Chunk 13-C-api-observability

> Canonical kickoff for the final Phase-13 chunk: M4 (API + ContentGuard + rate-limit) + M5 (observability) + M6 (OpenAPI) + M7 (CI/CD). Pre-staged 2026-05-17 by external reviewer.

## Prerequisite

Chunk B merged to `main`. Both `.human-approvals/{implementation,pci-security}-approved.txt` in place.

## Read first

0. `docs/external-review/` — every file. Specifically:
   - `STATUS.md`
   - `directives/2026-05-17-ops-check-and-pci-check-strict-flip.md`
   - All prior `30-review.md` files in `chunks/13-A1-domain/`, `chunks/13-A2-application/`, `chunks/13-B-infrastructure/`
1. `CLAUDE.md`.
2. `docs/architecture/adr-0001-core-architecture.md` D-13 (detection-and-alert guards), D-15 (rate-limiter), D-19 (encoded-PAN guard direction).
3. `docs/architecture/component-design.md` §3.5 (ContentGuard — **NFKC pre-pass + decoder pipeline + Luhn + track-data**), §4 (`@RestControllerAdvice` for ProblemDetail), §5 (rate-limit filter — must run BEFORE ContentGuard per G8-P0-1), §6 (ArchUnit controller-package rule), §7 (observability and structured logging).
4. `docs/architecture/api-contracts.md` — all 3 endpoints + actuator + RFC 9457 + `application/problem+json` (AC-T-5); `Retry-After: 300 s`; CONV-vs-UPSTREAM table; scale-6 in responses.
5. `docs/requirements/acceptance-criteria.md` — AC-001..AC-009 (HTTP); AC-010, AC-010b/c/d/e (PAN/track/encoded/NFKC); AC-014..AC-020 (conversion endpoints); AC-T-3, AC-T-4 (validation errors); AC-T-5 (problem+json); AC-T-6 (rate-limit-before-guard); AC-027b..e (concurrent dedup); AC-032 / 032b (logging hygiene).
6. `docs/operations/observability.md` §2.4 (server-side `X-Correlation-Id` prefix binding per G4-P1-13); §8 (warm-up after readiness UP).
7. `docs/operations/monitoring-alerting.md` A-021 (PAN-guard false-positive 0.1 % threshold over 30-day window).
8. `docs/operations/runbook.md` — error-budget responses + alert handling.
9. `docs/security/logging-monitoring-pci.md` §5 — redaction; HMAC `vN:` prefix; **no `description` text in logs**.
10. `docs/security/access-control-pci.md` §5 — `X-Request-Identity` header pass-through (audit-log only; no authz yet per OQ-010 Phase-12 closure).
11. `docs/security/secure-config-hardening.md` — `WEX_GATEWAY_REQUIRED` env-var startup check per R-008 mitigation.
12. `docs/security/secure-sdlc-pci.md` §1 — CI gate matrix (SAST + SCA + secret + IaC + container scan + SBOM + DAST + OpenAPI lint).
13. `docs/release/test-plan.md` — promotion-gate matrix.
14. `docs/security/change-control-pci.md` §1 — PR template.

## Scope

### M4 — API + ContentGuard + rate-limit

| Component | Detail |
|---|---|
| `PurchaseController` | `POST /api/v1/purchases` (FR-001); `GET /api/v1/purchases/{id}` (FR-002); `GET /api/v1/purchases/{id}/conversion?currency=…` (FR-003). Pure delegation to application services; no business logic in controller. |
| `WexRateLimiterFilter` | **Servlet `Filter`, NOT controller-method `@RateLimiter`** (per G8-P0-1). `@Order(Ordered.HIGHEST_PRECEDENCE + 100)` so it runs BEFORE `@RestControllerAdvice`. Uses Resilience4j `RateLimiter`. Returns `429 Too Many Requests` with RFC-9457 envelope on breach. **Remove any `@RateLimiter` annotation from controller methods if present in skeletons.** |
| `ContentGuard` (`@RestControllerAdvice`) | Three-step pipeline on `description`: (1) `Normalizer.normalize(description, Form.NFKC)` per G8-P0-3; (2) build candidate set — `[normalised]` plus successful decode through base64-standard / base64-urlsafe / hex / URL-encoded; (3) for each candidate run Luhn (AC-010b), track-data regex (AC-010c). Hit → `400 PAN_PATTERN_DETECTED` with `details.reason="luhn" / "luhn-encoded" / "track-data"`. **Stored `description` is the ORIGINAL input, not the NFKC form.** |
| `ProblemDetailExceptionHandler` (`@RestControllerAdvice`) | RFC 9457 envelopes for every error (AC-T-5). `application/problem+json` always. Mappings: validation → 400 `VALIDATION_FAILED`; PAN/track hits → 400 `PAN_PATTERN_DETECTED` / `TRACK_DATA_DETECTED`; conversion-not-available → 404 `CONVERSION_RATE_NOT_AVAILABLE`; upstream unavailable → 503 `UPSTREAM_UNAVAILABLE` with `Retry-After: 300`; rate-limited → 429. |
| Bean Validation | On request DTOs: bounded length on `description` (50 char per FR-001), required-fields, format constraints. |
| Server-bound `X-Correlation-Id` | Filter binds `<svcInstance>-<clientValueOrNew>` per G4-P1-13; echo bound value in response header. |
| `WEX_GATEWAY_REQUIRED` startup check | Readiness probe refuses UP when `WEX_GATEWAY_REQUIRED=true` and the gateway-trust header pattern is not in place. Per R-008. |

### M5 — Observability

| Component | Detail |
|---|---|
| Structured JSON logs | Logback JSON encoder; mandatory fields: timestamp, level, thread, logger, message, traceId, spanId, correlationId, requestId. **Never log raw `description`** — only `descriptionHash` (HMAC-SHA-256 with `vN:` prefix) + `descriptionLength`. |
| OpenTelemetry SDK | OTel auto-instrumentation for Spring MVC + JDBC + outbound HTTP. Exporter pluggable via env var; default OTLP/HTTP. |
| Metrics | Micrometer + Prometheus. Mandatory metric families: `http.server.requests` (RED), `purchase.create.validation_error.count{reason}` (A-021), `treasury.client.requests` (with `outcome` tag), `exchange_rate.single_flight.dedup_ratio`, `exchange_rate.hot_cache.hit_ratio`, `single_flight.loser_outcome{type}`, `db.connections.{active,max}`. |
| Health probes | `/actuator/health/liveness` + `/actuator/health/readiness`. Readiness DOWN until: DB pool active < max - 1 for ≥ 5 s; warm-up complete; `WEX_GATEWAY_REQUIRED` precondition met. |
| Warm-up job | Per observability.md §8: on container start, after readiness UP, fetch top-10 currencies for the latest quarter-end record date asynchronously. |
| Tail-based sampling | Configured via collector hint per G6-P1-12. Service-side: emit hints (`@important`) on error + slow paths. |

### M6 — OpenAPI

| Component | Detail |
|---|---|
| springdoc-openapi-starter-webmvc-ui | `/v3/api-docs` and `/swagger-ui`. |
| OpenAPI 3.1 spec | All 3 endpoints + actuator. Schemas for `Purchase`, `Conversion`, `ProblemDetail`. Dual-mode currency input. Examples for every response (AC-014 `exchangeRate: "1.370000"` — scale-6). |
| `oasdiff` CI gate | Compares generated OAS to checked-in baseline; fails on breaking change. |
| Spectral lint | OAS lint rules; 0 errors. |

### M7 — CI/CD

| Component | Detail |
|---|---|
| `.github/workflows/ci.yml` | Add: mvn verify, ArchUnit gates, Pitest gates, JaCoCo coverage, OpenAPI generate + oasdiff vs baseline, Spectral lint. Cache Maven deps. |
| `.github/workflows/security.yml` | Add or verify: Semgrep / SAST, OWASP Dependency-Check, GitLeaks (PR + push), Trivy container scan, CycloneDX SBOM. |
| `.github/workflows/deploy-{dev,staging,prod}.yml` | Skeleton wiring only; `workflow_dispatch` trigger; staging + prod gated on `.human-approvals/pci-production-approved.txt` existence via job-level `if:`. |
| pre-commit | GitLeaks pre-commit hook. |
| `Makefile` targets | Verify `make pci-check`, `make ops-check`, `make` wired correctly. |

## PCI-critical invariants verified at this merge

| Invariant | Test |
|---|---|
| **G8-P0-1** rate-limit filter runs BEFORE ContentGuard advice | `RateLimitOrderingIT`: under saturation, `contentguard.invocations.count` does NOT increment. AC-T-6. |
| **G8-P0-3** ContentGuard applies NFKC pre-pass; fullwidth-digit PAN rejected with `details.reason="luhn"` | `ContentGuardNfkcTest`: `"４２４２　４２４２　４２４２　４２４２"` → 400. AC-010e. |
| **G4-P0-5** base64 / hex / URL-encoded PAN rejected | `ContentGuardEncodedTest` (AC-010d). |
| **AC-T-5** every error response is `application/problem+json` RFC 9457 envelope | `ProblemJsonContentTypeTest` parameterized. |
| **AC-032 / 032b** no `description` text in any log; HMAC `vN:` prefix on `descriptionHash` | `LoggingPiiGuardTest`, `LoggingHashKeyTest` running in BOTH `local` and `ci` profiles (G8-P1-10). |
| **AC-T-3** widened Treasury fixtures pass through full HTTP→service→adapter→Treasury stack | `EndToEndTreasuryIT`. |
| **AC-026b** persistence-centric idempotency holds when revision lands between two HTTP calls | `RateRevisionEndToEndIT`. |
| **G4-P1-13** server-side `X-Correlation-Id` binding | `CorrelationIdBindingTest`. |
| **R-008** readiness DOWN when `WEX_GATEWAY_REQUIRED=true` without gateway evidence | `GatewayRequiredReadinessTest`. |

PR description **must** include this invariants table mapped to test class + method names.

## Out of scope

- No new ADRs.
- No production deployment. CI/CD scaffolding only; production-deploy markers still missing by design.
- No identity-aware authorisation (OQ-010 remains BLOCKING-for-prod per Phase 9 §7).
- No multi-region wiring (Phase 12 per G6-P1-7).
- No CSI-driver / Vault Agent integration (Phase 12).
- No image-signing / cosign trust chain (Phase 12).
- No edits to control-bundle files or `.human-approvals/`.

## Acceptance gates

| Gate | Target |
|---|---|
| Unit + integration + WebMvc tests | All green |
| ArchUnit | Layer rules + controller-package locality + no `double`/`float` |
| Pitest | ≥ 70 % package average; **≥ 85 % on `ContentGuard` + `RateSelectionPolicy` + `Money`** |
| Coverage on `web/*` + `config/*` | line ≥ 75 %, branch ≥ 65 % |
| `oasdiff` vs baseline | No breaking change unless explicitly versioned |
| Spectral lint | 0 errors |
| SAST | No HIGH / CRITICAL |
| SCA | No HIGH / CRITICAL without active exception (max 5 per G8-P1-3) |
| Secret scan | Clean |
| Container scan | No HIGH / CRITICAL on base image |
| SBOM | Generated and uploaded as CI artefact |
| `LoggingPiiGuardTest` | Passes in BOTH `local` and `ci` profiles |
| LOC | ≤ ~1,500 (target) / ≤ ~1,800 (hard upper bound); surface if exceeded |

## Branching, PR, CI

- Branch: `feature/chunk-c-api-observability` off `main` (post-B-merge).
- Within the branch, commit per sub-slice (M4 → M5 → M6 → M7) with each sub-slice locally green before moving on.
- All quality scripts + CI green required at PR-ready.

## PR description

```
change-id: WEX-CHUNK-C-api-observability
requirement link: FR-001..FR-006; AC-001..AC-009, AC-010, AC-010b/c/d/e, AC-014..AC-020, AC-T-3/T-4/T-5/T-6, AC-026b, AC-027b..e, AC-032, AC-032b; NFR-002/003 (latency), NFR-011/012 (TLS), NFR-013b/014b/016b/018b (observability + logging), NFR-021 (mutation); ADR-0001 D-13 / D-15 / D-19
risk assessment: Medium-High — HTTP surface exposed first time; PCI-critical content guard + rate-limit filter ordering must be correct (G8-P0-1, G8-P0-3, G4-P0-5). Mitigations: integration tests verify ordering at the servlet-container level; NFKC tests use Unicode fullwidth fixtures; logging-hygiene tests run in both local and CI profiles.
security impact: high. ContentGuard, rate-limit, RFC 9457 envelopes, structured logging redaction, X-Correlation-Id binding, gateway-required readiness gate. Every CHD-shaped injection path tested.
CDE impact: connected-to (audit destination). No CHD stored; guards reject before persistence. Out-of-CDE claim documented in pci-scope-and-cde.md §2.
test evidence:
  - JUnit + WebMvcTest + Testcontainers: <CI URL>
  - RateLimitOrderingIT: <output>
  - ContentGuardNfkcTest (fullwidth → 400): <output>
  - ContentGuardEncodedTest (base64/hex/URL): <output>
  - ProblemJsonContentTypeTest (parameterized): <output>
  - LoggingPiiGuardTest local + ci: <output>
  - oasdiff vs baseline: no breaking change
  - Spectral lint: 0 errors
  - SAST/SCA/Secret/Trivy/SBOM: <links>
  - Pitest ContentGuard / RateSelectionPolicy / Money / package avg: <%>
  - Coverage web/*: line <%>, branch <%>; config/*: line <%>
  - PCI-invariants table: <inline>
approval: <Architect + SecArch + SRE>
rollback class: A (code rollback per rollback-plan.md §4.2). Note: observability changes are config-only and revert cleanly; ContentGuard regex changes are A but require alerting on the validation_error rate post-deploy.
deployment window: maintenance (first HTTP surface live)
post-deploy validation: smoke POST /api/v1/purchases returning 201; smoke GET /api/v1/purchases/{id}/conversion?currency=EUR returning 200; smoke fullwidth-digit PAN returning 400; /actuator/health/readiness UP; rate-limit metric increments; correlation-id round-trips.
```

## Workflow

Per CLAUDE.md §2. Implement sub-slices in order: M4 → M5 → M6 → M7. Each sub-slice locally green before next.

## Completion summary

Write to `chunks/13-C-api-observability/20-summary.md`. CLAUDE.md §9 format. Flip `manifest.status` accordingly.

## Final-chunk note

This is the last Phase-13 chunk. After Chunk C merges, Phase 13 is complete. Phase 10 (Operational Readiness Gate) and Phase 11 (PCI Security Readiness Gate) are next, then Phase 12 produces `pci-production-approved.txt`. Production deployment remains blocked until that marker is created.
