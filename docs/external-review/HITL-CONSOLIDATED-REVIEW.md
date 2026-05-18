# HITL-CONSOLIDATED-REVIEW — Case-study terminal posture, all phases

**Reviewer:** External governance reviewer (canonical).
**Date:** 2026-05-18 (UTC).
**Triggered by:** `docs/external-review/HITL-READY.md` authored by dev at this commit (Trigger A per `directives/2026-05-18-hitl-gate-consolidation-protocol.md` §2.3).
**Verdict:** **READY FOR CASE-STUDY HITL WITH FINDINGS** (per refined verdict enum in `directives/2026-05-18-case-study-scope-clarification.md` §2.4).
**`main` HEAD at review time:** `7dfa267 feat(Phase 11): PCI security-readiness bundle + new HITL-gate directive + C2 carry-forward closure`.
**Audience:** the case-study assessor (and, for any real-deployment future, the multi-party signatory chain in `docs/security/change-control-pci.md §1`).

This is the canonical reviewer-authored consolidated verdict spanning Phase 13 (closed; 9 chunks), Phase 10 (Operational Readiness Gate), Phase 11 (PCI Security Readiness Gate), and any `13-POST-*` chunks (none). It supersedes (in part) the dev-authored provisional `30-review.md` files at `phases/10-operational-readiness/30-review.md` and `phases/11-pci-security-readiness/30-review.md` — those remain on disk as audit trail; this document is the canonical verdict.

---

## §1 — Verdict

**READY FOR CASE-STUDY HITL WITH FINDINGS.**

The cumulative dossier state is dossier-coherent and case-study-defensible. Every Phase-10 and Phase-11 source-doc requirement has a coverage-map row backed by a procedure, template, design, or live implementation. Every `BLOCKER:` row is genuinely production-infrastructure-dependent and routed to Phase 12 (real production cutover), which is **out of scope for case-study completion** per `directives/2026-05-18-case-study-scope-clarification.md` §2.2. The team has demonstrated production-deployability — the architecture, implementation, operations, security posture, and change-control discipline would survive enterprise scrutiny if a real deployment were on the table.

**Findings** (§4 below) are weighted-and-disclosed, not gate-blocking under case-study scope. Two of them (F-15 Idempotency-Key, Req 8 identity origin) would block a real production deploy and are correctly preserved as `BLOCKING-for-prod` flags that the Phase 12 multi-party signatory chain would adjudicate; for the case-study assessor, they demonstrate the team's discipline in flagging gaps rather than papering over them.

Two playbook entries pinned for future engagements (§4 F-INFO-6 / F-INFO-7).

---

## §2 — Scope

The consolidated review covers the cumulative state at `main` HEAD `7dfa267`.

### 2.1 — Phase 13 (Implementation) — closed; canonical verdicts preserved

| Chunk | Status on main | Canonical verdict file | Disposition |
|---|---|---|---|
| `13-PRE-dossier-bootstrap` | merged `b762bec` | `chunks/13-PRE-dossier-bootstrap/30-review.md` + `30-review-v2.md` | Canonical reviewer-authored. **Ratified.** |
| `13-PRE-readiness-check-fix` | merged `fdfe8e9` | `chunks/13-PRE-readiness-check-fix/30-review.md` | Canonical reviewer-authored. **Ratified.** |
| `13-A1-domain` | merged `04f19aa` | `chunks/13-A1-domain/30-review.md` | Canonical reviewer-authored. **Ratified.** |
| `13-A2-application` | merged `48dc656` | `chunks/13-A2-application/30-review.md` | Canonical reviewer-authored. **Ratified.** |
| `13-B-infrastructure` | superseded | — | superseded by `13-B1` + `13-B2` per accepted LOC-overage deviation. |
| `13-B1-persistence-cache` | merged `d96e08b` | `chunks/13-B1-persistence-cache/30-review.md` | Canonical reviewer-authored. **Ratified.** |
| `13-B2-treasury-singleflight` | merged `1b02dcf` + `c59b9cf` | `chunks/13-B2-treasury-singleflight/30-review.md` | Canonical reviewer-authored. **Ratified.** |
| `13-C-api-observability` | merged `ce73273` + `bf5f9de` + `c1ef322` | `chunks/13-C-api-observability/30-review.md` | Canonical reviewer-authored. **Ratified.** |
| `13-C2-observability-cicd` | merged `c92e146` | `chunks/13-C2-observability-cicd/30-review.md` + `30-review-v2.md` | Canonical reviewer-authored. **Ratified.** |
| `13-C3-openapi-cicd` | merged `5e77ac0` + 3 hygiene commits | `chunks/13-C3-openapi-cicd/30-review.md` | Canonical reviewer-authored. **Ratified.** |

Phase 13's reviewer verdicts were all authored under the per-chunk protocol that preceded the HITL-gate consolidation directive. They remain canonical per the HITL-gate directive §5 ("legacy reference" clause). Nothing in this consolidated review supersedes them; this section ratifies their cumulative substance.

### 2.2 — Phase 10 (Operational Readiness Gate)

| File | Status |
|---|---|
| `phases/10-operational-readiness/00-prompt.md` (79 LOC) | Dev-pre-staged; convention bend acknowledged + ratified by both the dev-authored Phase 10 30-review §F-LOW-2 and this consolidated review (§4 F-INFO-6 below). |
| `phases/10-operational-readiness/20-bundle.md` (251 LOC) | Dev-authored; coverage map across all 12 `docs/operations/*.md` source docs + 3 Phase-13 carry-forwards. |
| `phases/10-operational-readiness/30-review.md` (177 LOC) | **Reviewer-authored** under the prior per-phase protocol (commit `4e0ead2`); grandfathered as canonical per HITL-gate directive §5. **Ratified** by this consolidated review. |
| `phases/10-operational-readiness/manifest.yml` (65 LOC) | `status: accepted` on main. |

**Phase 10 verdict (consolidated): ACCEPTED WITH CONDITIONS.** All 14 conditions are forward-routed per the dev-authored Phase 10 30-review §4; under the case-study scope directive, the 11 Phase-12-routed items are **case-study-out-of-scope (Phase-12-equivalent — documented procedure / template / design is the gate-passing evidence)**.

### 2.3 — Phase 11 (PCI Security Readiness Gate)

| File | Status |
|---|---|
| `phases/11-pci-security-readiness/00-prompt.md` (105 LOC) | Dev-pre-staged (second activation of the bend); ratified per HITL-gate directive §2.1 (the bend is now the default under the new directive). |
| `phases/11-pci-security-readiness/20-bundle.md` (193 LOC) | Dev-authored; coverage map across 25 `docs/security/*.md` source docs + new `pci-dss-control-mapping.md` + 5 M7 CI artefacts + 6 carry-forward intakes. |
| `phases/11-pci-security-readiness/30-review.md` (158 LOC) | **Dev-authored PROVISIONAL** per HITL-gate directive §2.1. This consolidated review is its canonical ratification. |
| `phases/11-pci-security-readiness/manifest.yml` (74 LOC) | `status: accepted` on main (dev's mechanical state-machine flip per HITL-gate directive §2.1). Canonical verdict lives in this document. |

**Phase 11 verdict (consolidated): ACCEPTED WITH CONDITIONS, ratifying dev's provisional verdict with two amendments** (§4 F-AMEND-1 + F-AMEND-2 below).

### 2.4 — `13-POST-*` chunks

**None opened.** Per the user 2026-05-18 instruction "open any 13-POST-* chunks you judge necessary": dev judged none necessary. F-15 Idempotency-Key was preserved as Phase 10 §C1 BLOCKING-for-prod for Phase 12 multi-party signatory adjudication; under the case-study scope directive §4 ("F-15 ratified under case-study scope; option (a) [open 13-POST + implement] is not required for case-study completion"), this disposition is **ratified**.

### 2.5 — Cross-cutting directives in force at review time

| Directive | Effect |
|---|---|
| `directives/2026-05-17-forward-motion-bias.md` | Governs throughout. |
| `directives/2026-05-17-ops-check-and-pci-check-strict-flip.md` | Implemented in `13-PRE-readiness-check-fix`; defers strict mode in ops-check + pci-check to production-approval marker. |
| `directives/2026-05-18-phase-10-11-bulk-pass-protocol.md` | Activated at C2 merge; **superseded-in-part** by HITL-gate consolidation directive. |
| `directives/2026-05-18-hitl-gate-consolidation-protocol.md` | This document is its first canonical output. |
| `directives/2026-05-18-case-study-scope-clarification.md` | Governs the verdict semantics of this document. |

---

## §3 — Verification log

This section records the representative sample of dev-provisional claims spot-verified against `main` at review time. Per the HITL-gate directive Step 3.2, the consolidated review does not re-verify every claim — it samples to ensure the dev-provisional verdicts are not overstating closure. Six samples were checked across Phase 10 + Phase 11.

| # | Claim source | Claim content | Verification method | Verdict |
|---|---|---|---|---|
| 1 | Phase 11 §1.3 + §1.5 §C2 | `MalformedIdentifierException.getMessage()` no longer embeds raw input; `buildMessage()` carries length only | `git show main:src/main/java/com/example/purchaseconversion/application/exception/MalformedIdentifierException.java` — `buildMessage()` exists; `super(buildMessage(input))` invoked from both constructors; raw `input` field preserved only via `getInput()` for the centralised handler | ✅ **Ratified.** |
| 2 | Phase 11 §1.3 §C2 | `ProblemDetailExceptionHandlerTest.malformedId()` asserts `getMessage()` does NOT contain the raw value | `git show main:src/test/.../ProblemDetailExceptionHandlerTest.java` — test asserts `.doesNotContain(pan)`, `.doesNotContain("4242")`, `.contains("length=" + pan.length())` | ✅ **Ratified.** |
| 3 | Phase 11 §1.3 | `docs/security/pci-dss-control-mapping.md` authored as NEW deliverable, 171 LOC, 12-req PCI DSS v4.0.1 mapping | `wc -l docs/security/pci-dss-control-mapping.md` on main: 171. Spot-read of rows for Req 1.2.1, 2.2.1, 2.3, 3.2 (n/a defense), boundary control, defense-in-depth — all present with file:line traceability and explicit status (`CLOSED` / `Phase-12-deferred` / `n/a-evidenced`) | ✅ **Ratified.** |
| 4 | Phase 11 §1.2 | `logging-monitoring-pci.md` is 151 LOC; spec-side controls complete (destination, retention, immutability) | `wc -l` on main: 151. ✅ | ✅ **Ratified.** |
| 5 | Phase 11 §1.2 + §3 risk #5 | `incident-response-pci.md` is a 27-line stub | `wc -l` on main: 27. ✅ The honesty of dev's surfacing this as a gap is itself the right behaviour. | ✅ **Ratified.** (See F-AMEND-2 below.) |
| 6 | Phase 11 §1.5 §F1 | `security.yml` gating posture: Semgrep/OWASP-DC `continue-on-error: true`; Trivy `exit-code: '0'`; NOT flipped in this PR | `git show main:.github/workflows/security.yml` — confirmed advisory posture; flip path documented | ✅ **Ratified.** (See §6 PCI DSS posture below for case-study disposition.) |

**No fabricated claims observed across the sample.** Where the bundle surfaces a gap, the gap is genuine. Where the bundle marks `✅`, the artefact exists and is substantive.

---

## §4 — Findings

### F-AMEND-1 — Phase 11 §1.3 closure-summary counts: ratify with caveat

**Dev's claim (Phase 11 bundle §1.3 + §1.5 + provisional 30-review §3 F-LOW-2):** `pci-dss-control-mapping.md` closure summary = 18 CLOSED / 23 Phase-12-deferred / 7 n/a-evidenced / 1 BLOCKING-for-prod. Dev flagged this as a best-effort tally with dual-status rows.

**Consolidated verification:** Re-reading the mapping doc, multiple rows are dual-status (e.g., Req 2.2.1 is "CLOSED (config) / Phase-12-deferred (CIS benchmark)"; Req 4.2.1.1 is "CLOSED (client) / Phase-12-deferred (gateway)"; Req 6.4.1 is CLOSED for scans but the F1 gating-flip is its own carry-forward). The 49-count single-number partition is therefore an approximation, not a precise audit.

**Amendment:** **The closure-summary counts ARE NOT to be cited as audited numbers.** The mapping document itself is substantive and the per-row evidence pointers are verifiable; the single-number tally is best-treated as orientation, not as a closure inventory. For the case-study assessor: read the mapping doc per-row rather than relying on the summary. For a real-deployment QSA pass at Phase 12 (out of case-study scope): the QSA will produce their own per-row reading anyway.

**Severity:** LOW (audit-clarity).

### F-AMEND-2 — `incident-response-pci.md` stub: defer to case-study-out-of-scope (Phase 12 prep)

**Dev's claim (Phase 11 bundle §2.6 + provisional 30-review §2 Q3):** the 27-line stub is acknowledged; expansion deferred to Phase 12 tabletop prep with rationale that the operational `incident-response.md` is the canonical IR and the PCI-specific diff is small.

**Consolidated assessment:** Under the case-study scope directive, the operational `incident-response.md` (~210 LOC after Phase 10 expansion) **is** substantive. The PCI-specific diff (suspected CHD leak, key compromise, audit-sink tampering scenarios) is a 50-80 LOC focused expansion — small in absolute terms, and the cross-references to `incident-response.md` + `failure-modes-and-resilience.md §F-22` (HMAC key compromise) + `logging-monitoring-pci.md` already encode most of the substance.

**Amendment:** **The stub is acceptable for case-study completion** per `directives/2026-05-18-case-study-scope-clarification.md §2.2` (the documented procedure + cross-references constitute the design evidence; live tabletop execution is Phase-12-equivalent). For a real deployment at Phase 12, the assessor / QSA would still want the 50-80 LOC expansion before the first PCI tabletop. Tracked as Phase-12-equivalent follow-up `incident-response-pci-expansion`.

**Severity:** LOW (case-study-acceptable; real-prod follow-up).

### F-INFO-3 — HITL-gate consolidation directive: first canonical output

This document is the first canonical reviewer output under `directives/2026-05-18-hitl-gate-consolidation-protocol.md`. The dev's provisional Phase 11 30-review.md is the first dev-authored provisional verdict under that directive. The directive's "spot-verify a representative sample" protocol (Step 3.2) was applied in §3 above and validated dev's claims.

**Playbook pin for future HITL-gate reviewers:** the dev's provisional 30-review.md is the natural starting point. Read it, identify any claims that would change the verdict if untrue, spot-verify those specifically. Six samples sufficed for this consolidated review; more is appropriate for higher-risk gates (e.g., Phase-12-equivalent for a real deployment).

### F-INFO-4 — Phase 11 PR admitted source code (C2 closure)

Phase 10 30-review §C2 routed `malformed-identifier-exception-message-redaction` to Phase 11 with the rationale "Phase 10 is explicitly an evidence-only gate; a 3-LOC source change is *code*, not evidence, and belongs on a phase boundary that admits source modifications." The Phase 11 PR carried the 3-LOC change + 1 test assertion update.

**Consolidated ratification:** Phase 11's PCI evidence-gate framing IS consistent with admitting source changes that close PCI-relevant defects (security-relevant code lives here naturally). The pattern is acceptable as long as: (a) the source change closes a Phase-X-routed condition, not a new feature; (b) the bundle's coverage map records the closure with file:line traceability; (c) the test suite asserts the closure. All three are satisfied. **Ratified.**

**Playbook pin:** the per-phase "evidence-only" / "code-admitting" distinction is not binary — security-relevant patches at security gates are legitimate. The reviewer's discipline is verifying the patch is in-scope and tested.

### F-INFO-5 — Forward-motion-bias mid-review branching (third C-series instance + first phase-series instance)

The pattern of branching mid-review repeated four times across Phase 13 and Phase 10/11: C2-off-C, C3-off-C2, Phase-10-pre-staged-by-dev, Phase-11-off-Phase-10. The HITL-gate consolidation directive §2.1 legitimised this pattern — dev authoring provisional verdicts is now the default, and mid-review branching is no longer a deformation.

**Consolidated ratification:** the pattern is now **expected behaviour** under the new directive, not an exception. **Ratified.**

### F-INFO-6 — Two-instance convention bend on dev-authored 00-prompt.md (Phase 10 + Phase 11)

Per `directives/2026-05-18-phase-10-11-bulk-pass-protocol.md` §4 step 1, the reviewer was supposed to pre-stage `00-prompt.md`. Dev pre-staged both Phase 10 and Phase 11 prompts themselves. Phase 10's review §F-INFO-3 flagged this as a single-instance bend with a "future phases should follow nominal flow" expectation.

**Consolidated assessment:** the HITL-gate consolidation directive (which post-dated Phase 10's review) explicitly made dev-pre-staging the default for Phase 10 onward (§2.1 — "Dev may author all four dossier files... including 00-prompt.md"). So Phase 11's pre-stage is **not a bend** under the current governing directive — it is the protocol. The prior Phase 10 §F-INFO-3 expectation is superseded by the HITL-gate directive.

**Pin for the playbook:** when authoring `30-review.md` (or in this case, a consolidated review), check whether prior flagged "deformations" have been superseded by directives that post-dated the original review. Sticking to a stale expectation is itself a finding.

### F-INFO-7 — Pre-merge speculative-manifest convention bend recurred

Phase 11's `manifest.yml` was committed with `review_verdict: ACCEPTED_WITH_CONDITIONS`-equivalent metadata (via the `status: accepted` flip and the populated `intake_review_conditions` array) before the reviewer-authored consolidated verdict existed. Same pattern as Phase 10 (flagged at Phase 10 30-review §F-INFO-3 as "speculative manifest pre-population").

**Consolidated assessment:** under the HITL-gate directive, dev has explicit authority to flip manifest status as a mechanical state-machine transition (directive §2.1: "Status flips ... may be performed by the dev as mechanical transitions"). The reviewer's canonical verdict lives in `HITL-CONSOLIDATED-REVIEW.md` (this file). So the dev's manifest-flip is **mechanical correctness**, and the substantive verdict is in this consolidated review. **No bend.** The earlier Phase 10 F-INFO-3 expectation was authored before the HITL-gate directive and is superseded.

---

## §5 — Conditions matrix

Cumulative across Phase 10 + Phase 11 + Phase 13 (which is closed but its carry-forwards persist).

### 5.1 — Case-study state (canonical)

| # | Condition | Severity | Disposition | Routing |
|---|---|---|---|---|
| C2 | `malformed-identifier-exception-message-redaction` | LOW (defense-in-depth) | **CLOSED in Phase 11 PR** (`MalformedIdentifierException.buildMessage()` + test assertion) | — |
| C13 | `bundle-artifact-link-cosmetics` | LOW (cosmetic) | **CLOSED in Phase 10 hygiene** | — |
| C14 | `content-guard-test-nested-naming-drift` | LOW (audit-trail) | **CLOSED in Phase 10 hygiene** | — |
| F4 | `rate-revision-end-to-end-it-cache-invalidation-variant` | LOW (optional) | **DROPPED** per Phase 10 §Q1 (lower-layer invariants prove the contract) | — |
| F2 part 1 | `spectral-lint-node-setup-unconditional` | MED | **CLOSED pre-merge** in C3 hygiene commit `5ca15e6` | — |

### 5.2 — Phase-12-equivalent (case-study-out-of-scope; would be addressed before real production deploy)

Per `directives/2026-05-18-case-study-scope-clarification.md` §2.2, each item below is **acceptable for case-study completion** because the documented procedure / template / design exists. The "Phase-12-equivalent" label means: this is what a real production cutover would close; it is not what case-study completion requires.

| # | Condition | Severity | Case-study evidence | Phase-12-equivalent closure |
|---|---|---|---|---|
| C1 | `idempotency-key-blocking-for-prod` (F-15) | HIGH | `failure-modes-and-resilience.md §F-15` documents the gap explicitly + the implementation pattern (`Idempotency-Key` header + per-key request-hash table + `409 IDEMPOTENCY_KEY_REUSED` semantics) | Implementation; ~600 LOC. Phase 12 signatory adjudicates implement / waive-with-mitigation / accept-as-known-risk. |
| C3 | `oasdiff-vs-live-oas-pending-mvn` | MED | `.spectral.yaml` rules complete + `infra/openapi/baseline.yaml` baseline complete + `ci.yml::oasdiff` shape correct; baseline-parse-only fallback documented + PR-review-checklist mitigation | Maven-in-runner provisioning enables live OAS regeneration; live-vs-baseline diff replaces the self-diff tautology. |
| C4 | `audit-log-destination-provisioning` | MED | `logging-monitoring-pci.md` complete (retention 1y / 3mo online / WORM / signed); destination spec sink-agnostic | Sink choice (CloudWatch / Splunk / Loki / etc.); AOC; first audit-write smoke test in staging. |
| F1 | `security-workflow-gating-activation` | MED | `security.yml` 5-job scan suite installed; `vulnerability-management-pci.md` baseline-scan + triage + flip + validate procedure documented | Baseline scan on `main`; triage HIGH/CRITICAL findings; flip `continue-on-error: true → false` (Semgrep + OWASP-DC) and `exit-code: '0' → '1'` (Trivy); verify next PR triggers blocking behaviour. |
| F3 | `deploy-workflow-marker-freshness-check` | LOW | `deploy-prod.yml` marker-presence + token + strict-ops-check + strict-pci-check gates enforced; GitHub Environment `production` adds named-reviewer sign-off | Add `git log -1 --format=%ct HEAD` vs `stat -c %Y .human-approvals/pci-production-approved.txt` check (~10 LOC). |
| C5 | `load-test-execution-against-capacity-anchors` | MED | `capacity-scalability-plan.md §5` documents the k6 + WireMock-Treasury procedure; SLO targets pinned in `slo-sli.md §3` | k6 execution in staging against per-replica + cluster anchors; capacity-plan ratification. |
| C6 | `cb-calibration-drill-log` | MED | `docs/operations/drills/TEMPLATE-drill.md` complete (Pre-drill state → Injection → Detection → Response → Resolution → Findings → Action items → Lessons → Drill cost); F-01 scenario documented in `failure-modes-and-resilience.md` | First drill execution in staging; drill log at `docs/operations/drills/YYYY-MM-DD-treasury-cb-calibration.md`. |
| C7 | `class-a-rollback-rehearsal-log` | MED | `rollback-plan.md §4.2` documents Class-A code-rollback procedure; smoke-test set in §4.1 | First rehearsal in staging; drill log. |
| C8 | `class-b-rollback-rehearsal-log` | LOW | `rollback-plan.md §4.3` documents Class-B config-rollback procedure | First rehearsal in staging; drill log. |
| C9 | `first-quarterly-tabletop-drill-log` | LOW | `incident-response.md §5` documents drill cadence + PIR template | First quarterly tabletop; drill log. |
| C10 | `pagerduty-integration-provisioned` | MED | `monitoring-alerting.md §3` documents alert conditions + paging policy + routing config | Platform-team task: PagerDuty integration + first synthetic page test. |
| C11 | `oncall-rotation-real-names-populated` | LOW | `oncall-escalation.md §2` documents rotation structure + escalation tree (E1–E4) | Real team formation; populate names + contact tokens. |
| **Req 8 (OQ-010)** | `identity-origin-blocking-for-prod` | HIGH | `access-control-pci.md` documents the gateway-bound identity model; Req 8.2.1 explicitly flagged as BLOCKING-for-prod in `pci-dss-control-mapping.md` | Gateway provisioning + identity-passing contract (`X-Request-Identity` header) + verification at service boundary; Phase 12 signatory adjudicates. |
| **Phase 12 PCI hand-off (cumulative)** | First quarterly access review, ASV + pen-test vendor engagements, TPSP AOCs, HMAC key rotation drill, audit-log time-sync evidence, evidence-register consolidation | mixed | Each has a documented procedure / spec | Real-production execution; out of case-study scope. |
| `pci-dss-control-mapping-qsa-validation` | LOW (process) | dev-authored 12-req mapping with per-row file:line traceability | Phase 12 QSA engagement; QSA produces canonical per-row reading. |

**Total Phase-12-equivalent items: 19** (case-study-out-of-scope; each has documented procedure / template / design as gate-passing evidence).

### 5.3 — Not pursued at any phase

- `13-POST-idempotency-key` chunk to implement F-15 — explicitly **ratified as not required for case-study completion** per case-study scope directive §4. The documented gap + the implementation pattern is the case-study evidence.

---

## §6 — PCI DSS v4.0.1 posture

A dedicated section per HITL-gate directive Step 3.3 §6. This summarises the PCI posture for the case-study assessor; for a real-deployment QSA pass (out of case-study scope), the assessor would produce an independent reading.

### 6.1 — Scope posture

The service is **out of CDE** per `docs/security/pci-scope-and-cde.md` and `docs/security/cardholder-data-flow.md`. PAN never crosses the trust boundary by design — the boundary `ContentGuard` (`src/main/java/com/example/purchaseconversion/api/advice/ContentGuard.java`) enforces no-PAN via Luhn check + NFKC normalisation + track-data detection + encoded-PAN detection (AC-010b/c/d/e). The audit destination is categorised "connected-to"; the service is "connected-to" not CDE. Out-of-CDE posture significantly reduces the case-study's regulatory surface and is appropriate for the FX-conversion use case.

### 6.2 — 12-requirement coverage

`docs/security/pci-dss-control-mapping.md` is the canonical reference. Per the F-AMEND-1 caveat, the single-number tally (18 CLOSED / 23 Phase-12-deferred / 7 n/a-evidenced / 1 BLOCKING-for-prod) is best-treated as orientation, not as audited closure counts. The per-row evidence is verifiable.

**Particularly strong evidence rows:**

- Req 2.3 — refuse-to-start on missing HMAC key (`DescriptionHasher` ctor throws); tested by `LoggingHashKeyStartupTest`.
- Req 3.2 — account-data storage prohibited (schema has no PAN-typed columns); `description` is varchar(255) with boundary content-guard.
- Boundary control — `ContentGuard.@Nested PanLuhn / Track / Encoded / Nfkc` covers PAN-shaped input rejection.
- PII redaction on log — `LoggingPiiGuardTest.@Nested LocalProfile / CiProfile / CrossLevel / Fullwidth-confusable` proves PAN never lands in logs.
- Defense-in-depth on exception — `MalformedIdentifierException.buildMessage()` Phase-11 closure (this PR).

**Particularly weak rows (and dispositions):**

- Req 8.2.1 — identity origin (BLOCKING-for-prod; gateway-bound). Case-study-out-of-scope per the gateway provisioning being Phase-12-equivalent. The implementation pattern is documented; the gap is honest.
- Req 11.3.1 — pen test executed at least annually; vendor engagement deferred to Phase 12. Case-study-out-of-scope.
- Req 12.10.1 — incident response plan tested; first PCI tabletop deferred to Phase 12. Case-study-out-of-scope (the operational `incident-response.md` is substantive at ~210 LOC).

### 6.3 — Cryptographic posture

`encryption-key-management.md` documents:
- TLS 1.3 preferred / TLS 1.2 minimum (NFR-011/012; Phase-4 G4-P1-12).
- HMAC key (`wex.log.hash.key`) ≥ 256 bits; refuse-to-start in prod profile if absent.
- Key rotation procedure with `vN:` prefix maintaining cross-version correlation.
- First rotation drill deferred to Phase 12 (case-study-out-of-scope).

### 6.4 — SDLC posture

`secure-sdlc-pci.md` + the Phase-13 chunk PR pattern (PR #2–#11) provide the SDLC evidence. Every PR has a reviewer `30-review.md` + green CI run before merge; STATUS.md is the audit ledger. The chunked-PR + per-PR-reviewer pattern IS the change-control evidence for `docs/security/change-control-pci.md §1`.

For Phase 10 + Phase 11, the HITL-gate consolidation directive replaced per-PR reviewer 30-review.md files with this consolidated review; the change-control evidence shifts to (a) dev-authored provisional 30-review.md files + (b) this consolidated review as the audit trail. This is **acceptable** for case-study completion but should be documented for any future QSA pass — see §7 below.

---

## §7 — What the assessor (HITL audience) should weigh

For the case-study assessor reading this dossier, here is the plain-language summary of what's settled, what's deferred, and what's open.

### 7.1 — Settled (the team has demonstrated production-deployability)

- **Architecture.** ADR-0001 (core architecture) + component design + deployment architecture + PCI scope diagrams + threat model. All present, traceable, and grilled (Phase 4 + Phase 7 grills are in the dossier).
- **Implementation.** Java 21 / Spring Boot 3.x service with hexagonal architecture (ports + adapters), ArchUnit fitness functions, JaCoCo coverage + Pitest mutation testing gates, idempotent Liquibase migrations, structured observability instrumentation. Test suite passes; CI green at every Phase-13 merge.
- **Operations.** SLO/SLI definitions (11 SLIs, 11 SLOs with Treasury-uptime ceiling formula); 3 Grafana SLO dashboard JSONs; runbooks; rollback-plan with 6 classes; incident-response process (~210 LOC); drill template; on-call escalation structure.
- **Security.** PCI scope analysis (out-of-CDE); PAN handling (boundary content-guard + no-storage); HMAC-redacted logging; refuse-to-start invariant on missing secrets; vulnerability scanners installed (Trivy + OWASP-DC + Semgrep + GitLeaks); SBOM generation (CycloneDX, Maven-paired); deploy-prod marker-gate correctly enforcing PCI production approval.
- **Change control.** Per-PR reviewer-authored 30-review.md files for every Phase-13 chunk; dossier audit trail; STATUS.md ledger; `change-control-pci.md` procedure documented.

### 7.2 — Deferred (case-study-out-of-scope; would be addressed at real production cutover)

The 19 Phase-12-equivalent items in §5.2 above. Each has a documented procedure / template / design. The team has demonstrated knowledge of what closing each item would require; the case study does not require executing them because no real production infrastructure exists.

For the assessor: these are not gaps in the case study — they are the production-cutover punch list that any team would carry into real cutover.

### 7.3 — Open (judgment calls the assessor may want to weigh)

1. **F-15 Idempotency-Key (BLOCKING-for-prod).** The team flagged this gap, documented the implementation pattern, and deferred implementation to Phase 12 signatory adjudication rather than speculatively implementing 600 LOC for the case study. This demonstrates judgment — for a real deployment, the multi-party signatory chain would adjudicate (a) implement now / (b) waive with mitigation / (c) accept as known risk. The assessor may have a view on whether the deferral is correct case-study judgment or whether the team should have demonstrated implementation.

2. **Req 8 identity origin (BLOCKING-for-prod).** Same shape — gateway provisioning is Phase-12-equivalent; the implementation pattern is documented. Assessor may have a view.

3. **F1 security-workflow gating not yet flipped to blocking.** The team documented the baseline-scan + triage + flip + validate protocol but did not execute it (no live infrastructure to scan against). For a real deployment, this is a Phase 12 staging-shakedown task; for the case study, it demonstrates discipline (avoiding speculative gating flips that would block all future PRs on unknown CVE baseline).

4. **`incident-response-pci.md` is a 27-line stub.** The operational `incident-response.md` is substantive at ~210 LOC; the PCI-specific diff is small (50-80 LOC for CHD-leak / key-compromise / audit-sink-tampering scenarios). The team chose to defer the diff to Phase 12 tabletop prep. The assessor may want to weigh whether 27 lines is sufficient case-study evidence.

5. **HITL-gate consolidation directive activation.** Phase 10 used the per-phase reviewer protocol (reviewer-authored 30-review.md); Phase 11 used the new consolidated protocol (dev-authored provisional + this consolidated review). For a real-deployment QSA pass, the latter would benefit from supplementary per-phase change-control evidence; the team has not produced that yet. For the case study, the dossier audit trail is intact.

### 7.4 — What this is NOT

- This is not a QSA audit. A real QSA pass at Phase 12 (out of case-study scope) would produce its own independent reading of the PCI DSS control mapping and may disagree with dev's per-row dispositions (particularly on n/a-evidence sufficiency for Req 3 and on Phase-12-deferred items they consider implementable today).
- This is not a substitute for the multi-party signature procedure at `docs/security/change-control-pci.md §1`. That procedure (Architect + SRE + Security + Compliance signatures) governs the real `.human-approvals/pci-production-approved.txt` marker. For the case study, the marker is not created; the procedure is documented and demonstrated.
- This is not a production deployment. `deploy-prod.yml` correctly remains marker-gated; no real deployment will occur.

---

## §8 — Reviewer concluding notes

The team has produced a **substantive, internally-coherent, evidence-defensible** case-study dossier demonstrating production-deployability for a PCI Tier 1 enterprise FX-conversion service. The architecture is sound, the implementation discipline is high, the operational artifacts cover the bar, and the PCI posture is honest about what closes today vs what would close at real-production cutover.

The dossier discipline — explicit conditions tracking, honest BLOCKER routing, forward-motion-bias with explicit risk-documentation, per-PR reviewer audit trail through Phase 13, dev-authored provisional verdicts under the HITL-gate consolidation directive — is the strongest dimension of the case study. It demonstrates not just the artifact-level production-deployability but also the process-level production-deployability (a team that operates this way would survive a real enterprise SDLC review).

The two `BLOCKING-for-prod` flags (F-15 Idempotency-Key, Req 8 identity origin) are correctly preserved rather than speculatively closed. Judgment over feature-completeness is the right trade-off for case-study scope.

**Verdict re-stated: READY FOR CASE-STUDY HITL WITH FINDINGS.** All findings (§4 above) are non-blocking under case-study scope; the dossier is ready for the assessor's evaluation.

End of `HITL-CONSOLIDATED-REVIEW.md`.
