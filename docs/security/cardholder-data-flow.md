# Cardholder Data Flow

> **Status:** Phase 7 (PCI Security Design Session), 2026-05-17.
> **Owner:** SecArch.
> **Posture:** No cardholder data flow exists. This document is the evidence.

---

## 1. Data types — presence inventory

| PCI data type | Present in this service? | If "no": evidence |
|---|---|---|
| **PAN** (Primary Account Number) | **No** | No column shaped for it; API contract prohibits; boundary guards reject (AC-010b, AC-010d). |
| **CHD** (Cardholder Data, including PAN + cardholder name + expiry + service code) | **No** | Same. The service has no "cardholder" concept. |
| **SAD** (Sensitive Authentication Data: CVV/CVC, full track, PIN/PIN block) | **No** | API contract prohibits; track-data shape guard (AC-010c) explicitly rejects magnetic-stripe-shaped strings. |
| **CVV / CVC** | **No** | Same as SAD; 3–4-digit numbers in `description` are not flagged (length-only heuristic too noisy); the contract-level prohibition (A-017) is the primary control. |
| **Track data** (Track 1 / Track 2) | **No** | AC-010c track-data shape guard. |
| **Tokens / network tokens** | **No** | No tokenisation in this service (see [tokenization-and-pan-handling.md](tokenization-and-pan-handling.md)). |

## 2. Flow inventory

Every inbound, outbound, and internal flow in the system. None carry CHD.

| Flow ID | Ingress | Processing | Storage/logging | Egress | Data elements | Encryption | Scope impact |
|---|---|---|---|---|---|---|---|
| **FLOW-001** Create purchase | `POST /api/v1/purchases` | `PurchaseController` → `ContentGuard` → `PurchaseService` → `PurchaseRepositoryAdapter` | `purchase_transactions` table (`description`, `transaction_date`, `amount_usd`) + audit log (length + HMAC digest only) | `201 Created` response (echoes input fields) | Free-text `description` (bounded ≤ 50 chars, **guarded**); date; decimal amount | TLS 1.3 in/out; HMAC-SHA-256 for `description` log digest | Out-of-CDE: no CHD by contract |
| **FLOW-002** Retrieve purchase | `GET /api/v1/purchases/{id}` | `PurchaseController` → `PurchaseService` → `PurchaseRepositoryAdapter` | Read-only from `purchase_transactions` | `200` or `404` response | Same as FLOW-001 retrieved row | TLS 1.3 | Out-of-CDE |
| **FLOW-003** Retrieve converted purchase | `GET /api/v1/purchases/{id}/conversion?currency=…` | `ConversionController` → `ConversionService` → alias resolution → DB lookup → maybe Treasury client | DB read + maybe DB upsert of new rate rows; never CHD | Outbound HTTPS to `api.fiscaldata.treasury.gov`; `200`/4xx/5xx response | Treasury returns `country_currency_desc`, `record_date`, `effective_date`, `exchange_rate` (all public data); never CHD | TLS 1.3 in/out; TLS 1.2 minimum to Treasury | Out-of-CDE |
| **FLOW-004** Operability + observability | `/actuator/health/*`, `/actuator/prometheus`, `/v3/api-docs`, `/swagger-ui.html` | Spring Boot Actuator | Logs (JSON); metrics (Prometheus); traces (OTel) | Outbound to telemetry sinks | Service state, request metadata; `description` only as length + HMAC digest | TLS 1.3 to sinks | Security-impacting (logs may carry hashed PCI signal markers but never CHD) |
| **FLOW-005** Audit emission | Internal | `ContentGuard` reject path → audit-event emission | Append-only audit-log destination (Phase-7 ratified destination) | Telemetry sink; no API response carries audit events | `event=purchase_validation_failed`, `reason=luhn|luhn-encoded|track1|track2` etc.; `description.length`, `description.hash`; never the rejected payload | TLS 1.3; WORM destination in production | Security-impacting (evidence of the rejection) |
| **FLOW-006** Secret retrieval (startup-only) | Platform secrets store | `WexProperties` bean initialisation | None — secret loaded into JVM memory only | None — never leaves the JVM | `WEX_LOG_HASH_KEY` (HMAC secret); `WEX_DB_PASSWORD` (DB credential) | TLS to secrets store; in-memory only | Security-impacting |

## 3. Diagram — the "no-CHD" data flow

```
                    +------------------+
                    | API client       |
                    | (no CHD allowed) |
                    +------------------+
                             |
                             | HTTPS (TLS 1.3 preferred / 1.2 minimum)
                             |
                             v
                    +------------------+
                    | Ingress/Gateway  |   (prod only)
                    | - TLS terminate  |
                    | - Identity at    |
                    |   gateway        |
                    | - Rate limiting  |
                    +------------------+
                             |
                             v
                    +--------------------------------------------------+
                    |  wex-purchase-fx (single trust zone, out-of-CDE) |
                    |                                                  |
                    |   API layer                                      |
                    |     +-- ContentGuard (Luhn + track + encoded)    |
                    |     |    Rejects payment-data-shaped input       |
                    |     |    Logs audit-event WITHOUT payload        |
                    |     |                                            |
                    |     v   pass-through                             |
                    |   Application layer                              |
                    |     PurchaseService / ConversionService          |
                    |                                                  |
                    |   Domain layer                                   |
                    |     Pure POJOs; no CHD-shaped types              |
                    |                                                  |
                    |   Infrastructure                                 |
                    |     - PurchaseRepositoryAdapter                  |
                    |     - ExchangeRateRepositoryAdapter              |
                    |     - TreasuryClientAdapter                      |
                    |     - DescriptionHasher (HMAC-SHA-256)           |
                    +--------------------------------------------------+
                       |                |                |          |
                       |                |                |          |
                       v                v                v          v
                +-----------+   +-------------+   +------------+   +--------+
                | PostgreSQL|   | Telemetry   |   | Treasury   |   |Secrets |
                | (prod) /  |   | sinks (logs,|   | Fiscal Data|   | store  |
                | H2 (local)|   | metrics,    |   | API        |   | (Vault)|
                |           |   | traces, audit|  | (public)   |   |        |
                +-----------+   +-------------+   +------------+   +--------+
                  No CHD col      `description`     No CHD: only    HMAC key
                  exists          logged only as    public rate     loaded at
                                  length + HMAC     data            startup
                                  digest. Audit
                                  destination
                                  WORM in prod.
```

**Key observations:**
- The ContentGuard is the boundary. Anything past it is by-construction CHD-free (because what entered was not CHD per the contract, and the guard caught syntactic violations).
- The DB schema has no PAN-shaped column. `description` is bounded VARCHAR(50). `amount_usd` is `DECIMAL(19,2)` — a money column, not a PAN column.
- Treasury responses are public open-data; never carry CHD.
- Logs carry `description` as length + HMAC digest only; the rejected payload from a guard-trip is **never** logged.

## 4. Prohibited flows (explicitly denied by design)

| Flow | Why prohibited | Detection |
|---|---|---|
| Storing PAN, CHD, or SAD anywhere in `purchase_transactions` | No column shaped for it; CHECK constraints prevent it | DB schema review; column-name audit |
| Logging the verbatim `description` field | NFR-017 / AC-032 / AC-032b | `LoggingPiiGuardTest`; PMD rule (Phase 13) |
| Sending `description` to a third party (Treasury, telemetry sink, future integrations) | The Treasury request body contains only currency + date filters; logs carry only HMAC digest; no future integration is in scope v1 | Outbound traffic inspection (Phase 12 pre-prod) |
| Persisting CHD that bypasses the guard via base64 / hex / URL encoding | AC-010d encoded-PAN guard | Phase 13 implementation; pen-test in Phase 7 |

## 5. Evidence required (cross-reference)

| Evidence | Location | Frequency | Retention |
|---|---|---|---|
| Data discovery scan (DB columns + log samples) | `evidence-register.md` EVD-003 | Quarterly | 1 year |
| Log redaction test (PMD rule + integration test) | `evidence-register.md` EVD-010 + AC-032 | Per release | 1 year |
| Telemetry redaction test (metric labels do not carry `description`) | `evidence-register.md` EVD-010 | Per release | 1 year |
| Backup content validation (no CHD in backups) | Postgres backup sample audit | Quarterly | 1 year |
| Boundary-guard probe (CHD injection test set) | [pci-scope-and-cde.md §4.1](pci-scope-and-cde.md#41-segmentation-validation-method) | Quarterly | 1 year |
| Audit-event sampling for guard fires | `evidence-register.md` EVD-010 | Monthly | 1 year |

## 6. Linked artefacts

- [pci-scope-and-cde.md](pci-scope-and-cde.md) — the scope claim this document evidences.
- [cardholder-data-classification.md](cardholder-data-classification.md) — data inventory.
- [tokenization-and-pan-handling.md](tokenization-and-pan-handling.md) — explicit statement we don't.
- [pci-dss-control-matrix.md](pci-dss-control-matrix.md) — control mappings.
- [evidence-register.md](evidence-register.md) — central evidence index.
- [logging-monitoring-pci.md](logging-monitoring-pci.md) — audit destination + log redaction details.
