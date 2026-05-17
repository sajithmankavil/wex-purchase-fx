# System Context

> C4 Level 1 — system in its environment. For internal structure see [component-design.md](component-design.md); for the operational deployment see [deployment-architecture.md](deployment-architecture.md).
> **Status:** Phase 3 (Architecture & Design Session), 2026-05-17.

## Purpose

The **WEX Purchase Currency Conversion Service** stores purchase transactions denominated in U.S. dollars and, on retrieval, converts them to a chosen target currency using the most recent eligible exchange rate from the **U.S. Treasury Reporting Rates of Exchange** dataset, applying a 6-month rate-selection rule per the source requirements.

Two business outcomes:

1. A purchase, once stored, is durably retrievable by id (FR-001 / FR-002).
2. A stored purchase can be re-expressed in any currency Treasury publishes a rate for, as of the purchase date, provided an eligible rate exists in the 6-month window (FR-003).

A third, implicit outcome is **operability**: the service must run locally on a stock JDK 21 with one command, and the same codebase must be production-deployable behind an ingress with a PostgreSQL profile.

## Actors

| Actor | Type | Role | Trust | Auth (v1) | Auth (prod) |
|---|---|---|---|---|---|
| API client (case study) | Human or script | Submits `POST /purchases` and `GET /purchases/{id}/conversion`. | Trusted (local execution) | None | n/a |
| API client (production deployment) | Internal service | Same operations behind a gateway. | Trusted (network) | None at app layer | Identity established at gateway (OQ-010 BLOCKING-for-prod: mTLS, OIDC/JWT, or SPIFFE). |
| Operator / SRE | Human | Reads logs/metrics/traces; runs the runbook; triggers cache reset; deploys. | Trusted | n/a | RBAC via platform; not surfaced through the service's HTTP API. |
| Auditor / QSA | Human | Reviews evidence of PCI scope reduction. | Read-only on documents and audit logs. | n/a | n/a |

The service exposes **no end-user UI**. Swagger UI (`/swagger-ui.html`) is a developer-facing OpenAPI browser, not a customer surface.

## External systems

| External | Direction | Purpose | Auth | SLA assumption | Failure-mode posture |
|---|---|---|---|---|---|
| **Treasury Fiscal Data API** (`api.fiscaldata.treasury.gov`) | Outbound HTTPS | Source of authoritative exchange rates; queried on local cache miss; persisted on success. | Anonymous (no API key) | Unpublished. Empirically reachable; quarterly publish cadence (verified in Phase-3 prototype). | Bounded timeout, bounded retry with backoff+jitter, circuit breaker, single-flight per `(currency, window)`, schema-validated, sanity-checked (`> 0`, `≤ 10⁹`). If unreachable and no eligible local rate: `503 UPSTREAM_UNAVAILABLE`. |
| **Ingress / API gateway** (prod only) | Inbound HTTPS | TLS termination, network ACLs, identity establishment. | Platform | Production SLA inherited from platform. | Single point of network entry; readiness probe drives gateway health. |
| **Container platform / orchestrator** (prod only) | Lifecycle | Replica scheduling, health checks, rolling deploys, log/metric scraping. | Platform | Production SLA inherited. | Liveness vs readiness distinguish JVM health from dependency health; readiness DOWN drains traffic. |
| **PostgreSQL** (prod only) | Bidirectional | Durable system of record for purchases and exchange rates. | Service account; least privilege; secrets via env. | Production SLA inherited. | Readiness DOWN if DB unreachable. PITR + scheduled backups (managed by platform). |
| **Secrets provider** (prod only) | Inbound, startup-time | Source of `WEX_LOG_HASH_KEY` and (future) DB credentials. | Platform | n/a | Refuse-to-start on absence in `prod` / `staging` profiles. |
| **Telemetry sinks** (prod only) | Outbound | Logs (JSON), metrics (Prometheus scrape on `/actuator/prometheus`), traces (OTel exporter to collector). | Platform | n/a | Best-effort; loss of telemetry does not affect request handling but does flip alerting blind. |

The service has **no other** outbound dependencies. Notably: no message bus, no email, no third-party FX provider, no payment processor, no analytics SaaS.

## Context diagram

```
                   ┌───────────────────────────────────────────────────────────────┐
                   │                       Trust boundary (deployment-mode dependent)              │
                   │                                                               │
                   │    ┌─────────────┐                                            │
   API client ───▶ │    │  Ingress /  │ ────▶ ┌──────────────────────────────────┐ │
   (case study:    │    │  Gateway    │       │                                  │ │
    direct;        │    │ (prod only) │       │    wex-purchase-fx               │ │
    prod: via      │    └─────────────┘       │    (Spring Boot 3.x, JDK 21,     │ │
    gateway)       │                          │     embedded Tomcat)             │ │
                   │                          │                                  │ │
                   │                          │  ┌────────────────────────────┐  │ │
                   │      ┌─────────────┐     │  │  Stateless application     │  │ │
   Operator   ───▶ │ ───▶ │ Telemetry   │ ◀── │  │  layer                     │  │ │
   (logs/metrics/  │      │ sinks       │     │  │                            │  │ │
    traces, log    │      │ (Prom, OTel,│     │  │  • api, application,       │  │ │
    inspection)    │      │  Loki/etc.) │     │  │    domain, infrastructure  │  │ │
                   │      └─────────────┘     │  │  • CurrencyAliasTable      │  │ │
                   │                          │  │  • Resilience4j-wrapped    │  │ │
                   │                          │  │    TreasuryClient          │  │ │
                   │                          │  │  • single-flight gate      │  │ │
                   │                          │  │  • Caffeine hot cache      │  │ │
                   │                          │  └──────────────┬─────────────┘  │ │
                   │                          │                 │                │ │
                   │                          │                 ▼                │ │
                   │                          │  ┌────────────────────────────┐  │ │
                   │      ┌─────────────┐     │  │  Persistence               │  │ │
                   │      │ Secrets     │ ◀── │  │  • H2 file mode (local)    │  │ │
                   │      │ provider    │     │  │  • PostgreSQL (prod)       │  │ │
                   │      │ (prod only) │     │  └────────────────────────────┘  │ │
                   │      └─────────────┘     │                                  │ │
                   │                          └──────────────────┬───────────────┘ │
                   │                                             │                 │
                   │                                             ▼ HTTPS           │
                   │                                ┌──────────────────────────┐   │
                   │                                │  Treasury Fiscal Data API │   │
                   │                                │  (api.fiscaldata.        │   │
                   │                                │   treasury.gov)          │   │
                   │                                │   • anonymous            │   │
                   │                                │   • quarterly publish    │   │
                   │                                └──────────────────────────┘   │
                   │                                                               │
                   └───────────────────────────────────────────────────────────────┘
                                          (Internet)
```

## Trust boundaries

- **Inbound trust:** in case-study mode, the service is reached on `localhost`. In production, the ingress/gateway terminates TLS and (eventually) establishes identity (OQ-010 BLOCKING-for-prod). The service itself has no application-layer authentication in v1 (A-007).
- **Outbound trust:** Treasury is consumed over TLS 1.2+ with full certificate-chain validation and hostname verification. The response is schema-validated and sanity-checked; the service never trusts upstream data unconditionally.
- **Inter-process trust:** the service is a single OS process. No internal RPC; no sidecar.
- **Data sensitivity boundary:** the `description` free-text field is the only attack surface for accidental cardholder data. The PAN-pattern + track-data guards (AC-010b, AC-010c) reject hits at the API boundary. The primary control is the API contract's prohibition on payment data (A-017); guards are defense in depth.

## Data ownership

| Data | Owner | System of record | Access |
|---|---|---|---|
| Purchase transactions | This service | `purchase_transactions` table | API read; admin-only write outside the API (none planned). |
| Exchange rates (cached) | This service (derived) | `exchange_rates` table | API read; service writes on Treasury fetch. |
| Currency alias table | This service (curated) | `infrastructure/resources/currency-aliases.json` | Read at startup; PR-reviewed source-controlled change. |
| Audit events | This service → telemetry sink | Structured logs | Retention ≥ 1 yr / 3 mo online (NFR-016b). |
| HMAC log-hash key | Platform secrets provider | Out-of-process | Read once at startup via env var. |

The service does not own or process: identity / authentication state, customer payment data, accounting ledgers, downstream-system state, or analytics. Future integrations (CSV export, event-bus publish) are out-of-scope v1 (OQ-013).

## Key risks (Phase-3 carry-over)

| Risk | Source | Status at Phase-3 entry |
|---|---|---|
| R-001 Treasury API unavailability | NFR-006 / AC-022/023 | Open — mitigated by DB-primary cache + Resilience4j; SLO is bounded by Treasury effective uptime (G-P1-2; Phase 5/6 to ratify). |
| R-002 Treasury schema drift | AC-024 | Open — schema validator + nightly drift check (Phase 5). |
| R-005 6-month-window boundary inconsistency | A-003 / OQ-003 | Closed at Phase 2 (D-5 here ratifies); EOM-clamp ACs lock the boundary. |
| R-007 PII/CHD in `description` | NFR-015 / OQ-011 | Open — guards in place; PCI evidence Phase 7. |
| R-008 No app-layer auth | A-007 / OQ-010 | Open — BLOCKING-for-prod; case-study acceptable. |
| R-014 Alias-table drift | A-001 / OQ-016 | Open — drift detection counter; the Phase-3 prototype turned up the `Euro Zone-Euro` space caveat (collateral P-1). |
| R-017 Rate orientation | OQ-017 / G-P0-1 | **Closed at Phase 3** by the prototype (D-4 here). |
| R-022 H2 on cloud-sync drive | A-021 / G-P1-1 | Open — D-14 here ratifies the `WEX_DATA_DIR` default off cloud-sync prefixes; CI/runbook follow-up. |
| R-026 CONV-vs-UPSTREAM mis-routing | G-P0-3 | Closed at Phase 3 by the decision table in [api-contracts.md](api-contracts.md). |
| R-027 Treasury rate revision drift | G-P0-4 / A-018 | Closed at Phase 3 by versioned persistence (D-3 here). |

Full register: [docs/requirements/risk-register.md](../requirements/risk-register.md).

## What is *not* in scope (v1)

Listed here so reviewers don't have to chase across documents:

- App-layer authentication or authorization. Identity is established upstream of the service in any non-local deployment.
- Multi-tenant isolation. The service is single-tenant.
- Update or delete of purchases.
- Bulk import / export.
- List / pagination over purchases.
- Non-USD source amounts.
- Webhooks or event publication.
- Performance-test automation (explicitly excluded by the source requirement).
- A user interface.

Each of these is a discrete future feature; the architecture does not preclude any of them.
