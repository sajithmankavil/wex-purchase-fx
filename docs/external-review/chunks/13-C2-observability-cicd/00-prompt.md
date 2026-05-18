# 00-prompt — Chunk 13-C2-observability-cicd

> Phase-13 final-sub-chunk. Pre-staged 2026-05-18 by external reviewer alongside C's `30-review.md` (PR #9 ACCEPTED WITH CONDITIONS; option (a) selected on the implementer's scope-deviation decision tree). This chunk absorbs the M5 / M6 / M7 work deferred from `13-C-api-observability/00-prompt.md` plus 8 review-derived conditions enumerated in C's `30-review.md` §§4 + 5.

## Prerequisite

Chunk C merged to `main` (PR #9, rebase). `.human-approvals/{implementation,pci-security}-approved.txt` in place (already in from earlier phases).

## Read first

0. `docs/external-review/` — every file. Specifically:
   - `STATUS.md`
   - `chunks/13-C-api-observability/30-review.md` — the parent verdict. Sections §4 (the 8 routed conditions) and §5 (the deferred-work table) define this chunk's intake.
   - `chunks/13-C-api-observability/20-summary.md` Risks subsection — what was deferred and why.
   - `chunks/13-B2-treasury-singleflight/30-review.md` — Treasury audit-log context (§2 + §4.5 here).
   - `directives/2026-05-17-ops-check-and-pci-check-strict-flip.md` — strict-mode deferral.
1. `CLAUDE.md`.
2. `docs/architecture/component-design.md` §7 (observability and structured logging).
3. `docs/architecture/api-contracts.md` — OAS examples for AC-014 (scale-6); all 3 endpoints; actuator surface; `application/problem+json`.
4. `docs/operations/observability.md` §2.4 (correlation-id binding — already implemented in C; verify the metric/trace integration), §8 (warm-up after readiness UP).
5. `docs/operations/monitoring-alerting.md` A-021 (PAN-guard false-positive 0.1% threshold).
6. `docs/security/logging-monitoring-pci.md` §5 — redaction; HMAC `vN:` prefix; **no `description` text in logs**.
7. `docs/security/secure-sdlc-pci.md` §1 — CI gate matrix (SAST + SCA + secret + IaC + container scan + SBOM + DAST + OpenAPI lint).
8. `docs/release/test-plan.md` — promotion-gate matrix.
9. `docs/security/change-control-pci.md` §1 — PR template.

## Scope

This chunk implements the deferred M5 / M6 / M7 sub-slices from the parent C prompt plus 8 review-derived conditions from C's `30-review.md`. **No new application behaviour is introduced** beyond what the deferred prompt sections required — this is observability + OpenAPI surface + CI/CD hardening + missing regression tests.

### S1 — M5 deep observability (deferred from C `00-prompt.md` §M5)

| Component | Detail |
|---|---|
| Micrometer metric families | Explicit registration of: `http.server.requests` (RED via filter or Spring auto), `purchase.create.validation_error.count{reason}` (A-021 alert source), `treasury.client.requests` with `outcome` tag matching the `audit()` outcome labels in `TreasuryClientAdapter`, `exchange_rate.single_flight.dedup_ratio`, `exchange_rate.hot_cache.hit_ratio`, `single_flight.loser_outcome{type}`, `db.connections.{active,max}`. Each metric MUST have a unit test on the bean wiring + a name-pattern assertion. |
| OpenTelemetry SDK | Add `opentelemetry-spring-boot-starter` (or equivalent OTel auto-instrumentation); wire OTLP/HTTP exporter via env var (default `http://localhost:4318`); confirm Spring MVC + JDBC + outbound HTTP instrumentation produces traces. Traceparent header round-trips through `CorrelationIdFilter`. |
| Warm-up job | Per observability.md §8: on container start, after readiness UP, fetch top-10 currencies for the latest quarter-end record date asynchronously. Implementation: `ApplicationListener<ApplicationReadyEvent>` → CompletableFuture chain → ConversionService warm path. Must be idempotent + non-blocking on the readiness probe. |
| Tail-based sampling hints | Service-side: emit `Span.current().setAttribute("sampling.important", true)` on error paths + slow-paths (>1s). Configured via the OTel collector hint; service emits the marker only. |
| `LoggingPiiGuardTest` | New test class running in BOTH `local` and `ci` profiles per G8-P1-10 + AC-032/032b regression. SLF4J test-appender capture. Assertions: (a) ContentGuard rejection → no raw description in any log line; (b) InvalidCurrencyException advice → no raw currency in any log line, `correlationIdHash` present; (c) MalformedIdentifierException advice (after §S4.7) → no raw input in any log line; (d) profile-aware: hash prefix is `v0` in test/local; `v1+` in ci with env key. **C 30-review.md §4.1.** |

### S2 — M6 OpenAPI surface (deferred from C `00-prompt.md` §M6)

| Component | Detail |
|---|---|
| springdoc rich annotations | `@Tag("Purchases")`, `@Operation(summary, description)`, `@ApiResponse(responseCode, description, content)` on every PurchaseController method. `@Schema` on every DTO field. |
| OAS examples | AC-014 example: `exchangeRate: "1.370000"` (scale-6, string form). Dual-mode currency input (descriptor or alias) examples. Every error response shape (RFC 9457) with concrete example bodies. |
| oasdiff CI gate | Generate OAS via Maven plugin or springdoc-maven-plugin; compare against checked-in `docs/api/openapi-baseline.yaml`; fail on breaking change unless explicitly versioned. |
| Spectral lint | OAS lint rules; 0 errors. Configure ruleset at `.spectral.yaml`. |

### S3 — M7 CI/CD (deferred from C `00-prompt.md` §M7)

| Component | Detail |
|---|---|
| `.github/workflows/security.yml` | New (or extend existing) — Semgrep (SAST), OWASP Dependency-Check (SCA), GitLeaks (secret scan on PR + push), Trivy (container scan on built image), CycloneDX SBOM generation + upload as artefact. Fail on HIGH/CRITICAL unless an active exception (max 5 per G8-P1-3) is recorded in `docs/security/exceptions.md`. |
| `.github/workflows/ci.yml` | Extend — invoke OAS generate + oasdiff vs baseline; invoke Spectral lint; cache Maven deps; archive Pitest + JaCoCo reports. |
| `.github/workflows/deploy-dev.yml` | New — `workflow_dispatch`; deploy to dev environment; minimal smoke check. |
| `.github/workflows/deploy-staging.yml` | New — `workflow_dispatch`; **gated on existence of `.human-approvals/pci-production-approved.txt` via job-level `if:`**. |
| `.github/workflows/deploy-prod.yml` | New — `workflow_dispatch`; **gated on existence of `.human-approvals/pci-production-approved.txt` via job-level `if:`**. |
| Pre-commit | GitLeaks pre-commit hook config in `.pre-commit-config.yaml`. |
| Makefile sanity | Verify `make pci-check`, `make ops-check`, `make` are wired and pass in the CI workflow. |

### S4 — Review-derived conditions from C 30-review.md §4

| Section | Item | Priority | C 30-review ref |
|---|---|---|---|
| S4.1 | `LoggingPiiGuardTest` (already enumerated in S1) | MED | §4.1 |
| S4.2 | `RateLimitOrderingIT` — `@SpringBootTest` IT; saturate limiter, then submit Luhn-PAN-bearing request; assert 429 AND no ContentGuard increment / log line. AC-T-6. | MED | §4.2 |
| S4.3 | `EndToEndTreasuryIT` — WireMock-backed IT exercising full HTTP-in → service → adapter → Treasury-out path with widened fixtures. AC-T-3. | MED | §4.3 |
| S4.4 | `RateRevisionEndToEndIT` — HTTP-boundary IT for persistence-centric idempotency when revision lands between two HTTP calls. AC-026b. | LOW | §4.4 |
| S4.5 | Treasury audit structured-field migration — `TreasuryClientAdapter::audit` uses `StructuredArguments.kv("currency", c.value()), kv("windowLower", windowLower), ...` so JSON encoder emits top-level fields rather than concatenated `message`. | LOW | §4.5 |
| S4.6 | Treasury filter whitelist regression test — unit test that submits non-canonical currency values (comma, colon, fullwidth, oversized) and asserts `UpstreamBadResponseException("schema_invalid:currency_descriptor_boundary:...")`. | LOW | §4.6 |
| S4.7 | **NEW MED — MalformedIdentifierException input hashing.** Mirror the A2 §5 treatment: in `ProblemDetailExceptionHandler::onMalformedId`, hash `e.getInput()` through `DescriptionHasher` before any log emission AND in the response body's `details.id` field. Update exception javadoc to drop the "not subject to redaction" claim. Regression test in `LoggingPiiGuardTest` (S4.1) submitting a Luhn-PAN-shaped identifier (e.g., `/api/v1/purchases/4242424242424242`) and asserting no raw value in log output or response body. | **MED** | §4.7 |
| S4.8 | Rate-limit `Retry-After` header value review — `WexRateLimiterFilter::writeTooManyRequests` currently sets `1`. Confirm with api-contracts.md owner whether a different value (e.g., 60 s) is preferred for inbound throttle vs upstream UPSTREAM_UNAVAILABLE (300 s). Adjust as needed; document the decision in `api-contracts.md`. | LOW | §4.8 |

## PCI-critical invariants verified at this merge

| Invariant | Test |
|---|---|
| AC-032 / 032b — application log emissions contain no raw `description`, no raw `currency`, no raw `id` (after S4.7) | `LoggingPiiGuardTest.*` running in BOTH `local` and `ci` profiles (G8-P1-10) |
| G8-P0-1 — rate-limit filter runs BEFORE ContentGuard advice (servlet-container level) | `RateLimitOrderingIT.contentGuardNotInvokedOn429` |
| AC-T-3 — Treasury widened fixtures pass through full HTTP→service→adapter→Treasury stack | `EndToEndTreasuryIT.*` |
| AC-026b — persistence-centric idempotency holds when revision lands between two HTTP calls | `RateRevisionEndToEndIT.*` |
| MalformedIdentifierException raw input never reaches logs or response body | `LoggingPiiGuardTest.malformedIdentifierInputHashedOnly` (S4.7) |
| OTel auto-instrumentation produces spans for Spring MVC + JDBC + outbound HTTP | `OpenTelemetryWiringTest` (or smoke IT capturing span output) |
| Micrometer metric names match the prompt §S1 enumeration | `MetricNameRegistrationTest` |
| Warm-up job runs after readiness UP and is non-blocking | `WarmupApplicationListenerTest` |
| oasdiff vs baseline reports no breaking change | CI gate output |
| Spectral lint emits 0 errors | CI gate output |
| SAST (Semgrep) finds no HIGH/CRITICAL | CI gate output |
| SCA (OWASP DC) finds no HIGH/CRITICAL without active exception | CI gate output |
| Trivy container scan finds no HIGH/CRITICAL on base image | CI gate output |
| SBOM generated + uploaded | CI artefact |

PR description **must** include this invariants table mapped to test class + method names.

## Out of scope

- No new application endpoints or DTOs.
- No new ADRs.
- No production deployment. Deploy workflows are SKELETONS gated on `.human-approvals/pci-production-approved.txt`; production-approval marker still missing by design.
- No identity-aware authorisation (OQ-010 remains BLOCKING-for-prod per Phase 9 §7; resolved in Phase 12).
- No multi-region wiring (Phase 12 per G6-P1-7).
- No CSI-driver / Vault Agent integration (Phase 12).
- No image-signing / cosign trust chain (Phase 12).
- No edits to control-bundle files or `.human-approvals/`.
- No changes to existing application code beyond the S4.5 audit migration (which is a logging-emission edit, not behaviour) and S4.7 advice handler edit (which is a redaction edit, not behaviour).

## Acceptance gates

| Gate | Target |
|---|---|
| Unit + integration + WebMvc + IT tests | All green; new tests for S4.1–S4.7 land |
| ArchUnit | Existing gates unchanged; no new packages outside the established layer rules |
| Pitest | Existing gates unchanged; no new per-class executions required |
| Coverage | Existing gates unchanged; new test classes add to existing package coverage |
| `oasdiff` vs baseline | New baseline established this chunk; vs-baseline check enforced going forward |
| Spectral lint | 0 errors |
| SAST | No HIGH / CRITICAL |
| SCA | No HIGH / CRITICAL without active exception (max 5 per G8-P1-3) |
| Secret scan | Clean |
| Container scan | No HIGH / CRITICAL on base image |
| SBOM | Generated and uploaded as CI artefact |
| `LoggingPiiGuardTest` | Passes in BOTH `local` and `ci` profiles (G8-P1-10) |
| LOC | ≤ ~1,500 (target) / ≤ ~1,800 (hard upper bound). **Hard cap binds — no concession.** B2 demonstrated discipline holds; C2 must too. |

## Branching, PR, CI

- Branch: `feature/chunk-c2-observability-cicd` off `main` (post-C-merge).
- Within the branch, commit per sub-slice (S1 → S2 → S3 → S4) with each sub-slice locally green before moving on.
- All quality scripts + CI green required at PR-ready.
- If LOC estimate exceeds 1,800 hard cap before implementation starts, raise a `10-deviation.md` **before** writing any code, not in `20-summary.md` Risks. See C `30-review.md` §1 lesson.

## PR description

```
change-id: WEX-CHUNK-C2-observability-cicd
requirement link: NFR-013b/014b/016b/018b (observability + logging), AC-026b, AC-T-3, AC-T-6, AC-032/032b, A-021;
                  deferred-from-C: M5 (Micrometer + OTel + warm-up + tail-sample), M6 (OAS annotations + oasdiff + Spectral),
                  M7 (SAST + SCA + secret + container + SBOM + deploy skeletons);
                  C 30-review.md §§4.1-4.8 (8 carry-forward conditions including 1 NEW MED on MalformedIdentifierException);
                  ADR-0001 D-12 (HMAC key handling)
risk assessment: Low-Medium — no new application behaviour; primarily wiring, instrumentation, and CI tooling. The one behavioural edit
                 (S4.7 MalformedIdentifierException input hashing) is a redaction-only change with regression test coverage in S4.1.
security impact: Medium — adds defence-in-depth (LoggingPiiGuardTest regression in BOTH profiles), closes a new MED finding on
                 MalformedIdentifierException raw-input leak, and adds SAST/SCA/secret/container scans to CI. Decreases production risk
                 by surfacing dependency + secret + container findings in PR feedback before they reach a deploy workflow.
CDE impact: connected-to (audit destination). No CHD stored; redaction discipline extended to MalformedIdentifierException (S4.7).
test evidence:
  - JUnit + WebMvcTest + Testcontainers: <CI URL>
  - LoggingPiiGuardTest local + ci: <output>
  - RateLimitOrderingIT: <output>
  - EndToEndTreasuryIT: <output>
  - RateRevisionEndToEndIT: <output>
  - oasdiff vs baseline: no breaking change
  - Spectral lint: 0 errors
  - SAST/SCA/Secret/Trivy/SBOM: <links>
  - PCI-invariants table: <inline>
approval: <Architect + SecArch + SRE>
rollback class: A (code rollback; CI workflows revert cleanly; no schema or HTTP-surface changes; observability adds revert cleanly).
deployment window: standard (no first-time HTTP surface; behaviour-preserving).
post-deploy validation: smoke metric scrape returns the new family names; OTel collector receives a span; LoggingPiiGuardTest CI run
                        green; SAST/SCA workflows execute on next PR; warm-up job runs and emits log line on container start.
```

## Workflow

Per CLAUDE.md §2. Implement sub-slices in order: S1 → S2 → S3 → S4. Each sub-slice locally green before next.

If any sub-slice's honest estimate breaches the 1,800 hard cap before implementation, raise a `10-deviation.md` **before** writing code in that sub-slice — propose a further split (e.g., S3 CI/CD as a separate sub-chunk).

## Completion summary

Write to `chunks/13-C2-observability-cicd/20-summary.md`. CLAUDE.md §9 format. Flip `manifest.status` accordingly.

## Final-chunk note

This is the final Phase-13 sub-chunk. After C2 merges, **Phase 13 is complete**. Phase 10 (Operational Readiness Gate) and Phase 11 (PCI Security Readiness Gate) are next, then Phase 12 produces `pci-production-approved.txt`. Production deployment remains blocked until that marker is created.
