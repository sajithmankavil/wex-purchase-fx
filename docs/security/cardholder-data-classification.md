# Cardholder Data Classification

> **Status:** Phase 7 (PCI Security Design Session), 2026-05-17.
> **Owner:** SecArch.
>
> Data inventory of every field stored or transmitted by the service, with PCI classification per data type. Reads as a row-by-row "is this CHD?" — the answer is "no" for every row.

---

## 1. PCI data-type presence inventory

| Data element | Classification | Stored in this service? | Reason |
|---|---|---|---|
| PAN | CHD | **No** | No column shaped for it; API contract prohibits; AC-010b/d guards reject |
| Cardholder name | CHD (if with PAN) | **No** | No such column; not solicited |
| Expiration date | CHD (if with PAN) | **No** | No such column |
| Service code | CHD (if with PAN) | **No** | No such column |
| CVV / CVC | SAD | **No** | SAD storage prohibited post-authorisation; contract-level prohibition |
| Track data | SAD | **No** | AC-010c track-data shape guard rejects |
| PIN / PIN block | SAD | **No** | Out of scope (no PIN-handling capability) |
| Token / network token | Sensitive internal | **No** | No tokenisation in this service ([tokenization-and-pan-handling.md](tokenization-and-pan-handling.md)) |

## 2. Data we *do* store

### 2.1 `purchase_transactions` table

| Field | Type | PCI classification | Sensitivity | Storage policy |
|---|---|---|---|---|
| `id` | VARCHAR(36) UUID v7 | Not PCI | Public-ID-equivalent | Plain (server-generated; opaque; not enumerable in practice — 122 random bits) |
| `description` | VARCHAR(50) UTF-8 | **Untrusted free-text** | The ONLY surface for accidental CHD ingestion — see §3 | Plain *after* guards; never logged plaintext (NFR-017 / AC-032) |
| `transaction_date` | DATE (UTC) | Not PCI | Business metadata | Plain |
| `amount_usd` | DECIMAL(19,2) | Not PCI | Financial metadata | Plain (positive, scale exactly 2) |
| `created_at` / `updated_at` | TIMESTAMP(6) UTC | Not PCI | Operational | Plain |

### 2.2 `exchange_rates` table

All public open-data sourced from Treasury Fiscal Data API. No PCI classification applies.

| Field | Type | Classification |
|---|---|---|
| `country_currency_desc` | VARCHAR(64) | Public |
| `record_date` | DATE | Public |
| `effective_date` | DATE | Public |
| `exchange_rate` | DECIMAL(19,6) | Public |
| `source` | VARCHAR(32) | Operational |
| `fetched_at` | TIMESTAMP(6) | Operational |

### 2.3 Audit-log destination (external)

| Field | Classification | Notes |
|---|---|---|
| `event` | Not PCI | Enum: `purchase_created`, `purchase_validation_failed`, etc. |
| `reason` | Not PCI | Enum: `luhn`, `luhn-encoded`, `track1`, `track2`, `length`, etc. |
| `purchaseId` | Not PCI | UUID v7 |
| `descriptionLength` | Not PCI; low-leakage side-channel | Length integer only; residual TM-I-002 |
| `descriptionHash` | Not PCI | `vN:<hex>` HMAC-SHA-256 digest; non-reversible without the key |
| `correlationId`, `traceId`, `spanId` | Not PCI | Operational |

### 2.4 In-memory only (secrets)

| Data | Classification | Lifecycle |
|---|---|---|
| `WEX_LOG_HASH_KEY` | **High-sensitivity secret** | Startup load from platform secrets store; in-memory only; never logged; ≥ 256 bits (NFR-013b). See [encryption-key-management.md](encryption-key-management.md). |
| `WEX_DB_PASSWORD` | **High-sensitivity secret** | Same. |
| TLS private key | **High-sensitivity secret** | Held by the platform's TLS-terminating ingress; never in the service's address space. |

## 3. The `description` field — attack-surface analysis

This is the **only** field where a hostile / careless client could submit CHD-shaped content. Defense stack:

| Layer | Mechanism | What it catches |
|---|---|---|
| 1 — Contract | API contract + OpenAPI docs: "description must not contain payment data" | Accidental client misuse |
| 2 — Length | `@Size(max=50)` Bean Validation | Long PAN+name+expiry payloads (rejected pre-guard) |
| 3 — Luhn pattern | AC-010b: regex for 13–19-digit sequences with optional separators + Luhn check | Realistic PANs |
| 4 — Track-data shape | AC-010c: `%B…?` (track 1) and `;…?` (track 2) patterns | Magnetic-stripe payloads |
| 5 — Encoded pre-pass | AC-010d: base64 / hex / URL-decode + re-run Luhn + track guards | Base64-encoded PANs and similar |
| 6 — Audit emission | `purchase_validation_failed{reason}` on every reject | Evidence to QSA; drives A-021 / A-025 |
| 7 — Payload redaction | Rejected payload **never** logged | Even on guard trip, the bad content does not enter the audit trail |
| 8 — Length + HMAC digest only | NFR-017 / AC-032 / AC-032b | Successful descriptions logged as length + non-reversible digest |

**Accepted residuals at v1** (in [threat-model.md §7](threat-model.md)):
- Multi-encoding chain (URL-encoded base64 of a Luhn-valid PAN — AB-014). Phase 8 PCI grill re-attacks.
- Unicode confusable digits. Phase 8 considers Unicode normalisation.
- CVV-shaped 3-digit content (too short to flag without high false-positive rate).
- Split CHD across `description` and a future second free-text field (re-evaluate if such a field is introduced).

## 4. Required controls

| Control | Detail | Reference |
|---|---|---|
| PAN masking standard | n/a — no PAN to mask | — |
| Data minimisation | The service stores only what the source rule requires: `description`, `transaction_date`, `amount_usd`. No name, no contact info, no client identifier. | source-requirements.md |
| Retention purge job | v1: indefinite retention (OQ-008). PCI evidence: audit logs ≥ 1 year (NFR-016b). | OQ-008 |
| Data discovery cadence | Quarterly DB column-audit + log-sample review confirming no CHD-shaped data. Evidence in [evidence-register.md](evidence-register.md). | Quarterly |
| Exception approval process | Any future request to store PAN/SAD requires a new PCI design review (re-opening this gate). | Phase-7 process |

## 5. Linked artefacts

- [pci-scope-and-cde.md](pci-scope-and-cde.md) — overall scope claim.
- [cardholder-data-flow.md](cardholder-data-flow.md) — flow diagram showing no CHD.
- [tokenization-and-pan-handling.md](tokenization-and-pan-handling.md) — explicit not-tokenising statement.
- [encryption-key-management.md](encryption-key-management.md) — secrets + key management.
- [data-model.md](../architecture/data-model.md) — schema source-of-truth.
- [threat-model.md](threat-model.md) — STRIDE catalogue.
