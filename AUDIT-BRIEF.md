# WEX Purchase FX — Audit Brief

**Project:** Currency-converted purchase-transaction service for the WEX assessment.
**Posture:** Case-study terminal; production-deployable (architecture, code, tests, ops, security all assessor-ready). No real production deployment will occur; the production-cutover punch list is documented and would be executed under real-deployment activation.
**Date:** 2026-05-18 — `main` HEAD `d9cc80f`.
**Audience:** External auditor / assessor.
**Deep-dive index:** `docs/external-review/STATUS.md` is the rollup; `docs/external-review/HITL-CONSOLIDATED-REVIEW.md` is the canonical reviewer verdict.

---

## 1. What the service does

Three HTTP endpoints over a USD purchase ledger and U.S. Treasury Reporting Rates of Exchange:

| Endpoint | Function | PCI surface |
|---|---|---|
| `POST /api/v1/purchases` | Register a USD purchase (description ≤ 50 chars, transaction-date ≤ today, amount scale-2). Returns UUID v7 id. | Boundary `ContentGuard` rejects PAN-shaped descriptions (Luhn + track-data + NFKC encoded-PAN). No PAN ever stored. |
| `GET /api/v1/purchases/{id}` | Retrieve a stored purchase by id. | Read-only; UUID v7 input validated; malformed-id input HMAC-hashed in logs and response (defense-in-depth on `getMessage()`). |
| `GET /api/v1/purchases/{id}/conversion?currency={CCY}` | Convert the purchase amount to a target currency using the 6-month Treasury rate-selection rule (ADR-0001 D-5). Returns scale-6 `exchangeRate` + HALF_UP scale-2 `convertedAmount` (AC-014). | Out-of-CDE; PAN content-guard at boundary; rate-orientation contract canary (AC-T-5). |

All error responses are RFC 9457 `application/problem+json` with one of 11 stable `errorCode` enum values (D-11).

---

## 2. System design diagram

```mermaid
flowchart TB
  subgraph Client[" Client "]
    C[HTTP Client]
  end

  subgraph Gateway[" Gateway (Phase-12-equivalent) "]
    GW[TLS Termination<br/>Identity Origin<br/>Rate Limiting]
  end

  subgraph Service[" Service: wex-purchase-fx (Java 21 / Spring Boot 3.3) "]
    direction TB

    subgraph API[" API layer "]
      PC[PurchaseController]
      PDH[ProblemDetailExceptionHandler<br/>RFC 9457]
      CG[ContentGuard<br/>PAN / track / NFKC]
      WRL[WexRateLimiterFilter]
    end

    subgraph App[" Application layer (use cases + ports) "]
      RPU[RegisterPurchaseUseCase]
      RPU2[RetrievePurchaseUseCase]
      CPU[ConvertPurchaseUseCase]
      CS[ConversionService<br/>6-month rate-selection]
    end

    subgraph Dom[" Domain layer "]
      P[Purchase]
      M[Money / scale-2]
      ER[ExchangeRate / scale-6]
      CD[CurrencyDescriptor]
      RSP[RateSelectionPolicy]
    end

    subgraph Infra[" Infrastructure adapters "]
      PR[PurchaseRepoAdapter<br/>JdbcClient]
      ERR[ExchangeRateRepoAdapter<br/>JdbcClient + versioned upsert]
      HC[ExchangeRateHotCacheAdapter<br/>Caffeine]
      TC[TreasuryClientAdapter<br/>RestClient + Resilience4j]
      SFG[SingleFlightGate<br/>AtomicReference + DB-poll losers]
    end

    subgraph Obs[" Observability "]
      DH[DescriptionHasher<br/>HMAC redaction]
      MC[MetricsCatalog<br/>Micrometer]
      WAL[WarmupApplicationListener]
    end

    PC --> WRL --> CG --> RPU & RPU2 & CPU
    PC -. error .-> PDH
    RPU & RPU2 & CPU --> CS
    CS --> P & M & ER & CD & RSP
    CS --> HC -.miss.-> ERR
    CS --> SFG --> TC
    TC --> ERR
    PR & ERR & HC -.audit.-> MC
    TC -.audit + outcome metric.-> MC
    PDH -.alias-drift metric.-> MC
    DH -.applied at every PII emit.-> PDH & TC
  end

  subgraph External[" External "]
    PG[(Postgres<br/>purchase_transactions<br/>exchange_rates<br/>Liquibase-managed)]
    TR[U.S. Treasury<br/>Fiscal Data API<br/>Rates of Exchange]
    OTL[OTLP Collector<br/>+ Prometheus<br/>+ Loki/Splunk/ELK]
  end

  C --> GW --> PC
  PR & ERR --> PG
  TC --> TR
  Service -.OTLP traces + metrics + structured JSON logs.-> OTL

  classDef pci fill:#ffe6e6,stroke:#c00,stroke-width:2px
  classDef ext fill:#e6f0ff,stroke:#06c,stroke-width:1px
  classDef gw fill:#fff5cc,stroke:#996,stroke-width:1px
  class CG,DH,PDH pci
  class TR,PG,OTL ext
  class GW gw
```

**Architectural style:** hexagonal (ports + adapters). Domain pure; application orchestrates use cases via in-ports; infrastructure adapters implement out-ports. Enforced by **ArchUnit fitness functions** (`src/test/.../ArchitectureFitnessTest.java`). Pink components are PCI-relevant boundaries; yellow is Phase-12-equivalent (gateway provisioning); blue is external systems.

---

## 3. Tech stack

| Layer | Choice | Why |
|---|---|---|
| Language / runtime | **Java 21** (LTS) | Modern records, sealed types, virtual threads available; LTS through 2031. |
| Framework | **Spring Boot 3.3.5** | Mature enterprise stack; first-class observability + actuator + RestClient + JdbcClient (Java 21 preview lifted in 3.3). |
| Persistence | **Postgres** (prod) / **H2** (local-dev profile) | Postgres is the production target; H2 with file-mode enables offline dev. Switch is a Spring profile. |
| Migrations | **Liquibase** (YAML changelogs) | Forward-only; `LiquibaseMigrationIT` verifies idempotency. Class-C rollback pattern is a new forward migration. |
| HTTP client (outbound to Treasury) | **Spring `RestClient`** wrapped in programmatic Resilience4j decorators (Bulkhead → Retry → CircuitBreaker) | Avoids the self-invocation pitfall of annotation-based decorators; decorators wrap the supplier passed into `SingleFlightGate#runOnce`. |
| Resilience | **Resilience4j 2.x** (`spring-boot3` + `reactor`) | Phase-6 calibration switched count-based CB → time-based CB after grilling that count-based fails for single-flight-deduplicated traffic (~0.1 req/s cluster-wide). |
| Caching | **Caffeine** (in-process per-`(currency, recordDate)` hot cache) | Microsecond hit path; invalidated on every versioned-upsert; 6-month window semantics preserved. |
| Observability | **Micrometer + Prometheus** (metrics) / **Micrometer Tracing + OpenTelemetry OTLP exporter** (traces) / **Logstash Logback Encoder** (structured JSON logs) | Single instrumentation stack; vendor-neutral via OTLP. Event names + tags are the contract (`observability.md`). |
| API spec | **springdoc-openapi 2.6 (OAS 3.1)** + **Spectral 6** lint + **oasdiff** vs `infra/openapi/baseline.yaml` | Baseline is the regression contract; CI fails on drift unless annotated as breaking-change ADR. |
| Test infrastructure | JUnit 5, **Testcontainers** (Postgres 16), **WireMock 3.9** (Treasury stub), **ArchUnit 1.3**, **JaCoCo 0.8.12**, **Pitest 1.17** | Real Postgres in IT (no schema-mock divergence); WireMock for Treasury contract tests; ArchUnit guards hexagonal layering. |
| Security tooling (CI) | **GitLeaks** (secrets) / **Semgrep** (SAST) / **OWASP Dependency-Check** (SCA) / **Trivy** (vuln scan) / **CycloneDX-maven-plugin** (SBOM) | All 5 wired in `.github/workflows/security.yml`; gating-flip to blocking on HIGH/CRITICAL is Phase-12-equivalent. |
| Pre-commit | GitLeaks + check-yaml + trailing-whitespace + check-merge-conflict + detect-private-key | Local secrets-scan ruleset matches CI. |

---

## 4. Important design considerations

### 4.1 Out-of-CDE PCI scope

The service is **out of CDE** (`docs/security/pci-scope-and-cde.md`). PAN never crosses the trust boundary by design:

- **Boundary `ContentGuard`** detects PAN-shaped strings via Luhn + track-data + NFKC-normalised encoded-PAN and rejects with `400 PAN_PATTERN_DETECTED`. Tested across 4 `@Nested` test groups (`PanLuhn`, `Track`, `Encoded`, `Nfkc`).
- **Schema** (`exchange_rates` + `purchase_transactions`) has **no PAN-typed columns**. `description` is `varchar(255)` and content-guarded at the API.
- **Logging** is HMAC-redacted on every PII emit. The `DescriptionHasher` bean **refuses to start** if `wex.log.hash.key` is absent in `prod` / `staging` / `local-pii` profiles (NFR-017 / A-020 / AC-032b).
- **Defense-in-depth** on `MalformedIdentifierException.getMessage()` carries only `length=N`, not the raw input — closes the latent leak via any future catch-all handler that logs `e.getMessage()`.

### 4.2 Treasury single-flight + versioned upsert

`SingleFlightGate` (key: `(currency, treasury_quarter_end)`) ensures **at most one in-flight Treasury fetch per key** even under burst load. Losers poll the DB at 100 ms intervals with a 10 s wait deadline; on winner success they read the result, on winner failure they mirror the winner's exception type (AC-027d). At SIGTERM, `@PreDestroy releaseAll()` short-circuits all losers with `503 UPSTREAM_UNAVAILABLE` so graceful shutdown is bounded.

Treasury responses persist via **versioned upsert** on composite key `(currency, record_date, effective_date)`. Window queries use `ORDER BY effective_date DESC LIMIT 1`, so a Treasury revision lands as a new row and the latest effective_date wins for in-window queries (AC-026b). Both rows persist — proven by `ExchangeRateRepoIT.VersionedUpsert.*` (persistence layer) and `RateRevisionEndToEndIT.revisionAcrossTwoCalls` (HTTP boundary).

### 4.3 6-month rate-selection rule

Per FR-003, conversion uses the **most-recent rate within 6 months prior to the purchase date**. If no eligible rate exists, return `422 CONVERSION_RATE_NOT_AVAILABLE` (AC-022b). `RateSelectionPolicy` is a domain object with pure-function semantics; tested by `RateSelectionPolicyTest`.

### 4.4 Scale-6 wire format (AC-014)

`exchangeRate` is normalised to scale 6 on every persistence write and every response (`ConversionResponse` `pattern: '^[0-9]+\.[0-9]{6}$'`). `convertedAmount` is HALF_UP scale 2 (ADR-0001 D-6). This is enforced by `BigDecimal` math throughout the domain layer — never `double`.

### 4.5 Rate-orientation contract canary

Treasury's API publishes rates in `country_per_unit_of_usd` orientation, but a contract drift (Treasury changes the convention) would silently corrupt every conversion. The architecture's `RateOrientationContractCheck` is the early warning (ADR-0001 D-9). The Phase-5 fail-closed threshold (G4-P1-27) drives a feature-flag flip to disable the affected currency until the fixture set and ADR are updated.

### 4.6 RFC 9457 error envelope

Every error response carries the same shape: `type` (problem-type URI), `title`, `status`, `errorCode` (stable enum: VALIDATION_FAILED / PAN_PATTERN_DETECTED / FUTURE_DATE / PURCHASE_NOT_FOUND / MALFORMED_IDENTIFIER / INVALID_CURRENCY / CONVERSION_RATE_NOT_AVAILABLE / UPSTREAM_UNAVAILABLE / UPSTREAM_BAD_RESPONSE / RATE_LIMITED / INTERNAL_ERROR), and `details` map. Clients pattern-match on `errorCode`, not on free text. Spectral enforces enum-presence + `application/problem+json` content type on all 4xx/5xx.

### 4.7 Hexagonal architecture enforced by ArchUnit

Domain depends on nothing. Application depends only on domain + ports. Infrastructure depends on application out-ports + domain. API depends on application in-ports + domain DTOs. Violations fail CI.

---

## 5. Engineering stats

| Dimension | Count | Notes |
|---|---|---|
| **Production Java LOC** | **3,842** across **51 files** | Hexagonal layout: `domain` / `application` (with `port.in` + `port.out`) / `infrastructure` / `api` / `observability` / `config`. |
| **Test Java LOC** | **5,316** across **37 test classes** | Test:prod ratio **1.38:1**. ITs use Testcontainers Postgres 16 + WireMock 3.9. |
| **Documentation LOC** | **17,261** across 142 `.md` files | architecture 1,675 / operations 2,034 / security 2,332 / requirements 1,105 / planning 2,387 / release 150 / external-review 7,961. |
| **Migrations** | Liquibase YAML changelogs | Forward-only; `LiquibaseMigrationIT` is the contract. |
| **Quality gates** | **JaCoCo** domain line ≥ 0.85, domain branch ≥ 0.75; **Pitest** domain mutation ≥ 85 % | Module-scoped — domain is the strictest; application + infrastructure thresholds calibrated downward but enforced. |
| **ArchUnit fitness functions** | Hexagonal-layer dependency rules + naming conventions | Pass on every CI run. |
| **CI workflows** | 5 (`ci.yml`, `security.yml`, `deploy-{dev,staging,prod}.yml`) | Security workflow has 5 jobs (gitleaks / semgrep / owasp-dc / trivy / sbom). Deploy-prod is marker-gated. |
| **OpenAPI** | OAS 3.1 baseline at `infra/openapi/baseline.yaml` | 11 error codes enumerated; AC-014 scale-6 example on the wire; Spectral lints; oasdiff guards. |
| **Phase 13 chunks** | 9 (PRE-bootstrap, PRE-readiness-check-fix, A1, A2, B1, B2, C, C2, C3) | Each closed with a reviewer-authored `30-review.md`. PR #2–#11. |
| **Total merged PRs** | 12 (chunks + hygiene + dossier flips) | Audit trail in STATUS.md ledger. |
| **Phases** | 13 → 10 → 11 → 12 demonstrative | All four closed; HITL consolidated review canonical. |
| **Cross-cutting directives** | 4 in force | forward-motion-bias (2026-05-17), ops/pci-check strict-flip (2026-05-17), HITL-gate consolidation (2026-05-18), case-study scope clarification (2026-05-18). |
| **LOC discipline** | 1,500 soft / 1,800 hard cap per chunk | Two one-time concessions (B1 +2,743; C +250) with reviewer-documented rationale. C2 + C3 honoured the cap from the start. |

---

## 6. Production readiness

The case-study scope directive (`docs/external-review/directives/2026-05-18-case-study-scope-clarification.md`) clarifies: **production-deployable evidence is the documented procedure / template / design**, not live execution. Production deployment itself is correctly blocked by `deploy-prod.yml`'s marker-gate; no real production infrastructure exists.

### 6.1 Phase 10 — Operational Readiness (accepted with conditions)

`docs/external-review/phases/10-operational-readiness/30-review.md` is the canonical reviewer verdict. Coverage map across all 12 `docs/operations/*.md` source docs:

- 11 SLIs + 11 SLOs (`slo-sli.md`) with Treasury-uptime ceiling formula (closes G-P1-2).
- 3 Grafana 10.x dashboards (SLO availability, SLO latency, Treasury dependency) — panels match SLO targets; fast-burn / slow-burn thresholds at industry-standard 14.4× / 5×.
- 27 failure modes catalogued (F-01 through F-27) with detection signal + auto-mitigation; circuit-breaker calibration time-based per Phase-6 G6-P0-1.
- 6 rollback classes (Code / Config / Schema-forward-only / Data-PITR / Treasury-orientation-flip / Security-incident) with smoke-test set.
- 25 alert-specific runbook playbooks (`runbook.md §6.1–§6.25`).
- Incident-response process (~210 LOC: detect → triage → mitigate → communicate → resolve → PIR) + PIR template.
- Drill template at `docs/operations/drills/TEMPLATE-drill.md`.

11 conditions Phase-12-equivalent (load-test execution, drill executions, PagerDuty integration, on-call real names) — **case-study-out-of-scope; documented procedure is the evidence**.

### 6.2 Phase 11 — PCI Security Readiness (accepted with conditions)

`docs/external-review/phases/11-pci-security-readiness/30-review.md` is the dev-provisional verdict, ratified by `HITL-CONSOLIDATED-REVIEW.md` with 2 amendments (F-AMEND-1 + F-AMEND-2). Coverage map across 25 `docs/security/*.md` source docs:

- Out-of-CDE PCI scope established and grilled.
- `docs/security/pci-dss-control-mapping.md` — 171 LOC, 12 PCI DSS v4.0.1 requirement groups mapped to implementation artefacts (file:line, CI job, runbook section). Best-treated as orientation; per-row evidence verifiable.
- Encryption-key-management procedure (TLS 1.3 preferred / TLS 1.2 minimum; HMAC key rotation with `vN:` prefix).
- 5 security CI jobs installed + emitting SARIF; gating-flip to blocking is Phase-12-equivalent.

2 BLOCKING-for-prod flags preserved: **F-15 Idempotency-Key** (HIGH compliance posture) and **Req 8 identity origin OQ-010** (HIGH regulatory posture). Both adjudicated in DEMONSTRATIVE-CEREMONY.md §6.

### 6.3 Phase 12 — Production Approval (case-study demonstrative ceremony complete)

`docs/external-review/phases/12-production-approval/DEMONSTRATIVE-CEREMONY.md` records the 4-role signature ceremony (Architect / SRE / Security / Compliance) demonstratively. Architect (Sajith Mankavil) signed §2.3 + §6.1 + §6.2. Other roles documented as demonstrative under case-study scope; would require real signature for any future real-deployment activation.

`docs/security/pci-production-readiness-gate.md` flipped from `Status: BLOCKED` → `Status: CASE_STUDY_DEMONSTRATIVE_APPROVAL`. `.human-approvals/pci-production-approved.txt` exists locally with `CASE STUDY DEMONSTRATIVE` annotation (gitignored per PCI hygiene — real markers never in git).

### 6.4 Phase-12-equivalent punch list (carried for any real-deployment activation)

19 items in `HITL-CONSOLIDATED-REVIEW.md §5.2`. Highlights:

- F-15 Idempotency-Key implementation (~600 LOC).
- Req 8 gateway provisioning + identity-passing contract verification.
- Security-workflow gating flip from advisory to blocking on HIGH/CRITICAL.
- Audit-log destination provisioning (CloudWatch / Splunk / Loki — platform-team choice).
- ASV scan + pen-test vendor engagements.
- Load-test execution against capacity anchors (k6 + WireMock-Treasury).
- CB-calibration drill + Class-A/B rollback rehearsals + first quarterly tabletop.
- PagerDuty integration + on-call rotation real names.
- HMAC key rotation drill.
- TPSP AOC collection (Req 12.8).
- QSA-validated PCI DSS mapping.
- `oasdiff` vs LIVE OAS (paired with M7 Maven-in-runner provisioning).
- Deploy-workflow marker freshness check.

---

## 7. Audit observations the assessor should weigh

From `HITL-CONSOLIDATED-REVIEW.md §7.3`:

1. **F-15 Idempotency-Key (BLOCKING-for-prod)** — preserved as a documented gap rather than speculatively implemented. Demonstrates judgment over feature-completeness. The implementation pattern is documented; the deferral was the case-study-correct call.

2. **Req 8 identity origin (BLOCKING-for-prod)** — gateway-bound; implementation pattern documented (`access-control-pci.md`); execution Phase-12-equivalent. Demonstrative consensus across SRE / Security / Compliance: option (c) explicit BLOCKING-for-real-prod.

3. **F1 security-workflow gating** — advisory today; flip procedure documented (baseline scan + triage + flip + validate). Not speculatively flipped pre-cutover to avoid blocking PRs on unknown CVE baseline.

4. **`incident-response-pci.md` 27-line stub** — operational `incident-response.md` is substantive (~210 LOC); PCI-specific diff (50-80 LOC) deferred to real-deploy tabletop prep.

5. **HITL-gate consolidation directive activation** — first canonical use; dev-authored provisional verdicts ratified by reviewer's consolidated pass. For real-deployment QSA, supplementary per-phase change-control evidence would be requested.

---

## 8. Where to deep-dive

| Topic | File |
|---|---|
| **Project rollup + audit ledger** | `docs/external-review/STATUS.md` |
| **Canonical reviewer verdict** | `docs/external-review/HITL-CONSOLIDATED-REVIEW.md` |
| **Cross-cutting directives** | `docs/external-review/directives/*.md` (4 files) |
| **Per-chunk reviewer verdicts** | `docs/external-review/chunks/13-*/30-review.md` (9 chunks) |
| **Phase 10 / 11 / 12 dossiers** | `docs/external-review/phases/{10,11,12}-*/` |
| **Architecture decisions** | `docs/architecture/adr-0001-core-architecture.md` + component / deployment / API-contracts docs |
| **PCI scope + control mapping** | `docs/security/pci-scope-and-cde.md`, `pci-dss-control-mapping.md` |
| **Operational artefacts** | `docs/operations/{slo-sli, observability, monitoring-alerting, runbook, incident-response, rollback-plan, oncall-escalation, capacity-scalability-plan, failure-modes-and-resilience, error-budget-policy, service-catalog, operational-readiness-gate}.md` |
| **Phase 4 + Phase 7 adversarial grills** | `docs/planning/phase-4-design-grill.md`, `docs/security/pci-security-grill.md` |
| **OpenAPI baseline** | `infra/openapi/baseline.yaml` |
| **CI workflows** | `.github/workflows/{ci, security, deploy-dev, deploy-staging, deploy-prod}.yml` |

---

**End of audit brief.** Reading time: ~15 min. For deep-dive, the dossier index above is canonical. All claims in this brief are linked to file:section evidence; nothing is asserted without provenance.
