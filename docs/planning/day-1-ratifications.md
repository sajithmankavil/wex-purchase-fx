# Day-1 Ratifications — End of Phase 2

> **Date:** 2026-05-17
> **Status:** Closed. These decisions are the entry conditions for Phase 3.
> **Cadence note:** these ratifications are recorded here (under `docs/planning/`) instead of `RESUME_PROMPT.md` because the planning-phase-guard hook restricts writes to `docs/{requirements,planning,architecture,security,operations,release}/`. The human owner may copy the relevant lines into `RESUME_PROMPT.md` so they survive a fresh-session resume.

---

## 1. P0 findings — ratified

All five P0 findings from [docs/planning/requirements-grill.md](requirements-grill.md) §1 are **ratified** as resolved in the manner the grill recommended.

| ID | Finding | Resolution | Phase that closes it |
|---|---|---|---|
| G-P0-1 | Treasury rate orientation unverified | Empirical verification against ≥ 3 currencies of known canonical cross-rate before ADR-0001 signs. Result recorded in ADR-0001 §Rate-orientation finding. | Phase 3 (prototype-first) |
| G-P0-2 | 6-month window asymmetric under EOM clamp | A-003 ratified (calendar months + EOM clamp + inclusive both ends). AC-018b / AC-019b lock the EOM-clamp boundary. | Closed at Phase 2 (this ratification) |
| G-P0-3 | `CONVERSION_RATE_NOT_AVAILABLE` vs `UPSTREAM_UNAVAILABLE` boundary | Decision table in FR-003. AC-020b / AC-021b / AC-022b lock intermediate cases. | Closed at Phase 2 (this ratification); Phase 3 records the table in `docs/architecture/api-contracts.md`. |
| G-P0-4 | Same-`record_date` Treasury revision policy missing | A-018 ratified: versioned persistence keyed by `(country_currency_desc, record_date, effective_date)`; query selects max `effective_date`; previously-served responses are not retroactively mutated. AC-026b. | Closed at Phase 2 (this ratification); Phase 3 records schema in `docs/architecture/data-model.md`. |
| G-P0-5 | PCI scope load-bearing on a single regex | A-017 ratified: primary PCI control = API-contract prohibition on payment data. PAN-pattern guard (A-016) + track-data shape guard (AC-010c) + monitoring + IR are defense-in-depth. Phase-7 PCI design package evidences this stack. | Phase 7 |

## 2. Severity escalations — ratified

| OQ | Old severity | New severity | Effect |
|---|---|---|---|
| OQ-017 (Treasury rate orientation) | IMPORTANT (Phase 3 design) | **BLOCKING-for-Phase-3-exit** | Phase 3 cannot exit until ADR-0001 records empirically-verified orientation. |
| OQ-009 (Idempotency-Key) | IMPORTANT (Phase 3 design) | **IMPORTANT for v1; BLOCKING-for-prod** | Optional / P1 for case-study acceptance; required for any non-case-study production deployment. |

## 3. Stack ratification (D-6 locked)

The proposed stack is **ratified now**. ADR-0001 records it as a decision, not a proposal — Phase 3 does not re-litigate.

```
Language:     Java 21
Framework:    Spring Boot 3.x (Web, Validation, Data-JPA, Actuator)
Build:        Maven (./mvnw)
Persistence:  H2 file-mode (local) / PostgreSQL (prod profile); Flyway migrations
Resilience:   Resilience4j (timeout, retry, circuit breaker, bulkhead)
API docs:     springdoc-openapi (OpenAPI 3.1)
Telemetry:    Micrometer + OpenTelemetry (W3C traceparent)
Logging:      SLF4J / Logback (JSON, structured)
Testing:      JUnit 5, AssertJ, Mockito, WireMock, Pitest (≥ 70 % mut), ArchUnit, jqwik
Container:    Eclipse Temurin JRE 21 (Distroless option recorded in ADR-0001 trade-off)
```

ADR-0001 still records: HALF_UP-vs-HALF_EVEN trade-off, EOM-clamp asymmetric-window note, versioned-rate persistence, identifier choice (UUID v7 working; ULID alternative), and the Treasury rate-orientation finding from the Phase-3 prototype.

## 4. Bundle / overlay edits (seven items) — drafted; awaiting human application

The seven edits to `AGENT_PROJECT_INSTRUCTIONS.md` and `CLAUDE.md` proposed in [requirements-grill.md](requirements-grill.md) §10 were **reviewed and approved in concept**. Drafting them as a unified diff for human application is captured in the session transcript.

The `.claude/hooks/planning_phase_guard.py` hook restricts Claude-Code writes to `docs/{requirements,planning,architecture,security,operations,release}/` until the implementation-approval marker exists. This is *more restrictive* than `AGENT_PROJECT_INSTRUCTIONS.md` §3 (which lists `CLAUDE.md` and `AGENT_PROJECT_INSTRUCTIONS.md` as agent-editable before approval). Reconciliation:

- **Recommended path (lowest friction):** the human owner applies the diff manually. Bundle/overlay edits are policy; the human owns them anyway.
- **Alternative:** add `CLAUDE.md` and `AGENT_PROJECT_INSTRUCTIONS.md` (and `RESUME_PROMPT.md`, `README.md`) to the hook's `ALLOWED_BEFORE_APPROVAL` tuple. A one-line edit in `.claude/hooks/planning_phase_guard.py`.
- **Defer:** leave the new policy lines in `docs/requirements/*` and `docs/planning/requirements-grill.md` only; the bundle stays unchanged.

Until the human decides, **the policy carried by Phase-2's in-place doc edits is authoritative**; the bundle text simply does not yet reflect it.

## 5. Phase-3 sequencing

Decision: **prototype-first.**

1. Treasury rate-orientation prototype (closes G-P0-1 / OQ-017 / R-021). Live API hit against ≥ 3 currencies of known canonical cross-rate. Recorded in `docs/planning/phase-3-prototype-log.md`.
2. **PAUSE** for human sign-off on the prototype finding.
3. `docs/architecture/adr-0001-core-architecture.md` — full ADR-0001 capturing the ratified stack, the prototype finding, HALF_UP trade-off, EOM-clamp note, versioned persistence, identifier choice.
4. `docs/architecture/system-context.md` → `component-design.md` → `data-model.md` → `api-contracts.md` → `deployment-architecture.md`.
5. Phase-3 design session document at `docs/planning/design-session.md`.
6. Hand off to Phase 4 (Design Grill).

## 6. Pointers

- Phase-2 grill: [docs/planning/requirements-grill.md](requirements-grill.md)
- Phase-1 + Phase-2 requirements artefacts: [docs/requirements/](../requirements/)
- Bundle/overlay diff: in session transcript; awaiting human application.
- ADR-0001 scaffold: [docs/architecture/adr-0001-core-architecture.md](../architecture/adr-0001-core-architecture.md) — currently TBD; populated in Phase 3 step 3.

---

**Closeout:** Phase 2 ends here. Phase 3 begins with the Treasury prototype; everything downstream waits for the human "proceed to Phase 3" signal *and* the prototype finding.

---

## Day-2 ratifications (2026-05-17 — end of Phase 4 hardening pass)

Five working assumptions surfaced during the Phase-4 hardening review (F1–F5) are **ratified as KEEP**. Source-language deviations and project-level decisions noted explicitly.

| F | Assumption | Position | Source / project deviation |
|---|---|---|---|
| **F1** | Reject inbound `amountUsd` with scale > 2 (`400 SCALE_EXCEEDED`); do **not** silently round. | **KEEP** — pinned in [acceptance-criteria.md](../requirements/acceptance-criteria.md) AC-008. | Source says "rounded to the nearest cent" — could be read as "the service rounds." We chose strict rejection to make the wire-level contract explicit; clients who want soft-rounding must do it client-side. Phase-9 readiness gate inherits this. |
| **F2** | Reject future-dated `transactionDate` with `422 FUTURE_DATE`. | **KEEP** — A-002; AC-006. | Source silent. A "purchase transaction" with a future date contradicts the term and cannot have an eligible rate at creation time. |
| **F3** | "50 characters" interpreted as UTF-16 code units (Java `String.length()`). | **KEEP** — A-013; OQ-005 remains NICE. | Source ambiguous (could be code points or grapheme clusters). Phase-9 inherits; OQ-005 may be re-opened if a client surfaces a real grapheme-cluster boundary issue. |
| **F4** | `exchangeRate` surfaced at scale 6 in the API response (Treasury's variable scale normalised on persistence and response). | **KEEP** — Phase-4 G4-P0-3; api-contracts.md §1 / §5. | Source silent on rate precision. The earlier "full Treasury precision" wording was retired; scale 6 makes contract tests stable. |
| **F5** | `409 IDEMPOTENCY_CONFLICT` (not `422`) for same `Idempotency-Key` with a different payload. | **KEEP for v1; revisit at Phase 13.** | Industry: IETF draft uses `422`; some implementations use `409`. We picked `409` v1; OQ-009 is BLOCKING-for-prod and the Phase-13 implementer revisits with the latest IETF draft state. |

**Closeout:** these ratifications are the human owner's working positions, acceptable for v1 case-study delivery. Each remains overridable at the Phase-9 implementation-readiness gate; if any is overridden, the corresponding AC and architecture text is the change vector.
