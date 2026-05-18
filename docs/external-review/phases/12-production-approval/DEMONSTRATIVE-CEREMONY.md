# DEMONSTRATIVE-CEREMONY — Phase 12 Production Approval (Case-Study)

**Authored:** 2026-05-18 — by external governance reviewer.
**Scope:** Case-study demonstrative per `directives/2026-05-18-case-study-scope-clarification.md` §2.3. **No real production deployment will occur.**
**Inputs:** `HITL-CONSOLIDATED-REVIEW.md`, the Phase 10 + 11 dossiers, `docs/security/change-control-pci.md`, `docs/security/pci-production-readiness-gate.md`, `docs/security/pci-dss-control-mapping.md`.

This document records what each of the 4 multi-party signatory roles would review and how they would conclude under case-study terminal posture. The reviewer **does not sign on behalf of any role**; the records below describe each role's evaluation surface + question-set + the case-study disposition. The user (acting as Architect) reviews this document, may amend, and then optionally closes the ceremony per `00-prompt.md` §5.

---

## §1 — Ceremony framing

Per `docs/security/change-control-pci.md §1` + `docs/security/pci-production-readiness-gate.md`, a real production deployment requires:

| Approval row | Role | Case-study mapping |
|---|---|---|
| Security architecture approval | SecArch | Demonstrative |
| PCI/QSA-style review | Compliance (or QSA proxy) | Demonstrative |
| ROC/AOC path confirmed | Compliance | Demonstrative |
| ASV scan plan/evidence | SecArch | Demonstrative |
| Pen-test/segmentation test plan/evidence | SecArch | Demonstrative |
| Operational readiness | SRE | Demonstrative |
| Release readiness | Architect | **User (Sajith)** |

Plus the case-study-specific constraint: the marker file at `.human-approvals/pci-production-approved.txt`, if created, must be annotated `CASE STUDY DEMONSTRATIVE — NOT VALID FOR REAL PRODUCTION DEPLOYMENT` per case-study scope directive §2.3.

The two `BLOCKING-for-prod` flags from `HITL-CONSOLIDATED-REVIEW.md §5.2` require per-role adjudication and are recorded in §5 below.

---

## §2 — Architect (Release readiness)

### 2.1 — Evaluation surface

The Architect reviews:

- **Architecture coherence.** ADR-0001 + component design + deployment architecture + Phase 4 + Phase 7 grills. Verifies hexagonal architecture is sound, ports/adapters are tested, the rate-orientation contract check is present, and the Treasury single-flight pattern is correctly implemented.
- **Implementation discipline.** Phase 13 chunk PRs (PR #2–#11) each landed with a reviewer-authored `30-review.md`; ArchUnit fitness functions enforce hexagonal boundaries; JaCoCo + Pitest gates enforce coverage + mutation thresholds.
- **Release readiness.** Rollback plan (6 classes, A–F); deploy-prod workflow marker-gate; smoke-test set in `rollback-plan.md §4.1`; release-notes template.
- **Dossier audit trail.** STATUS.md, every `chunks/13-*/30-review.md`, the HITL consolidated review.

### 2.2 — Architect's question-set (what they ask)

1. Does the architecture survive the Phase 4 + Phase 7 adversarial grills without unaddressed findings? **Answer:** Yes — all grill findings either closed in Phase 13 chunks or routed forward with documented disposition.
2. Are the chunked-PR + reviewer-`30-review.md` audit trail records sufficient for a real-deployment change-control review? **Answer:** Yes for case study; for real deployment, the Phase 10/11 HITL-gate consolidation directive supplements rather than supersedes the per-PR records (HITL-CONSOLIDATED-REVIEW §4 F-INFO-3 records this).
3. Is the rollback plan executable? **Answer:** Procedures documented for all 6 classes; rehearsal evidence pending (Phase-12-equivalent for real deploy; case-study-out-of-scope per directive §2.2).
4. Are the 2 BLOCKING-for-prod flags acceptable for the Architect to sign? **Answer:** See §5 below.

### 2.3 — Case-study conclusion (Architect — user-owned)

> *(Drafted by reviewer based on the user's §6.1 + §6.2 signatures; user may amend in-place.)*
>
> **Position:** **SIGN-DEMONSTRATIVE under case-study scope; the dossier demonstrates production-deployability for the WEX FX-conversion service.**
>
> **Rationale:** The cumulative state across Phase 13 (9 chunks, 11 PRs, per-PR reviewer-authored `30-review.md` audit trail), Phase 10 (operational readiness — coverage map across all 12 ops source docs; 3 Grafana dashboards; drill template; incident-response process), and Phase 11 (PCI security readiness — 25 security source docs covered; new `pci-dss-control-mapping.md` with 12-req v4.0.1 mapping; C2 defense-in-depth closure on `MalformedIdentifierException.getMessage()`) constitutes a substantive, internally-coherent, evidence-defensible case-study dossier. The HITL-CONSOLIDATED-REVIEW.md verdict (READY FOR CASE-STUDY HITL WITH FINDINGS) is canonical reviewer-authored verification of the dev-provisional verdicts, with 5 conditions closed and 19 Phase-12-equivalent items correctly recorded as case-study-out-of-scope per `directives/2026-05-18-case-study-scope-clarification.md §2.2`. The case-study scope clarifies that the documented procedure / template / design is the gate-passing evidence; live execution is not required. The team has demonstrated production-deployability across architecture, implementation, operations, security, and change-control discipline — survivable scrutiny under enterprise SDLC review.
>
> **Demonstrative-sign decision:** [x] **Sign with reservations** (the two BLOCKING-for-prod flags below; non-blocking under case-study scope, gating for a real deploy).
>
> **Reservations (preserved for any real-deployment activation; case-study-acceptable):**
>
> 1. **F-15 Idempotency-Key (HIGH compliance posture)** — adjudicated in §6.1 with the Architect's signature. For case-study scope, the documented gap + implementation pattern is sufficient evidence of judgment over feature-completeness. For real deploy, the 4-role signatory chain (re-engaging at that time) must explicitly choose option (a) implement / (b) waive-with-mitigation / (c) accept-as-known-risk before marker creation.
> 2. **Req 8 identity origin OQ-010 (HIGH regulatory posture)** — adjudicated in §6.2 with the Architect's signature. The demonstrative consensus across SRE / Security / Compliance (§6.2) is option (c) explicit BLOCKING-for-real-prod; gateway provisioning + identity-passing contract verification is a hard prerequisite for any real production deploy. Case-study-acceptable because the gateway is Phase-12-equivalent infrastructure that does not exist pre-cutover.
> 3. **The 19 Phase-12-equivalent items** in `HITL-CONSOLIDATED-REVIEW.md §5.2` — accepted as case-study-out-of-scope; each has a documented procedure / template / design as the gate-passing evidence. If a real deployment is initiated at any future point, the 19 items become the production-cutover punch list and must be addressed before real marker creation.
>
> **Signed (case-study Architect role):** Sajith Mankavil — 2026-05-18.

---

## §3 — SRE (Operational readiness)

### 3.1 — Evaluation surface

The SRE reviews:

- **SLOs + SLIs.** `slo-sli.md` defines 11 SLIs + 11 SLOs with the Treasury-uptime ceiling formula. Targets ratified at Phase 5.
- **Dashboards.** 3 Grafana JSON templates (`infra/dashboards/slo-availability.json`, `slo-latency.json`, `treasury-dependency.json`) — panels match SLO targets; fast-burn/slow-burn thresholds at industry-standard 14.4×/5×.
- **Alerts + paging.** `monitoring-alerting.md` defines alert conditions + paging policy. PagerDuty integration is Phase-12-equivalent (case-study-out-of-scope).
- **Runbooks.** `runbook.md` (25 alert-specific playbooks §6.1–§6.25) + `incident-response.md` (~210 LOC: detect → triage → mitigate → communicate → resolve → PIR) + `oncall-escalation.md` (severity ladder + escalation tree E1–E4).
- **Failure modes.** `failure-modes-and-resilience.md` enumerates F-01 through F-27 with detection signal + auto-mitigation. Single-flight gate, CB calibration, graceful shutdown all implemented.
- **Capacity.** `capacity-scalability-plan.md` per-replica + cluster + 12-month growth anchors; DB pool sized 20 per replica; bulkhead 10 concurrent Treasury calls.

### 3.2 — SRE's question-set

1. Can a new on-call engineer run this service in production? **Answer:** Yes given the runbook + escalation tree + 25 alert playbooks. Real on-call rotation names + paging tokens are Phase-12-equivalent.
2. What's the SEV1 detection-to-mitigation budget? **Answer:** 5 min ack + 15 min mitigation start, per `incident-response.md §2`. Validated by drill at Phase-12-equivalent (no real drills executed pre-cutover; case-study-out-of-scope).
3. Is the rollback plan rehearsed? **Answer:** Procedures documented; rehearsals are Phase-12-equivalent.
4. Is the capacity plan validated? **Answer:** Theoretical; load-test execution is Phase-12-equivalent.
5. Are there sufficient drill logs? **Answer:** No drill execution pre-cutover; drill TEMPLATE provided at `docs/operations/drills/TEMPLATE-drill.md`; first execution would happen at staging cutover (Phase-12-equivalent).

### 3.3 — Case-study conclusion (SRE — demonstrative)

> **Demonstrative position:** A real SRE reviewing this dossier would likely conclude **READY-WITH-RESERVATIONS** for a real-deployment signoff, with reservations being the unexecuted drills + load-test + PagerDuty integration. For case-study purposes, these are correctly out-of-scope; the SRE would acknowledge the procedural readiness and defer the execution work to staging cutover.
>
> **Demonstrative-sign disposition:** SIGN-DEMONSTRATIVE under case-study scope.

---

## §4 — Security (SecArch — owns 3 approval rows)

### 4.1 — Evaluation surface

The SecArch reviews:

- **Security architecture approval row.**
  - PCI scope analysis (`pci-scope-and-cde.md`) — out-of-CDE posture.
  - Cardholder-data flow (`cardholder-data-flow.md`) — PAN never crosses trust boundary.
  - Boundary `ContentGuard` (Luhn + NFKC + track-data + encoded-PAN) — `ContentGuardTest.@Nested PanLuhn/Track/Encoded/Nfkc`.
  - HMAC-redacted logging (`DescriptionHasher` + `LoggingPiiGuardTest`).
  - Defense-in-depth on `MalformedIdentifierException.getMessage()` — Phase 11 C2 closure.
  - Refuse-to-start invariant on missing HMAC key — F-18 + `LoggingHashKeyStartupTest`.
  - Threat model (Phase 4 + Phase 7 grills).
- **ASV scan plan/evidence row.** `asv-scan-plan.md` documents vendor selection (Phase-12-equivalent). No scan executed pre-cutover.
- **Pen-test/segmentation test plan/evidence row.** `penetration-test-plan.md` + `network-segmentation.md` document the plans. No execution pre-cutover (Phase-12-equivalent).
- **Vulnerability management.** `vulnerability-management-pci.md` SLA: HIGH 7d / CRITICAL 24h. Scanners installed: Trivy + OWASP-DC + Semgrep + GitLeaks (`security.yml` 5 jobs). **F1 — gating not yet flipped to blocking on HIGH/CRITICAL.**

### 4.2 — SecArch's question-set

1. Is PAN handled correctly throughout? **Answer:** Out-of-CDE by design; boundary content-guard + no-storage in schema + redacted logging + defense-in-depth on exception messages. Strong evidence.
2. Are secrets handled correctly? **Answer:** HMAC key required at startup; refuse-to-start if absent in prod profile; never logged; per `secrets-policy.md`.
3. Is the vulnerability scan gating enforced? **Answer:** Scanners installed and emitting SARIF + reports; gating flip from advisory to blocking is Phase-12-equivalent (F1 carry-forward). The flip should be done at staging shakedown AFTER a baseline-scan + triage step to avoid blocking all PRs on existing CVEs.
4. Are TLS minimums enforced? **Answer:** TLS 1.3 preferred / TLS 1.2 minimum; pinned CA bundle; hostname verification (NFR-011/012; Phase-4 G4-P1-12).
5. Is Req 8 identity origin (OQ-010 BLOCKING-for-prod) acceptable? **Answer:** See §5 below.

### 4.3 — Case-study conclusion (Security — demonstrative)

> **Demonstrative position:** A real SecArch reviewing this dossier would likely conclude **READY-WITH-RESERVATIONS** for a real-deployment signoff. Reservations:
> - F1 gating flip should land in PR before real-deploy authorization.
> - Req 8 identity origin must be resolved (gateway provisioning + identity-passing contract) before real-deploy authorization.
> - ASV scan + pen-test must execute against the actual deployed staging environment.
>
> For case-study purposes, the architectural + implementation evidence is strong (boundary controls, redaction, refuse-to-start, scanners installed). The execution gaps are Phase-12-equivalent.
>
> **Demonstrative-sign disposition:** SIGN-DEMONSTRATIVE under case-study scope; would WITHHOLD-SIGN for a real deploy pending F1 flip + Req 8 resolution + ASV/pen-test execution.

---

## §5 — Compliance (PCI/QSA-style review + ROC/AOC path)

### 5.1 — Evaluation surface

The Compliance reviewer (or QSA proxy) reads:

- **PCI DSS v4.0.1 control mapping** (`pci-dss-control-mapping.md`) — 171 LOC, 12-req coverage. Closure-summary 18 CLOSED / 23 Phase-12-deferred / 7 n/a-evidenced / 1 BLOCKING-for-prod. (Per HITL-CONSOLIDATED-REVIEW §4 F-AMEND-1, the single-number tally is best-treated as orientation; per-row evidence is verifiable.)
- **Evidence register** (`evidence-register.md`, 51 LOC) — per-control evidence pointers.
- **PCI security grill** (`pci-security-grill.md`) — adversarial review of the PCI posture (Phase 7).
- **PCI scope** (`pci-scope-and-cde.md`) — out-of-CDE posture rationale.
- **Change control** (`change-control-pci.md`) — PR template + categories + audit trail.
- **Third-party service providers** (`third-party-service-provider-pci.md`) — Treasury + cloud + telemetry vendor AOCs (Phase-12-equivalent for collection).
- **Compensating controls** (`compensating-controls.md`) — none required at v1 (out-of-CDE).

### 5.2 — Compliance's question-set

1. Is the PCI DSS v4.0.1 mapping defensible against QSA scrutiny? **Answer:** Substantive 12-req coverage with file:line traceability. QSA at real engagement may disagree on n/a-evidence sufficiency for Req 3 + on Phase-12-deferred items they consider implementable today. Documented in `pci-dss-control-mapping.md` as dev-authored (F-LOW-1 in Phase 11 provisional 30-review).
2. Is the ROC/AOC path documented? **Answer:** `qsa-roc-readiness.md` documents the path; vendor engagement is Phase-12-equivalent.
3. Are the change-control records sufficient as audit trail? **Answer:** Per-PR `30-review.md` audit trail through Phase 13 + STATUS.md ledger + HITL-CONSOLIDATED-REVIEW.md. Per HITL-gate consolidation directive activation, Phase 10/11 use consolidated rather than per-phase reviewer records; for real-deployment QSA pass, supplementary per-phase evidence may be requested.
4. Is the audit-log destination posture acceptable? **Answer:** Spec-side complete (`logging-monitoring-pci.md`: 1y retention / 3mo online / WORM / signed); destination provisioning is Phase-12-equivalent.
5. Are the 2 BLOCKING-for-prod flags adjudicated? **Answer:** Documented; awaiting demonstrative adjudication below.

### 5.3 — Case-study conclusion (Compliance — demonstrative)

> **Demonstrative position:** A real QSA / Compliance reviewer would conclude **READY-WITH-RESERVATIONS** for case-study purposes and **NOT-READY** for real deploy. For real deploy, the dossier needs (a) QSA-validated PCI DSS mapping, (b) AOC collection for TPSPs, (c) ASV + pen-test execution evidence, (d) audit-log destination provisioned with WORM evidence.
>
> For case-study scope, the procedural readiness is sufficient: the documented procedures + designs + specs demonstrate the team would meet a real QSA engagement bar if the prerequisites were in place.
>
> **Demonstrative-sign disposition:** SIGN-DEMONSTRATIVE under case-study scope.

---

## §6 — `BLOCKING-for-prod` adjudications

Two flags require explicit demonstrative adjudication per Phase 12 `00-prompt.md` §5 step 2.

### 6.1 — F-15 Idempotency-Key (HIGH compliance posture)

**Source:** `failure-modes-and-resilience.md §F-15` + Phase 10 30-review §C1 + HITL-CONSOLIDATED-REVIEW §5.2.

**Hazard:** duplicate `POST /api/v1/purchases` requests under network retry produce duplicate purchases (AC-001b documents the behaviour).

**Implementation pattern documented:** `Idempotency-Key` header + per-key request-hash storage table + `409 IDEMPOTENCY_KEY_REUSED` semantics + IT. ~600 LOC.

**Adjudication options:**
- (a) Implement now — open `chunks/13-POST-idempotency-key/`; ~600 LOC.
- (b) Waive with documented mitigation — client-side retry-with-deduplication + idempotency contract documented at gateway boundary.
- (c) Accept as known risk — document for first 90-day post-cutover monitoring; first dup-incident triggers (a).

**Case-study demonstrative position (per HITL-CONSOLIDATED-REVIEW §7.3 #1):** ratified under case-study scope as (b) or (c); the team has demonstrated judgment by surfacing the gap with the implementation pattern documented rather than speculatively implementing 600 LOC for the case study. For real deploy, the 4-role signatories would adjudicate; case-study assessor weighs whether deferral is correct.

> **Architect's adjudication (user-owned):** __Sajith Mankavil_______________
>
> **SRE's demonstrative adjudication:** prefers (a) or (c) — (b) shifts the burden to client correctness which is harder to validate at the service boundary.
>
> **Security's demonstrative adjudication:** indifferent — F-15 is not a PCI control; it's an operational correctness invariant.
>
> **Compliance's demonstrative adjudication:** indifferent unless duplicates would create CHD-relevant records (they don't — `description` is the only carry-through field and is content-guarded).

### 6.2 — Req 8 identity origin OQ-010 (HIGH regulatory posture)

**Source:** `pci-dss-control-mapping.md Req 8.2.1` + Phase 11 bundle §3 risk #1 + HITL-CONSOLIDATED-REVIEW §5.2.

**Hazard:** the service has no app-layer authentication. Identity is established at the gateway. Until the gateway is provisioned + the identity-passing contract (`X-Request-Identity` header or similar) is implemented + verified at the service boundary, Req 8.2.1 cannot be closed.

**Implementation pattern documented:** `access-control-pci.md` documents the gateway-bound identity model.

**Adjudication options:**
- (a) Implement gateway binding — provision gateway + define identity-passing contract + verify at boundary. Phase-12-equivalent (no gateway exists pre-cutover).
- (b) Waive pending platform provisioning — document the gap; real-deploy authorization conditional on (a) at platform-team provisioning time.
- (c) Accept as Phase-12-prerequisite — gating real-deploy on (a); explicit BLOCKING for real-prod.

**Case-study demonstrative position:** ratified under case-study scope as (b) or (c) — gateway provisioning is a platform-team task that cannot be executed pre-cutover. The documented identity model in `access-control-pci.md` is sufficient case-study evidence.

> **Architect's adjudication (user-owned):** ______Sajith Mankavil___________
>
> **SRE's demonstrative adjudication:** (c) — explicit BLOCKING-for-real-prod; the gateway provisioning is non-trivial and identity-passing contract must be verified end-to-end.
>
> **Security's demonstrative adjudication:** (c) — Req 8 is a hard PCI requirement; gateway must be provisioned and identity contract verified before any real deploy.
>
> **Compliance's demonstrative adjudication:** (c) — Req 8.2.1 is non-waivable; gateway provisioning is the prerequisite.

---

## §7 — Demonstrative marker disposition

If the user (acting as Architect) closes the ceremony, the optional next step per `00-prompt.md` §5 step 5 is to create the demonstrative marker file. **The reviewer cannot create this file.**

**Path:** `.human-approvals/pci-production-approved.txt`

**Required content (per case-study scope directive §2.3):**

```
CASE STUDY DEMONSTRATIVE — NOT VALID FOR REAL PRODUCTION DEPLOYMENT

APPROVED_FOR_PCI_PRODUCTION_RELEASE

Demonstrative signatures (case-study; not legally binding):
  Architect: <user signature>     <date>
  SRE: demonstrative              <date>
  Security: demonstrative         <date>
  Compliance: demonstrative       <date>

Inputs:
  - docs/external-review/HITL-CONSOLIDATED-REVIEW.md
  - docs/external-review/phases/12-production-approval/DEMONSTRATIVE-CEREMONY.md

Reservations:
  - F-15 Idempotency-Key — adjudicated as <option>; see DEMONSTRATIVE-CEREMONY §6.1
  - Req 8 identity origin (OQ-010) — adjudicated as <option>; see §6.2
  - 19 Phase-12-equivalent items — case-study-out-of-scope per directives/2026-05-18-case-study-scope-clarification.md §2.2
```

**Effect on `deploy-prod.yml`:** the workflow's marker-gate would treat this file as present + token-valid + flip strict-mode-passing. However, **no real production infrastructure exists**; the deploy step is a stub (`echo "would deploy <sha> to prod"`). No real deployment occurs.

For the case study, creating this file is **optional** — the ceremony record in this document is sufficient evidence that the team would meet the bar of a real multi-party signature procedure. The marker file's primary purpose is to ungate `deploy-prod.yml`, which has no real production target.

---

## §8 — Phase 12 closure checklist (user-owned)

When the user is ready to close the demonstrative ceremony:

- [ ] §2.3 Architect's conclusion filled in (user-owned).
- [ ] §6.1 + §6.2 Architect's adjudications filled in (user-owned).
- [ ] (Optional) `.human-approvals/pci-production-approved.txt` created per §7 (user-only; reviewer policy-denied).
- [ ] (Optional) `docs/security/pci-production-readiness-gate.md` updated from `Status: BLOCKED` → `Status: CASE_STUDY_DEMONSTRATIVE_APPROVAL` with 7 approval rows populated.
- [ ] `manifest.yml::status` flipped `prompt_received → ceremony_recorded → complete`.
- [ ] STATUS.md updated to reflect ceremony closure (reviewer can do this once user confirms).

End of `DEMONSTRATIVE-CEREMONY.md`.
