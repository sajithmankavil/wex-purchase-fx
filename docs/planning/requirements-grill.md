# Requirements Grill — Phase 2

> **Status:** COMPLETED — 2026-05-14
> **Verdict:** **CONDITIONALLY PROCEED** to Phase 3 (Architecture & Design Session).
> Conditions: the five P0 findings below must be pinned (decisions logged or empirical verification scheduled as Phase-3 sign-off gates). All P1 findings must be either resolved or explicitly accepted by the human owner before the Phase-3 design grill exits.
>
> **Adversarial panel:** Principal engineer, product analyst, security reviewer with PCI background, SRE who has been paged at 2 a.m. for currency-conversion regressions, QA lead, business stakeholder, auditor.
> **Inputs reviewed:**
> - `docs/requirements/source-requirements.md` (verbatim)
> - `docs/requirements/{functional,non-functional,acceptance-criteria,assumptions-and-open-questions,open-questions,risk-register,requirements-analysis,traceability-matrix}.md`
> - `CLAUDE.md`, `AGENT_PROJECT_INSTRUCTIONS.md`, `security-profile.yml`
> - Decision log D-1..D-7 in `RESUME_PROMPT.md`
>
> **Scope note.** This is Phase 2. The grill is allowed (and required) to attack proposed NFR anchors, but it does not have to ratify them; figures still tagged "(proposed)" remain valid inputs to Phase 5 capacity planning provided the grill records the question.

---

## 0. Executive verdict

The Phase-1 artifact set is internally consistent at the level the source can support. The structural ambiguities that remain — and there are several — are all *boundary* ambiguities (rate orientation, EOM-clamp on the 6-month window, Treasury same-day duplicates, the CONVERSION_RATE_NOT_AVAILABLE / UPSTREAM_UNAVAILABLE boundary) rather than scope ambiguities. None of them require source rewriting; all of them require pinned policy and pinned tests. That is what this grill delivers.

The five **P0** items below are correctness-class risks that can produce silent wrong answers (inverted rate, asymmetric window, wrong error code, lost rate revision). They are *blockers for Phase-3 sign-off*, not for Phase-3 start; the design session is the right venue to close them. The eleven **P1** items are production-readiness gaps that must be closed before the design grill exits. The remainder are P2 / NICE.

**Source-document edits in Phase 2:** acceptance criteria added (AC-018b, AC-020b, AC-021b, AC-022b, AC-024b, AC-026b, AC-027d, AC-032b, AC-T-5); assumptions extended (A-017..A-022); new open questions registered (OQ-018..OQ-023); new risks recorded (R-021..R-027). The verbatim `source-requirements.md` is untouched.

---

## 1. Adversarial findings — P0 (correctness or compliance blockers for Phase-3 sign-off)

| ID | Title | Observation | Evidence (existing IDs) | Risk | Recommended fix | Owner | Target gate |
|---|---|---|---|---|---|---|---|
| **G-P0-1** | Treasury rate orientation unverified | The Treasury Reporting Rates of Exchange dataset publishes `exchange_rate` as "the rate at which the U.S. Federal Government converts foreign currency into U.S. dollars" — but the per-record `effective_date`/`record_date` documentation, and the specific orientation (`USD per foreign-unit` vs `foreign-unit per USD`) on each currency record, must be verified empirically before the formula `convertedAmount = amountUsd × exchangeRate` is frozen. The worked example in `AGENT_PROJECT_INSTRUCTIONS.md` §6 (`123.45 × 1.3700 = 169.13`) implicitly commits to "foreign per USD" but is not citing a Treasury record. If a currency's published rate is inverted, every converted amount for that currency will be `1/x` of correct, silently. | OQ-017, A-009, A-010, A-011, R-017 (existing) | High — silent financial correctness bug; passes all happy-path unit tests with the wrong number. | Phase-3 prototype: hit live Treasury for at least three currencies of known reference value (CAD, EUR, JPY) on a known date, assert `amountUsd × exchangeRate ≈ canonical_cross_rate ± 0.5 %`. Pin orientation in ADR-0001. Add a contract test that fails if the dataset's `units_per_dollar`-style column flips convention. **Upgrade OQ-017 severity from IMPORTANT to BLOCKING-for-Phase-3-exit.** | Architect | Phase 3 (design session) |
| **G-P0-2** | 6-month window length is asymmetric under end-of-month clamp | `LocalDate.minusMonths(6)` applied to `transactionDate=2026-08-31` returns `2026-02-28` (Feb has 28 days in 2026). The eligible window is then `2026-02-28 ≤ record_date ≤ 2026-08-31`, which is **184 days**. Applied to `transactionDate=2026-05-15` it returns `2025-11-15`, which is **182 days**. So the eligible window length varies 178–187 days across the calendar. AC-018 locks the inclusive lower bound only for the symmetric case (`2026-04-15 → 2025-10-15`); it does not lock the EOM-clamp case. A reasonable reviewer could implement the rule differently — e.g., `transactionDate.minusDays(183)` — and pass AC-018 while failing the EOM case. | A-003, AC-018, AC-019, OQ-003, R-005 (existing) | Medium-High — reviewer disagreement risk; ambiguous rule = inconsistent results across re-implementations; reproducibility risk against the *source rule's* literal text. | Pin the rule explicitly in `acceptance-criteria.md` with **two new acceptance criteria**: (a) EOM-clamp lower bound — `transactionDate=2026-08-31`, lone rate `record_date=2026-02-28` → eligible; (b) one day outside EOM clamp — same date, lone rate `2026-02-27` → 422. Document the asymmetry explicitly in ADR-0001. **Ratify A-003 (calendar months + EOM clamp + inclusive both ends).** | Architect | Phase 3 (design session) |
| **G-P0-3** | `CONVERSION_RATE_NOT_AVAILABLE` vs `UPSTREAM_UNAVAILABLE` boundary is under-specified for "Treasury reachable but unhelpful" | AC-022 (Treasury unreachable + local rate exists → success) and AC-023 (unreachable + no local rate → 503) are clear. But three intermediate states are not explicitly tested: (i) Treasury reachable, Treasury returns 200 with empty list, no eligible local rate → expected `422 CONVERSION_RATE_NOT_AVAILABLE`; (ii) Treasury reachable but returns 4xx for the requested currency (not 5xx) → expected `400 INVALID_CURRENCY` if the rejection is "unknown currency," else `502 UPSTREAM_BAD_RESPONSE`; (iii) Treasury returns rates *all* outside the 6-month window → `422 CONVERSION_RATE_NOT_AVAILABLE`, never `503`. The narrative covers these correctly but the AC matrix does not lock them. A future implementer could conflate (i) with (iii) or worse, return `503` for (i). | FR-003, AC-022/023, requirements-analysis §11, R-001, R-002 (existing) | Medium — incorrect error code masks a real "rate truly doesn't exist" answer as an "upstream" problem, which changes client retry behaviour and SLO accounting. | Add acceptance criteria for (i), (ii), (iii). Add a decision table to `functional-requirements.md` FR-003. Make `CONVERSION_RATE_NOT_AVAILABLE` the *terminal* answer when the rule has been correctly applied with all data available; reserve `UPSTREAM_UNAVAILABLE` exclusively for "we could not apply the rule because the data isn't here yet and our budgets are exhausted." | Architect | Phase 3 (design session) |
| **G-P0-4** | Same-`record_date` Treasury conflict — persistence policy is undefined | `AGENT_PROJECT_INSTRUCTIONS.md` §7 declares a `UNIQUE(country_currency_desc, record_date)` constraint. Treasury can — rarely, but it happens — republish a record on the same `record_date` (correction, revision, reclassification). The current text leaves the conflict policy implicit: do we upsert (replace local) or preserve (keep first-write)? Each choice is defensible; the wrong choice creates either *silent rate change for an already-converted purchase* (upsert) or *stale rate served indefinitely* (preserve). OQ-002 names a sort tie-breaker `(record_date desc, id asc)`, but that is a query-time decision and does not answer the persistence-time decision. | A-011, OQ-002, R-012, R-002 (existing) | Medium-High — financial correctness drift; audit trail confusion; "why did my conversion change yesterday?" support burden. | Decide and document an **upsert-with-versioning** policy: persist new revisions as a separate row with `effective_date` or `version` column; query selects the *latest* `effective_date` for a given `(country_currency_desc, record_date)`. This satisfies UNIQUE-key concerns by changing the key to `(country_currency_desc, record_date, effective_date)`. Add AC and an integration test that asserts: "Treasury republishes a rate ≠ existing local value → next conversion uses the republished value; previously-served conversions are not retroactively mutated." | Architect | Phase 3 (design session) |
| **G-P0-5** | PCI out-of-CDE scope is asserted, not evidenced — the PAN-guard alone does not survive adversarial review | A-016 / NFR-015 / AC-010b establish a Luhn-based PAN-pattern guard on the `description` field. As written, the guard does not defend against: (a) PANs presented without any check digit (the Luhn check is a property of PANs, but an attacker submitting an attempted-PAN they made up may not be Luhn-valid and would slip through); (b) Track 2 / track 1 magnetic-stripe payloads (contain `=`, `^`, expiry digits); (c) CVV/CVC values (3–4 digits, indistinguishable from order suffixes); (d) sensitive authentication data more generally; (e) PAN encoded as base64 / URL-safe / homoglyph; (f) PAN split across the description and a future second free-text field. The PCI grill (Phase 8) is the place to close most of this, but **the scope claim is currently load-bearing on a single regex**, and the requirements docs imply the guard is the *primary* control. It is more honestly framed as a *signal*, with the *primary* control being: "the API contract does not accept payment data; clients who submit payment data are violating contract." | NFR-015, NFR-016, A-016, AC-010b, OQ-011, R-007 (existing) | High (compliance) — auditor will reject "we filter Luhn-valid digits" as sufficient out-of-CDE evidence; QSA will ask about the broader attack surface; case-study reviewer will probe it. | Re-frame the PCI control stack as: (1) contract-level prohibition (state in API contract and OpenAPI description that the service does not accept payment data); (2) defense-in-depth pattern guard (current Luhn rule, **plus** track-data regex, **plus** CVV-shape heuristic where it is unambiguous); (3) post-write monitoring (audit-event count, sample dashboards); (4) explicit incident response when a hit occurs. Expand `docs/security/pci-scope-and-cde.md` and the eventual `pci-security-grill.md` to attack each item explicitly. Add AC-010c covering track-data shape rejection. | SecArch | Phase 7 (PCI design) — but the *requirements-level framing* must change in Phase 2. |

---

## 2. Adversarial findings — P1 (production-readiness gaps; resolve or accept before design grill exits)

| ID | Title | Observation | Evidence | Risk | Recommended fix | Owner | Target gate |
|---|---|---|---|---|---|---|---|
| **G-P1-1** | H2 file-mode default on a OneDrive-synced working directory is structurally unsafe | The project root is `C:\Users\sajit\OneDrive\Documents\Claude\Projects\WEX Case Study\wex-purchase-fx\`. Default H2 file-mode paths land under that root. OneDrive (and Dropbox/iCloud) sync H2's `.mv.db` *while it is being written*, which can corrupt the file or produce sync-conflict copies. The case-study reviewer who clones into a similar path will hit this. | NFR-028, NFR-029, A-008, R-006, R-013 (existing) | Medium — silent corruption on the reviewer's machine; "doesn't work on Windows" reputation hit. | Make the H2 file path explicit and configurable via env var `WEX_DATA_DIR` with a default that targets `${user.home}/.wex-purchase-fx/data` (off any cloud-sync drive). Document in README and runbook. Add a startup check that warns if `WEX_DATA_DIR` resolves under a known cloud-sync prefix. | Architect | Phase 3 (deployment architecture) |
| **G-P1-2** | SLO anchors (NFR-005 99.5 %, NFR-006 99.0 %) are bounded by Treasury's effective uptime, which is unpublished | The service's availability for FR-003 (the only endpoint that depends on Treasury for cache-miss requests) cannot exceed Treasury's availability multiplied by our cache-hit ratio. If Treasury runs at, say, 99.0 % monthly, and our cache hit ratio is 95 %, our effective SLO ceiling is `0.95 + 0.05 × 0.99 = 99.95 %` — fine. But under cache cold start or thundering herd or alias drift, our reachable ceiling drops sharply. The SLO is plausible but unverified against the actual upstream. | NFR-005, NFR-006, OQ-014, R-009, R-011 (existing) | Medium — SLO that is contractually unachievable because of upstream. | Phase-5 capacity plan must include a Treasury-availability estimate (a 7-day passive observation; multi-burn-rate alert tuning has to assume this number). Phase-6 reliability grill ratifies or rejects 99.5 % / 99.0 %. If unverifiable, **drop NFR-005 from 99.5 % to 99.0 %** and document the upstream-ceiling formula in `operations/slo-sli.md`. | SRE | Phase 5 + 6 |
| **G-P1-3** | PAN-pattern false-positive feedback loop is not in the observability spec | OQ-011 names a metric `purchase.create.validation_error.count{reason=pan_pattern}`. NFR-018's metric list does not include it. AC-033 does not assert it. Without the metric being a *first-class* observability output, the team will not detect the false-positive rate, will not learn the legitimate-but-blocked patterns, and cannot make the "no allow-list v1" decision durable. | NFR-018, AC-033, OQ-011, R-007 (existing) | Medium — silent loss of legitimate user requests; PCI evidence weakens (the guard's calibration is invisible). | Add the metric to NFR-018; assert exposure in AC-033; document the dashboard tile in `operations/observability.md` and `operations/monitoring-alerting.md` with an alert when the daily false-positive rate exceeds an agreed threshold (initially: alert only on first hit, refine after empirical baseline). | SRE | Phase 5 (operational design) |
| **G-P1-4** | Logging HMAC key — origin, rotation, scope, storage — unspecified | NFR-017 and AC-032 require the `description` field to be logged only as length + HMAC-SHA-256 digest, "with a per-environment key." Where the key lives, how it rotates, who can read it, what happens on rotation (correlation across pre- and post-rotation logs breaks), is unspecified. A naïve implementation will put it in `application.yml`, where it will be committed. | NFR-017, AC-032 (existing) | Medium-High — key in source = compliance finding; key never rotated = compliance finding; key rotation breaks correlation queries with no documented mitigation. | Specify: (a) key origin = environment variable `WEX_LOG_HASH_KEY`, mandatory in `prod` profile, optional with a no-op fallback in `local`/`test`; (b) rotation policy documented in `docs/security/secrets-policy.md`; (c) digest output includes a key-version prefix (e.g., `v1:<hex>`) so old-and-new digests can be distinguished across rotations; (d) no key value ever in source. Add AC-032b. | SecArch | Phase 7 (security design) |
| **G-P1-5** | CORS / browser-origin client policy is missing | OpenAPI is served and Swagger UI is exposed. If a real client is browser-based (a single-page admin tool, for example), the API must declare an explicit CORS policy. Source is silent; NFRs do not name it. Default Spring Boot behaviour is "no CORS," which means Swagger UI testing from a separate origin works only by accident. | FR-006, NFR-034 (existing) | Low-Medium — usability and operability gap; one-line policy decision missing. | Decide and document the CORS policy: v1 default = "no CORS" (gateway responsibility), with a `WEX_CORS_ALLOWED_ORIGINS` env var that turns on a strict allow-list when set. Add AC for behaviour with and without the env var. New OQ-020. | Architect | Phase 3 (design session) |
| **G-P1-6** | Audit log retention and integrity not stated | PCI-aware posture (NFR-016) implies an audit log. Retention duration, integrity (append-only, signed), and the access-control around the audit log are not in NFRs. A PCI DSS 4.0.1 ROC will ask. | NFR-015, NFR-016, NFR-017 (existing) | Medium — audit finding; "no audit retention policy" is a routine ROC failure. | Add NFR-016b: audit log retention ≥ 1 year, with the most recent 3 months online and searchable; integrity via WORM destination or append-only signed sink in production; access logged and reviewed quarterly. New OQ-022. | SecArch | Phase 7 (security design) |
| **G-P1-7** | Metric cardinality controls absent | Labels like `currency`, `errorCode`, `endpoint`, `outcome` can combine to high cardinality (~200 currencies × 9 error codes × 3 endpoints × 5 outcomes = thousands of series). For a Micrometer/Prometheus backend, this is real cost and is a P1 ops concern at scale. | NFR-018, NFR-020 (existing) | Low-Medium — observability cost / scraping latency. | State cardinality budget in NFR-018 and operations docs: keep `currency` as a label only on a separately-named histogram (`exchange_rate.lookup.duration_by_currency`) with a configurable allow-list; default RED counters do not carry `currency`. New OQ to register. | SRE | Phase 5 |
| **G-P1-8** | Idempotency on POST is "optional P1," but the API contract does not state the consequence for clients who skip it | A double-click on a "Create Purchase" form will create two purchases with two ids. AC do not assert this. Without explicit statement, clients will reasonably assume idempotent retries; the failure mode is hidden until production. | FR-001, OQ-009, R-020 (existing) | Low-Medium — duplicate spend-side state; trivial to surface; expensive to retrofit after launch. | Add a sentence to FR-001 that explicitly states "without Idempotency-Key, two identical POSTs produce two purchases with distinct ids." Add AC-001b. Decide in Phase 3 whether `Idempotency-Key` is P0 or P1 *for v1 acceptance* — it is P1 for the case study, but for any production deployment it should be P0. | Architect | Phase 3 |
| **G-P1-9** | `application/problem+json` content type is implied but not asserted | NFR-035 and existing ACs use RFC 9457 Problem Details shape, but no AC asserts the `Content-Type: application/problem+json` response header. Reviewers and integration tests that assert headers will catch divergence at the wire level. | NFR-035, AC-T-2 (existing) | Low — wire-level interoperability and conformance. | Tighten AC-T-2 to also assert `Content-Type: application/problem+json` on every error response. | API Designer | Phase 3 |
| **G-P1-10** | Treasury rate sanity-validation (zero / negative / absurd) is not in ACs | A Treasury bug (or attacker-positioned proxy) could deliver `exchange_rate=0`, negative, or absurdly large. The schema validator should reject. Today, the boundary is not asserted. | NFR-023, R-002, AC-T-3 (existing) | Medium — `convertedAmount=0` slips through; auditor finds. | Add AC-024b asserting that `exchange_rate ≤ 0` or `exchange_rate > 10^9` (an arbitrarily generous sanity ceiling) is rejected as `502 UPSTREAM_BAD_RESPONSE`. | Architect | Phase 3 |
| **G-P1-11** | Concurrent same-id conversion request behaviour is only partially asserted | AC-027b (single-flight per cache key) is named, but the broader concurrency property — that two concurrent conversions for the *same purchase id* see the same effective rate — is not. If both requests hit while a Treasury fetch is mid-flight, do they both see the new rate, or does one see "stale none" and the other "newly fetched"? Either is defensible, but the answer must be pinned. | AC-027b, R-015 (existing) | Low-Medium — subtle race condition. | Add AC-027d: two concurrent `GET /purchases/{id}/conversion?currency=X` requests served while the single-flight Treasury fetch is in flight either both succeed with the *same* `exchangeRate` or both fail consistently — never split. | Architect | Phase 3 |

---

## 3. Adversarial findings — P2 / NICE (clarifications, polish)

| ID | Title | Observation | Recommendation |
|---|---|---|---|
| **G-P2-1** | Treasury client pagination params under-specified | A-010 says "paginated minimally" without enumerating page-size limits, sort key, or behaviour on `next` link. | Pin in Phase-3 component design: server-side filter on `country_currency_desc` and `record_date`, sort `record_date desc`, fixed page size (e.g., 100), follow `links.next` until either eligible rate found or window exhausted. |
| **G-P2-2** | Single-flight cache key conceptual ambiguity | AC-027b says "same `(country_currency_desc, transactionDate)`" but the underlying data is keyed by `record_date`. Two different `transactionDate` values may map to the same effective `record_date`. | Phase-3 design pins the cache key to `(country_currency_desc, lookup_window_lower, lookup_window_upper)` where the window is derived from the eligible-rate-selection rule; document in `architecture/component-design.md`. |
| **G-P2-3** | Cache TTL 24h is conservative — Treasury rates publish quarterly | A-012 (24h TTL) is safe but wasteful. | Document a 7-day TTL as the recommended production setting; 24h remains default for v1 to keep an aggressive freshness posture; ADR-0001 records the trade-off. |
| **G-P2-4** | Warm-up job spec absent | R-009 mitigation names a "warm-up job on startup" but does not enumerate which currencies / what dates. | Phase-5 capacity plan defines the warm-up set (top-N currencies by request volume; default seed = empty for the case study). |
| **G-P2-5** | Treasury User-Agent header best practice | Public Treasury Fiscal Data API recommends clients identify themselves. | Configure `User-Agent: wex-purchase-fx/<version> (contact:<email>)` on the Treasury client; expose via `WEX_TREASURY_USER_AGENT`. |
| **G-P2-6** | HALF_UP counter-argument not in ADR yet | A-004 names the counter-argument (HALF_EVEN avoids systemic positive bias on ties; some EU/UK consumer-finance jurisdictions require it). Locked at HALF_UP per `AGENT_PROJECT_INSTRUCTIONS.md`. | ADR-0001 (Phase 3) records the counter-argument explicitly. No requirement change. |
| **G-P2-7** | Grapheme-cluster length is unlikely to matter for "Office supplies" but matters for emoji-bearing descriptions | OQ-005 covers this. | Stays NICE. Phase-3 design records the chosen semantic. |
| **G-P2-8** | ETag / Last-Modified on conversion GET | The conversion response is a pure function of `(purchase, rate)`; supports HTTP caching | Phase-3 design considers `ETag` based on `(purchase.id, exchangeRateDate, rateValueHash)`; out of scope for v1. |
| **G-P2-9** | Cold-start preload of the ISO 4217 ⇄ Treasury alias table | A-001 / OQ-016 — the alias table itself must be loaded at startup. Behaviour if it fails to load (refuse to start? start in degraded mode where only `country_currency_desc` works?) is undefined. | Phase-3 component design specifies: alias table is a source-controlled JSON file; readiness check fails if it is missing or fails parsing. |
| **G-P2-10** | RoundingMode behaviour for negative amounts is moot but should be documented | Source forbids negative `amountUsd`; HALF_UP on positive amounts is unambiguous. | One-line note in ADR-0001 records the fact rather than the policy. |

---

## 4. Missing requirements identified

| ID | Missing requirement | Where it should land |
|---|---|---|
| **M-1** | Explicit framing of "the API contract does not accept payment data" as the *primary* PCI control. | `functional-requirements.md` FR-001 input table; `security/pci-scope-and-cde.md`. |
| **M-2** | Persistence-time conflict policy for Treasury rate revisions. | `functional-requirements.md` FR-003 rate-selection narrative; `architecture/data-model.md`. |
| **M-3** | Treasury rate sanity-validation rule (positive, bounded). | `functional-requirements.md` FR-003 narrative; AC-024b. |
| **M-4** | CORS policy. | NFR-014b; AC for default and overridden behaviour. |
| **M-5** | Audit-log retention and integrity. | NFR-016b; `security/logging-monitoring-pci.md`. |
| **M-6** | Logging HMAC key management. | NFR-017 expanded; `security/secrets-policy.md`. |
| **M-7** | H2 data-dir configurability + cloud-sync warning. | NFR-028 / NFR-029 augmented; runbook. |
| **M-8** | Metric cardinality budget. | NFR-018 / NFR-020 augmented. |
| **M-9** | Idempotency-absent semantics on POST. | FR-001; AC-001b. |
| **M-10** | `Content-Type: application/problem+json` assertion on error responses. | AC-T-2 tightened. |
| **M-11** | Concurrent conversion under in-flight Treasury fetch — consistency property. | AC-027d. |
| **M-12** | EOM-clamp boundary cases for the 6-month window. | AC-018b, AC-019b. |
| **M-13** | Treasury reachable + empty result vs all-out-of-window → `CONVERSION_RATE_NOT_AVAILABLE`. | AC-020b, AC-022b. |
| **M-14** | Track-data shape (track 1 / track 2) rejection on description content guard. | AC-010c (new). |

Acceptance criteria in items M-9..M-13 are added to `acceptance-criteria.md` as part of this grill (see §7 below).

---

## 5. Contradictions and ambiguous terms

| # | Item | Resolution |
|---|---|---|
| 1 | "Within the last 6 months" — calendar months vs day count. | **Resolved.** A-003 stands: calendar-month subtraction with EOM clamp, inclusive both ends. AC-018b / AC-019b lock the EOM case. OQ-003 closed. |
| 2 | Future-dated `transactionDate`. | **Resolved.** A-002 stands: reject with `422 FUTURE_DATE`. OQ-001 closed in this grill. (Rationale: a future purchase has no eligible rate by construction; rejecting at FR-001 is the cheaper, clearer failure point.) |
| 3 | Currency input format — `country_currency_desc` vs ISO 4217. | Already resolved by D-3 / A-001. No contradiction; alias-table maintenance is the residual question (OQ-016). |
| 4 | Tie-breaker on same `record_date`. | **Partially resolved.** Field choice is `record_date` (A-011). Sort order at query time is `(record_date desc, id asc)` (OQ-002 working). Persistence-time conflict policy is the new gap → see G-P0-4 → resolved by Phase-3 design as "store as separate version row, query latest." |
| 5 | Rounding mode. | Resolved. HALF_UP per A-004 / D-5; counter-argument recorded in ADR-0001 (Phase 3). |
| 6 | `exchangeRate` representation. | Decimal string at full Treasury precision (A-015, AGENT §6). OQ-015 stays NICE. |
| 7 | "50 characters". | A-013: UTF-16 code units. OQ-005 NICE. |
| 8 | `CONVERSION_RATE_NOT_AVAILABLE` vs `UPSTREAM_UNAVAILABLE`. | **Resolved.** See G-P0-3; pinned in this grill via AC additions and FR-003 decision table. |
| 9 | "Production-ready" — what NFRs the source implies. | The source phrase "build as if for Production" is a deliberately broad enabling clause. All NFRs derived from it remain *proposed* until ratified in Phase 5/6. Document this explicitly in `non-functional-requirements.md` preamble. |
| 10 | "Unique identifier". | UUID v7 working (A-005). ULID documented alternative. Ratified in ADR-0002 (Phase 3). |

---

## 6. Non-functional gaps

Captured as P1 findings G-P1-* above. Cross-summary:

- NFR latency anchors (NFR-001..004) — proposed; ratification in Phase 5/6. **Grill does not block on these.**
- NFR availability (NFR-005..006) — proposed; bounded by Treasury reality (G-P1-2). Phase 5/6 must close.
- NFR-014 input validation is solid; complement with CORS (G-P1-5) → NFR-014b.
- NFR-015/016 PCI scope — re-framed by G-P0-5; primary control is the API contract, defense in depth is the guard.
- NFR-017 (logs) — HMAC key management (G-P1-4) → NFR-017 expanded.
- NFR-018 — PAN false-positive metric (G-P1-3) and cardinality budget (G-P1-7) added.
- NFR-016 — audit-log retention/integrity (G-P1-6) → NFR-016b.

---

## 7. Acceptance-test gaps and added criteria (this grill adds these)

The following ACs are added to `docs/requirements/acceptance-criteria.md` in this phase:

| New AC | Purpose | Maps to grill finding |
|---|---|---|
| **AC-001b** | Two identical POSTs without `Idempotency-Key` produce two purchases with distinct ids. | G-P1-8 |
| **AC-010c** | Track-data shape (`%B`, `;`, `=`-delimited stripe) rejected as `400 PAN_PATTERN_DETECTED`. | G-P0-5 |
| **AC-018b** | EOM-clamp boundary, eligible. `transactionDate=2026-08-31`, only rate `record_date=2026-02-28` → eligible (200). | G-P0-2 |
| **AC-019b** | EOM-clamp boundary, ineligible. `transactionDate=2026-08-31`, only rate `record_date=2026-02-27` → 422. | G-P0-2 |
| **AC-020b** | Treasury reachable, returns 200 with empty list, no eligible local rate → `422 CONVERSION_RATE_NOT_AVAILABLE` (never 503). | G-P0-3 |
| **AC-021b** | Treasury responds 4xx "unknown currency" for a currency-input that *was* present in the alias table → still `400 INVALID_CURRENCY` returned to the client. (Alias-table drift; see OQ-016.) | G-P0-3, OQ-016 |
| **AC-022b** | Treasury reachable but returns rates *all* with `record_date < transactionDate.minusMonths(6)` → `422 CONVERSION_RATE_NOT_AVAILABLE`. | G-P0-3 |
| **AC-024b** | Treasury returns `exchange_rate ≤ 0` or `exchange_rate > 10^9` → `502 UPSTREAM_BAD_RESPONSE`. | G-P1-10 |
| **AC-026b** | Treasury returns *revised* rate for an existing `(country_currency_desc, record_date)` — next conversion uses the revised value; previously-served responses are not retroactively mutated. Persistence stores both versions; query selects latest. | G-P0-4 |
| **AC-027d** | Two concurrent `GET /purchases/{id}/conversion?currency=X` while a single-flight Treasury fetch is in flight — both responses agree on `exchangeRate` or both fail consistently. | G-P1-11 |
| **AC-032b** | Log lines for a successful FR-001 do not contain the description's verbatim value *and* do not contain an HMAC digest computed with a hard-coded key (i.e., the configured key env var is in effect). | G-P1-4 |
| **AC-T-5** | Every error response carries `Content-Type: application/problem+json`. | G-P1-9 |

All of these are added to `acceptance-criteria.md` as part of this grill.

---

## 8. Open-question disposition (each existing OQ)

For each OQ, severity classification for **the next phase (Phase 3 design session)**:

| OQ | Current severity | Next-phase severity | Rationale |
|---|---|---|---|
| OQ-001 future-date | IMPORTANT (Phase 2) | **RESOLVED in this grill** (reject with 422 FUTURE_DATE). | A-002 ratified; no Phase-3 carry. |
| OQ-002 same-`record_date` tie-breaker | IMPORTANT (Phase 3) | **IMPORTANT (Phase 3)** — query-time only; persistence policy now handled by G-P0-4. | Empirical check against live Treasury still required. |
| OQ-003 calendar months vs day count | IMPORTANT (Phase 2) | **RESOLVED in this grill** (calendar months + EOM clamp, inclusive). | A-003 ratified; AC-018b/AC-019b lock it. |
| OQ-004 currency universe | IMPORTANT (Phase 3) | **IMPORTANT (Phase 3)** — unchanged. | Live Treasury catalog; OpenAPI uses example list. |
| OQ-005 50 chars semantics | NICE (Phase 3) | **NICE (Phase 3)** — unchanged. | UTF-16 code units (A-013). |
| OQ-006 rounding mode | RESOLVED | RESOLVED — counter-argument recorded in Phase-3 ADR-0001. | |
| OQ-007 list/paginate endpoint | NICE | **NICE** — out of scope v1. | |
| OQ-008 retention policy | IMPORTANT (Phase 5) | **IMPORTANT (Phase 5)** — unchanged. Plus new OQ-022 (audit-log retention). | |
| OQ-009 Idempotency-Key | IMPORTANT (Phase 3) | **IMPORTANT (Phase 3)** + raise to **BLOCKING-for-prod** (G-P1-8). | |
| OQ-010 identity origin | BLOCKING-for-prod (Phase 7) | **BLOCKING-for-prod (Phase 7)** — unchanged. | |
| OQ-011 PAN guard policy | IMPORTANT (Phase 7) | **IMPORTANT (Phase 7)** — unchanged. Plus G-P0-5 widens scope. | |
| OQ-012 single-region vs DR | IMPORTANT (Phase 5) | **IMPORTANT (Phase 5)** — unchanged. | |
| OQ-013 downstream WEX compat | NICE | **NICE** — unchanged. | |
| OQ-014 SLO ratification | IMPORTANT (Phase 5+6) | **IMPORTANT (Phase 5+6)** — plus G-P1-2 (Treasury-ceiling formula). | |
| OQ-015 rational rate | NICE | **NICE** — unchanged. | |
| OQ-016 alias-table ownership | IMPORTANT (Phase 3) | **IMPORTANT (Phase 3)** — unchanged. Plus G-P2-9 (preload). | |
| OQ-017 rate orientation | IMPORTANT (Phase 3) | **BLOCKING-for-Phase-3-exit** (G-P0-1). | Silent-1/x risk. |

**New OQs registered by this grill:** OQ-018 (CONV-vs-UPSTREAM boundary policy), OQ-019 (HMAC key management), OQ-020 (CORS), OQ-021 (Treasury same-day revision conflict resolution), OQ-022 (audit-log retention/integrity), OQ-023 (H2 file path on cloud-sync drives).

**OQs resolved in this grill:** OQ-001 (future date → reject); OQ-003 (calendar months + EOM clamp + inclusive). Both ratified as A-002 / A-003.

---

## 9. New risks identified

| ID | Risk | L | I | Score | Mitigation lead | Phase to address |
|---|---|---|---|---|---|---|
| **R-021** | Treasury rate orientation mis-applied → silent 1/x conversion error for one or more currencies. | 2 | 5 | 10 (Medium) | Architect | Phase 3 (empirical verification) |
| **R-022** | H2 file corruption / sync conflict on a OneDrive-/Dropbox-/iCloud-synced working directory. | 4 | 3 | 12 (Medium) | Architect / SRE | Phase 3 (data-dir config) |
| **R-023** | Logging HMAC key in source → secret-in-repo finding; or no rotation policy → audit finding. | 3 | 3 | 9 (Medium) | SecArch | Phase 7 |
| **R-024** | Metric cardinality explosion under per-currency labelling. | 2 | 3 | 6 (Low) | SRE | Phase 5 |
| **R-025** | CORS misconfiguration in a real deployment exposes Swagger UI or the API to untrusted origins. | 2 | 3 | 6 (Low) | Architect / SecArch | Phase 3 / 7 |
| **R-026** | `CONVERSION_RATE_NOT_AVAILABLE` returned where `UPSTREAM_UNAVAILABLE` is correct (or vice-versa), causing wrong client-side retry behaviour and wrong SLO accounting. | 3 | 3 | 9 (Medium) | Architect | Phase 3 (decision table) |
| **R-027** | Treasury revises a rate for a `record_date` already in our local DB; downstream readers see drift between two conversions of the same purchase if the persistence policy is upsert-without-versioning. | 2 | 4 | 8 (Medium) | Architect | Phase 3 (versioned persistence) |

---

## 10. Recommended improvements to `AGENT_PROJECT_INSTRUCTIONS.md` / `CLAUDE.md`

These are *recommendations to the human owner*; this grill does not modify those files.

| # | Document | Section | Change | Why |
|---|---|---|---|---|
| 1 | `AGENT_PROJECT_INSTRUCTIONS.md` | §6 (API design rules) | Add: "Error responses carry `Content-Type: application/problem+json`." | Wire-level RFC 9457 conformance; AC-T-5 will assert. |
| 2 | `AGENT_PROJECT_INSTRUCTIONS.md` | §8 (Treasury integration) | Add a single-line rule: "Rate orientation (`USD→target` or `target→USD`) shall be empirically verified against the live Treasury dataset before ADR-0001 is signed." | Closes G-P0-1 as a *bundle-level* invariant rather than a project decision. |
| 3 | `AGENT_PROJECT_INSTRUCTIONS.md` | §11 (Observability) | Add metric `purchase.create.validation_error.count{reason}` to the non-exhaustive list. | Closes G-P1-3 at the spec source. |
| 4 | `AGENT_PROJECT_INSTRUCTIONS.md` | §12 (Security) | Add: "HMAC-SHA-256 key for description-digest logging is loaded from `WEX_LOG_HASH_KEY` in non-local profiles; key version is prefixed onto the digest output." | Closes G-P1-4. |
| 5 | `AGENT_PROJECT_INSTRUCTIONS.md` | §7 (Persistence) | Clarify: "When Treasury republishes a record for an existing `(country_currency_desc, record_date)`, store as a new version row keyed by `(country_currency_desc, record_date, effective_date)`. Query selects max(effective_date)." | Closes G-P0-4. |
| 6 | `CLAUDE.md` | §7 (Operations rule) | Add a one-liner that "audit log retention ≥ 1 year, 3 months online, append-only or signed in production" is a Phase-5/7 deliverable, not optional. | Closes G-P1-6 at the bundle level. |
| 7 | `CLAUDE.md` | §10 (high-risk systems) | This service is high-risk by D-1 (PCI-adjacent). Reaffirm "separate recommendation from execution" already applies. | Documents alignment; no behavioural change. |

**Neither file is edited by this grill.** Items 1–7 are presented as recommendations to the human owner; bundle/overlay file edits remain a human decision.

---

## 11. Exit-criteria checklist for Phase 2

| Criterion | Status |
|---|---|
| Adversarial review performed (principal, product, security, SRE, QA, business, auditor lenses). | ✅ |
| Contradictions and ambiguous terms enumerated and resolved or flagged. | ✅ |
| Missing requirements enumerated (M-1..M-14). | ✅ |
| Acceptance-test gaps identified and the new ACs added to `acceptance-criteria.md` in this phase. | ✅ |
| P0 findings each carry a recommended fix, owner, and target gate. | ✅ |
| P1 findings each carry a recommended fix, owner, and target gate. | ✅ |
| Open-question register updated; per-OQ severity recorded for Phase 3. | ✅ |
| New OQs (OQ-018..OQ-023) registered in `open-questions.md` and `assumptions-and-open-questions.md`. | ✅ |
| New risks (R-021..R-027) registered in `risk-register.md`. | ✅ |
| Traceability matrix updated to link every grill finding to existing or new IDs. | ✅ |
| Recommended improvements to `AGENT_PROJECT_INSTRUCTIONS.md` and `CLAUDE.md` documented; no edits made by Claude Code. | ✅ |
| Source-requirements.md untouched. | ✅ |
| `.human-approvals/` untouched. | ✅ |
| Verdict recorded. | ✅ |

---

## 12. Verdict

**CONDITIONALLY PROCEED to Phase 3 (Architecture & Design Session).**

Conditions, all to be closed *before Phase-3 exit* (not before Phase-3 start):

1. **G-P0-1** Treasury rate orientation empirically verified; recorded in ADR-0001.
2. **G-P0-2** EOM-clamp boundary cases pinned (now in AC-018b/AC-019b); ADR-0001 records the asymmetric-window note.
3. **G-P0-3** `CONVERSION_RATE_NOT_AVAILABLE` vs `UPSTREAM_UNAVAILABLE` decision table added to `architecture/component-design.md` and `architecture/api-contracts.md`; ACs now pin it.
4. **G-P0-4** Treasury same-`record_date` revision policy chosen and recorded in `architecture/data-model.md`; AC-026b added.
5. **G-P0-5** PCI control-stack re-framing reflected in `security/pci-scope-and-cde.md` (primary control = API contract; defense-in-depth = guards; monitoring; incident response).

P1 findings must be resolved or explicitly accepted by the human owner before the **Phase-4 design grill** exit.

Phase 2 hands over to Phase 3 with:
- 14 new requirement-level artefacts (M-1..M-14)
- 12 new acceptance criteria
- 6 new open questions (OQ-018..OQ-023)
- 7 new risks (R-021..R-027)
- 0 source-document edits
- 0 human-approval-marker creations or edits
- 0 implementation files created or edited

Pause cadence: Phase 3 begins only on explicit "proceed to Phase 3" approval from the human owner.
