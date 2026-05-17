# Open Questions Register

> Focused register of open questions awaiting human/stakeholder input. Each item has a severity, a target resolution gate, and the decision needed. Assumptions in force (the answer "while we wait") are recorded in `assumptions-and-open-questions.md`.
>
> **Severity:**
> - **BLOCKING** — must be resolved before its target gate completes.
> - **IMPORTANT** — should be resolved before its target gate; assumption may stand if explicitly accepted.
> - **NICE** — useful clarification; not on critical path.

| ID | Severity | Question | Working assumption | Resolution gate | Decision needed from |
|---|---|---|---|---|---|
| OQ-001 | RESOLVED Phase 2 | Is a future-dated `transactionDate` valid? | A-002: reject with `422 FUTURE_DATE`. | **Closed in Phase 2 (Requirements Grill) 2026-05-14.** | Product owner — ratified |
| OQ-002 | RESOLVED Phase 4 | When multiple Treasury rate records share the same `record_date` for the same `country_currency_desc`, how do we tie-break at *query time*? | **Closed by Phase-4 refinement (G4-P1-4): the surrogate `id` was dropped; the composite PK `(country_currency_desc, record_date, effective_date)` is unique by construction; query-time order `(record_date desc, effective_date desc)` is fully deterministic. No fourth tie-break key is required.** | **Closed in Phase 4 (Design Grill) 2026-05-17.** | Architect — ratified |
| OQ-003 | RESOLVED Phase 2 | "Within the last 6 months" — calendar months (A-003) vs 183 days vs 6 × 30 = 180 days? Boundary tests change. | Calendar months with EOM clamp, inclusive both ends. AC-018b/AC-019b lock the EOM-clamp boundary. | **Closed in Phase 2 (Requirements Grill) 2026-05-14.** | Product owner — ratified |
| OQ-004 | IMPORTANT | Should the canonical supported-currency universe be the live Treasury catalog (changes over time) or a frozen subset documented in OpenAPI? | Live Treasury catalog; OpenAPI uses an example list, not an enum. | Phase 3 (Design Session) | Architect + API consumer team |
| OQ-005 | NICE | "50 characters" — UTF-16 code units (A-013), code points, or grapheme clusters? Affects edge cases at the boundary. | UTF-16 code units (`String.length()`). | Phase 3 (Design Session) | Architect |
| OQ-007 | NICE | Should the service expose a list/pagination endpoint over stored purchases? | Out of scope for v1. | Phase 9 (Implementation Readiness) | Product owner |
| OQ-008 | IMPORTANT | What is the retention policy for stored purchases? Is soft-delete required? Is anonymisation required? | Indefinite retention v1; hard-delete only via DB admin out-of-band. | Phase 5 (Operational Design Session) | Data-governance owner |
| OQ-009 | IMPORTANT (BLOCKING for prod) | Does `POST /api/v1/purchases` support `Idempotency-Key` as defined in draft-ietf-httpapi-idempotency-key-header? Phase-2 grill (G-P1-8) escalates: P1 for the case study, P0 for production. | Optional header, P1 for v1 acceptance. AC-001b documents the absence-of-key consequence. | Phase 3 (Design Session) | Architect |
| OQ-010 | BLOCKING for prod | For any non-case-study deployment: where does request identity originate (mTLS gateway, OIDC, JWT, SPIFFE)? Without this, app-layer authz cannot be designed. | A-007: no app-layer auth in v1; service behind a trusted gateway only. | Phase 7 (PCI/Security Design) | Platform security |
| OQ-011 | IMPORTANT | PAN-pattern content guard (A-016) policy — reject (current), mask, or warn-only? Allow-list for legitimate long-digit descriptions (e.g., 19-digit order numbers)? Phase-2 grill (G-P0-5) widens scope to track-data and the broader control stack. | Reject; no allow-list v1; false-positive feedback loop via metric `purchase.create.validation_error.count{reason=pan_pattern}`. Track-data rejection added via AC-010c. | Phase 7 (PCI/Security Design) | SecArch |
| OQ-012 | IMPORTANT | Single-region v1 or cross-region DR? Affects RPO/RTO, replication, DNS, runbook. | Single region v1. | Phase 5 (Operational Design Session) | SRE + product owner |
| OQ-013 | NICE | Any required compatibility with downstream WEX systems (CSV export, message-bus publish-on-create)? | None v1. | Phase 3 (Design Session) | Product owner |
| OQ-014 | IMPORTANT | Are the proposed SLO anchors (NFR-005 99.5 % availability monthly; NFR-006 99.0 % successful-conversion) correct for the product? Phase-2 grill (G-P1-2) adds: bounded by Treasury effective uptime; Phase 5 must measure and apply the cache-hit ceiling formula. | Anchored figures used in Phase 5 capacity plan. | Phase 5 (Operational Design Session) + Phase 6 (Reliability/Scalability Grill) | SRE + product owner |
| OQ-015 | NICE | Should the API surface `exchangeRate` as a rational (numerator/denominator) rather than decimal string? | Decimal string at full Treasury precision. | Phase 3 (Design Session) | Architect |
| OQ-016 | IMPORTANT | Who owns the ISO-4217 ⇄ Treasury `country_currency_desc` alias table? How is drift detected when Treasury renames a currency descriptor (rare but happens)? | Alias table is source-controlled with the app; a daily Treasury-catalog reconciliation job emits `currency_alias_drift_detected` events. Startup readiness check refuses UP if the alias table is missing or fails to parse (G-P2-9). | Phase 3 (Design Session) | Architect |
| OQ-017 | **BLOCKING for Phase-3 exit** | When Treasury returns rates with **inverted exchange convention** (rate as `target → USD` vs `USD → target` — Treasury dataset documents the convention per record), which orientation do we use? Always invert if needed? Verify against the actual dataset schema in Phase 3 prototype. Phase-2 grill (G-P0-1) escalates severity to BLOCKING-for-Phase-3-exit: a silent 1/x error is undetectable by happy-path unit tests. | Use the rate as published by Treasury and convert in the direction implied by the dataset's documented convention; verify direction empirically against ≥ 3 currencies of known canonical cross-rate. | Phase 3 (Design Session) | Architect (must verify empirically; record in ADR-0001) |
| OQ-018 | IMPORTANT | Decision table for `CONVERSION_RATE_NOT_AVAILABLE` vs `UPSTREAM_UNAVAILABLE` for the intermediate cases (Treasury reachable + empty result; Treasury 4xx for alias-known currency; all returned rates outside window). | Working answers locked in ACs 020b/021b/022b: terminal "rule applied, no eligible rate" returns 422; "could not apply the rule because data is unreachable and budgets are exhausted" returns 503. | Phase 3 (Design Session) — decision table in `api-contracts.md` | Architect |
| OQ-019 | IMPORTANT | Logging HMAC key origin, rotation policy, scope, and storage. Effects: secret management, log-correlation continuity across rotation. | A-020: `WEX_LOG_HASH_KEY` env var; mandatory in `prod`/`staging`; `vN:` version prefix on the digest. | Phase 7 (PCI/Security Design) — full policy in `security/secrets-policy.md` | SecArch |
| OQ-020 | IMPORTANT | CORS policy for browser-origin clients. | A-022: default no CORS; `WEX_CORS_ALLOWED_ORIGINS` env var enables strict allow-list when set. | Phase 3 (Design Session) | Architect |
| OQ-021 | IMPORTANT | Treasury same-`record_date` revision conflict resolution. | A-018: store as separate version row keyed by `(country_currency_desc, record_date, effective_date)`; query selects max(`effective_date`); previously-served responses are not retroactively mutated. AC-026b. | Phase 3 (Design Session) — schema in `architecture/data-model.md` | Architect |
| OQ-022 | IMPORTANT | Audit-log retention duration, online window, and integrity (append-only, signed, WORM). | Working answer: ≥ 1 year retention, 3 months online, append-only or signed in production. Final policy via Phase-7 design. | Phase 7 (PCI/Security Design) — policy in `security/logging-monitoring-pci.md` | SecArch |
| OQ-023 | NICE | H2 data directory when project root is on OneDrive/Dropbox/iCloud. | A-021: `WEX_DATA_DIR` env var, default off any known cloud-sync prefix; startup warns on detected sync prefix. | Phase 3 (Deployment Architecture) | Architect |

## OQ resolved during Phase 1

| ID | Question | Resolution |
|---|---|---|
| OQ-006 | Rounding mode for converted amount. | **HALF_UP** to scale 2 (A-004) per `AGENT_PROJECT_INSTRUCTIONS.md` §5. HALF_EVEN trade-off recorded in `assumptions-and-open-questions.md` and to be re-stated in ADR-0001. |

## OQ resolved during Phase 2 (Requirements Grill, 2026-05-14)

| ID | Question | Resolution |
|---|---|---|
| OQ-001 | Is a future-dated `transactionDate` valid? | **Reject with `422 FUTURE_DATE`** (A-002 ratified). See `docs/planning/requirements-grill.md` §5 row 2. |
| OQ-003 | "Within the last 6 months" — calendar months vs day count? | **Calendar months + EOM clamp + inclusive both ends** (A-003 ratified). Window length varies 178–187 days; AC-018b/AC-019b lock the EOM-clamp boundary. |

## OQ resolved during Phase 4 (Design Grill, 2026-05-17)

| ID | Question | Resolution |
|---|---|---|
| OQ-002 | Tie-breaker for multiple Treasury records sharing the same `record_date`. | **Closed by G4-P1-4** (surrogate `id` dropped). Composite PK `(country_currency_desc, record_date, effective_date)` is unique; query-time order `(record_date desc, effective_date desc)` is deterministic. |

## Cross-references

- Assumptions backing these OQs: `assumptions-and-open-questions.md` §Assumptions in force.
- Risks each OQ contributes to: `risk-register.md`.
- Gate exit criteria that require closure of these OQs: each gate document in `docs/planning/`.

