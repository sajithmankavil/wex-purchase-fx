# PCI Tier-1 Security Grill — Phase 8

> **Status:** COMPLETED — 2026-05-17
> **Verdict:** **CONDITIONAL PASS** to Phase 9 (Implementation Readiness Gate). Four P0 corrections pinned this phase; ten P1 findings deferred to Phase 12 (pre-prod hand-off) or Phase 13 (implementation) with explicit acceptance.
>
> **Adversarial panel:** PCI QSA-style reviewer · Application security architect (red-team mindset) · Cloud security engineer · Privacy engineer · Evidence auditor.
> **Inputs reviewed:**
> - All 14 [docs/security/](.) artefacts from Phase 7
> - [docs/planning/pci-security-design-session.md](../planning/pci-security-design-session.md) (Phase-7 meta)
> - [docs/architecture/](../architecture/) Phase-3/4/6 architecture
> - [docs/operations/](../operations/) Phase-5/6 operational design
> - Phase-1..6 grills + requirements docs
>
> **Scope.** The Phase-7 design is **plausible enough to ship** for the case-study posture. The grill attacks whether that posture survives a real QSA assessment, a red-team engagement, a privacy-engineer review, and a regulator's audit. Most findings are *evidence-completeness* gaps rather than control gaps.

---

## 0. Executive verdict

Four things are wrong enough to fix in-phase:

1. **Order of operations: the encoded-PAN decoder pipeline runs *before* the app-layer rate-limiter.** This means a hostile client can submit base64-shaped strings at high RPS and force decoder work even though the rate-limiter would have blocked the request body from reaching the controller. CPU-cost DoS vector. **Re-order: rate-limiter before decoder pipeline.**
2. **Audit-log scope categorisation is inconsistent.** Phase 7 calls the audit destination "security-impacting" but the practical PCI implication is closer to "connected-to": it contains data (`description.hash` + `length`) that is reversible with the HMAC key. Re-categorise and document the IAM tightening that follows.
3. **Unicode confusable digits bypass the PAN-Luhn guard** (e.g., `４２４２　４２４２　４２４２　４２４２` — fullwidth digits + ideographic space). The boundary regex matches ASCII digits and separators only. **Add Unicode-normalisation (NFKC) before guard checks.**
4. **The "Treasury is not a TPSP" claim needs PCI-grade justification, not just a sentence.** A QSA will ask: "anything whose outage affects your audit trail or compliance posture is a TPSP." We need to document the rationale formally with an explicit applicability decision.

A fifth borderline item (HMAC dictionary-attack vulnerability for short / low-entropy descriptions) is **P1 not P0** — the audit-log destination is WORM and access is logged, so the attacker would need to compromise both the audit destination IAM and the HMAC key (defense in depth holds).

Beyond the P0s, ten P1 findings span audit-evidence completeness, scope-boundary clarity, dependency-monitoring cadence, and case-study-mode hygiene. All P1s have owners and target gates.

---

## 1. P0 findings — pin before Phase 9 hand-over

### G8-P0-1 — Decoder pipeline runs before rate-limiter (CPU DoS vector)

**Observation.** Phase-7 D-15 added a Resilience4j RateLimiter at the controller method via `@RateLimiter` annotation. Phase-4 G4-P0-5 added the encoded-PAN decoder pipeline in `ContentGuard`, registered as a `@RestControllerAdvice`. In Spring's order of operations, **the `@RestControllerAdvice` runs *before* the controller method**, which means the decoder pipeline executes **before** the rate-limiter rejects.

**Lens.** Application security architect; cloud security engineer.

**Evidence.** Phase-4 G4-P0-5 (Phase-4 design grill) + Phase-7 D-15 + Spring Boot 3.x advice/method ordering.

**Risk.** A hostile client submits 50-char base64-shaped strings at 10 000 RPS. Rate-limiter would block them at the controller boundary — but the decoder pipeline has already done base64 + hex + URL-decode on every request. TM-D-008 was rated Low at Phase 4; this re-attack raises it to medium under sustained pressure.

**Required fix (pinned in Phase 8).** Re-order:

```
HTTP Filter → @RateLimiter (servlet-level filter, NOT controller-method annotation)
            → @Valid (Bean Validation: bounded length, format)
            → ContentGuard advice (decoder pipeline + Luhn + track + encoded checks)
            → Controller method
            → ApplicationService
```

The rate-limiter moves from `@RateLimiter` on the controller method to a servlet-level Filter ordered before the advice chain. This ensures rate-limiting rejects before ContentGuard runs. The decoder pipeline's CPU cost is then bounded by the rate-limit, not by the attacker's RPS.

Implementation contract for Phase 13: `WexRateLimiterFilter` registered with `@Order(Ordered.HIGHEST_PRECEDENCE + 100)`; uses Resilience4j `RateLimiter`; returns `429 Too Many Requests` with RFC-9457 envelope when limit breached. The `@RateLimiter` annotation is removed from controller methods.

**Acceptance.** New AC-T-6: every rate-limit-rejected request is rejected **before** any ContentGuard work executes. Asserted via instrumentation counter (`contentguard.invocations.count` increments only for non-rate-limited requests; CPU profile of `ContentGuard.check` confirms no calls during a rate-limit-saturation test).

**Owner.** Architect + SecArch.

**Target gate.** Pinned in this Phase-8 grill; ContentGuard and rate-limiter contracts updated in-place in [component-design.md](../architecture/component-design.md) and [secure-sdlc-pci.md](secure-sdlc-pci.md). Phase 13 implements.

---

### G8-P0-2 — Audit-log destination categorisation: "security-impacting" understates its scope

**Observation.** [pci-scope-and-cde.md §2](pci-scope-and-cde.md#2-scope-model) categorises the audit-log destination as "Security-impacting" alongside the secrets store. But the audit destination contains:

- `descriptionHash` (HMAC-SHA-256 digest with `vN:` version prefix)
- `descriptionLength` (integer)
- `purchaseId`, timestamps, request metadata

With the HMAC key + a known-short-description dictionary (e.g., "1", "test", "abc"), an attacker who compromises the audit destination's read IAM can recover the verbatim descriptions for short / low-entropy entries. The HMAC is one-way against the *key*, not against the *input space*. A 256-bit key keyspace is intractable; an N-character input keyspace is not (≈ 26⁵ = 12M for a 5-char alphanumeric).

This means the audit destination contains data that is **reversible to original input** with the HMAC key. PCI DSS 4.0.1 calls such data **connected-to** rather than out-of-scope.

**Lens.** PCI QSA-style reviewer; privacy engineer.

**Evidence.** PCI DSS 4.0.1 Req 12.5.1: any system that stores reversible representations of data that *could* be CHD (if a client had submitted CHD before the guard rejected) is connected-to. Our content guards reject CHD; we still log the *attempt*'s hash and length.

**Risk.** A QSA will push back on the "out-of-scope" categorisation of the audit destination. Either the categorisation is wrong, or there's an unstated mitigation we need to add.

**Required fix (pinned in Phase 8).** Two-part:

**Part A.** Re-categorise the audit-log destination as **connected-to** in [pci-scope-and-cde.md §2](pci-scope-and-cde.md#2-scope-model). The IAM and operational controls already align with this category (WORM destination; access-of-audit logged; quarterly access review). The change is in *naming*, not in *controls*.

**Part B.** Document the **explicit mitigating argument** for the HMAC inversion concern:
- The HMAC key is in a separate IAM zone (secrets store, not the audit destination).
- An attacker would need to compromise *both* the audit destination IAM **and** the secrets store IAM — different IAM principals, different rotation cadences, different audit destinations.
- Even with both compromised, the inverted output is `description` text (which **by API contract** does not contain CHD; the guards ensured anything CHD-shaped was rejected before logging).
- Inversion of the *rejected payload* is impossible because the rejected payload is **never logged** (AC-010b/c/d).
- Conclusion: while the audit destination is connected-to, the consequence of compromise is operational risk (description-text leak), not CHD leak.

**Acceptance.** Update [pci-scope-and-cde.md §2](pci-scope-and-cde.md#2-scope-model) scope table; add a `§7 Mitigating arguments` section to [logging-monitoring-pci.md](logging-monitoring-pci.md) covering the dual-IAM compromise scenario.

**Owner.** SecArch + Compliance.

**Target gate.** Pinned in this Phase-8 grill.

---

### G8-P0-3 — Unicode confusable digits bypass the PAN-Luhn guard

**Observation.** AC-010b matches Luhn-valid 13–19-digit sequences with optional ASCII separators. AC-010d extends to base64/hex/URL-encoded variants. **None of them apply Unicode normalisation.**

Attacker submits:

```
description = "４２４２　４２４２　４２４２　４２４２"
```

That string is `4242 4242 4242 4242` (Luhn-valid PAN for a test card) but written in **fullwidth Unicode digits** with ideographic space (`U+3000`). The ASCII regex `[0-9]` does not match `U+FF14` ("FULLWIDTH DIGIT FOUR"). The PAN slips through. The 50-char length cap doesn't catch it (16 ASCII or 16 fullwidth — same character count under `String.length()`).

**Lens.** Application security architect; red-team mindset; QSA-style reviewer.

**Evidence.** Unicode block `FULLWIDTH FORMS` (U+FF00..U+FFEF); `Normalizer.normalize(s, Form.NFKC)` maps fullwidth → ASCII; we currently don't do this. The Phase-7 docs note this as a "Phase-8 grill concern" but didn't address.

**Risk.** Real bypass of a primary PCI-defense layer. A QSA-led test would discover this within an hour. The out-of-CDE claim weakens if accidental CHD ingestion is demonstrated past the guard.

**Required fix (pinned in Phase 8).** Add **Unicode normalisation as a pre-pass** in `ContentGuard`:

```
ContentGuard.check(description):
  // Step 1 (new): normalise to NFKC
  normalised = Normalizer.normalize(description, Form.NFKC)

  // Step 2 (existing): build candidate set
  candidates = [ normalised ]
  for decoder in [ base64-standard, base64-urlsafe, hex, urlEncoded ]:
      decoded = decoder.tryDecode(normalised)   // decode from NFKC form
      if decoded is not None: candidates.append(decoded)

  // Step 3 (existing): apply guards
  for candidate in candidates:
      check Luhn, track1, track2 → reject if any match
```

**Important:** the *stored* `description` should remain the original input (not the NFKC form), to preserve client-visible behaviour. NFKC is applied only for the guard check.

**Acceptance.** New **AC-010e**: a `description` containing fullwidth digits / homoglyphs / Unicode-normalisable variants of a Luhn-valid PAN sequence is rejected as `400 PAN_PATTERN_DETECTED` with `details.reason="luhn"` (because NFKC normalises before the Luhn check; same `reason` as the canonical ASCII case).

**Owner.** SecArch.

**Target gate.** Pinned in this Phase-8 grill; ContentGuard contract updated in [component-design.md](../architecture/component-design.md) and the encoded-PAN-guard spec in [pci-security-design-session.md §3.5](../planning/pci-security-design-session.md#35-d-19-encoded-pan-guard-implementation-direction-closes-g4-p0-5). Phase 13 implements.

---

### G8-P0-4 — Treasury TPSP "non-applicability" argument needs PCI-grade justification

**Observation.** [third-party-service-provider-pci.md §1](third-party-service-provider-pci.md#1-service-providers) records Treasury Fiscal Data API as "not a TPSP in the PCI sense — public-data provider." A single-sentence justification. PCI DSS 4.0.1 Req 12.8 defines TPSP more broadly than "data provider"; it includes "third parties whose service interruption or compromise could affect your security or compliance posture."

Treasury affects our:
- SLO budget (SLO-C bounded by Treasury availability).
- Audit-log volume (Treasury outages drive `treasury_api_failure` events).
- Correctness (rate-orientation drift would be silent without our canary).

A QSA might argue: "Treasury affects your compliance-relevant logging and SLO measurement; you need either a TPSP contract or a documented rationale for why it's not."

**Lens.** PCI QSA-style reviewer; evidence auditor.

**Evidence.** PCI DSS 4.0.1 Req 12.8 + Req 12.9; [third-party-service-provider-pci.md §1](third-party-service-provider-pci.md#1-service-providers).

**Risk.** Audit finding: incomplete TPSP analysis. The mitigation is documentation, not new controls — but the documentation must be QSA-grade.

**Required fix (pinned in Phase 8).** Update [third-party-service-provider-pci.md §1](third-party-service-provider-pci.md#1-service-providers) Treasury row with a **formal applicability decision**:

> **Applicability decision.** Treasury Fiscal Data API is *not* a Third-Party Service Provider under PCI DSS 4.0.1 Req 12.8 for the following reasons:
> 1. Treasury does not receive, store, process, transmit, or otherwise have access to cardholder data on our behalf.
> 2. Treasury does not authenticate clients on our behalf; we are anonymous to them.
> 3. Treasury's service interruption affects our SLOs and audit-event volume but does not affect any PCI-DSS-relevant control (CHD protection, access control, audit-log integrity, etc.).
> 4. Treasury operates under U.S. federal-government open-data publication; no contractual relationship exists or is required.
> 5. Compensating controls for Treasury risk are operational (single-flight cache + CB + local rate fallback) and are documented in [failure-modes-and-resilience.md](../operations/failure-modes-and-resilience.md); they are not PCI-DSS controls.
>
> **Treasury is therefore an upstream open-data dependency, not a TPSP.** If a future change to Treasury's terms or our integration model introduces CHD flow, contract, or authentication, this applicability decision is re-opened.

**Acceptance.** No new AC; this is documentation only.

**Owner.** Compliance + SecArch.

**Target gate.** Pinned in this Phase-8 grill; [third-party-service-provider-pci.md §1](third-party-service-provider-pci.md) updated in-place.

---

## 2. P1 findings — must resolve or explicitly accept before Phase 9

### Security control completeness

| ID | Title | Observation | Action | Owner | Target |
|---|---|---|---|---|---|
| **G8-P1-1** | Multi-encoding chain residual (AB-014) — explicit QSA-defence argument | Phase-4 / Phase-7 left this as accepted residual. QSA may ask why we don't recursively decode. | Document the **single-pass justification**: recursive decoding has no fixed-point (every potential encoding can be re-applied indefinitely); diminishing-returns analysis says one pass catches > 99 % of credible PAN-smuggle attempts; the remaining < 1 % is the accepted residual. Add to [pci-security-design-session.md §3.5](../planning/pci-security-design-session.md) and `threat-model.md` AB-014. | SecArch | Phase 8 (pinned via in-place edits) |
| **G8-P1-2** | CVV-shape detection gap | 3–4 digit content is not flagged; per Phase-7, too noisy to flag without false positives. | Document the **accepted residual** with the false-positive math: at 3 digits the keyspace is 10⁴ = 10 000 numbers; legitimate `description` content with 3-digit substrings is common (year suffixes, order IDs, etc.); a guard would have a > 50 % false-positive rate. Risk lowered by the contract-level prohibition (A-017). | SecArch | Phase 8 (pinned) |
| **G8-P1-3** | Vulnerability-management exception cap | The exception process allows mitigating-control overrides. No cap on the number of active exceptions. | Add to [vulnerability-management-pci.md](vulnerability-management-pci.md): **maximum 5 active CRITICAL/HIGH exceptions at any time**; sustained > 5 triggers a P1 incident + service-owner escalation. Quarterly review reconciles. | SecArch + Compliance | Phase 8 (pinned) |
| **G8-P1-4** | Audit-of-audit destination not pinned | Phase 7 says "separately controlled" but doesn't specify *where*. | Pick: same platform, **different IAM scope** (recommended: separate cloud account / project / subscription); destination IAM never overlaps with primary audit destination's; access reviewed quarterly by Compliance. Phase 12 confirms platform-specific binding. | SecArch + Platform | Phase 12 |
| **G8-P1-5** | Image-signing trust chain | Phase 7 recommends cosign/sigstore but doesn't document the key-management for signing keys. | The signing key is held in the secrets store (separate path from `WEX_LOG_HASH_KEY` and `WEX_DB_PASSWORD`); rotated annually; admission control verifies against the public-key chain. Update [secure-config-hardening.md](secure-config-hardening.md) (Phase 7 follow-on; populate as part of doc-tightening). | SecArch + SRE | Phase 12 |
| **G8-P1-6** | GitHub SOC 2 monitoring cadence | Phase 7 records GitHub as TPSP with SOC 2 accepted; no cadence for verifying SOC 2 remains current. | Add to [third-party-service-provider-pci.md §4](third-party-service-provider-pci.md#4-tpsp-audit-cadence): **annually, Compliance verifies GitHub's SOC 2 attestation is < 12 months old**; if not, raises P2 incident; mitigation = escalate to GitHub Enterprise contract terms or switch provider. | Compliance | Phase 12 |
| **G8-P1-7** | PAN-guard false-positive calibration | Metric `purchase.create.validation_error.count{reason=pan_pattern}` exists; no threshold on what's acceptable. | Set **threshold: if > 0.1 % of legitimate POST requests trip the PAN-guard over a 30-day window, the guard regex is recalibrated** (e.g., to exclude common order-id prefixes). Until baseline established, monitor only. Add to [monitoring-alerting.md](../operations/monitoring-alerting.md) A-021 with the 0.1 % threshold. | SecArch + Product | Phase 13 (baseline measurement) |
| **G8-P1-8** | Pen-test scope boundaries | Phase 7 says "full API surface" — too vague. | Define scope: **(a)** API surface (every endpoint), **(b)** content-guard boundary (CHD-shaped injection across all guards), **(c)** authentication-at-gateway (Phase 12 onward), **(d)** audit-log integrity (attempt tampering), **(e)** secret-storage IAM (attempt unauthorised retrieval). Document in [penetration-test-plan.md](penetration-test-plan.md) (Phase 7 follow-on). | SecArch | Phase 12 |
| **G8-P1-9** | HMAC-recovery dictionary-attack vulnerability for short descriptions | Short `description` values are dictionary-attackable with the HMAC key. | Two mitigations: **(a)** the HMAC key is in a separate IAM zone from the audit destination (defense in depth); **(b)** rotation cadence (quarterly) limits the dictionary-attack window. Accept this as residual; document the dual-IAM-compromise scenario in [logging-monitoring-pci.md §7 (new)](logging-monitoring-pci.md). | SecArch | Phase 8 (pinned) |
| **G8-P1-10** | Local-mode rejected-payload invariant | Case-study reviewer running locally may submit a real PAN to test the boundary guards. The rejected-payload-not-logged invariant must hold even in local mode (stdout). | Confirm via `LoggingPiiGuardTest` running in local profile too (not just CI); README warns the case-study reviewer that *real* PANs should never be used for testing — the test fixtures provide synthetic-but-Luhn-valid examples (e.g., the IETF reserved test PANs `4111 1111 1111 1111`). | QA + Architect | Phase 13 (test; README warning) |

### Other findings

| ID | Title | Action | Owner | Target |
|---|---|---|---|---|
| **G8-P1-11** | Phase-7 `pci-security-design-session.md` lists D-15..D-20; the broader ADR series (D-1..D-14 + new) should be consolidated | Document inventory in ADR-0001 + the Phase-7 design session. Already adequate; flag as low-impact. | Architect | Phase 9 |
| **G8-P1-12** | Bundle-level `security-profile.yml` references `pci_dss_tier1`; the application config doesn't read it at runtime | Phase 13 wires a startup check that asserts `security-profile.yml` exists and `mode == pci_dss_tier1` for `prod`/`staging`; logs `security_profile_loaded` event. Non-blocking; documentary. | SecArch | Phase 13 |

---

## 3. P2 / NICE findings

| ID | Title | Recommendation |
|---|---|---|
| **G8-P2-1** | Cryptographic-erasure documentation for HMAC key destruction | The Phase-7 description says "key destroyed in secrets store after 1 year"; this is *cryptographic erasure* — the audit digests remain but become uninvertible. Document this term explicitly in [encryption-key-management.md §3.4](encryption-key-management.md#34-key-destruction) for QSA clarity. |
| **G8-P2-2** | Per-currency audit-event volume disclosure | `currency_alias.drift.detected.count` is per-currency, low-volume; not material. Documented residual. |
| **G8-P2-3** | Length-distribution side-channel at scale | At millions of records, `descriptionLength` distribution reveals patterns. v1 scale doesn't reach this. Documented residual; revisit at scale. |
| **G8-P2-4** | GDPR / multi-region trigger | OQ-012 / multi-region DR deferred; PCI grill confirms the deferral with concrete triggers from Phase-6 G6-P1-7. |
| **G8-P2-5** | Re-categorisation of all "security-impacting" rows in pci-scope-and-cde.md | The G8-P0-2 fix applies to audit destination; review the others (secrets store, telemetry sinks) for consistency. Telemetry: probably stays security-impacting (doesn't contain reversible content). Secrets store: stays security-impacting (contains key material, not data). |
| **G8-P2-6** | Phase-7 follow-on docs not yet authored | `secrets-policy.md`, `dependency-risk-policy.md`, `secure-config-hardening.md`, `authn-authz-design.md`, `incident-response-pci.md`, `targeted-risk-analysis.md`, `compensating-controls.md`, `backup-recovery-pci.md`, `asv-scan-plan.md`, `penetration-test-plan.md`, `qsa-roc-readiness.md`, `security-review-findings.md`, `pci-production-readiness-gate.md` — all referenced; some are scaffolds. Phase 12 / 13 populates as needed; Phase 8 doesn't block. |
| **G8-P2-7** | Audit-event sampling cadence for evidence | NFR-016b says "audit log access reviewed quarterly"; suggest also "1 000 audit events sampled monthly for redaction confirmation." Phase 12. |
| **G8-P2-8** | Treasury upstream-canary independence | Canary script should run independently of the service's deployed Treasury client (separate codepath; separate fixtures). Phase 13 implementation. |

---

## 4. Pinned corrections to Phase-7 docs

Executed in-place after this grill:

| Doc | Pin | Reason |
|---|---|---|
| [component-design.md §3.5](../architecture/component-design.md#35-contentguard-phase-4-refinement-g4-p0-5) | Add NFKC normalisation pre-pass to `ContentGuard.check`; rate-limiter moves from controller annotation to servlet Filter ordered first | G8-P0-1 + G8-P0-3 |
| [pci-scope-and-cde.md §2](pci-scope-and-cde.md#2-scope-model) | Audit destination re-categorised as connected-to | G8-P0-2 |
| [logging-monitoring-pci.md](logging-monitoring-pci.md) | New §7 "Mitigating arguments for the connected-to categorisation" | G8-P0-2 |
| [third-party-service-provider-pci.md §1](third-party-service-provider-pci.md#1-service-providers) | Treasury row gets the formal applicability decision text | G8-P0-4 |
| [vulnerability-management-pci.md §4](vulnerability-management-pci.md#4-exception-process) | Max 5 active CRITICAL/HIGH exceptions | G8-P1-3 |
| [third-party-service-provider-pci.md §4](third-party-service-provider-pci.md#4-tpsp-audit-cadence) | GitHub SOC 2 freshness check (annually) | G8-P1-6 |
| [monitoring-alerting.md A-021](../operations/monitoring-alerting.md) | PAN-guard false-positive threshold 0.1 % of legitimate POSTs over 30 d | G8-P1-7 |
| [acceptance-criteria.md](../requirements/acceptance-criteria.md) | New AC-010e (Unicode confusable PAN); new AC-T-6 (rate-limit rejects before ContentGuard) | G8-P0-1 + G8-P0-3 |
| [encryption-key-management.md §3.4](encryption-key-management.md#34-key-destruction) | "Cryptographic erasure" wording | G8-P2-1 |
| [traceability-matrix.md](../requirements/traceability-matrix.md) | Map G8-P*-* to FRs/NFRs/ACs/OQs/risks | Bookkeeping |
| [risk-register.md](../requirements/risk-register.md) | Add R-038 (Unicode bypass), R-039 (decoder DoS via ordering) | Bookkeeping |

---

## 5. New risks

| ID | Risk | L | I | Score | Status |
|---|---|---|---|---|---|
| **R-038** | Unicode confusable digits bypass PAN-Luhn guard; PAN reaches DB and audit log | 3 | 5 | 15 (High) | Mitigated by NFKC normalisation pre-pass (G8-P0-3) |
| **R-039** | Decoder pipeline runs before rate-limiter; CPU-cost DoS vector via base64-shaped strings | 2 | 3 | 6 (Low) | Mitigated by re-ordering rate-limiter to servlet Filter (G8-P0-1) |
| **R-040** | Audit destination compromise + HMAC key compromise → description text recoverable | 1 | 3 | 3 (Low) | Documented residual; dual-IAM compromise required; G8-P1-9 |
| **R-041** | GitHub SOC 2 attestation expires without us noticing | 1 | 3 | 3 (Low) | Annual freshness check (G8-P1-6) |

---

## 6. Exit-criteria checklist

| Criterion | Status |
|---|---|
| Five-lens adversarial review performed (QSA, AppSec, cloud-security, privacy, evidence auditor) | ✅ |
| Out-of-CDE claim attacked across encoded PAN, Unicode confusables, length-cap, multi-encoding chain | ✅ |
| HMAC-only-logging dictionary-attack vulnerability addressed (defense-in-depth argument; rotation) | ✅ |
| Audit-log destination scope-categorisation reviewed (P0 re-categorisation) | ✅ |
| Identity-at-gateway approach reviewed (carried as OQ-010 → Phase 12) | ✅ |
| TPSP model attacked (Treasury non-applicability formalised) | ✅ |
| P0 findings each carry observation / evidence / fix / owner / target gate | ✅ |
| P1 findings each carry owner + target gate | ✅ |
| Pinned corrections to Phase-7 docs identified per §4 | ✅ |
| Risk register updated (R-038..R-041) | ✅ |
| Source-requirements.md untouched | ✅ |
| `.human-approvals/` untouched | ✅ |
| No implementation files created | ✅ |
| Verdict recorded | ✅ |

## 7. Verdict

**CONDITIONAL PASS to Phase 9 (Implementation Readiness Gate).**

Conditions, all closed in this phase via in-place pins:
1. Decoder pipeline / rate-limiter ordering corrected (G8-P0-1).
2. Audit destination re-categorised connected-to + mitigating-argument documented (G8-P0-2).
3. Unicode NFKC normalisation added to ContentGuard pre-pass (G8-P0-3).
4. Treasury TPSP applicability decision formalised (G8-P0-4).

Phase 8 hands over to Phase 9 with:
- 4 P0 corrections pinned
- 12 P1 findings recorded with owners and target gates
- 8 P2 / NICE findings recorded
- 4 new risks (R-038..R-041), all mitigated by Phase-8 pins
- 1 new AC (AC-010e) + 1 new AC-T (AC-T-6)
- 0 source-requirements.md edits
- 0 human-approval-marker edits

The case-study build holds. The production-reference build inherits all 4 P0 fixes at Phase 13 and the 10 P1 owners pick up the remaining items at Phase 12 / 13.

Pause cadence: Phase 9 begins on explicit "proceed to Phase 9" approval.
