# Requirements Audit — what was asked vs what was built

**Date:** 2026-05-18 — `main` HEAD `d9cc80f`.
**Method:** I re-read the source requirements verbatim (`docs/requirements/source-requirements.md`), the user's project overlay (`AGENT_PROJECT_INSTRUCTIONS.md`), and the project posture instructions (CLAUDE.md / project-instructions), and compared each clause against what's actually on `main`. This audit is **honest, not promotional** — it flags everything we added beyond the ask, every assumption we took, and every ambiguity we resolved one way without explicit ratification.

---

## 1. The "source" has three layers

It matters which document mandated each requirement because the assessor's bar differs:

| Layer | Document | What it is | Override priority |
|---|---|---|---|
| **L1 — Verbatim** | `docs/requirements/source-requirements.md` (extract of `WEX-Requirement.docx`) | The literal WEX ask. ~50 lines of text. | Authoritative source. |
| **L2 — User overlay** | `AGENT_PROJECT_INSTRUCTIONS.md` | The user's project-specific direction layered on top of the WEX text. Locks Java 21 / Spring Boot 3 / dual-mode currency / RFC-9457-style errors / etc. | Takes precedence on naming, API shape, rounding mode, error codes per its own §0. |
| **L3 — Project posture** | `CLAUDE.md` + project-instructions stream (case-study terminal, PCI Tier 1, enterprise SDLC) | The governance framework + the user-stated intent ("build best-in-class system; PCI Tier 1 posture; production-deployable case study"). | Drives the Phase-13 chunked-PR + Phase-10/11/12 evidence gates + dossier discipline. |

Most of what we delivered is **L2-mandated** (the overlay is detailed). Some is L1-strictly-required. A meaningful slice is **team-added engineering** under the implicit L3 PCI Tier 1 framing — flagged below.

---

## 2. L1 — Verbatim source coverage (every clause)

The WEX ask has 14 explicit clauses. Coverage on `main`:

| Source clause | Delivered | Where | Verdict |
|---|---|---|---|
| Req #1 — store purchase with description / transaction date / USD amount | `POST /api/v1/purchases` | `PurchaseController.create()` | ✅ |
| Req #1 — `description` ≤ 50 chars | Bean Validation `@Size(max=50)` | `PurchaseRequest.java` | ✅ |
| Req #1 — `transactionDate` valid date format | ISO-8601 binding (Spring date converter) | `PurchaseRequest.java` | ✅ |
| Req #1 — purchase amount valid positive, rounded to nearest cent | strictly `> 0`; scale exactly 2 (no implicit rounding) | `Money.of()` + `PurchaseRequest.java` validation pattern | ✅ — **but see §6 #1: interpretive choice** |
| Req #1 — unique identifier | UUID v7 server-generated | `PurchaseId.java` + `RegisterPurchaseUseCase` | ✅ |
| Req #2 — retrieve converted to Treasury-supported currency | `GET /api/v1/purchases/{id}/conversion?currency={CCY}` | `PurchaseController.convert()` | ✅ |
| Req #2 — based on rate active for the date of the purchase | 6-month rule (latest `effective_date` in window) | `RateSelectionPolicy.java` + `ConversionService.java` | ✅ |
| Req #2 — response includes id / description / date / USD amount / exchange rate used / converted amount | All 6 fields + 2 enhancements (see §5 #1) | `ConversionResponse.java` | ✅ |
| Req #2 — rate ≤ purchase date, within 6 months | `[purchaseDate.minusMonths(6), purchaseDate]` inclusive both ends (calendar-month + EOM clamp) | `RateSelectionPolicy.java` | ✅ |
| Req #2 — error if no rate in window | `422 CONVERSION_RATE_NOT_AVAILABLE` (RFC 9457) | `ConversionRateNotAvailableException` + `ProblemDetailExceptionHandler.onRateNotAvailable` | ✅ |
| Req #2 — converted amount rounded to 2 decimals | `HALF_UP` scale-2 (L2 prescribes mode) | `Money.multiply()` + `ConversionService` | ✅ |
| Technical — Java language | Java 21 | `pom.xml` `<java.version>21</java.version>` | ✅ |
| Technical — own design for frameworks | Spring Boot 3.3.5 (L2 prescribes stack) | `pom.xml` | ✅ |
| Technical — "build as if for Production" + all functional automated testing | 37 test classes / 5,316 test LOC; ArchUnit + JaCoCo + Pitest | `src/test/java/`, `pom.xml` quality gates | ✅ |
| Technical — non-functional testing (performance) not needed | Explicitly out of scope; documented as Phase-12-equivalent | `capacity-scalability-plan.md §5` | ✅ |
| Technical — repository fully functional **without separate DB / web server / servlet container** | H2 file mode by default + embedded Tomcat (Spring Boot) + embedded Liquibase. Confirmed: `application.yml` default `WEX_DB_URL=jdbc:h2:file:...` | `src/main/resources/application.yml` | ✅ |

**Verbatim-source coverage: 14/14 ✓.** No clause from the WEX document is unaddressed on `main`.

---

## 3. L2 — User overlay coverage (`AGENT_PROJECT_INSTRUCTIONS.md`)

Overlay clauses mostly mirror or extend L1. New items added by the overlay:

| Overlay clause | Delivered | Verdict |
|---|---|---|
| Java 21 + Spring Boot 3.x | ✓ `pom.xml` 3.3.5 | ✅ |
| H2 default + Postgres production profile | ✓ application.yml env-driven | ✅ |
| Liquibase migrations | ✓ `src/main/resources/db/changelog/` + `LiquibaseMigrationIT` | ✅ |
| Resilience4j for timeout/retry/CB | ✓ programmatic decorators in `TreasuryClientAdapter` | ✅ |
| Micrometer + Spring Boot Actuator | ✓ `MetricsCatalog` + `/actuator/*` | ✅ |
| JUnit 5, AssertJ, Mockito | ✓ `pom.xml` | ✅ |
| WireMock for Treasury API tests | ✓ `EndToEndTreasuryIT`, `RateRevisionEndToEndIT`, etc. | ✅ |
| OpenAPI documentation | ✓ springdoc 2.6 + `infra/openapi/baseline.yaml` | ✅ |
| Modular monolith (not microservices) | ✓ single `wex-purchase-fx` module | ✅ |
| Required internal packages: `api`/`application`/`domain`/`infrastructure`/`config`/`observability`/`exception` | ✓ exact match | ✅ |
| `BigDecimal` only; never `double`/`float` for money | ✓ ArchUnit guard | ✅ |
| `Idempotency-Key` (P1, BLOCKING-for-prod for real deploy) | ⚠ Documented; not implemented for the case study | See §6 #2 |
| `currencyIdentifier` accepts either Treasury descriptor OR ISO 4217 code | ✓ alias table at `src/main/resources/currency-aliases.json` | ✅ |
| `RoundingMode.HALF_UP` (project default) | ✓ ADR-0001 + `Money.multiply()` | ✅ |
| Versioned REST endpoints `/api/v1/...` | ✓ | ✅ |
| Required endpoints `POST /api/v1/purchases` + `GET /api/v1/purchases/{id}/conversion?currency=...` | ✓ + 1 extra (see §5 #2) | ✅ |
| Planning sequence (Requirements → Grill → Design → Grill → Ops → Reliability → Security → PCI Grill → Readiness → Human approval → Implementation) | ✓ all 10 phases executed | ✅ |
| Human-approval gate before implementation | ✓ `.human-approvals/implementation-approved.txt` referenced; phase-13 chunks gated | ✅ |

**Overlay coverage: 17/17 ✓** with one deferred item (Idempotency-Key, intentional per Phase 10/11 BLOCKING-for-prod handling — see §6 #2).

---

## 4. L3 — Project posture coverage (PCI Tier 1, enterprise SDLC)

Driven by `CLAUDE.md` + the user's "build best-in-class" direction:

- PCI Tier 1 posture established → out-of-CDE scope analysis, content-guard, HMAC-redacted logging, refuse-to-start invariant on missing HMAC key, defense-in-depth on exception messages.
- Phase-13 chunked-PR delivery with per-PR reviewer `30-review.md`.
- Phase-10/11/12 evidence gates with bulk-pass directive + HITL-gate consolidation directive + case-study scope clarification directive.
- Adversarial Phase-2 / Phase-4 / Phase-6 / Phase-8 grills (requirements / design / reliability / PCI security).
- 17,261 LOC of documentation across 142 markdown files (architecture / operations / security / requirements / planning / release / external-review).

This layer is **the largest scope expansion**. Strictly, the WEX document doesn't say "PCI Tier 1" — only "as if for Production." The user's overlay implies a PCI posture but is not explicit either. **Most of the dossier work (`docs/external-review/*`, `docs/security/*` 30 files, `docs/operations/*` 14 files, `docs/planning/*` 11 files) is L3-driven.**

This is acknowledged transparently in the case-study scope clarification directive: the project demonstrates production-deployability; documented procedure / template / design is the gate-passing evidence; execution work is Phase-12-equivalent.

---

## 5. Scope extensions beyond what was asked (engineering additions)

These are things the team added that no layer explicitly required. Each is defensible engineering, but assessors should know they are **team-added** rather than user-asked:

| # | Addition | Rationale | Where | Acceptable? |
|---|---|---|---|---|
| 1 | `ConversionResponse` includes `exchangeRateDate` + `targetCurrency` (the source asks for 6 fields; we return 8) | Lets clients unambiguously interpret which Treasury rate was applied + echoes the resolved canonical descriptor. | `ConversionResponse.java`, API contracts §1 | Yes — purely additive, no behavioural risk. |
| 2 | Extra endpoint `GET /api/v1/purchases/{id}` (raw retrieve without conversion) | "FR-002 — Implicit, required to enable FR-003" per functional-requirements.md. Source asks for "retrieve converted"; we added raw retrieve as a useful REST primitive. | `PurchaseController.get()` | Yes — minor scope creep, but conventional. |
| 3 | **`SingleFlightGate`** — at-most-one-Treasury-fetch-per-key with bounded loser wait, DB-poll losers, mirror-winner-exception, graceful-shutdown release | Anticipated Treasury rate-limiting + concurrent burst load. Phase-3 design decision (ADR-0001 D-9). Not source-mandated. | `infrastructure/treasury/SingleFlightGate.java` | Yes — defensible production concern; ratified by Phase-4 + Phase-6 grills. |
| 4 | **Hot cache (Caffeine, 24h TTL, per-(currency, recordDate))** with versioned-upsert invalidation contract | Microsecond hit path; 24h TTL because Treasury rates publish quarterly. Not source-mandated. | `infrastructure/treasury/ExchangeRateHotCacheAdapter.java` | Yes — defensible performance optimization. |
| 5 | **Rate-orientation contract canary** | Defense against Treasury silently flipping convention (`country_per_USD` ↔ `USD_per_country`). A 1/x error would be invisible to happy-path tests. Phase-2 grill G-P0-1 (BLOCKING-for-Phase-3-exit). | ADR-0001 D-9; `OrientationContractFixtureTest` | Yes — high-leverage defensive control. |
| 6 | **PAN content guard at API boundary** (Luhn + track-data + NFKC encoded-PAN) | Out-of-CDE evidence; defense-in-depth. Not source-mandated. Driven by L3 PCI posture. | `api/advice/ContentGuard.java` | Yes — required by L3 PCI scope. |
| 7 | **HMAC-redacted logging** (`DescriptionHasher`) with refuse-to-start invariant on missing key in prod profile | Defense-in-depth on PII emission. Not source-mandated. Driven by L3 PCI posture. | `observability/DescriptionHasher.java` | Yes — required by L3 PCI scope. |
| 8 | **Treasury filter whitelist** (regex on filter string) | Defense against filter-syntax injection (comma / colon would split filter clauses on Treasury side). B2 30-review §3 carry-forward. | `TreasuryClientAdapter.DESCRIPTOR_WHITELIST` | Yes — defensive coding. |
| 9 | **Defense-in-depth on `MalformedIdentifierException.getMessage()`** | C2 30-review §F5 / Phase 11 §C2 closure. Source-message strips raw input; carries only `length=N`. | `MalformedIdentifierException.buildMessage()` | Yes — closes a latent leak path. |
| 10 | **`/actuator/health/readiness`** with DB-pool saturation gate | Drain trigger for graceful rollout. Not source-mandated; SRE concern. | `HealthController.java` + `application.yml::wex.readiness.*` | Yes — operational concern. |
| 11 | **Versioned persistence of Treasury rates** (composite PK `(currency, record_date, effective_date)`) | Treasury revisions land as new rows; query-time `max(effective_date)` wins. Phase-2 grill G-P0-4. Not source-mandated. | `db/changelog/V001__initial.sql` + `ExchangeRateRepoAdapter` | Yes — defensive against Treasury data revisions. |
| 12 | **3 Grafana SLO dashboard JSONs** | Operations evidence. Not source-mandated. Driven by L3 SRE posture. | `infra/dashboards/*.json` | Yes — operational concern. |
| 13 | **Phase-13 chunked-PR + per-PR reviewer `30-review.md` audit trail** | SDLC discipline. Driven by user's overlay + L3 PCI posture. | `docs/external-review/chunks/13-*/30-review.md` | Yes — the audit trail itself is part of the deliverable. |
| 14 | **Phase-10/11/12 evidence gates + 4 cross-cutting directives** | Bulk-pass evidence-gate protocol invented mid-project. Driven by user mid-project direction. | `docs/external-review/phases/*` + `directives/*` | Yes — the process is part of the case-study deliverable. |

None of these is unjustified. But the assessor should know that the **delivered surface is meaningfully larger than the WEX-document literal ask**, driven by L2 overlay + L3 PCI Tier 1 posture.

---

## 6. Interpretive choices — places we resolved ambiguity one way

### 6.1 "Purchase amount: must be a valid positive amount rounded to the nearest cent" — strict reject vs round inbound

The phrase is ambiguous:
- (a) **Reject scale > 2** — the client must submit at cent precision.
- (b) **Round inbound** — the service rounds any precision down to cents.

We chose **(a) strict reject** (AC-008): a `POST` with `amountUsd="10.005"` returns `400 VALIDATION_FAILED`. Rationale: rounding inbound silently is a bug-source (consider `999.99499...` payloads); explicit precision keeps amounts auditable. Defensible interpretation, but **not the only valid reading**.

**Risk:** if the WEX assessor expects (b), our API would surface as overly strict. Mitigation: AC-008 is documented; behaviour is testable.

### 6.2 "Idempotency-Key" — overlay says optional/P1; we deferred to Phase-12-signatory

L2 overlay (`AGENT_PROJECT_INSTRUCTIONS.md`) says: *"Optional `Idempotency-Key` header. Replay with same key + same payload returns the original 201. Same key + different payload returns 409 IDEMPOTENCY_CONFLICT. See OQ-009."*

We **did not implement**. We documented the gap as `F-15 / OQ-009` (BLOCKING-for-prod for any real deploy) and deferred to the Phase 12 signatory chain. Architect (you) signed §6.1 in `DEMONSTRATIVE-CEREMONY.md`.

**Risk:** the L2 overlay calls this P1 ("strongly implied production hygiene"). We treated it as P1-deferred. A strict reading would say it should be implemented. The case-study scope clarification directive §4 ratified the deferral.

**If the assessor expects a delivered Idempotency-Key:** this is the most likely strict-source gap. ~600 LOC remediation path documented in `failure-modes-and-resilience.md §F-15`.

### 6.3 "Currencies supported by the Treasury Reporting Rates of Exchange API" — Treasury descriptor vs ISO 4217

Source says "supported by the API". Treasury's API publishes records with `country_currency_desc` (e.g., `"Canada-Dollar"`). Most clients think in ISO 4217 codes (`"CAD"`).

We chose **dual-mode input**: accept either descriptor or ISO code; resolve via curated alias table (`currency-aliases.json`); store the resolved canonical descriptor in `ConversionResponse.targetCurrency`. L2 overlay mandates this; L1 source is ambiguous.

**Risk:** alias-table staleness (R-014). Treasury rarely renames descriptors; we mitigate with a startup readiness check that refuses UP on missing/malformed alias table.

### 6.4 "Within the last 6 months" — calendar months vs day count

Source says "within the last 6 months". Could mean:
- (a) **Calendar months** with month-arithmetic (so a 31-day month and a 28-day month both count as 1 month).
- (b) **183 days** (half of 365).
- (c) **180 days** (6 × 30).

We chose **(a) calendar-month subtraction with end-of-month clamp**, inclusive both ends. Phase-2 grill ratified (OQ-003 closed). Window length varies 178–187 days depending on starting calendar position. AC-018b / AC-019b lock the EOM-clamp boundary.

**Risk:** a strict reading might prefer (b) for determinism. We chose (a) because "months" is the source word; (a) matches accounting convention. Defensible, but assessor should know the alternatives were considered.

### 6.5 "Transaction date: must be a valid date format" — future-date acceptable?

Source doesn't forbid future-dated purchases. We chose to **reject `transactionDate > today` with `422 FUTURE_DATE`** (A-002). Phase-2 grill ratified (OQ-001 closed).

**Rationale:** future-dated purchases contradict the term "purchase transaction" and cannot have an eligible rate at creation time. **Defensible interpretation, but technically beyond source.**

### 6.6 Error envelope — RFC 9457 Problem Details

Source silent on error format. L2 overlay says `errorCode` field. We chose **RFC 9457 `application/problem+json`** with `errorCode` as an extension member. Modern standard.

### 6.7 Identifier format — UUID v7 specifically

Source says "unique identifier"; L2 overlay says "globally unique". We chose **UUID v7** (time-ordered, opaque, globally unique) over UUID v4 / ULID. Documented in ADR-0001. Time-ordering improves index locality; opacity prevents enumeration. ULID is the documented alternative.

### 6.8 Authentication — none in v1

Source silent. We chose **no application-layer auth** for the case study (A-007); service deployed behind a trusted gateway in real environments. OQ-010 escalated to **BLOCKING-for-prod** (Req 8 identity origin). Architect signed §6.2 in DEMONSTRATIVE-CEREMONY.md.

**Risk:** strict reading might expect at least Bearer-token auth. Our position: PCI Req 8 is gateway-bound; v1 service is out-of-CDE; identity is established externally. If assessor expects auth-in-service, this is a delivered gap.

### 6.9 Description character semantics — UTF-16 code units

Source: "must not exceed 50 characters". We chose **UTF-16 code units** (`String.length()`). Alternatives: code points, grapheme clusters. OQ-005 still **NICE** (not formally resolved). Edge case: a 50-code-unit input that contains 25 surrogate pairs is 50 by our measure but 25 visible characters.

---

## 7. Things that look like potential gaps (but aren't)

1. **No multi-region DR** — OQ-012 documented; "single region v1". Source silent.
2. **No retention/soft-delete policy** — OQ-008 documented; "indefinite retention v1". Source silent.
3. **No live SLO measurement** — capacity plan is theoretical; load tests are Phase-12-equivalent per L1 ("performance testing not needed").
4. **No live audit-log destination** — Phase-11-deferred; sink choice is platform-team responsibility per `logging-monitoring-pci.md`.
5. **No live security-workflow gating (HIGH/CRITICAL block merge)** — Phase-11-equivalent; scanners installed and emitting SARIF.

All five are **correctly out of scope** under either L1 ("performance testing not needed") or L3 (case-study scope directive §2.2: documented procedure / template / design is the gate-passing evidence).

---

## 8. What I'd surface to a strict assessor

If the WEX assessor reads only the L1 verbatim source and grades against it strictly, these are the items most likely to draw a question:

| # | Item | Status | Strict-reading risk | Defensible reading |
|---|---|---|---|---|
| 1 | Idempotency-Key not implemented | Documented as BLOCKING-for-prod; deferred | **HIGHEST RISK** — L2 overlay names it as a feature; we deferred | Phase 12 signatory adjudication documented; F-15 implementation pattern documented; ~600 LOC remediation path; the deferral is a documented judgment call |
| 2 | Strict-reject vs round-inbound for amount precision | Strict reject (AC-008) | Ambiguous source phrasing | AC-008 tested; behaviour is auditable; rounding inbound is the bug-prone alternative |
| 3 | Future-dated transaction rejection | `422 FUTURE_DATE` (A-002) | Adds restriction not in source | Phase-2-grill-ratified; semantically defensible |
| 4 | Calendar-month vs day-count for "6 months" | Calendar-month + EOM clamp | Window length varies 178–187 days | Phase-2-grill-ratified; source uses "months" |
| 5 | UTF-16 code units for "50 characters" | `String.length()` | Surrogate-pair edge case | OQ-005 NICE; common Java idiom |
| 6 | Extra `GET /api/v1/purchases/{id}` raw retrieve | Implemented (FR-002) | Beyond source's "retrieve converted" | Useful REST primitive; no behavioural risk |
| 7 | PAN content guard + HMAC redaction + refuse-to-start | All implemented | Source doesn't require PCI-specific defenses | L3 PCI Tier 1 posture mandates these; documented and grilled |
| 8 | Req 8 identity origin not addressed | BLOCKING-for-prod; gateway-bound | Source silent | A-007 documented; Phase 12 signatory adjudication recorded |

**The single largest risk:** **Idempotency-Key was named in L2 and we deferred it.** If the assessor reads L2 carefully and expects a delivered feature, this is a gap. Our defense: the case-study scope clarification directive §4 explicitly ratifies the deferral. But strict reading could surface this.

---

## 9. Recommendation

**Read this audit in tandem with `AUDIT-BRIEF.md`.** The brief shows what was built; this audit shows what was asked and what we extended or deferred. Together they let the assessor calibrate scope expectations against the source.

If the assessor wants the most-likely-disputed item (Idempotency-Key) closed, it is a ~600 LOC follow-up chunk (`chunks/13-POST-idempotency-key/`) that the dev agent can deliver. The pattern is documented in `failure-modes-and-resilience.md §F-15`; the case-study scope directive §4 ratifies its deferral but does not preclude implementation.

**Net assessment:** the team delivered a substantive case-study dossier that addresses every L1 clause, every L2 clause except Idempotency-Key (intentional deferral), and meaningfully extended the surface under L3 PCI Tier 1 posture. Scope creep is honest, documented, and grilled. No L1 clause is silently violated. The single L2 clause we deferred is openly flagged at the Phase 12 demonstrative ceremony with signatory adjudication recorded.

End of requirements audit.
