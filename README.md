# WEX Purchase FX — Currency-converted Purchase Transaction Service

Java/Spring Boot service for the WEX take-home assessment. Stores USD purchase transactions, retrieves them, and converts each amount to a chosen currency using the **U.S. Treasury Reporting Rates of Exchange API** under the six-month rate-selection rule.

---

## 0. Reader paths

Reading time tiers, ordered from shortest to deepest:

| Depth | Time | Material |
|---|---|---|
| Summary | 30 s | This section. Three endpoints; requirements 1 and 2 of the brief are met. 238 unit-test methods (executing as 271 cases via parameterised expansion) run on `mvn test` in CI; 45 Testcontainers integration tests run locally via `mvn verify` (Postgres + WireMock; not in CI — see §6). `mvn spring-boot:run -Dspring-boot.run.profiles=local` boots a self-contained service on H2 with no external database or servlet container required, per the brief. |
| Core | 5 min | README §§ 1–4 below; source under [`src/main/java/com/example/purchaseconversion/domain/`](src/main/java/com/example/purchaseconversion/domain/) (398 LOC). |
| Extended exercise | 15 min | [`AUDIT-BRIEF.md`](AUDIT-BRIEF.md) — single-document tour of the architecture and the operational/security dossier. |
| Full dossier | 1 h | [`docs/external-review/`](docs/external-review/) — per-chunk PR reviews, consolidated HITL verdict, demonstrative production-approval ceremony. |

---

## 0.1 Scope acknowledgement

The brief specifies a 5-business-day take-home covering store, retrieve, and convert. **The core deliverable** is:

- 3 controllers + 3 DTOs in [`src/main/java/.../api/`](src/main/java/com/example/purchaseconversion/api/)
- `ConversionService` + 6-month rate-selection in [`src/main/java/.../application/`](src/main/java/com/example/purchaseconversion/application/)
- Treasury HTTP client + JDBC repos in [`src/main/java/.../infrastructure/`](src/main/java/com/example/purchaseconversion/infrastructure/)
- approximately 3,800 production Java LOC and 5,300 test Java LOC

**Beyond the brief**, the repository includes a deliberate enterprise-architecture exercise covering the 131-document dossier under [`docs/`](docs/), the five cross-cutting directives, the bulk-pass review protocol, the Phase 12 demonstrative production-approval ceremony, the PCI DSS v4.0.1 control mapping, and the `ContentGuard` rejecting PAN-shaped descriptions. These additions are not required by the brief; the rationale for each is itemised in §5.

For verification against the brief alone, §§ 1–2 and the three `curl` commands in §2 are sufficient. For evaluation of the broader engineering posture, the dossier is the canonical reference.

---

## 1. What the service does (the brief)

| Endpoint | Function |
|---|---|
| `POST /api/v1/purchases` | Register a USD purchase (description ≤ 50 chars, transaction-date valid + ≤ today, amount positive scale-2). Returns a **UUID v7** id. |
| `GET /api/v1/purchases/{id}` | Retrieve a stored purchase by id. |
| `GET /api/v1/purchases/{id}/conversion?currency={CCY}` | Convert the stored amount using the 6-month Treasury rate-selection rule. Returns id + description + transactionDate + original USD amount + scale-6 `exchangeRate` + HALF_UP scale-2 `convertedAmount`. |

**Rate-selection rule** (per brief): the exchange rate used must be the most recent Treasury rate with `record_date ≤ purchaseDate` AND `purchaseDate − record_date ≤ 6 months`. If no eligible rate exists, returns **422 `CONVERSION_RATE_NOT_AVAILABLE`** with the search window in the response — matching the brief's "*an error should be returned stating the purchase cannot be converted*."

**Errors:** all error responses conform to **RFC 9457 Problem Details** (`application/problem+json`) with a stable `errorCode` enum. See [`infra/openapi/baseline.yaml`](infra/openapi/baseline.yaml).

---

## 2. Local execution

The service ships with embedded **Tomcat** and **H2** (file-mode), so no external installation is required, per the brief.

```bash
# Build + boot (one terminal). Requires Java 21 + Maven 3.9+.
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

```bash
# 1. Register a purchase (another terminal).
curl -sX POST http://localhost:8080/api/v1/purchases \
     -H 'Content-Type: application/json' \
     -d '{"description":"Coffee at Logan","transactionDate":"2025-11-10","amountUsd":"100.00"}' | jq
# → { "id": "01992a..." }

# 2. Retrieve the stored purchase.
curl -s http://localhost:8080/api/v1/purchases/01992a... | jq

# 3. Convert to CAD using the rate active on 2025-11-10 (or the most-recent rate
#    within the prior 6 months).
curl -s "http://localhost:8080/api/v1/purchases/01992a.../conversion?currency=Canada-Dollar" | jq
# → {
#     "id": "01992a...",
#     "description": "Coffee at Logan",
#     "transactionDate": "2025-11-10",
#     "amountUsd": "100.00",
#     "currency": "Canada-Dollar",
#     "exchangeRate": "1.370000",
#     "convertedAmount": "137.00"
#   }
```

**API contract (live, browsable):** when the service is running, Swagger UI at `http://localhost:8080/swagger-ui.html` and raw OpenAPI YAML at `http://localhost:8080/v3/api-docs.yaml`. The committed regression baseline is [`infra/openapi/baseline.yaml`](infra/openapi/baseline.yaml); CI diffs the live spec against it on every PR.

**Consumer integration artefacts** — see [`infra/README.md`](infra/README.md) for the full guide:

- **Postman collection** — [`infra/postman/wex-purchase-fx.postman_collection.json`](infra/postman/wex-purchase-fx.postman_collection.json). Importable into Postman or compatible clients. Includes happy-path and error-case requests with pre-baked test assertions. Compatible with the `newman` CLI for headless execution.
- **Bruno collection** — [`infra/bruno/wex-purchase-fx/`](infra/bruno/wex-purchase-fx/). Plain-text `.bru` request files version-controlled alongside the API, with no proprietary cloud-sync dependency. Headless execution via `bru run`.
- **Typed SDKs** — generated via `openapi-generator-cli generate -i infra/openapi/baseline.yaml -g <typescript-fetch|java|python|go|rust|...> -o build/clients/<lang>`. Full invocations and per-language notes in [`infra/README.md §3`](infra/README.md).

---

## 3. Engineering metrics

### Code

| | |
|---|---:|
| Production Java LOC | **3,872** across 51 files |
| Test Java LOC | **5,402** across 37 files (23 `*Test.java` + 13 `*IT.java` + 1 base helper) |
| Test methods | **238 in source** (226 `@Test` + 12 `@ParameterizedTest`) executing as **271 cases** in Surefire (parameter expansion) — all green in CI on `mvn test` |
| Integration tests | **45** (Failsafe `*IT.java`) — local-only via `mvn verify`; require Testcontainers Postgres 16 + WireMock 3.9 |
| Test/code ratio | **1.40×** |
| Maven dependencies | 25 |

### Production code by hexagonal-architecture layer

| Layer | LOC | Role |
|---|---:|---|
| `domain` | 398 | Pure POJOs / value objects / entities / ports — no Spring, no DB, no HTTP |
| `application` | 802 | `ConversionService` orchestration + 6-month rate-selection use case |
| `infrastructure` | 1,256 | Persistence + Treasury client + single-flight gate + cache + health |
| `api` | 1,009 | HTTP controllers + filters + DTOs + `@RestControllerAdvice` |
| `observability` | 285 | `MetricsCatalog` + `DescriptionHasher` + warm-up listener |

### Quality gates (enforced in `pom.xml`; fail the `mvn verify` build)

| Gate | Threshold |
|---|---|
| JaCoCo line coverage — `domain` | ≥ 85 % |
| JaCoCo branch coverage — `domain` | ≥ 75 % |
| JaCoCo line coverage — `application` | ≥ 80 % |
| JaCoCo line coverage — `infrastructure` | ≥ 75 % |
| Pitest mutation score — `domain` | ≥ 85 % |
| Pitest mutation score — `application` | ≥ 80 % |
| Pitest mutation score — `infrastructure` (critical paths) | ≥ 80 % |
| ArchUnit fitness functions | hexagonal-layer dependency rules + naming conventions |

CI uploads the JaCoCo + Pitest reports as workflow artefacts on every run — downloadable from any green CI run on the PR page.

---

## 4. Tech stack

- **Java 21** + **Spring Boot 3.3.5** (web, actuator, validation, JDBC)
- **PostgreSQL** (production) + **H2 file-mode** (local) via **Liquibase** migrations
- **Caffeine** hot-cache, keyed on `(currency, recordDate)` per ADR-0001 D-10
- **Resilience4j** — timeout / retry-with-jitter / circuit-breaker / bulkhead (programmatically composed)
- **Micrometer + Prometheus** (metrics), **Micrometer Tracing + OpenTelemetry OTLP** (traces), **Logstash Logback Encoder** (structured JSON logs)
- **springdoc-openapi** + **Spectral** lint + **oasdiff** drift check
- **JUnit 5** + **Testcontainers** (Postgres 16) + **WireMock 3.9** (Treasury stub) + **ArchUnit 1.3**
- **JaCoCo 0.8.12** + **Pitest 1.17.0**

---

## 5. Rationale for scope additions

The brief does not require the controls and conventions below. Each is documented with its rationale.

| Addition | Rationale |
|---|---|
| **`ContentGuard` rejecting PAN-shaped descriptions** (Luhn + track-data + NFKC encoded-PAN) | Enterprise-default posture: any free-text field from an untrusted client is a PAN-disclosure risk. Out-of-CDE design only holds if the boundary enforces it under hostile input. |
| **HMAC-SHA-256 redaction of `description` in logs** | Same as above. Logs are the most common leak vector for PII. `DescriptionHasher` refuses to start in `prod` / `staging` if the key is absent, so the redaction can't silently disappear. |
| **Single-flight gate keyed on `(currency, treasury_quarter_end)`** | Treasury's public API has no published rate-limit. Concurrent first-fetches on a cold cache would fan out N requests for the same quarter. The gate collapses them to one upstream call. |
| **Versioned upsert on `(currency, record_date, effective_date)`** | Treasury *does* republish rates for a previously-published `record_date`. Without versioning, a stored conversion result could become silently inconsistent with a re-fetched rate. |
| **OpenAPI 3.1 baseline + `oasdiff` regression gate** | Consumer-driven API discipline. The API contract is checked against a committed baseline on every PR; intentional changes require an ADR. |
| **RFC 9457 Problem Details for errors** | Stable contract for error responses. The `errorCode` enum lets a client pattern-match without parsing the human-readable `detail`. |
| **6-month rule with end-of-month clamp** | The brief says "within the last 6 months." Edge case: a purchase on May 31 looking for a rate within the prior 6 months — what does "6 months before May 31" mean? November 30 or November 28/30/31? The ADR's EOM clamp is the explicit policy (ADR-0001 D-5). |
| **Refuse-to-start on missing log-hash key** in `prod`/`staging` | If a deploy somehow forgets the secret, the service must not run in a state where it could log PII unredacted. Loud failure beats silent leak. |

---

## 6. Building and testing

The repo follows standard Maven layout. CI runs `mvn verify` (unit + integration via Testcontainers + JaCoCo gates + ArchUnit) on every PR — see [`.github/workflows/ci.yml`](.github/workflows/ci.yml).

```bash
# Full quality gates (matches CI): docs + ops + pci-check (Python helpers) + mvn verify.
make ci

# Or directly:
mvn -B verify                     # unit + integration + JaCoCo + ArchUnit
mvn -P pit pitest:mutationCoverage # mutation testing (slower)
mvn spring-boot:run -Dspring-boot.run.profiles=local  # boot the service
```

Integration tests use **Testcontainers** (spins Postgres 16 in a Docker container) and **WireMock 3.9** (Treasury stub). Docker daemon required for `mvn verify`.

**Branching:** work happens on short-lived feature branches off `main` (e.g. `f2`), rebased onto latest `main` before merge to avoid drift, and merged via PR after review. `main` is protected — no direct pushes. Every PR must show a green `mvn test` run before merge; the PR description records the pass/fail count as evidence. PRs are squash-merged so `main` history stays one commit per feature.

---

## 7. Repository navigation

| Topic | Path |
|---|---|
| **15-min assessor tour** | [`AUDIT-BRIEF.md`](AUDIT-BRIEF.md) |
| **Operating model** (consolidated from 5 directives) | [`docs/external-review/OPERATING-MODEL.md`](docs/external-review/OPERATING-MODEL.md) |
| **Canonical reviewer verdict** | [`docs/external-review/HITL-CONSOLIDATED-REVIEW.md`](docs/external-review/HITL-CONSOLIDATED-REVIEW.md) |
| **Project rollup / audit ledger** | [`docs/external-review/STATUS.md`](docs/external-review/STATUS.md) |
| **Per-chunk reviewer verdicts** | [`docs/external-review/chunks/`](docs/external-review/chunks/) (9 chunks × ~4-7 files each) |
| **Phase 10 / 11 / 12 dossiers** | [`docs/external-review/phases/`](docs/external-review/phases/) |
| **Architecture decisions** | [`docs/architecture/adr-0001-core-architecture.md`](docs/architecture/adr-0001-core-architecture.md) (14 decisions D-1..D-14) |
| **Requirements + traceability** | [`docs/requirements/`](docs/requirements/) |
| **Operations** | [`docs/operations/`](docs/operations/) — 12 files: SLO/SLI, observability, runbook (25 playbooks), incident-response, rollback-plan (6 classes), capacity, failure-modes (F-01..F-27) |
| **PCI scope + control mapping** | [`docs/security/pci-scope-and-cde.md`](docs/security/pci-scope-and-cde.md), [`docs/security/pci-dss-control-mapping.md`](docs/security/pci-dss-control-mapping.md) |
| **OpenAPI baseline** | [`infra/openapi/baseline.yaml`](infra/openapi/baseline.yaml) |
| **Integration artefacts** (Postman / Bruno / SDK gen) | [`infra/README.md`](infra/README.md) |
| **CI workflows** | [`.github/workflows/`](.github/workflows/) (`ci.yml`, `security.yml`, `deploy-*.yml`) |

### Recommended reading order

1. This README §§ 0 / 0.1 / 1 / 2 (5 min).
2. Source code under [`src/main/java/.../domain/`](src/main/java/com/example/purchaseconversion/domain/) then `application/conversion/` (10 min — implementation of the brief's requirements).
3. [`AUDIT-BRIEF.md`](AUDIT-BRIEF.md) for the extended-exercise tour (15 min).
4. [`docs/architecture/adr-0001-core-architecture.md`](docs/architecture/adr-0001-core-architecture.md) for the architectural decisions (10 min).
5. Tests under [`src/test/java/`](src/test/java/), beginning with `ConversionServiceTest`, then `EndToEndTreasuryIT`.

---

## 8. Operational invariants (one-liners)

| Area | Invariant |
|---|---|
| **PCI** | Out-of-CDE by design. Boundary `ContentGuard` rejects PAN-shaped descriptions. Description never logged plain — HMAC-SHA-256 redaction. |
| **Secrets** | `wex.log.hash.key` mandatory in `prod` / `staging` / `local-pii`. Service refuses to start if absent. Never logged. |
| **Treasury upstream** | Single-flight gate. Bounded retry + circuit-breaker. Graceful shutdown releases gate state on SIGTERM. |
| **Rate versioning** | Revisions land as a new row keyed `(currency, record_date, effective_date)`. Original rows immutable. `max(effective_date)` wins for in-window queries. |
| **Rollback** | 6 classes documented (A code / B config / C schema forward-only / D data PITR / E Treasury orientation flip / F security incident). |
| **HMAC-key rotation** | New `vN+1:` prefix; old digests stay correlatable within their key window. |

---

## 9. Authoring and AI-tooling disclosure

This project was authored using **[Claude Code](https://claude.com/claude-code)** as the implementation partner, with the developer (Sajith Mankavil) in the **architect + lead-reviewer** role.

What the developer authored:
- The operating model in [`docs/external-review/OPERATING-MODEL.md`](docs/external-review/OPERATING-MODEL.md) and all 5 cross-cutting directives.
- The architectural decisions (ADR-0001 D-1..D-14).
- The requirements analysis + traceability matrix.
- Every reviewer-prompt envelope (the chunk `00-prompt.md` files specify what Claude Code was asked to implement).
- The integration and acceptance criteria.
- The merge-or-amend judgment on every PR.

What Claude Code generated:
- Java implementation per the chunk specs.
- Test methods per the prompt's invariants.
- Reviewer `30-review.md` files (Phase 13 chunks); developer reviewed each diff before merge.
- Dev-authored provisional `30-review.md` files (Phase 11 onward, per the HITL-gate consolidation directive); reviewed by the consolidated reviewer pass.

Every commit landed via a PR. PRs were reviewed (by reviewer agent in Phase 13; by developer for the cleanup PRs); the `30-review.md` audit trail is the record of that review.

Engineering author: **Sajith Mankavil** ([`sajith.mankavil@gmail.com`](mailto:sajith.mankavil@gmail.com)) — architect / product-owner role for the case study.

### 9.1 CI workflow history disclosure

For most of the project's two-day active window, the CI workflow's "Tests" step was a placeholder shim invoking `npm test` or `pytest`, neither of which applied to a Java/Maven project. Every "CI green" claim through chunk `13-C3` was therefore **workflow-passed but Java-unrun**. The Java test suites existed and were executed locally before each PR; CI was not exercising them.

PR #17 identified this as its first finding and replaced the shim: `mvn test` now runs in CI on every PR. The first end-to-end CI execution surfaced **12 latent issues** — two production defects (`BigDecimal` scale stripped on the wire; PAN-shaped input echoed back via the RFC 9457 `instance` URI), four `ApplicationContext`-load failures, three test-correctness defects, two compile errors, and one concurrency-race test fix. Full inventory in [`AUDIT-BRIEF.md §7B`](AUDIT-BRIEF.md). All twelve issues have been remediated and the suite passes in CI on `mvn test`.

Local test discipline existed; the CI gate that should have enforced it was non-operational. This disclosure is included so that readers interpret earlier "CI green" claims in the dossier accordingly.

---

## 10. License

See [`LICENSE`](LICENSE) — MIT.

Underlying frameworks (Spring Boot, Resilience4j, Caffeine, Logstash Logback Encoder, springdoc, Micrometer, OpenTelemetry, JUnit, Testcontainers, WireMock, ArchUnit, JaCoCo, Pitest, etc.) are used under their respective open-source licenses.

---

## 11. Phase history

```
Phases 1–7   Requirements + design + design-grill + operational-design + reliability-grill + PCI-design + PCI-grill
              All under docs/requirements/ + docs/architecture/ + docs/planning/ + docs/security/

Phase 8      Implementation-readiness gate → human approval via .human-approvals/implementation-approved.txt

Phase 13     Chunked implementation (11 PRs, 9 chunks)
              docs/external-review/chunks/13-*/30-review.md

Phase 10     Operational Readiness Gate (bulk-pass)
Phase 11     PCI Security Readiness Gate (bulk-pass)
Phase 12     Production Approval (case-study demonstrative ceremony)
              docs/external-review/phases/{10,11,12}-*/

HITL gate    docs/external-review/HITL-CONSOLIDATED-REVIEW.md (canonical)
              Verdict: READY FOR CASE-STUDY HITL WITH FINDINGS
```

Phase 13 ran before Phases 10 and 11 because the implementation produced the evidence required to close the readiness gates. Phase 12 is the multi-party signature procedure documented in [`docs/security/change-control-pci.md §1`](docs/security/change-control-pci.md); for the case study it ran demonstratively per the case-study scope-clarification directive — see [`docs/external-review/phases/12-production-approval/DEMONSTRATIVE-CEREMONY.md`](docs/external-review/phases/12-production-approval/DEMONSTRATIVE-CEREMONY.md). No production deployment has occurred; `deploy-prod.yml` remains marker-gated.
