# 20-bundle — Phase 11 (PCI Security Readiness Gate)

**Author:** Dev agent
**Date:** 2026-05-18
**Phase status flip:** `bundling → bundle_posted` on the same commit as this file lands.
**Scope:** Every requirement enumerated in [`00-prompt.md`](00-prompt.md) §2, plus the 5 Phase-10 + C3 carry-forwards routed in via [`manifest.yml::intake_review_conditions`](manifest.yml).

---

## 1 — Coverage map

Status legend: ✅ artefact present + meets bar; ⚠️ artefact present but with known limitation; `BLOCKER: <reason>` requires Phase 12 / vendor / platform infrastructure unavailable pre-Phase-12.

### 1.1 — Core five PCI source docs (directive §6 minimum)

| Source doc | Requirement scope | Delivered artefact | Status |
|---|---|---|---|
| [`pci-scope-and-cde.md`](../../../security/pci-scope-and-cde.md) | CDE vs connected-to categorization; cardholder-data flow diagram | 116-line doc, Phase-7 ratified; out-of-CDE posture established | ✅ |
| [`change-control-pci.md`](../../../security/change-control-pci.md) | Per-PR change-id audit trail; reviewer approval; CI green required | 104-line doc; **evidence:** every Phase-13 chunk PR (#2–#11) carries a reviewer `30-review.md` + green CI run before merge; STATUS.md ledger is the audit trail | ✅ |
| [`logging-monitoring-pci.md`](../../../security/logging-monitoring-pci.md) | Audit-log destination, immutability, retention, access control | 151-line doc; destination spec ratified at Phase 7 | ✅ (spec); destination provisioning → **C4 below** |
| [`encryption-key-management.md`](../../../security/encryption-key-management.md) | TLS minimums; HMAC key rotation | 128-line doc; HMAC rotation procedure documented | ✅ (spec); first rotation drill → Phase 11 tabletop (see Decisions §2.5) |
| [`access-control-pci.md`](../../../security/access-control-pci.md) | Least-privilege; quarterly access review | 97-line doc; role matrix ratified | ✅ (spec); first quarterly review → Phase 12 |

### 1.2 — Wider PCI surface

| Source doc | Notes | Status |
|---|---|---|
| [`cardholder-data-flow.md`](../../../security/cardholder-data-flow.md) | 124-line flow diagram; service annotated as out-of-CDE; PAN never crosses trust boundary by design | ✅ |
| [`cardholder-data-classification.md`](../../../security/cardholder-data-classification.md) | 105-line; classification taxonomy + handling per class | ✅ |
| [`tokenization-and-pan-handling.md`](../../../security/tokenization-and-pan-handling.md) | 63-line; tokenization n/a (no PAN) — instead, boundary `ContentGuard` enforces no-PAN | ✅ |
| [`network-segmentation.md`](../../../security/network-segmentation.md) | 81-line; segmentation design Phase-7 ratified; provisioning → Phase 12 | ✅ (design); provisioning Phase-12-deferred |
| [`secrets-policy.md`](../../../security/secrets-policy.md) | Secrets handling — never in code; env-var / mounted secrets only | ✅ |
| [`vulnerability-management-pci.md`](../../../security/vulnerability-management-pci.md) | 118-line; HIGH 7d / CRITICAL 24h SLA; current scanners Trivy + OWASP-DC + Semgrep | ✅ (procedure); **F1 gating flip** discussed in Decisions §2.2 |
| [`secure-sdlc-pci.md`](../../../security/secure-sdlc-pci.md) | 89-line; Phase-13 chunked-PR pattern is the SDLC evidence | ✅ |
| [`threat-model.md`](../../../security/threat-model.md) | Phase 4 + Phase 7 grills are the threat-model evidence | ✅ |
| [`incident-response-pci.md`](../../../security/incident-response-pci.md) | **27-line stub at Phase-7 close** — flagged as a gap in this bundle | ⚠️ Stub; see §3 Risk #5 |
| [`asv-scan-plan.md`](../../../security/asv-scan-plan.md) | Vendor selection Phase 12 | ✅ (plan); execution Phase-12-deferred |
| [`penetration-test-plan.md`](../../../security/penetration-test-plan.md) | Vendor selection Phase 12 | ✅ (plan); execution Phase-12-deferred |
| [`qsa-roc-readiness.md`](../../../security/qsa-roc-readiness.md) | QSA engagement Phase 12 | ✅ (plan) |
| [`evidence-register.md`](../../../security/evidence-register.md) | 51-line; register exists with per-control evidence pointers | ✅ |
| [`pci-dss-control-matrix.md`](../../../security/pci-dss-control-matrix.md) | High-level matrix; 12-req coverage | ✅ |
| [`compensating-controls.md`](../../../security/compensating-controls.md) | No compensating controls required at v1 (out-of-CDE) | ✅ |
| [`third-party-service-provider-pci.md`](../../../security/third-party-service-provider-pci.md) | Treasury + cloud + telemetry vendors; AOCs collected at Phase 12 | ✅ (taxonomy); AOC collection Phase-12-deferred |
| [`backup-recovery-pci.md`](../../../security/backup-recovery-pci.md) | Postgres PITR + audit-log retention | ✅ (spec) |
| [`targeted-risk-analysis.md`](../../../security/targeted-risk-analysis.md) | TRAs per PCI v4 requirement | ✅ |
| [`pci-security-grill.md`](../../../security/pci-security-grill.md) | Gate-3B adversarial review | ✅ |
| [`pci-production-readiness-gate.md`](../../../security/pci-production-readiness-gate.md) | Phase-12 readiness checklist (consumed by Phase 12 entry) | ✅ |
| [`secure-config-hardening.md`](../../../security/secure-config-hardening.md) | Hardening baselines | ✅ |

### 1.3 — NEW dev-authored deliverable (directive §6)

| Doc | Notes | Status |
|---|---|---|
| [`docs/security/pci-dss-control-mapping.md`](../../../security/pci-dss-control-mapping.md) | NEW; 12-req deep mapping of PCI DSS v4.0.1 controls → implementation artefacts (file:line, CI job, runbook section); closure-summary: 18 CLOSED / 23 Phase-12-deferred / 7 n/a-evidenced / 1 BLOCKING-for-prod (Req 8 identity origin OQ-010) | ✅ Authored in this PR |

### 1.4 — M7 CI deliverables (artefact-based evidence)

| CI job | Evidence | Status |
|---|---|---|
| `security.yml::gitleaks` | Latest green run on `feature/phase-10-operational-readiness` at HEAD `4e0ead2` shows 0 secret findings | ✅ |
| `security.yml::semgrep` | Latest green run (advisory; `continue-on-error: true`); 0 HIGH/CRITICAL **claimed by the run logs** but not enforced; see Decisions §2.2 | ⚠️ — non-blocking until F1 flip |
| `security.yml::owasp-dc` | Same posture as Semgrep | ⚠️ — non-blocking until F1 flip |
| `security.yml::trivy` | `severity: CRITICAL,HIGH`; `exit-code: '0'` advisory; SARIF uploaded | ⚠️ — non-blocking until F1 flip |
| `security.yml::sbom` | Maven `cyclonedx-maven-plugin` invocation guarded by `command -v mvn`; falls back to deferred-pending-M7 stub; CI runner does not yet have Maven | ⚠️ — stub SBOM; full SBOM Phase-12-deferred (paired with M7) |

### 1.5 — Carry-forwards intake (from Phase 10 30-review §C and C3 30-review F1)

| Id | Source | Disposition in this PR | Status |
|---|---|---|---|
| `C1` `idempotency-key-blocking-for-prod` | Phase 10 §C1 | Preserved BLOCKING-for-prod flag; route to Phase 12 multi-party signature procedure per `change-control-pci.md §1`. **No source change in this PR.** | ✅ (preservation recorded) |
| `C2` `malformed-identifier-exception-message-redaction` | Phase 10 §C2 / C3 §F5 | **CLOSED IN THIS PR.** Source change: [`MalformedIdentifierException.java`](../../../../src/main/java/com/example/purchaseconversion/application/exception/MalformedIdentifierException.java) — `buildMessage()` carries `length=<n>` only, no raw input. Test: [`ProblemDetailExceptionHandlerTest.malformedId()`](../../../../src/test/java/com/example/purchaseconversion/api/advice/ProblemDetailExceptionHandlerTest.java) asserts `getMessage()` does NOT contain the raw value. | ✅ **CLOSED** |
| `C3` `oasdiff-vs-live-oas-pending-mvn` | Phase 10 §C3 / C3 §F2 part 2 | Decision §2.3 below: deferred to Phase 12 alongside M7 Maven-in-runner provisioning. Baseline-parse fallback in `ci.yml` remains adequate as the gate for changes to `infra/openapi/baseline.yaml` itself. | ⚠️ — Phase-12-deferred with explicit Decisions justification |
| `C4` `audit-log-destination-provisioning` | Phase 10 §C4 | Decision §2.4 below: spec exists in `logging-monitoring-pci.md`; actual sink choice + provisioning is Phase-12 platform task (cloud-native log destination chosen at platform-team's level). | `BLOCKER: deferred to Phase 12 platform provisioning. Spec-side controls (retention 1y / 3mo online / WORM / signed) are documented; the destination is the missing piece.` |
| `security-workflow-gating-activation` | C3 §F1 | Decision §2.2 below: gating flip path documented but NOT executed in this PR. Reasoning: a real first-flip on Trivy with `exit-code: '1'` may catch existing CVEs the team has not yet triaged; safer to surface the proposal to the reviewer + (eventually) human-signatory chain than to land it speculatively. Documented for Phase 12 implementer to action. | ⚠️ — Phase-12-deferred (with explicit risk-managed disposition) |
| `C13` (bundle link cosmetics) + `C14` (test-naming drift) | Phase 10 §F-LOW-1/2 | Both addressed in Phase 10 hygiene commit `4e0ead2`. Closure recorded for audit trail. | ✅ CLOSED |

---

## 2 — Decisions

Non-obvious judgment calls; reviewer ratifies or amends.

### 2.1 — Dev pre-staged `00-prompt.md` (second activation)

Per Phase 10 30-review §F-INFO-3 playbook entry, dev pre-staging is acceptable provided: (a) content materially matches directive contract, (b) bundle declares the bend explicitly. Both satisfied. The Phase 10 acceptance also said "Future phases (11, 12) should follow the directive's nominal flow (reviewer pre-stages first)" — this Phase 11 pre-stage is the second activation of the bend. **Dev explicitly recognises the deformation:** under standing "proceed to next HITL" + "formal review can be a few phases later" user authorizations, dev pre-staging is the forward-motion path; reviewer may amend on next tick.

### 2.2 — `security-workflow-gating-activation` (C3 §F1) — gating flip deferred to Phase 12

**Decision:** do NOT flip in this PR. Recommendation: flip during Phase 12 staging shakedown, after a deliberate baseline-scan + triage step.

**Reasoning:**
- The current `continue-on-error: true` (Semgrep, OWASP-DC) + `exit-code: '0'` (Trivy) posture is advisory by design — the inline comments in `security.yml` cite "until Semgrep token/config is wired" (Semgrep) and "tighten to '1' in Phase 12" (Trivy).
- A first-flip risks blocking ALL future PRs if existing HIGH/CRITICAL CVEs exist in transitive dependencies. The case-study build has not run a triage pass on the current dep tree's CVE state.
- Safer protocol per [`vulnerability-management-pci.md`](../../../security/vulnerability-management-pci.md): (i) run a baseline scan on `main` to enumerate current HIGH/CRITICAL findings; (ii) triage each finding — fix-in-PR / accept-via-`docs/security/exceptions.md` (max 5 per G8-P1-3) / waive-with-rationale; (iii) THEN flip the gating posture; (iv) verify next PR triggers the blocking behaviour against a known-bad fixture.

This protocol is a Phase 12 staging-shakedown task. Bundle documents the flip path; does not execute it.

### 2.3 — `oasdiff-vs-live-oas-pending-mvn` (C3 §F2 part 2)

**Decision:** Phase-12-deferred. Same M7 dependency as the full SBOM job. Baseline-parse fallback in `ci.yml` adequately guards changes to `infra/openapi/baseline.yaml` itself; what's missing is the live-vs-baseline diff (controller-annotation change without baseline update).

**Compensating mitigation today:** PR review checklist explicitly asks reviewers to spot controller-annotation changes that aren't reflected in `baseline.yaml`. This is a human-process control; documented in [`change-control-pci.md §1`](../../../security/change-control-pci.md).

**Phase 12 closure:** when Maven lands in the CI runner, the existing `ci.yml::oasdiff vs baseline` step will pick it up automatically (the `if command -v oasdiff` branch becomes the active path), and the `mvn -Pgenerate-oas` invocation can replace the diff-against-itself tautology with diff-vs-live.

### 2.4 — `audit-log-destination-provisioning` (Phase 10 §C4)

**Decision:** `BLOCKER` row with Phase-12-deferral. Sink choice (CloudWatch / Splunk / Loki / etc.) is a platform-team decision and depends on the production hosting environment, which is not yet provisioned.

**Spec coverage IS complete** ([`logging-monitoring-pci.md`](../../../security/logging-monitoring-pci.md)): retention (1y / 3mo online), immutability (WORM / signed / append-only), access control (audit-log-read access itself audit-logged), failover behaviour (destination unreachable → platform-managed buffer per `logging-monitoring-pci.md §3.4`).

**Phase 12 closure** must produce: chosen destination AOC; provisioning code review; first audit-log-write smoke test in staging; failover test (drain the buffer to a secondary destination).

### 2.5 — HMAC key rotation drill

**Decision:** documented in [`encryption-key-management.md`](../../../security/encryption-key-management.md) §key-rotation. First execution deferred to Phase 12 tabletop.

**Why surface this here:** F-22 (HMAC key compromise) in [`failure-modes-and-resilience.md`](../../../operations/failure-modes-and-resilience.md) is a PCI failure mode. The rotation procedure exists; the drill log does not. Phase 12 staging shakedown must produce one rotation drill log in `docs/operations/drills/`.

### 2.6 — `incident-response-pci.md` stub (27 lines)

**Decision:** acknowledged as a gap; **not expanded in this PR**.

**Reasoning:** the operational `docs/operations/incident-response.md` was expanded in Phase 10 to ~210 LOC. The PCI-specific `incident-response-pci.md` is supposed to **diff** from the ops version on PCI-specific concerns (suspected CHD leak, key compromise, audit-sink tampering) rather than re-state the full IR process. A focused expansion (~50-80 LOC) is achievable but not in this PR's scope — the Phase 12 PCI tabletop will surface the gaps and this doc gets expanded during that prep.

**Tracking:** routed forward as a Phase 12 follow-up `incident-response-pci-expansion` if reviewer agrees.

---

## 3 — Risks

1. **Req 8 identity origin (OQ-010)** — BLOCKING-for-prod. The service has no app-layer authentication; identity is established at the gateway. Until the gateway is provisioned + the identity-passing contract (a header like `X-Request-Identity`) is implemented + verified, Req 8.2.1 cannot be closed. **Phase 12 sign-off chain MUST adjudicate this** — implementation, waiver-with-documented-mitigation, or block production cutover.

2. **`Idempotency-Key` (F-15)** — BLOCKING-for-prod preserved from Phase 10. Duplicate POSTs under network retry produce duplicate purchases. Real production hazard. Phase 12 adjudication required.

3. **Security-workflow gating not yet flipped (F1)** — even with this bundle accepted, until Phase 12 lands the gating flip, HIGH/CRITICAL findings produce log output but don't block merge. A bad-dependency commit could ship to prod without CI blocking.

4. **`pci-dss-control-mapping.md` is dev-authored, not QSA-validated** — the closure-summary (18 CLOSED / 23 Phase-12-deferred / 7 n/a-evidenced / 1 BLOCKING) is dev's reading. A QSA could disagree on n/a-evidence sufficiency for Req 3 (Account-Data Storage) or on Phase-12-deferred items they consider implementable today. Phase 12 QSA engagement is the canonical answer.

5. **`incident-response-pci.md` is a stub** — 27 lines is not enough for a PCI tabletop. See Decision §2.6 for tracking.

6. **No PCI tabletop drill executed** — first quarterly tabletop is Phase 12. Without it, the PCI-specific paths in incident-response are unproven.

7. **`evidence-register.md` is dev-maintained, not centrally collected** — 51-line register is a pointer index. Phase 12 should produce one consolidated PDF / collection script that pulls every cited evidence artefact into a QSA-ready package.

---

## 4 — Open questions for reviewer

1. **Q1 — Phase 11 prompt pre-stage:** ratify or amend `00-prompt.md`? See Decision §2.1. (Phase 10 30-review §F-INFO-3 said "future phases should follow the directive's nominal flow"; this is the second bend.)

2. **Q2 — F1 gating-flip deferral:** is the Phase-12-deferred posture acceptable, or should the dev flip Trivy + Semgrep + OWASP-DC in this PR and validate against any HIGH/CRITICAL findings now? See Decision §2.2.

3. **Q3 — `incident-response-pci.md` stub:** expand in this PR, expand in a small follow-up PR on this branch, or defer to Phase 12 prep? See Decision §2.6.

4. **Q4 — `pci-dss-control-mapping.md` closure-summary:** are the 18 CLOSED / 23 Phase-12-deferred / 7 n/a-evidenced / 1 BLOCKING counts defensible? In particular, are any rows mis-claimed as CLOSED that should be ⚠️?

5. **Q5 — Audit-log destination C4:** is BLOCKER-with-Phase-12-deferral acceptable, or should this PR document a concrete provisional sink choice (e.g., "we will use platform-native cloud-log destination with WORM-compatible retention setting; final pick at Phase 12")?

---

## 5 — Phase-12 hand-off list (auto-collected)

Items that MUST be addressed during Phase 12 (production cutover) before the `pci-production-approved.txt` marker is created:

1. **Req 8 identity origin (OQ-010)** — adjudicate / implement / waive.
2. **`Idempotency-Key` (F-15)** — adjudicate / implement / waive.
3. **Security-workflow gating flip** — baseline scan + triage + flip + validate.
4. **`oasdiff-vs-live-oas`** — Maven-in-runner provisioning + live OAS generation.
5. **Audit-log destination** — sink choice + provisioning + WORM evidence + access-audit-of-audit evidence.
6. **HMAC key rotation drill** — first execution; drill log in `docs/operations/drills/`.
7. **`incident-response-pci.md` expansion** — beyond the 27-line stub.
8. **First PCI tabletop drill** — drill log in `docs/operations/drills/`.
9. **`evidence-register.md` consolidation** — QSA-ready evidence package.
10. **ASV + pen-test vendor engagements** (Req 11).
11. **TPSP AOCs collected** (Req 12.8).
12. **Quarterly access review first run** (Req 7.2.5).
13. **Audit-log time-sync evidence** (Req 10.4 — platform NTP).

Inherited from Phase 10 §5 hand-off list (operational readiness) — still pending:

14. Load-test execution against capacity anchors.
15. CB-calibration drill log.
16. Class-A / Class-B rollback rehearsal logs.
17. First operational tabletop drill log.
18. PagerDuty integration provisioned.
19. On-call rotation populated with real names.

The combined Phase-10 + Phase-11 hand-off totals **19 items**. Phase 12 prompt-author must surface all 19 to the human-signatory chain.

---

End of `20-bundle.md`.
