# Phase-3 Prototype Log

> **Status:** Running — Phase 3 has not formally started (gate not yet opened by human "proceed to Phase 3").
> **Cadence note:** the only Phase-3 work performed before architecture-doc drafting is the empirical verification that closes G-P0-1 / OQ-017 / R-021. After this finding lands, Phase 3 PAUSES for human sign-off per the agreed prototype-first sequencing.

---

## Run 1 — Treasury rate orientation (closes G-P0-1)

**Date:** 2026-05-17
**Method:** Direct GET against the Treasury Fiscal Data API, no API key required, anonymous.
**Endpoint queried:**

```
GET https://api.fiscaldata.treasury.gov/services/api/fiscal_service/v1/accounting/od/rates_of_exchange
    ?fields=record_date,country_currency_desc,exchange_rate,effective_date
    &filter=country_currency_desc:in:(Canada-Dollar,Euro%20Zone-Euro,Japan-Yen)
    &sort=-record_date
    &page[size]=24
```

**Currencies of known canonical cross-rate (selected for unambiguous orientation test):**

- **Canada-Dollar (CAD).** Known canonical range 2024-2026: 1 USD ≈ 1.35–1.45 CAD; 1 CAD ≈ 0.69–0.74 USD.
- **Euro Zone-Euro (EUR).** Known canonical range 2024-2026: 1 USD ≈ 0.85–0.96 EUR; 1 EUR ≈ 1.04–1.17 USD.
- **Japan-Yen (JPY).** Known canonical range 2024-2026: 1 USD ≈ 142–161 JPY; 1 JPY ≈ 0.0062–0.0070 USD.

**Records returned (24 rows, 3 currencies × 8 quarter-ends):**

| country_currency_desc | record_date | effective_date | exchange_rate |
|---|---|---|---|
| Canada-Dollar | 2026-03-31 | 2026-03-31 | 1.393 |
| Euro Zone-Euro | 2026-03-31 | 2026-03-31 | 0.87 |
| Japan-Yen | 2026-03-31 | 2026-03-31 | 159.41 |
| Canada-Dollar | 2025-12-31 | 2025-12-31 | 1.369 |
| Euro Zone-Euro | 2025-12-31 | 2025-12-31 | 0.851 |
| Japan-Yen | 2025-12-31 | 2025-12-31 | 156.61 |
| Canada-Dollar | 2025-09-30 | 2025-09-30 | 1.392 |
| Euro Zone-Euro | 2025-09-30 | 2025-09-30 | 0.852 |
| Japan-Yen | 2025-09-30 | 2025-09-30 | 148.0 |
| Canada-Dollar | 2025-06-30 | 2025-06-30 | 1.367 |
| Euro Zone-Euro | 2025-06-30 | 2025-06-30 | 0.853 |
| Japan-Yen | 2025-06-30 | 2025-06-30 | 144.3 |
| Canada-Dollar | 2025-03-31 | 2025-03-31 | 1.435 |
| Euro Zone-Euro | 2025-03-31 | 2025-03-31 | 0.924 |
| Japan-Yen | 2025-03-31 | 2025-03-31 | 149.36 |
| Canada-Dollar | 2024-12-31 | 2024-12-31 | 1.438 |
| Euro Zone-Euro | 2024-12-31 | 2024-12-31 | 0.961 |
| Japan-Yen | 2024-12-31 | 2024-12-31 | 156.85 |
| Canada-Dollar | 2024-09-30 | 2024-09-30 | 1.352 |
| Euro Zone-Euro | 2024-09-30 | 2024-09-30 | 0.893 |
| Japan-Yen | 2024-09-30 | 2024-09-30 | 142.47 |
| Canada-Dollar | 2024-06-30 | 2024-06-30 | 1.37 |
| Euro Zone-Euro | 2024-06-30 | 2024-06-30 | 0.935 |
| Japan-Yen | 2024-06-30 | 2024-06-30 | 160.63 |

**Orientation analysis — does `1 USD × exchange_rate = foreign_amount`, or does `1 USD / exchange_rate = foreign_amount`?**

- **CAD test.** Treasury 2024-06-30 = `1.37`. Canonical 1 USD ≈ 1.37 CAD that quarter. Multiplicative: 1 × 1.37 = 1.37 CAD ✓. Divisive: 1 / 1.37 ≈ 0.73 CAD ✗.
- **EUR test.** Treasury 2026-03-31 = `0.87`. Canonical 1 USD ≈ 0.87 EUR that quarter. Multiplicative: 1 × 0.87 = 0.87 EUR ✓. Divisive: 1 / 0.87 ≈ 1.15 EUR ✗.
- **JPY test.** Treasury 2024-06-30 = `160.63`. Canonical 1 USD ≈ 160 JPY that quarter. Multiplicative: 1 × 160.63 = 160.63 JPY ✓. Divisive: 1 / 160.63 ≈ 0.0062 JPY ✗.

All three currencies, all eight quarters: the multiplicative reading matches reality; the divisive reading does not.

## Verdict (G-P0-1 / OQ-017 / R-021)

**Treasury publishes `exchange_rate` as "units of foreign currency per ONE U.S. dollar."** The formula in `AGENT_PROJECT_INSTRUCTIONS.md` §6 and FR-003 — `convertedAmount = amountUsd × exchangeRate` — is **CORRECT**.

The G-P0-1 silent 1/x risk is **not realized** with the multiplicative formula. The case-study example (`123.45 × 1.37 = 169.13`) is consistent with Treasury's empirically observed convention.

**OQ-017 status:** **CLOSED — orientation verified empirically.** ADR-0001 records this finding and pins the formula. Phase 3 may exit on this dimension.

**R-021 status:** **Mitigated.** The risk drops from L=2/I=5/Score=10 to a residual L=1/I=5/Score=5 (Low) — residual covers the rare-but-possible case that a *future* currency added to the dataset uses inverted convention. Mitigation: contract test asserts the multiplicative formula against a fixed reference set on each release; daily/weekly canary against three live currencies; alert on disagreement.

## Collateral findings (not anticipated by the grill)

| ID | Finding | Action |
|---|---|---|
| **P-1** | `country_currency_desc` for the Eurozone is `Euro Zone-Euro` — note the **space** between "Euro" and "Zone." The hyphen-only assumption (which the grill implicitly carried for examples like `Canada-Dollar`) is **wrong** for at least one major currency. URL-encoding (`%20`) is required for the query filter. | Confirms OQ-016 (alias-table maintenance) is real. Phase-3 alias-table design must (a) include `EUR → Euro Zone-Euro`, (b) preserve the embedded space, (c) document the curated-vs-derived character of these identifiers. The PAN-pattern guard regex must not over-fit on the hyphen pattern. |
| **P-2** | Across 8 consecutive quarter-end records per currency (24 rows total), every record has `effective_date == record_date`. **No revisions were observed** in this two-year window. | Does not invalidate G-P0-4 / A-018 (versioned persistence) — revisions are documented as rare-but-possible by Treasury process and the absence of revisions in 24 records is consistent with that. Phase-3 design retains versioned persistence as defensive correctness. **Follow-up:** query a longer historical window (e.g., 10 years) during Phase 3 to find the empirical revision rate. |
| **P-3** | Treasury returns `exchange_rate` as a **string** in JSON with variable decimal scale (`"1.37"`, `"1.393"`, `"159.41"`, `"148.0"`). | Confirms `BigDecimal`-from-string parsing path (A-015 / NFR-030). Domain parser must accept any valid decimal string and preserve full Treasury precision. Schema validator must not enforce a fixed scale on `exchange_rate`. AC-T-3 fixture set must include trailing-zero (`148.0`), 2-decimal (`0.87`), 3-decimal (`1.393`), 4-decimal, and zero-decimal cases. |
| **P-4** | All `record_date` values are quarter-end (03-31, 06-30, 09-30, 12-31). | Confirms quarterly publication cadence (A-009, NFR-018-adjacent). 24-hour rate cache TTL (A-012) is *very* conservative against a quarterly publish; a 7-day TTL is the right production default per grill §3 G-P2-3. ADR-0001 records the trade-off. |
| **P-5** | The Treasury **dataset documentation page** (`fiscaldata.treasury.gov/datasets/...`) returned **HTTP 403** to the WebFetch tool. The HTML docs are likely Cloudflare-protected against bot user agents. **The data API itself is anonymous-accessible.** | Adjust documentation references in Phase 3: link the *API endpoint* (open) rather than the *documentation page* (gated) for any operational evidence. Document the field semantics in our own ADR-0001 rather than relying on a stable upstream URL. |

## Implications for Phase-3 architecture

1. **ADR-0001** records: orientation verified multiplicative; quarterly cadence; variable decimal scale; alias-table embedded-space caveat; no revisions observed in 24 records; cache TTL trade-off (24 h conservative vs 7 d efficient).
2. **`docs/architecture/data-model.md`** records: `exchange_rate` storage type = `DECIMAL(19,6)` or unbounded-scale `NUMERIC` (TBD in Phase 3 design); `effective_date` column kept regardless of empirical no-revisions observation.
3. **`docs/architecture/api-contracts.md`** records: response `exchangeRate` is a decimal string at full Treasury precision; example values cover variable scale.
4. **`docs/architecture/component-design.md`** records: Treasury client schema validator accepts `exchange_rate` as a decimal-string with any non-negative scale; rate sanity guard (`> 0`, `≤ 10^9`) is the only post-parse range check; orientation contract test runs against a recorded fixture set spanning the three reference currencies.

## Pre-Phase-3 sequencing (unchanged)

1. ✅ **Treasury rate-orientation prototype** — closes G-P0-1 / OQ-017 / R-021. *(This run.)*
2. ⏸ **PAUSE for human sign-off** before Phase-3 architecture work begins.
3. Pending: ADR-0001 (full draft), system-context, component-design, data-model, api-contracts, deployment-architecture, design-session document.
4. Pending: bundle/overlay diff applied by human owner (the planning-phase-guard hook restricts Claude-Code writes outside `docs/{requirements,planning,architecture,security,operations,release}/`).
