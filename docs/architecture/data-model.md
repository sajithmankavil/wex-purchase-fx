# Data Model

> Persistence schema and lifecycle. References [adr-0001-core-architecture.md](adr-0001-core-architecture.md) for the rationale of versioned rate persistence, BigDecimal mapping, and indexing choices.
> **Status:** Phase 3 (Architecture & Design Session), 2026-05-17.
> **Phase-4 grill refinements (2026-05-17):** `exchange_rates` surrogate `id` dropped; composite PK promoted; sanity bound raised to 10³⁰; `effective_date >= record_date` CHECK softened; scale-6 normalisation pinned (G4-P0-3 / G4-P1-2 / G4-P1-3 / G4-P1-4). See [design-grill.md](../planning/design-grill.md) §6.

---

## 1. Entities

| Entity | Purpose | Key fields | Retention | Sensitivity |
|---|---|---|---|---|
| `Purchase` | Stored purchase transaction. System of record. | `id` (UUID v7), `description` (≤ 50 chars), `transaction_date` (DATE, UTC, no time/zone), `amount_usd` (DECIMAL(19,2)) | Indefinite v1; soft-delete + retention policy is OQ-008 (Phase 5). | Out-of-CDE. The free-text `description` is the only attack surface for accidental cardholder data; PAN-pattern and track-data guards reject hits at the API boundary. |
| `ExchangeRate` | Cached rate record from the Treasury Fiscal Data API. **Versioned by `effective_date`** (A-018; closes G-P0-4 / R-027). Not the system of record for rates — Treasury is — but the durable copy that allows conversions to succeed when Treasury is unreachable. | `country_currency_desc` (VARCHAR(64)), `record_date` (DATE), `effective_date` (DATE), `exchange_rate` (DECIMAL(19,6)), `source` (VARCHAR(32)), `fetched_at` (TIMESTAMP) | Indefinite; periodic prune of pre-window rows is a P3 follow-up. | Public data; not sensitive. |

The service does **not** persist: converted amounts, conversion-result responses, user identities, authentication state, audit-log content (the audit-log destination is external). Converted amounts are computed at retrieval time only.

---

## 2. Schema (DDL — engine-agnostic SQL)

The DDL below is the **logical** schema. Flyway migration files translate to engine-specific syntax for H2 (file mode) and PostgreSQL. No H2-specific extensions, no PostgreSQL-specific types beyond what H2 also supports.

```sql
-- V1__init_purchase_transactions.sql
CREATE TABLE purchase_transactions (
    id              VARCHAR(36)    NOT NULL,                    -- UUID v7 canonical form
    description     VARCHAR(50)    NOT NULL,
    transaction_date DATE          NOT NULL,
    amount_usd      DECIMAL(19,2)  NOT NULL,
    created_at      TIMESTAMP(6)   NOT NULL,
    updated_at      TIMESTAMP(6)   NOT NULL,
    CONSTRAINT pk_purchase_transactions PRIMARY KEY (id),
    CONSTRAINT chk_amount_positive CHECK (amount_usd > 0),
    CONSTRAINT chk_description_length CHECK (CHAR_LENGTH(description) <= 50),
    CONSTRAINT chk_description_not_blank CHECK (CHAR_LENGTH(TRIM(description)) > 0)
);

-- V2__init_exchange_rates.sql  (Phase-4 refined: composite PK, no surrogate id, widened sanity bound)
CREATE TABLE exchange_rates (
    country_currency_desc    VARCHAR(64)    NOT NULL,
    record_date              DATE           NOT NULL,
    effective_date           DATE           NOT NULL,
    exchange_rate            DECIMAL(19,6)  NOT NULL,
    source                   VARCHAR(32)    NOT NULL,           -- e.g., 'TREASURY-V1'
    fetched_at               TIMESTAMP(6)   NOT NULL,
    CONSTRAINT pk_exchange_rates PRIMARY KEY (country_currency_desc, record_date, effective_date),
    CONSTRAINT chk_rate_positive CHECK (exchange_rate > 0),
    CONSTRAINT chk_rate_sanity   CHECK (exchange_rate <= 1e30),  -- raised from 1e9 to admit hyperinflation currencies
    CONSTRAINT chk_effective_not_null CHECK (effective_date IS NOT NULL)
);

CREATE INDEX ix_exchange_rates_lookup
    ON exchange_rates (country_currency_desc, record_date DESC, effective_date DESC);
```

**Phase-4 grill refinement (2026-05-17 — G4-P1-2 / G4-P1-3 / G4-P1-4).** The original DDL carried a `VARCHAR(36) id` surrogate PK and a strict `effective_date >= record_date` CHECK. The Phase-4 grill found: (a) the surrogate is redundant with the natural unique key; (b) the strict CHECK is empirically unverified — all 24 Treasury records observed in the prototype had equality, and Treasury could in principle publish a correction with `effective_date < record_date`; (c) the 10⁹ sanity ceiling rejects hyperinflation currencies (Zimbabwean dollar peak ~10²⁵). The DDL above is the refined version.

Field rationale and consequences are in §3–§7 below.

---

## 3. `purchase_transactions`

| Column | Type | Constraint | Rationale |
|---|---|---|---|
| `id` | `VARCHAR(36)` | PK, not null | UUID v7 canonical-form text. PK is the value the API exposes, opaque to clients, k-sortable by creation time. We do **not** use a separate database-generated integer key; the API id and the row identity are the same value. |
| `description` | `VARCHAR(50)` | NOT NULL; CHECK length ≤ 50; CHECK trim > 0 | Source rule: ≤ 50 characters (UTF-16 code units per A-013; OQ-005 NICE). Application enforces the same length pre-DB so the response error is `400 VALIDATION_ERROR{code=LENGTH}` rather than an integrity-violation SQLState. |
| `transaction_date` | `DATE` | NOT NULL | No time, no zone. Application enforces `transactionDate ≤ today (UTC)` per A-002; future-dated rejected at FR-001 with `422 FUTURE_DATE`. |
| `amount_usd` | `DECIMAL(19,2)` | NOT NULL; CHECK > 0 | BigDecimal at the application level. `DECIMAL(19,2)` admits amounts up to 99,999,999,999,999,999.99 — practically unbounded. Inbound scale > 2 is rejected by the API (AC-008) so the DB CHECK is belt-and-suspenders. |
| `created_at` | `TIMESTAMP(6)` | NOT NULL | UTC. Microsecond precision keeps log/event correlation crisp. Not a business field. |
| `updated_at` | `TIMESTAMP(6)` | NOT NULL | UTC. v1 has no update operation, so `updated_at == created_at`. Reserved for future use. |

Notes:
- **No surrogate integer key.** The UUID *is* the PK. This sacrifices a few bytes per index entry in exchange for a one-to-one mapping between the API id and the row identity.
- **No `version` / optimistic-locking column.** v1 has no update operation. When updates land (e.g., for soft-delete) a `version BIGINT` column will be added.
- **`description` is logged as length + HMAC digest only** (NFR-017; AC-032). The DB stores the verbatim string; it does not leave the trust boundary except in the API response to the caller.

---

## 4. `exchange_rates` (versioned)

| Column | Type | Constraint | Rationale |
|---|---|---|---|
| `id` | `VARCHAR(36)` | PK, not null | UUID v7. Internal surrogate; not exposed by the API. |
| `country_currency_desc` | `VARCHAR(64)` | NOT NULL; part of unique key | Treasury's canonical descriptor, e.g., `Canada-Dollar`, `Euro Zone-Euro`, `Japan-Yen`. **Note the space** in the Eurozone descriptor (Phase-3 prototype collateral finding P-1). 64 chars is a generous bound; the longest descriptor observed in Treasury data is ~32 chars. |
| `record_date` | `DATE` | NOT NULL; part of unique key | Authoritative date for the 6-month window logic (A-011). All Treasury records observed have quarter-end record_dates. |
| `effective_date` | `DATE` | NOT NULL; part of unique key | Treasury's "rate effective from" date. In the 24 records sampled by the Phase-3 prototype, every `effective_date == record_date`; the column is **defensive** against the rare Treasury republish scenario. (A-018; closes G-P0-4.) |
| `exchange_rate` | `DECIMAL(19,6)` | NOT NULL; CHECK > 0; CHECK ≤ 1e30 | Treasury publishes variable decimal scale (`"1.37"`, `"1.393"`, `"159.41"`, `"148.0"`). `DECIMAL(19,6)` captures 6 fractional digits. **Persistence and API response both normalise to scale 6** (Phase-4 grill G4-P0-3) — e.g., Treasury's `"148.0"` is stored *and* returned as `"148.000000"`. Earlier "full Treasury precision" wording is retired. Sanity ceiling raised to 10³⁰ to admit hyperinflation currencies (G4-P1-3). |
| `source` | `VARCHAR(32)` | NOT NULL | Provenance marker. v1 uses `'TREASURY-V1'` exclusively. Reserved for future multi-source disambiguation (e.g., manually entered correction). |
| `fetched_at` | `TIMESTAMP(6)` | NOT NULL | UTC. The time *we* fetched the row from Treasury, not Treasury's publish time. Used to compute cache age, drift detection, and TTL invalidation. |

### 4.1 Unique key and revision policy

`UNIQUE (country_currency_desc, record_date, effective_date)`.

This is the load-bearing change vs. Phase-2's earlier `UNIQUE (country_currency_desc, record_date)`:

- **Identical row revisit** (Treasury returns the same `exchange_rate` for `(currency, record_date, effective_date)` we already have) → INSERT raises duplicate-key → the adapter swallows it as a no-op (idempotent fetch).
- **New revision** (Treasury returns the same `(currency, record_date)` with a later `effective_date` and possibly a different `exchange_rate`) → INSERT succeeds with the new triple → eligible-rate query selects max(`effective_date`) → next conversion uses the new value.
- **Previously-served conversions are not retroactively re-computed.** The original row stays; the new row coexists. The application returns whichever version was the maximum `effective_date` at the time the query ran.

Phase-3 prototype empirical note: in 24 records over 2 years, no revisions were observed. The versioning is *defensive*, not load-bearing on observed traffic. Cost is one column + one row per revision; near-zero at observed rates.

### 4.2 Lookup index

```sql
CREATE INDEX ix_exchange_rates_lookup
    ON exchange_rates (country_currency_desc, record_date DESC, effective_date DESC);
```

Supports the eligible-rate query directly:

```sql
SELECT *
  FROM exchange_rates
 WHERE country_currency_desc = ?
   AND record_date BETWEEN ? AND ?     -- (txDate - 6mo EOM-clamped, txDate)
 ORDER BY record_date DESC, effective_date DESC
 LIMIT 1;
```

This is a covering index for the lookup; the first row returned is the answer (max `record_date`, then max `effective_date` as tie-break per OQ-002 with A-018).

---

## 5. Schema constraints in plain English

- **`description` is bounded UTF-16 code units ≤ 50.** Java's `String.length()` is the contract; grapheme-cluster semantics are a NICE Phase-3 follow-up (OQ-005).
- **`amount_usd` is strictly positive, scale exactly 2.** Inbound scale > 2 is rejected with `400 SCALE_EXCEEDED` (AC-008). Never silently rounded.
- **`transaction_date` carries no time, no zone.** UTC date semantics (A-014).
- **`exchange_rate` is strictly positive and ≤ 1e9.** Treasury-side sanity bounds (A-019; AC-024b). Outside-range records are rejected at the boundary as `502 UPSTREAM_BAD_RESPONSE` and never persisted.
- **`effective_date >= record_date`.** Treasury's semantic. A row failing this check is a Treasury data bug; the schema check prevents it from being persisted.
- **No FK between `purchase_transactions` and `exchange_rates`.** They have independent lifecycles; conversion is a query-time join in application code.

---

## 6. Migrations

Flyway. One migration per logical change. Numbering starts at `V1__`. Production-bound migrations are reviewed for safety (NOT NULL adds with default, online index creation in Postgres, etc.); the case study has no concurrent traffic to migrate around.

Initial migrations:

```
src/main/resources/db/migration/
├── V1__init_purchase_transactions.sql
└── V2__init_exchange_rates.sql
```

Profile-specific migrations live under sub-paths if needed:
- `db/migration/h2/V…` for H2-only DDL (none anticipated).
- `db/migration/postgres/V…` for PostgreSQL-only DDL (e.g., `BRIN` indexes, partitioning).

Both H2 and PostgreSQL execute the engine-agnostic `db/migration/` set; profile sub-paths layer on top.

Test migrations (synthetic seed data for integration tests) live under `src/test/resources/db/test-fixtures/` and are loaded by `@Sql` annotations or test-only Flyway placements — never auto-applied to the main schema.

---

## 7. Data lifecycle

| Stage | Description | Mechanism |
|---|---|---|
| **Create** | `POST /api/v1/purchases` → `INSERT INTO purchase_transactions` within a transaction; commit before `201` returns. | Spring `@Transactional`; default REQUIRED propagation; READ_COMMITTED isolation. |
| **Read** | `GET /api/v1/purchases/{id}` → `SELECT … WHERE id = ?`. | Read-only transaction. |
| **Convert** | `GET /…/conversion?currency=…` → read purchase, read eligible rate (possibly via Treasury upsert), compute, respond. Conversion result is **not** persisted. | Read-only transaction for the lookup; write transaction only if Treasury fetch upserts new rates. |
| **Upsert rate** | TreasuryClientAdapter → `INSERT … ON CONFLICT (currency, record_date, effective_date) DO NOTHING`. Identical revisions are silently dropped; new revisions land as new rows. | Postgres `ON CONFLICT DO NOTHING`; H2 `MERGE INTO …` or `INSERT … WHERE NOT EXISTS`. |
| **Update** | Not supported v1. | n/a |
| **Soft delete** | Not supported v1; OQ-008 Phase 5. | n/a |
| **Hard delete** | DB admin operation, out-of-band, for non-production. Runbook covers `wex-data-reset.sql`. | Manual. |
| **Backup** | Local: copy the H2 file with the service stopped. Production: Postgres PITR + scheduled snapshots managed by platform. | Out-of-process. |
| **Restore** | Replace file (H2) / point-in-time restore (Postgres). Validate row count and a checksum query post-restore. | Out-of-process. |

---

## 8. JPA mapping notes

- Entities live in `infrastructure.persistence.jpa` and are translated to and from domain objects in `PurchaseRepositoryAdapter` / `ExchangeRateRepositoryAdapter`. **Domain objects do not carry JPA annotations.**
- `id` is bound as `String` (UUID v7 canonical form), generated by the application, not by `@GeneratedValue`.
- `BigDecimal` fields are mapped with explicit `@Column(precision = 19, scale = 2)` (or `scale = 6` for `exchange_rate`); no implicit precision/scale inheritance.
- `@Version` columns are absent v1. They will be added when an update operation is introduced.
- Lazy-loading is irrelevant — there are no relations. Both tables are queried independently.

---

## 9. Backup, restore, and data retention

- **Local (case study).** Backup = copy `${WEX_DATA_DIR}/wex.mv.db` with the service stopped. Restore = replace the file. No formal schedule; the data is synthetic.
- **Production reference.** Postgres managed by the platform with PITR (point-in-time recovery) and ≥ 7-day retention. Backup integrity is validated quarterly via restore-to-staging. RPO ≤ 5 min, RTO ≤ 30 min for a single-region restart (NFR-007).
- **Audit-log retention** is *not* in this schema — audit events flow to an external sink with ≥ 1-year retention and 3-month online (NFR-016b). The application emits the events; the platform owns the retention.

---

## 10. Sensitivity classification (PCI alignment)

| Data | Classification | Storage policy |
|---|---|---|
| `description` | "free-text untrusted." Subject to PAN-pattern and track-data guards at the API boundary. | Persisted verbatim *only after* the boundary guards have passed (AC-010b, AC-010c). Logged only as length + HMAC digest. |
| `transaction_date`, `amount_usd` | Non-sensitive monetary metadata. | Persisted verbatim. Logged in plain text. |
| `country_currency_desc`, `record_date`, `effective_date`, `exchange_rate`, `source`, `fetched_at` | Public data sourced from a U.S. government open dataset. | Persisted verbatim. Logged in plain text. |
| `id` (purchase) | Opaque server-generated UUID v7. Not enumerable. | Persisted as the PK. Logged in plain text. |
| Audit events | Operational telemetry. Some carry redacted hints (rejected payload reason); no plain `description`. | Emitted to external sink. |
| Secrets (HMAC key, future DB creds) | Sensitive. | Never persisted in this schema; loaded from env at startup. |

No PAN, no SAD, no CVV/CVC, no track data, no PIN, no payment-network tokens. The schema **cannot** store any of those by design — there is no column shaped for them — and the guards at the boundary reject attempts to smuggle them into `description`.

---

## 11. Capacity estimates (informative; refined in Phase 5)

| Estimate | Value | Source |
|---|---|---|
| `purchase_transactions` row size (Postgres) | ~120 bytes incl. overhead | from column types |
| `exchange_rates` row size | ~110 bytes incl. overhead | from column types |
| Anticipated rows in `exchange_rates` for v1 | ~200 currencies × ~8 quarter-ends × ~1 effective_date version = ~1 600 rows | empirical from prototype |
| Anticipated row growth in `purchase_transactions` | per case-study acceptance: dozens. Production reference: assume 10⁷ over the design horizon (NFR-010). | NFR-010 |
| Disk for 10⁷ purchases at 120 B | ~1.2 GB + indexes | sizing |

H2 file mode is comfortable up to several GB; PostgreSQL handles 10⁷ rows with the lookup index in milliseconds. No partitioning v1.
