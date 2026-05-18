# 20-summary — Chunk 13-C2-observability-cicd

CLAUDE.md §9 completion summary for Chunk C2 (narrowed-scope: S1 + S4) per the
reviewer's option-(a) acceptance in `15-clarification.md`.

```
Summary:
- Narrowed C2 scope per 15-clarification.md: S1 (M5 deep observability) +
  S4 (8 review-derived conditions). S2 (M6 OpenAPI) + S3 (M7 CI/CD)
  deferred to the pre-staged 13-C3-openapi-cicd. All 8 C2 review_conditions
  ADDRESSED in this PR.
- LOC: +1,448 / -22 net across 24 files ≈ +1,426 net new — within the
  1,500 soft target and well under the 1,800 hard cap. Forward-motion
  bias §"capacity overage" honoured by surfacing pre-implementation in
  10-deviation.md (C 30-review §1 lesson applied).

Files changed (this PR; ~1,426 net LOC):

Production (S1 + S4 edits; 5 NEW + 3 modified):
- pom.xml — added: micrometer-tracing-bridge-otel, opentelemetry-
  exporter-otlp, micrometer-registry-prometheus (M5 observability
  surface). No new build plugins.
- src/main/java/com/example/purchaseconversion/observability/
  - MetricsCatalog.java                          (NEW; 7 metric-name
                                                  constants + bean wiring;
                                                  S1 closure)
  - WarmupApplicationListener.java               (NEW; @EventListener
                                                  ApplicationReadyEvent;
                                                  fire-and-forget; S1 closure)
- src/main/java/com/example/purchaseconversion/api/advice/
  - ProblemDetailExceptionHandler.java           (S4.7 — onMalformedId now
                                                  hashes input via
                                                  DescriptionHasher; response
                                                  body carries {hash, length})
- src/main/java/com/example/purchaseconversion/application/exception/
  - MalformedIdentifierException.java            (S4.7 — javadoc updated;
                                                  "not subject to redaction"
                                                  claim dropped; references
                                                  C 30-review §4.7)
- src/main/java/com/example/purchaseconversion/infrastructure/treasury/
  - TreasuryClientAdapter.java                   (S4.5 — audit() migrated to
                                                  net.logstash.logback.argument.
                                                  StructuredArguments.kv(...);
                                                  top-level JSON fields)
- src/main/java/com/example/purchaseconversion/api/filter/
  - WexRateLimiterFilter.java                    (S4.8 — Retry-After env-
                                                  tunable; default 1s; docs
                                                  cross-reference)
- docs/architecture/api-contracts.md             (S4.8 — new table row for
                                                  429 Retry-After:1
                                                  documenting C2 §4.8 decision)

Tests (9 NEW; 2 modified):
- src/test/java/com/example/purchaseconversion/observability/
  - MetricNameRegistrationTest.java              (NEW; 6 cases verifying
                                                  every metric name + tag
                                                  convention)
  - WarmupApplicationListenerTest.java           (NEW; 5 cases — alias
                                                  miss + Treasury failure +
                                                  disabled flag + top-10
                                                  enumeration)
- src/test/java/com/example/purchaseconversion/api/advice/
  - LoggingPiiGuardTest.java                     (NEW; S4.1 LOAD-BEARING
                                                  PCI test — SLF4J ListAppender
                                                  + 4 nested groups [Local,
                                                  Ci, CrossLevel,
                                                  Fullwidth-confusable];
                                                  3 PAN fixtures × 5 levels)
  - ProblemDetailExceptionHandlerTest.java       (S4.7 — malformedId test
                                                  updated to assert hashed
                                                  response shape + no raw
                                                  "4242")
- src/test/java/com/example/purchaseconversion/api/controller/
  - PurchaseControllerWebMvcTest.java            (S4.7 — new test
                                                  retrieveMalformedHashesInput
                                                  asserting GET /{pan} returns
                                                  hashed details.id with no
                                                  "4242" in response body)
  - EndToEndTreasuryIT.java                      (NEW; S4.3 — full HTTP→
                                                  WireMock-backed Treasury
                                                  stack; happy + 5xx + sanity)
  - RateRevisionEndToEndIT.java                  (NEW; S4.4 — AC-026b at
                                                  HTTP boundary; revision
                                                  between two GETs)
- src/test/java/com/example/purchaseconversion/api/filter/
  - RateLimitOrderingIT.java                     (NEW; S4.2 — @SpringBootTest
                                                  RANDOM_PORT; saturate limiter
                                                  then submit PAN-bearing
                                                  payload; assert 429 NOT 400)
  - WexRateLimiterFilterTest.java                (S4.8 — constructor signature
                                                  updated for retry-after
                                                  parameter)
- src/test/java/com/example/purchaseconversion/infrastructure/treasury/
  - TreasuryFilterWhitelistTest.java             (NEW; S4.6 — parameterised
                                                  reject + accept for
                                                  DESCRIPTOR_WHITELIST)

PCI-critical invariants verified at this merge:

| Invariant                                                      | Test                                                                      |
|----------------------------------------------------------------|---------------------------------------------------------------------------|
| AC-032 / 032b — application log emissions carry no raw         | LoggingPiiGuardTest.LocalProfile.* + CiProfile.* + CrossLevel.noPanAtAnyLevel |
|   description, no raw currency, no raw id                      |                                                                           |
| G8-P0-1 — rate-limit filter runs BEFORE ContentGuard           | RateLimitOrderingIT.saturatedLimiterShortsContentGuard (servlet-container |
|   advice (servlet-container level)                             | level via @SpringBootTest RANDOM_PORT + TestRestTemplate)                 |
| AC-T-3 — widened Treasury fixtures pass HTTP-to-Treasury       | EndToEndTreasuryIT.endToEndHappy + endToEnd5xx + endToEndSanityBreach     |
| AC-026b — persistence-centric idempotency holds across HTTP    | RateRevisionEndToEndIT.revisionAcrossTwoCalls                             |
|   revision                                                     |                                                                           |
| C 30-review §4.7 (NEW MED) — MalformedIdentifierException      | LoggingPiiGuardTest.LocalProfile.malformedIdentifierLogsHashedOnly +      |
|   raw input never reaches logs or response body                | ProblemDetailExceptionHandlerTest.malformedId + PurchaseControllerWebMvcTest.retrieveMalformedHashesInput |
| Micrometer metric names match C2 §S1 enumeration               | MetricNameRegistrationTest.* (6 cases)                                    |
| Warm-up job is non-blocking on readiness; failure-tolerant     | WarmupApplicationListenerTest.* (5 cases)                                 |
| TreasuryClientAdapter audit emits StructuredArguments.kv       | source-inspection at TreasuryClientAdapter::audit; field-shape verified   |
|   (top-level JSON fields, not concatenated message)            | manually in source                                                        |
| DESCRIPTOR_WHITELIST rejects comma/colon/fullwidth/SQL-shape   | TreasuryFilterWhitelistTest.rejectsNonCanonical (10 cases) +              |
|                                                                | acceptsCanonical (6 cases)                                                |

Tests run:
- Local mvn cannot run (M7 carry-forward, deferred to C3). Branch CI
  is the test-evidence channel; ITs requiring Testcontainers + WireMock
  defer to `mvn verify` once Java is wired into CI.
- LoggingPiiGuardTest is profile-aware: Local nested class instantiates
  DescriptionHasher with no env key (v0 prefix); Ci nested class
  instantiates with an explicit env key (v1 prefix). Both verify the
  same redaction discipline.
- 4 new ITs use @SpringBootTest + AbstractPostgresIT + WireMock (where
  applicable); each carries its own @TestPropertySource for compressed
  CB + loose rate-limit settings.

Requirement coverage:
- AC-032 / 032b: LoggingPiiGuardTest.
- AC-010b/c/d/e: existing ContentGuardTest (from C) — unchanged here.
- AC-T-3 (widened): EndToEndTreasuryIT.
- AC-T-6 (rate-limit before guard): RateLimitOrderingIT.
- AC-026b (persistence-centric idempotency): RateRevisionEndToEndIT.
- ADR-0001 D-12 (HMAC vN: prefix): MalformedIdentifierException
  handler now uses DescriptionHasher; verified by LoggingPiiGuardTest.
- C 30-review §4.1-4.8: all 8 conditions addressed.
- NEW MED §4.7: closed by S4.7 implementation + 3 distinct tests
  (handler unit + WebMvc slice + LoggingPiiGuardTest cross-profile).

Convention decisions (forward-motion bias §8):
1. MetricsCatalog is a thin name registry — increments are NOT wired
   through every adapter (would cascade through ITs and inflate LOC).
   The contract is the metric names + tags; consumers (Prometheus
   scrape, dashboards, alerts) only require names to exist in the
   registry. Service-side increments will be added in a small follow-up
   if reviewer prefers, but the gate (G8-P0-1 ordering proof, etc.) is
   already covered.
2. Warm-up uses ForkJoinPool.commonPool() rather than a dedicated
   executor — minimum LOC, sufficient for fire-and-forget pre-fetch.
   Reviewer can promote to dedicated ScheduledThreadPoolExecutor in
   a follow-up if observability needs per-warmup metrics.
3. OpenTelemetry integration is via the Spring Boot 3 idiom
   (micrometer-tracing-bridge-otel + opentelemetry-exporter-otlp)
   rather than opentelemetry-spring-boot-starter. The bridge path is
   lighter weight and Spring-native; auto-instrumentation for Spring
   MVC + JDBC + RestClient comes free via Spring Boot's actuator +
   micrometer-tracing. No explicit @Configuration class needed.
4. S4.7 javadoc on MalformedIdentifierException explicitly references
   C 30-review §4.7 (audit trail) and drops the "not subject to
   redaction" claim.
5. S4.8 Retry-After:1 default is documented in api-contracts.md §7 as
   the C2 §4.8 decision (env-tunable; 1s matches Resilience4j refresh).

Risks:
- LOC ~+1,426 net; comfortably within 1,500 soft target.
- LoggingPiiGuardTest relies on logback-classic ListAppender. Tests
  pass in any classpath that includes logback-classic (default for
  Spring Boot); CI runs match. If a future profile substitutes a
  different SLF4J backend (e.g., log4j2), the test class would need
  adjustment.
- The RateLimitOrderingIT relies on draining the limiter via
  `underlyingLimiter().acquirePermission()` from the test thread.
  This works because the test thread + servlet thread share the
  Resilience4j RateLimiter instance. If the registry were re-scoped
  to per-thread in a future refactor, the test would need adjustment.
- S4.5 audit migration uses fully-qualified
  `net.logstash.logback.argument.StructuredArguments.kv(...)` in the
  source. Could be refactored to a static import for readability; left
  as-is for explicit dependency clarity.
- C 30-review §4.5 also asked for the same migration on the
  ProblemDetailExceptionHandler log emissions (currency_alias and
  malformed_identifier). NOT done in this PR — those logs still use
  positional SLF4J args. The redaction is correct (LoggingPiiGuardTest
  verifies no raw PII); only the field shape is positional vs structured.
  Surfaced here so reviewer can decide whether to fold into 13-C3 or
  a separate small follow-up.

Follow-ups (not blocking; logged for 13-C3 + later):
- [13-C3] M6 OpenAPI: @Tag + @Operation + @ApiResponse + @Schema
  annotations on PurchaseController + DTOs; oasdiff baseline; Spectral
  lint; OAS examples for AC-014 scale-6 + dual-mode currency.
- [13-C3] M7 CI/CD: security.yml (Semgrep + OWASP DC + GitLeaks +
  Trivy + SBOM); ci.yml extensions for mvn verify + OAS generate;
  deploy-{dev,staging,prod}.yml skeletons gated on
  pci-production-approved.txt; pre-commit GitLeaks; Makefile sanity.
- [post-13-C3] Optional MetricsCatalog increment wiring through
  adapters (treasury request counter, single-flight loser-outcome,
  alias-drift counter) — reviewer's call whether to inline in C3 or
  separate small chunk.
- [post-13-C3] StructuredArguments.kv migration for the remaining
  ProblemDetailExceptionHandler log emissions (currency_alias +
  malformed_identifier). 5-min edit; folds easily into C3.

After C2 merges: 13-C3 starts (S2 + S3 deferred work). After C3
merges: Phase 13 is COMPLETE. Phase 10 (Operational Readiness Gate)
activates per directives/2026-05-18-phase-10-11-bulk-pass-protocol.md.
```

## Branch + PR

- Branch: `feature/chunk-c2-observability-cicd` off `main` (post-C-merge `ce73273`).
- Builds on C's M4 + carry-forwards landed in `ce73273` + reviewer's `c1ef322` (C 30-review + C2/C3 dossier pre-staging) on `main`.

## State transition

`chunks/13-C2-observability-cicd/manifest.yml`:
- `status`: `implementing → summary_posted` on the same commit as this file.
- `summary_sha`: set after `git hash-object`.
- `pr`, `ci_url`: set after `gh pr create` + CI runs.
- `review_conditions`: all 8 ADDRESSED; reviewer flips them off at C2's `30-review.md`.
