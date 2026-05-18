# WEX Purchase FX — Currency-converted Purchase Transaction Service

Enterprise-grade Java service for the WEX assessment. Stores USD purchase transactions, retrieves them, and converts each amount to a chosen currency using the **U.S. Treasury Reporting Rates of Exchange API** and the six-month rate-selection rule.

> **Project posture:** Case-study terminal. **Production-deployable, not deployed.**
> Architecture, code, tests, operations, and security evidence are all assessor-ready. The production-cutover punch list is documented; would be executed under real-deployment activation. `deploy-prod.yml` remains correctly marker-gated — no real production push can occur from this repository.

**For external assessors, start with [`AUDIT-BRIEF.md`](AUDIT-BRIEF.md)** — a single-document tour of the project, ~15 min read.

---

## 1. What the service does

| Endpoint | Function |
|---|---|
| `POST /api/v1/purchases` | Register a USD purchase (description ≤ 50 chars, transaction-date ≤ today, amount scale-2). Returns a **UUID v7** id. PAN-shaped descriptions rejected at the boundary by `ContentGuard` (Luhn + track-data + NFKC encoded-PAN). |
| `GET /api/v1/purchases/{id}` | Retrieve a stored purchase by id. Malformed-id input is HMAC-hashed in logs **and** the response body (defense-in-depth: `getMessage()` carries length only, never the raw value). |
| `GET /api/v1/purchases/{id}/conversion?currency={CCY}` | Convert the stored amount using the 6-month Treasury rate-selection rule (**ADR-0001 D-5**). Returns scale-6 `exchangeRate` + HALF_UP scale-2 `convertedAmount` (**AC-014**). |

All errors conform to **RFC 9457 Problem Details** (`application/problem+json`) with a stable `errorCode` enum.
OpenAPI 3.1 contract published at [`infra/openapi/baseline.yaml`](infra/openapi/baseline.yaml); CI guards drift via `oasdiff` + Spectral lint.

---

## 2. Engineering metrics

### Code

| | |
|---|---:|
| **Production Java LOC** | **3,842** across 51 files |
| **Test Java LOC** | **5,316** across 37 files (23 `*Test.java` + 13 `*IT.java`) |
| **Test/code ratio** | **1.38×** |
| `@Test` + `@ParameterizedTest` annotations | 248 |
| Maven dependencies | 25 |
| Liquibase migrations | 2 (+ master changelog) |

### Production code by hexagonal-architecture layer

| Layer | LOC | Role |
|---|---:|---|
| `domain` | 398 | Pure POJOs / value objects / entities / ports — no Spring, no DB, no HTTP |
| `application` | 802 | Use cases + service orchestration |
| `infrastructure` | 1,248 | Persistence + Treasury client + single-flight gate + cache + health |
| `api` | 979 | HTTP controllers + filters + DTOs + `@RestControllerAdvice` |
| `observability` | 285 | `MetricsCatalog` + `DescriptionHasher` + warm-up listener |

### Delivery + dossier

| | |
|---|---:|
| **Commits on `main`** | **36** |
| **PRs merged** | **15** |
| Project active range | 2026-05-17 → 2026-05-18 |
| **Phase 13 chunks accepted** | **9** (PRE×2 / A1 / A2 / B1 / B2 / C / C2 / C3) + 1 superseded (B → B1+B2) |
| **Bulk-pass evidence phases** | **2** (Phase 10 Ops, Phase 11 PCI) |
| **Demonstrative ceremonies** | **1** (Phase 12 case-study) |
| **HITL-CONSOLIDATED-REVIEW** | **1** — verdict `READY FOR CASE-STUDY HITL WITH FINDINGS` |
| Cross-cutting directives in force | 5 |
| **Markdown documentation** | **129 docs, 17,261 LOC** under `docs/` (191 `.md` files repo-wide) |

### Quality gates (enforced in `pom.xml`)

| Gate | Threshold |
|---|---|
| JaCoCo line coverage — `domain` | ≥ 85 % |
| JaCoCo branch coverage — `domain` | ≥ 75 % |
| JaCoCo line coverage — `application` | ≥ 80 % |
| JaCoCo line coverage — `infrastructure` | ≥ 75 % |
| Pitest mutation score — `domain` | ≥ 85 % |
| Pitest mutation score — `application` | ≥ 80 % |
| Pitest mutation score — `infrastructure` (critical paths) | ≥ 80 % |

### PCI DSS v4.0.1 posture (see [`docs/security/pci-dss-control-mapping.md`](docs/security/pci-dss-control-mapping.md))

| Status | Count |
|---|---:|
| CLOSED (implemented + verified) | 18 |
| Phase-12-equivalent (documented procedure / design is the gate-passing evidence) | 23 |
| n/a-evidenced (out-of-CDE) | 7 |
| BLOCKING-for-prod (signatory adjudication; not blocking case-study) | 1 (Req 8 identity origin) |

---

## 3. Tech stack

- **Java 21** + **Spring Boot 3.3.5** (web, actuator, validation, JDBC)
- **PostgreSQL** (production) + **H2** (local mode) via **Liquibase** migrations
- **Caffeine** hot-cache (per-currency, `expireAfterWrite`) — keyed on `(currency, recordDate)` per ADR-0001 D-10
- **Resilience4j** — timeout / retry-with-jitter / circuit-breaker / bulkhead (programmatically composed)
- **Micrometer + Prometheus** (metrics) / **Micrometer Tracing + OpenTelemetry OTLP exporter** (traces) / **Logstash Logback Encoder** (structured JSON logs)
- **springdoc-openapi** + **Spectral** lint + **oasdiff** drift check
- **JUnit 5** + **Testcontainers** (Postgres 16) + **WireMock 3.9** (Treasury stub) + **ArchUnit 1.3**
- **JaCoCo 0.8.12** + **Pitest 1.17.0**

---

## 4. Repository navigation

| Topic | Path |
|---|---|
| **Single-document assessor tour** | [`AUDIT-BRIEF.md`](AUDIT-BRIEF.md) |
| **Project rollup + audit ledger** | [`docs/external-review/STATUS.md`](docs/external-review/STATUS.md) |
| **Canonical reviewer verdict** | [`docs/external-review/HITL-CONSOLIDATED-REVIEW.md`](docs/external-review/HITL-CONSOLIDATED-REVIEW.md) |
| **Cross-cutting directives** | [`docs/external-review/directives/`](docs/external-review/directives/) (5 directives) |
| **Per-chunk reviewer verdicts** | `docs/external-review/chunks/13-*/30-review.md` (9 chunks) |
| **Phase 10 / 11 / 12 dossiers** | [`docs/external-review/phases/`](docs/external-review/phases/) |
| **Architecture** | [`docs/architecture/`](docs/architecture/) — ADR-0001 (14 decisions D-1..D-14) + component / deployment / API-contracts / data-model + system-context |
| **Requirements + traceability** | [`docs/requirements/`](docs/requirements/) — source / functional / non-functional / acceptance criteria / traceability matrix / risk register |
| **Planning + adversarial grills** | [`docs/planning/`](docs/planning/) — Phase 4 design grill + Phase 6 reliability/scalability grill |
| **PCI scope + control mapping** | [`docs/security/pci-scope-and-cde.md`](docs/security/pci-scope-and-cde.md), [`pci-dss-control-mapping.md`](docs/security/pci-dss-control-mapping.md), [`pci-security-grill.md`](docs/security/pci-security-grill.md) |
| **Operations** | [`docs/operations/`](docs/operations/) — 12 files: SLO/SLI, observability, monitoring-alerting, runbook (25 playbooks), incident-response (~210 LOC), rollback-plan (6 classes A–F), oncall-escalation, capacity-scalability-plan, failure-modes (F-01..F-27), error-budget-policy, service-catalog, ops-readiness-gate |
| **OpenAPI baseline** | [`infra/openapi/baseline.yaml`](infra/openapi/baseline.yaml) |
| **Grafana dashboards** | [`infra/dashboards/`](infra/dashboards/) — 3 templates (SLO availability / SLO latency / Treasury dependency) |
| **Drill log scaffolding** | [`docs/operations/drills/`](docs/operations/drills/) — README + template |
| **CI/CD workflows** | [`.github/workflows/`](.github/workflows/) — `ci.yml`, `security.yml`, `deploy-{dev,staging,prod}.yml` |
| **Production approval marker** | [`.human-approvals/`](.human-approvals/) (`.txt` files gitignored by policy) |

### Suggested assessor reading order

1. [`AUDIT-BRIEF.md`](AUDIT-BRIEF.md) — 15 min single-doc tour.
2. [`docs/external-review/HITL-CONSOLIDATED-REVIEW.md`](docs/external-review/HITL-CONSOLIDATED-REVIEW.md) — canonical reviewer verdict.
3. [`docs/architecture/adr-0001-core-architecture.md`](docs/architecture/adr-0001-core-architecture.md) — the 14 architectural decisions in one document.
4. [`docs/external-review/STATUS.md`](docs/external-review/STATUS.md) — audit ledger; every phase / chunk / PR.
5. Source code under [`src/`](src/) — start with `domain/`, then `application/`, then `infrastructure/`, then `api/`.
6. Tests under [`src/test/java/`](src/test/java/) — 248 test methods across 37 classes.

---

## 5. Building + running

This project follows standard Maven layout. Maven-in-runner is a tracked Phase-12-equivalent item (M7 carry-forward); CI today runs the make-based gates that don't require `mvn`.

```bash
# Local dev quality gates (Python helpers; no JVM required)
make ci          # = docs-check + ops-check + pci-check + lint + typecheck + test + security

# Build + test (requires Maven 3.9+ and Java 21)
mvn clean verify

# Run the service against the bundled local config (H2 in-memory)
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Run integration tests (Testcontainers spins Postgres 16 + WireMock 3.9)
mvn -P integration verify
```

---

## 6. Operational invariants (one-liners)

| Area | Invariant |
|---|---|
| **PCI** | Out-of-CDE by design. PAN never crosses trust boundary (boundary `ContentGuard`; no PAN-typed schema columns). Description never logged plain — HMAC-SHA-256 redaction via `DescriptionHasher`. |
| **Secrets** | `wex.log.hash.key` mandatory in `prod` / `staging` / `local-pii`. Service **refuses to start** if absent. Never logged, never echoed in errors. |
| **Treasury upstream** | Single-flight gate keyed on `(currency, treasury_quarter_end)` — concurrent first-fetches collapse to one request. Bounded retry + circuit-breaker. Graceful shutdown releases gate state on SIGTERM. |
| **Rate versioning** | Revisions land as a **new row** keyed `(currency, record_date, effective_date)`. Original rows are immutable. `max(effective_date)` wins for in-window queries (**AC-026b**). |
| **Rollback** | 6 classes documented (A code / B config / C schema forward-only / D data PITR / E Treasury orientation flip / F security incident). Rehearsal cadence quarterly. |
| **HMAC-key rotation** | New `vN+1:` prefix; old digests stay correlatable within their key window. Procedure in [`docs/security/encryption-key-management.md`](docs/security/encryption-key-management.md). |

---

## 7. Project journey

```
Phase 1  Requirements ingestion                      ──╮
Phase 2  Requirements grill                            │
Phase 3  Design session                                │  Documented under docs/
Phase 4  Design grill (adversarial)                    │  and docs/planning/
Phase 5  Operational design session                    │
Phase 6  Reliability/scalability grill                 │
Phase 7  PCI security design + adversarial grill     ──╯
Phase 8  Implementation readiness gate
Phase 13 Chunked implementation (11 PRs, 9 chunks)   ──╮  External-review
Phase 10 Operational Readiness Gate (bulk-pass)        │  dossier under
Phase 11 PCI Security Readiness Gate (bulk-pass)       │  docs/external-review/
Phase 12 Production Approval (case-study demo)       ──╯
```

Phase 13 ran **before** Phase 10/11 because the implementation produced the evidence the readiness gates needed. Phase 12 is the **multi-party signature procedure** documented in [`docs/security/change-control-pci.md §1`](docs/security/change-control-pci.md) — for the case study, it runs **demonstratively** ([`docs/external-review/phases/12-production-approval/DEMONSTRATIVE-CEREMONY.md`](docs/external-review/phases/12-production-approval/DEMONSTRATIVE-CEREMONY.md)).

---

## 8. Authoring + tooling

This project was built using **[Claude Code](https://claude.com/claude-code)** under the operating model in [`CLAUDE.md`](CLAUDE.md). Every implementation chunk landed via a PR with a reviewer-authored `30-review.md` (Phase 13) or a dev-authored provisional `30-review.md` ratified by the consolidated HITL pass (Phases 10–12). The dossier under [`docs/external-review/`](docs/external-review/) is the full audit trail of that collaboration.

Engineering author: **Sajith Mankavil** (architect / PO role for the case study).

---

## 9. License + reuse

No `LICENSE` file is provided. Default: **all rights reserved**. The repository is a portfolio / case-study deliverable; reuse of the code requires the author's permission.

Underlying frameworks (Spring Boot, Resilience4j, Caffeine, Logstash Logback Encoder, springdoc, Micrometer, OpenTelemetry, JUnit, Testcontainers, WireMock, ArchUnit, JaCoCo, Pitest, etc.) are used under their respective open-source licenses.
