# Assumptions & Open Questions

> Reconciled with `AGENT_PROJECT_INSTRUCTIONS.md` on 2026-05-14. A-001/A-004/A-011 updated; OQ-002 partially closed; OQ-006 closed.
>
> **Phase-2 (Requirements Grill) update, 2026-05-14:** OQ-001 and OQ-003 ratified and closed (their working assumptions A-002 and A-003 stand). Six new assumptions A-017..A-022 added. Six new open questions OQ-018..OQ-023 added. See `docs/planning/requirements-grill.md`.
>
> **A-IDs** are working assumptions the team is proceeding on; each must be ratified or overturned in Phase 2 (requirements grill) or Phase 3 (design session). **OQ-IDs** are open questions awaiting input; severity is **BLOCKING / IMPORTANT / NICE**.
>
> See also: `open-questions.md` (focused OQ register) and `risk-register.md` (formal risk register).

---

## Assumptions in force

| ID | Assumption | Rationale | Confidence | Ratification owner |
|---|---|---|---|---|
| A-001 | Target currency input accepts **either** Treasury `country_currency_desc` (e.g., `Canada-Dollar`) **or** ISO 4217 code (e.g., `CAD`). The service maintains a curated alias table and resolves any input to exactly one canonical `country_currency_desc`. Ambiguous/unknown inputs return `400 INVALID_CURRENCY`. | Per `AGENT_PROJECT_INSTRUCTIONS.md` §6 and the project decision recorded in this conversation. Dual-mode keeps client ergonomics while preserving Treasury's canonical identifier. Alias-table staleness is a maintenance risk (R-014). | High | Architect (Phase 3) |
| A-002 | A `transactionDate` in the future is rejected at FR-001 with `422 FUTURE_DATE`. | Future-dated purchases contradict the term *purchase transaction* and cannot have an eligible rate at creation time. | **Ratified Phase 2 (2026-05-14)** — see grill §5, OQ-001 closed. | Product Analyst (closed) |
| A-003 | "Within the last 6 months" = `transactionDate.minusMonths(6) ≤ record_date ≤ transactionDate`, calendar-month subtraction with end-of-month clamp, inclusive both ends. | "Months" is the source word; matches accounting convention. 183-day alternative noted in OQ-003. Note: window length varies 178–187 days; AC-018b/AC-019b lock EOM-clamp behaviour. | **Ratified Phase 2 (2026-05-14)** — see grill §5, OQ-003 closed. | Architect + Product Analyst (closed) |
| A-004 | Monetary rounding for `convertedAmount` is **`HALF_UP`** to scale 2. | Per `AGENT_PROJECT_INSTRUCTIONS.md` §5. Trade-off against HALF_EVEN (banker's rounding): HALF_UP is the lay/regulatory default and matches most invoicing systems; HALF_EVEN avoids systemic positive bias on ties. Documented in ADR-0001. | High | Architect (ADR-0001) |
| A-005 | Unique identifier format = **UUID v7** (time-ordered, opaque, globally unique). | Satisfies "unique identifier"; time-ordering improves index locality; opaque prevents enumeration. ULID is the documented alternative. | High | Architect (ADR-0002 candidate) |
| A-006 | Service exposes a **REST/JSON** HTTP API. | Conventional; testable; well-tooled in Java. Source silent on transport; guideline pins this. | High | Architect (ADR-0001) |
| A-007 | **No authentication for v1** at the application layer — service deployed behind a trusted gateway/mesh in real environments; for the case study, no auth. | Source silent. Adding incomplete auth would be worse than none. Documented as a deploy prerequisite (OQ-010). | Medium | SecArch (Phase 7) |
| A-008 | Embedded relational database: **H2 file mode** by default (durable across restart); JPA mappings DB-agnostic so Postgres production profile is a config switch only. | Satisfies source's "no separate databases" while preserving durability. | High | Architect (ADR-0001) |
| A-009 | Treasury Reporting Rates of Exchange is consumed via the public **fiscaldata.treasury.gov v1 API**, JSON, no API key. | Confirmed available with anonymous access. Rate-limited; mitigated by local persistence + cache + single-flight. | High | Architect (Phase 3) |
| A-010 | Treasury endpoint: `rates_of_exchange`; queried with server-side filter on `country_currency_desc` and `record_date ≤ transactionDate`, ordered by `record_date desc`, paginated minimally. | Reduces over-the-wire payload. | Medium | Architect (Phase 3) |
| A-011 | The **`record_date`** field of the Treasury record is the authoritative date for the 6-month window logic. | Per `AGENT_PROJECT_INSTRUCTIONS.md` §5 and §8. Closes half of OQ-002. | High | Architect (Phase 3) |
| A-012 | Per-`(country_currency_desc, record_date)` rate cache TTL = **24 hours**, single-flight de-duplicated. | Treasury rates publish quarterly; daily refresh more than enough. | Medium | SRE (Phase 5) |
| A-013 | `description` is **bounded UTF-8** input; length measured in `String.length()` (UTF-16 code units). Grapheme-cluster alternative noted (OQ-005). | Pragmatic default. Surrogate-pair edge case documented. | Medium | Architect (Phase 3) |
| A-014 | All times in API are **UTC**; `transactionDate` carries no time or zone. | Avoids zone-edge ambiguity at purchase boundaries. | High | Architect (Phase 3) |
| A-015 | Errors follow **RFC 9457 Problem Details** with extension members `errorCode` (machine-readable) and `details` (structured context). | Modern standard; backward-compatible with the guideline's `errorCode` field. | High | Architect (Phase 3) |
| A-016 | A **PAN-pattern content guard** runs at the API boundary on the `description` field, rejecting Luhn-valid 13–19-digit sequences with optional separators. Audit-logged; rejected payloads are not logged. | Defends out-of-CDE posture (NFR-015). Risks false positives on legitimate long digit strings (e.g., order numbers); tunable allow-list in security policy. | Medium | SecArch (Phase 7) |
| A-017 | **Primary PCI control is the API contract**: the service does not accept payment data; the PAN-pattern guard (A-016) and the track-data shape guard (AC-010c) are defense-in-depth signals, not the primary control. | Reframed by Phase-2 grill G-P0-5. A single regex is insufficient out-of-CDE evidence; the contract-level prohibition is. | High | SecArch (Phase 7) |
| A-018 | **Treasury rate persistence is versioned**: storage key is `(country_currency_desc, record_date, effective_date)`; query selects max(`effective_date`). Treasury revisions of an existing `record_date` are stored as new rows, not in-place mutations. | Phase-2 grill G-P0-4. Prevents retroactive financial drift; preserves audit trail. | High | Architect (Phase 3) |
| A-019 | **Treasury rate sanity bounds**: `exchange_rate > 0` and `exchange_rate ≤ 10^9`. Outside-range records are rejected as `502 UPSTREAM_BAD_RESPONSE` and not persisted. | Phase-2 grill G-P1-10. Protects against upstream bug or attacker-positioned proxy. | High | Architect (Phase 3) |
| A-020 | **Logging HMAC key origin**: `WEX_LOG_HASH_KEY` env var, mandatory in `prod`/`staging`, optional documented fallback in `local`/`test`. Digest output carries a `vN:` version prefix so pre/post-rotation digests are distinguishable. | Phase-2 grill G-P1-4. No secret in source; correlation survives rotation. | High | SecArch (Phase 7) |
| A-021 | **H2 data directory**: `WEX_DATA_DIR` env var, default `${user.home}/.wex-purchase-fx/data`. Startup warns if the resolved path is under a known cloud-sync prefix (`OneDrive`, `Dropbox`, `iCloud Drive`). | Phase-2 grill G-P1-1. Avoids file corruption on the reviewer's machine when the repo lives on a synced drive. | High | Architect (Phase 3) |
| A-022 | **CORS policy**: v1 default = no CORS. Env var `WEX_CORS_ALLOWED_ORIGINS`, when set to a comma-separated allow-list, enables a strict CORS policy for those origins only; wildcard not supported. | Phase-2 grill G-P1-5. Pinned default is "no CORS" (gateway responsibility); allow-list available for legitimate browser clients. | Medium | Architect (Phase 3) |

## Hard rules locked in

- Money: `BigDecimal` only; never `double`/`float`.
- No PAN/SAD anywhere — enforced by content guard (A-016, NFR-015).
- No secrets in source; no secrets in `.human-approvals/`.
- Implementation does not begin until the human approval marker exists.

---

## Open questions awaiting input

(Full register in `open-questions.md`. Quick reference here.)

| ID | Severity | Question | Resolution gate |
|---|---|---|---|
| OQ-001 | RESOLVED Phase 2 | Is a future-dated `transactionDate` valid? (A-002.) | Closed: **reject with `422 FUTURE_DATE`** (A-002). |
| OQ-002 | RESOLVED Phase 4 | Tie-breaker for multiple Treasury records with same `record_date` for same currency. | **Closed by Phase-4 G4-P1-4: surrogate `id` dropped; composite PK `(country_currency_desc, record_date, effective_date)` is unique; query-time order `(record_date desc, effective_date desc)` is deterministic.** |
| OQ-003 | RESOLVED Phase 2 | "Within the last 6 months" — calendar months (A-003) vs 183 days vs 180 days? | Closed: **calendar months + EOM clamp, inclusive both ends** (A-003). AC-018b / AC-019b lock the EOM-clamp boundary. |
| OQ-004 | IMPORTANT | Canonical supported currency universe — live Treasury catalog vs frozen subset for OpenAPI enum stability? | Phase 3 design |
| OQ-005 | NICE | "50 characters" — UTF-16 code units (A-013) vs code points vs grapheme clusters? | Phase 3 design |
| OQ-006 | RESOLVED | Rounding mode for converted amount. | Closed: **HALF_UP** (A-004). |
| OQ-007 | NICE | Should we expose a list/paginate endpoint? | Out of scope v1 |
| OQ-008 | IMPORTANT | Retention policy for stored purchases; soft-delete required? | Phase 5 operational design |
| OQ-009 | IMPORTANT | `Idempotency-Key` support on `POST /purchases`? Escalated to **BLOCKING-for-prod** by Phase-2 grill (G-P1-8). | Phase 3 design |
| OQ-010 | BLOCKING for prod | Where does identity originate for any non-case-study deployment? | Phase 7 PCI/security design |
| OQ-011 | IMPORTANT | PAN-pattern guard policy: reject (A-016), mask, or warn-only? Allow-list policy for legitimate digit strings? | Phase 7 PCI/security design |
| OQ-012 | IMPORTANT | Single-region v1 vs cross-region DR? | Phase 5 operational design |
| OQ-013 | NICE | Required compatibility with downstream WEX systems (CSV/event-bus)? | Phase 3 design |
| OQ-014 | IMPORTANT | SLO target ratification — are NFR-005/006 figures correct? G-P1-2 adds: bound by Treasury effective uptime — Phase 5 must record a Treasury-availability estimate. | Phase 5 + Phase 6 grills |
| OQ-015 | NICE | Surface rate as rational (numerator/denominator) rather than decimal? | Phase 3 design |
| OQ-016 | IMPORTANT | Alias-table maintenance process for ISO 4217 ⇄ Treasury `country_currency_desc`. Who owns it; how do we detect drift? | Phase 3 design |
| OQ-017 | BLOCKING for Phase-3 exit | Treasury rate orientation (USD→target vs target→USD). Empirical verification required. | Phase 3 design (was: IMPORTANT) |
| OQ-018 | IMPORTANT | Policy table for `CONVERSION_RATE_NOT_AVAILABLE` vs `UPSTREAM_UNAVAILABLE` for the intermediate cases (Treasury reachable + empty result; Treasury 4xx for known alias; all rates outside window). Working answers locked in ACs 020b/021b/022b. | Phase 3 design (decision table in `api-contracts.md`) |
| OQ-019 | IMPORTANT | Logging HMAC key origin/rotation/scope. Working answer in A-020. | Phase 7 PCI/security design |
| OQ-020 | IMPORTANT | CORS policy for browser-origin clients. Working answer in A-022. | Phase 3 design |
| OQ-021 | IMPORTANT | Treasury same-`record_date` revision conflict resolution. Working answer in A-018 (versioned persistence). | Phase 3 design |
| OQ-022 | IMPORTANT | Audit-log retention duration, online-window, and integrity (append-only / signed / WORM). | Phase 7 PCI/security design |
| OQ-023 | NICE | H2 data directory policy when project root is on a cloud-sync drive. Working answer in A-021. | Phase 3 (deployment architecture) |

