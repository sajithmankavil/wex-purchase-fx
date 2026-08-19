# WEX Purchase FX — Architecture Walkthrough

Regenerated directly from the codebase at commit `091b30c` (no reliance on prior notes). References are to real files; every claim below was checked against the repo, not assumed. Where the design docs under `docs/architecture/*.md` describe something different from what's actually implemented, that's called out explicitly rather than papered over — this doc is for planning modernization work, so gaps are stated plainly.

---

## 1. High-level architecture

**What it does:** a service that stores USD purchase transactions and, on request, converts a stored purchase into a chosen currency using the U.S. Treasury's published exchange rates, applying a 6-month rate-selection rule (the most recent Treasury rate on or before the purchase date, no older than 6 months).

**External dependencies:** exactly one — the **Treasury Fiscal Data API** (`api.fiscaldata.treasury.gov`), called anonymously over HTTPS with no API key. There is no message queue, no other microservice, no third-party FX provider, no auth provider, and no payment processor anywhere in the code. The only other "dependency" is the database itself (H2 file-mode locally, PostgreSQL in the production-reference design — see §3).

**Request path** (the richest case, currency conversion):

```
Client
  │ GET /api/v1/purchases/{id}/conversion?currency=CAD
  ▼
WexRateLimiterFilter          (Servlet Filter — 429 on breach, before anything else runs)
  ▼
CorrelationIdFilter           (Servlet Filter — binds X-Correlation-Id into MDC)
  ▼
PurchaseController.convert()  (api/controller — parses id, delegates)
  ▼
ConversionService.convert()   (application/conversion — orchestrates)
  │
  ├─ CurrencyAliasPort.resolve(currency)         → 400 if unresolvable
  ├─ PurchaseRepositoryPort.findById(id)          → 404 if missing
  ├─ ExchangeRateHotCachePort.findInWindow(...)   → in-process Caffeine cache, checked first
  ├─ ExchangeRateRepositoryPort.findInWindow(...) → DB, checked on cache miss
  ├─ TreasuryClientPort.fetchRates(...)           → live HTTP call, only on cache+DB miss
  ▼
Money.multiply(rate)  → HALF_UP, scale 2
  ▼
ConversionResponse (200) or RFC 9457 problem+json (4xx/5xx)
```

Every exception is caught centrally by `ProblemDetailExceptionHandler` (`src/main/java/com/example/purchaseconversion/api/advice/ProblemDetailExceptionHandler.java`) — no controller does its own error handling.

---

## 2. Layer breakdown

The pattern is **hexagonal / ports-and-adapters**, and it's mechanically enforced, not just a naming convention — `src/test/java/com/example/purchaseconversion/architecture/ArchitectureTests.java` runs ArchUnit rules on every build that fail if `domain` picks up a Spring/JPA import, if `domain` depends on anything beyond the JDK plus one approved UUID library, or if `application` picks up any Spring annotation at all.

| Package | Path | Responsibility |
|---|---|---|
| `domain` | `src/main/java/.../domain/` | Framework-free business types: `Money.java` (BigDecimal-only, HALF_UP rounding), `Purchase.java`, `PurchaseId.java` (UUID v7), `ExchangeRate.java`, `CurrencyDescriptor.java`, `RateSelectionPolicy.java` (the 6-month rule, a pure static function). |
| `application` | `src/main/java/.../application/` | Use-case orchestration, also framework-free. `port/in/` (inbound interfaces: `RegisterPurchaseUseCase`, `RetrievePurchaseUseCase`, `ConvertPurchaseUseCase`), `port/out/` (outbound interfaces infrastructure implements), `purchase/PurchaseService.java`, `conversion/ConversionService.java`, `exception/` (all extend `DomainException`). |
| `infrastructure` | `src/main/java/.../infrastructure/` | Adapters — the only layer that touches I/O. `persistence/` (JDBC repos), `cache/ExchangeRateHotCacheAdapter.java` (Caffeine), `treasury/TreasuryClientAdapter.java` + `SingleFlightGate.java`, `currency/CurrencyAliasTableAdapter.java`, `health/` (two custom `HealthIndicator`s). |
| `api` | `src/main/java/.../api/` | HTTP boundary. `controller/PurchaseController.java` (pure delegation, no business logic), `dto/`, `advice/` (`ProblemDetailExceptionHandler`, `ContentGuard` + `ContentGuardAdvice` for PAN detection), `filter/` (`WexRateLimiterFilter`, `CorrelationIdFilter`). |
| `config` | `src/main/java/.../config/WexConfig.java` | Manual `@Bean` factories wiring the framework-free `application` services into Spring — this is *how* those services stay framework-free: they're plain `new`-ed objects, not `@Service`-annotated. |
| `observability` | `src/main/java/.../observability/` | `MetricsCatalog.java` (Micrometer counters), `DescriptionHasher.java` (HMAC redaction for logs), `WarmupApplicationListener.java`. |

**One documented-vs-actual mismatch worth flagging here:** `docs/architecture/adr-0001-core-architecture.md` (decision D-1) describes a top-level `exception` package as part of the layout. It doesn't exist — exceptions live inside `application/exception/` and `api/advice/exception/` instead.

---

## 3. Data model and persistence

Yes, there's a database — accessed via plain Spring **`JdbcClient`**, hand-written SQL, **no JPA/Hibernate anywhere** in this codebase (no `@Entity`, no `spring-boot-starter-data-jpa` dependency at all). This directly contradicts `docs/architecture/adr-0001-core-architecture.md` (D-1, D-2) and `docs/architecture/data-model.md` §8, both of which describe a JPA-based persistence layer — that design was apparently abandoned in favor of `JdbcClient`, and the docs were never updated.

Two tables, two Liquibase changesets:

| Table | Changeset | Key |
|---|---|---|
| `purchase_transactions` | `src/main/resources/db/changelog/changesets/v1-purchase-transactions.yaml` | `id VARCHAR(36)` (UUID v7 string), no surrogate key |
| `exchange_rates` | `.../v2-exchange-rates.yaml` | composite `(country_currency_desc, record_date, effective_date)` — versioned, never updated in place |

"Entity classes" are the `domain` records above (`Purchase`, `ExchangeRate`) — there's no separate JPA-entity layer. "Repository interfaces" are the outbound ports: `PurchaseRepositoryPort` / `ExchangeRateRepositoryPort` (`application/port/out/`), implemented by `PurchaseRepoAdapter` / `ExchangeRateRepoAdapter` (`infrastructure/persistence/`).

**Migrations are Liquibase**, not Flyway — worth stating explicitly because `docs/architecture/data-model.md` §6 and `docs/architecture/deployment-architecture.md` §2.1 both describe Flyway with `V1__...sql`-style files. Neither exists; `pom.xml` depends on `liquibase-core`, and the actual migration files are YAML changesets under `db/changelog/`, wired via `src/main/resources/db/changelog/db.changelog-master.yaml`. Schema evolution is additive-only so far — two changesets, no rollback-in-anger history yet, though `LiquibaseMigrationIT.java` does test an apply→rollback→reapply cycle.

**Transaction boundaries: there aren't any, explicitly.** A repo-wide search for `@Transactional` returns nothing. Every `JdbcClient` call is its own auto-committed unit of work. This is fine for the single-statement operations (one `INSERT`, one `SELECT`) but means `ExchangeRateRepoAdapter`'s upsert-then-invalidate-cache sequence has no atomicity guarantee across a list of rates — a mid-loop crash leaves a partial write with no rollback. `docs/architecture/data-model.md` §7 claims explicit `@Transactional` boundaries with `READ_COMMITTED` isolation; the code has none of that.

---

## 4. Key design decisions

- **Hexagonal architecture, ArchUnit-enforced** (see §2) — the standout decision in this codebase. Most repos describe layering as intent; this one fails the build on a violation.
- **Modular monolith, not microservices** — `docs/architecture/adr-0001-core-architecture.md` explicitly records microservices and serverless as considered-and-rejected, citing the case-study constraint of running locally with no separately installed infrastructure.
- **Resilience4j applied programmatically, not via annotations** — `TreasuryClientAdapter.java` composes `Bulkhead.decorateSupplier` → `Retry.decorateSupplier` → `CircuitBreaker.decorateSupplier` by hand. The class's own Javadoc explains why: the wrapped call happens inside a self-invocation (`SingleFlightGate.runOnce()`'s closure), and Spring AOP's annotation-based interception silently doesn't apply across that kind of call. A subtle, real gotcha, not an arbitrary style choice.
- **A hand-rolled concurrency primitive**: `SingleFlightGate.java` (`infrastructure/treasury/`) deduplicates concurrent Treasury fetches keyed by `(currency, quarter-end)`. One caller becomes the "winner" and does the real fetch; others poll the database for up to 10 seconds rather than blocking on the winner's `Future`. This is non-trivial hand-written concurrent code — worth reading directly if evaluating engineering depth.
- **PAN/PII defense-in-depth, not the primary control**: the primary protection against cardholder-data disclosure is that no field in the API is shaped to hold one. `ContentGuard.java` (decodes base64/hex/URL-encoded candidates, checks Luhn + track-data regexes) and `DescriptionHasher.java` (HMAC-SHA-256 redaction before anything free-text hits a log line) are explicitly documented as defense-in-depth on top of that, not the primary control.
- **What would surprise a new reader**: the amount of documentation under `docs/` (architecture, security, operations — dozens of files) versus the actual runtime footprint (single JAR, no container, two DB tables). The design docs describe a considerably more built-out production system (containerized, JPA-backed, Flyway-migrated, `@Transactional`-bounded) than what's actually running. Anyone onboarding by reading the docs first will form an inaccurate mental model of the code.

---

## 5. Testing

Two test types exist, distinguished purely by naming convention and Maven plugin binding:

- **Unit / slice tests** (`*Test.java`, 24 files) — run by Surefire, bound to `mvn test`. Plain unit tests (domain, application services with Mockito-mocked ports), `@WebMvcTest` controller slices, plus `ArchitectureTests.java` (ArchUnit) and property-based tests via **jqwik** (`MoneyTest.java` — asserts HALF_UP rounding against an independently computed reference across a generated input range).
- **Integration tests** (`*IT.java`, 13 files) — run by Failsafe, bound to `mvn verify`. Dependency handling is exclusively **Testcontainers** (`org.testcontainers:{junit-jupiter,postgresql}`) for real-Postgres tests (`PurchaseRepoIT`, `ExchangeRateRepoIT`, `LiquibaseMigrationIT`, `DurabilityRestartIT`, etc.), plus **WireMock** for stubbing the Treasury HTTP dependency (`EndToEndTreasuryIT`, `TreasuryClientIT`). No mocks are used for IT-level DB access — it's real Postgres via containers, not H2-in-memory-as-a-stand-in.

Test framework: **JUnit 5.11.3**, AssertJ 3.26.3, Mockito 5.14.2, ArchUnit 1.3.0, jqwik 1.9.1, Testcontainers 1.20.4, WireMock (standalone) 3.9.2 — all pinned in `pom.xml`.

**Current run:** `mvn test` passes 246 tests, 0 failures, 0 errors, 3 skipped, verified fresh against this exact commit.

**Well-tested:** the 6-month rate-selection rule (`RateSelectionPolicyTest.java` has an explicit boundary table — exact-day, one-day-under, end-of-month-clamp cases on both sides); `Money`'s rounding behavior (both example-based and jqwik property tests); every documented error path has a controller-slice test asserting the exact status + error code.

**Thin or unverifiable:** the 13 `*IT.java` files are **not run in CI at all** — `.github/workflows/ci.yml` runs `make test` (`mvn test`, Surefire only) and explicitly excludes Failsafe, with a comment in the workflow citing GitHub-hosted-runner container fragility as the reason. So the only integration-level evidence this project has is whatever a developer produces by running `mvn verify` locally with a working Docker daemon — there's no automated, repeatable signal for it anywhere in the pipeline.

---

## 6. Build and dependencies

**Maven**, single module, no Gradle anywhere. Parent: `org.springframework.boot:spring-boot-starter-parent:3.3.5`. **Java 21** (`<java.version>21</java.version>`).

Key runtime dependencies: `spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-jdbc` (not `-data-jpa`), `spring-boot-starter-actuator`, `liquibase-core`, `com.github.ben-manes.caffeine:caffeine` 3.1.8, `io.github.resilience4j:resilience4j-spring-boot3` 2.2.0, `springdoc-openapi-starter-webmvc-ui` 2.6.0, `net.logstash.logback:logstash-logback-encoder` 7.4, `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`, `com.github.f4b6a3:uuid-creator` 5.3.3 (UUID v7 generation — the JDK's own `UUID.randomUUID()` only produces v4).

Notable plugins in `pom.xml`:
- **Surefire/Failsafe split** — Surefire runs `**/*Test.java` and explicitly excludes `**/*IT.java`; Failsafe runs `**/*IT.java` only, bound to the `integration-test`/`verify` goals. This split is what lets CI run `mvn test` safely without Docker.
- **JaCoCo** — four separate `check` executions with different thresholds per package (`domain` 85%/75% line/branch, `application.*` 80% line, `infrastructure.*` 75%/65%, `api.*`+`config` 75%/65%).
- **Pitest** — five separate `mutationCoverage` executions targeted at specific classes rather than whole packages (`RateSelectionPolicy`+`Money` at 85%, `ConversionService` specifically at 80%, `ContentGuard` at 85%, etc.) — a level of granularity that's a real signal of care, not just a blanket coverage number.

---

## 7. Containerization

**None exists.** No `Dockerfile`, no `docker-compose.yml`, no `.dockerignore`, no container-registry wiring in the build — confirmed by a repo-wide search, not just an absence in the obvious places.

This is despite `docs/architecture/deployment-architecture.md` §2.2 describing one in real detail: base image `eclipse-temurin:21-jre-jammy` (Distroless considered and rejected to keep `jcmd`/`jstack` available), non-root user `wex` (UID 10001), `WORKDIR /app`, a `HEALTHCHECK` hitting `/actuator/health/liveness`, `EXPOSE 8080`, built via `./mvnw spring-boot:build-image` (Cloud Native Buildpacks) to `ghcr.io/<org>/wex-purchase-fx:<version>`. None of it is implemented — `pom.xml`'s `spring-boot-maven-plugin` entry has no `<configuration>` block, so the `build-image` goal is never invoked anywhere.

The only place a container registry is referenced in the actual repo is a conditional step in `.github/workflows/security.yml` (a Trivy image scan gated on `if: hashFiles('Dockerfile') != ''`) — which has never run, because the condition has never been true. The service currently only runs as a bare JVM process (`mvn spring-boot:run` or `java -jar`).

---

## 8. CI/CD

**GitHub Actions.** No Jenkinsfile, no GitLab CI config. Five workflow files under `.github/workflows/`:

- **`ci.yml`** — triggers on every PR and every push to `main`. One job, sequential steps, any failure stops the pipeline: `docs-check` → `ops-check` → `pci-check` → `lint` (`mvn compile`) → `typecheck` (no-op, folded into lint) → **`mvn test`** (unit only) → boot the app and fetch its live OpenAPI spec → **diff it against the committed `infra/openapi/baseline.yaml`** (`oasdiff`, with a Python fallback if the binary isn't present) → Spectral lint on the OpenAPI file → upload JaCoCo/Pitest reports as artifacts. Integration tests (`*IT.java`) are explicitly excluded from this pipeline (§5).
- **`security.yml`** — six jobs (GitLeaks secrets scan, Semgrep SAST, OWASP Dependency-Check, Trivy filesystem scan, Trivy container scan [never runs, §7], CycloneDX SBOM, OWASP ZAP DAST against a locally-booted instance), on PR/push/weekly cron. Most are `continue-on-error: true` (advisory), with comments stating they'll be flipped to blocking once a findings baseline is established.
- **`deploy-dev.yml`, `deploy-staging.yml`, `deploy-prod.yml`** — all three are explicit scaffolds, each says so in its own header comment. `workflow_dispatch`-only (never automatic). Staging and prod are gated on human-created approval-marker files under `.human-approvals/` (which currently contains only a `README.md`, no actual markers). Every deploy step inside these three workflows is a stub that echoes what it *would* do — there is no real deploy target anywhere. **Deployment today is entirely manual** (run the jar directly), and the "automation" that exists is unexercisable scaffolding.

---

## 9. Configuration and secrets

Config is loaded via `src/main/resources/application.yml`, environment-variable-driven throughout (`${VAR:default}` placeholders) — there are **no separate `application-{profile}.yml` files**. Profile-specific *behavior* comes from `@Profile`/`spring.profiles.active` checks in code and from `<springProfile>` blocks in the logging config (`src/main/resources/logback-spring.xml`), not from separate YAML files per environment.

Profiles are separated by behavior, not by file: `default,dev,test,local` get human-readable console logs; `prod,staging,ci` get JSON. `DescriptionHasher.java`'s constructor refuses to start the application (`IllegalStateException`) if the active profile contains `prod`/`staging` and `WEX_LOG_HASH_KEY` isn't set — a real, code-enforced fail-closed pattern, not just a documented policy.

**Secrets**: the only application secret referenced anywhere is `WEX_LOG_HASH_KEY` (the HMAC key backing `DescriptionHasher`). It's read from a plain environment variable — no Vault, no cloud secrets-manager SDK, no key-management integration of any kind in the code. `docs/architecture/deployment-architecture.md` describes a "platform secrets provider" abstractly, but nothing in the codebase talks to one. There's no `.env` file committed, and `security-profile.yml` (a small declarative policy file at the repo root) states the intended PCI-Tier-1 posture, but it's read by the project's `make *-check` scripts as a policy reference, not by the application at runtime.

---

## 10. Observability

**Logging**: SLF4J/Logback, format is profile-dependent — plain-text console pattern locally, structured JSON (`net.logstash.logback.encoder.LogstashEncoder`) in `prod,staging,ci` (`logback-spring.xml`). MDC is included automatically in the JSON output (`includeMdc: true`).

**Correlation IDs**: yes — `CorrelationIdFilter.java` binds an `X-Correlation-Id` (client-supplied or generated) into SLF4J MDC on every request, echoes it back as a response header, and clears it after the request completes.

**Health checks**: Spring Actuator, plus two custom `HealthIndicator` beans — `DbPoolHeadroomHealthIndicator.java` (flips readiness DOWN if the HikariCP pool stays saturated) and `GatewayRequiredHealthIndicator.java` (a defense-in-depth check for a misconfigured deployment missing an upstream trust header). `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` are all exposed per `application.yml`.

**Metrics**: Micrometer + `micrometer-registry-prometheus`, scraped at `/actuator/prometheus`. `MetricsCatalog.java` centralizes custom counter names as compile-time-checked constants (`treasury.client.requests`, `exchange_rate.single_flight.dedup_ratio`, `exchange_rate.hot_cache.hit_ratio`, `currency_alias.drift.detected`, etc.) rather than scattering string literals through the codebase.

**Distributed tracing**: `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` are present as dependencies, and `application.yml` has an `OTEL_EXPORTER_OTLP_ENDPOINT` hook (unset by default). There's no evidence of manually-created spans beyond whatever Spring's own auto-instrumentation provides — the plumbing exists, but nothing in the tests or code confirms it's ever been exercised end-to-end against a real collector.

---

## 11. Current state assessment

**Genuinely well-done**: the hexagonal architecture is real and enforced (§2, §4), not aspirational. The 6-month rate-selection rule and monetary rounding are tested with real rigor (boundary tables, property-based tests, class-targeted mutation-testing thresholds). The RFC 9457 error-handling is centralized and consistent. The PAN/PII redaction pattern (`ContentGuard` + `DescriptionHasher`) is a genuinely thoughtful defense-in-depth layer for a service that explicitly isn't supposed to handle cardholder data at all.

**Noticeably missing or under-built for a 2026 production service**: no containerization whatsoever (§7) despite it being fully speced in the design docs; no real deploy automation — three deploy workflows are unexercisable scaffolding (§8); integration tests exist but have never run in CI and require a developer's local Docker (§5); no secrets-manager integration, just a bare env var (§9); no transaction boundaries anywhere in the persistence layer (§3); no evidence tracing has ever actually been exercised end-to-end (§10).

**What would surprise a new senior engineer joining this project**: reading `docs/architecture/*.md` first and then opening the code. The docs describe JPA entities, Flyway migrations, explicit `@Transactional` boundaries, and a fully-specified container image — none of which exist. A new engineer who trusts the docs over the code will misdescribe this system in their first design review.

**Production-readiness: 4/10.** The core domain logic and its test coverage would earn a much higher score in isolation — the business-rule correctness is solid. But "production-ready" has to include the operational envelope, and that envelope is mostly missing: nothing can be containerized or deployed today without someone building that tooling first, the only automated test signal is unit-level, secrets handling is the bare minimum, and the architecture documentation actively misleads about what's implemented. This reads as a well-built domain core inside an unfinished operational shell — which, for a case-study project, is a reasonable place to have stopped, but it's not a system anyone could hand off to an on-call rotation today.
