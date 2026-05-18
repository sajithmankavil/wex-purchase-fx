# 00-prompt — Phase 11 (PCI Security Readiness Gate)

**Authored:** 2026-05-18 — dev pre-stage under the standing "proceed thru next phases all the way to next HITL" + "keep a watch for reviewer's input and incorporate as need be, but formal review can be a few phases later" authorizations. The reviewer pre-staged Phase 11's `00-prompt.md` is the nominal flow per `directives/2026-05-18-phase-10-11-bulk-pass-protocol.md` §4 step 1; this is a forward-motion-bias bend, second activation of the same pattern that the Phase 10 30-review §2 Q4 ratified ("on first activation of any phase under the bulk-pass directive, the dev pre-staging is acceptable provided the content materially matches the directive's contract and the bundle declares the bend explicitly in §Decisions").
**Phase:** 11 — PCI Security Readiness Gate
**Trigger:** Phase 10 ACCEPTED WITH CONDITIONS (`phases/10-operational-readiness/30-review.md`, 2026-05-18); Phase 11 pre-stage trigger fires per directive §6.

---

## 1 — Purpose

Phase 11 is the **evidence gate** that the service can be run under PCI DSS v4.0.1 Tier 1 posture. The dev compiles all evidence into a single `20-bundle.md`; the reviewer signs off (or returns gaps) in one deep pass.

Unlike Phase 10 (operational), Phase 11 is regulatory — the failure mode of a gap here is a QSA finding at the Phase 12 cutover review or a real-world incident with PCI scope. The reviewer's verification bar is **higher** than Phase 10 (per Phase 10 30-review §F-INFO-3 playbook entry): claims about audit-log destinations, key rotation procedures, security-workflow gating, and PCI control mapping must be spot-checked against actual source and CI artefacts, not trusted by textual assertion.

## 2 — Source documents (the evidence inventory)

Every section of every file below is in scope. Bundle coverage map must reference each file by name + section.

**Core five (directive §6 minimum):**

- [`docs/security/pci-scope-and-cde.md`](../../../security/pci-scope-and-cde.md) — CDE vs connected-to categorization; cardholder-data flow diagram.
- [`docs/security/change-control-pci.md`](../../../security/change-control-pci.md) — change-control procedure; every Phase-13 PR's change-id audit trail.
- [`docs/security/logging-monitoring-pci.md`](../../../security/logging-monitoring-pci.md) — audit-log destination, immutability, retention, access control.
- [`docs/security/encryption-key-management.md`](../../../security/encryption-key-management.md) — in-transit + at-rest encryption; HMAC key rotation procedure.
- [`docs/security/access-control-pci.md`](../../../security/access-control-pci.md) — least-privilege evidence; access audit trail.

**Wider PCI surface (also in scope; required by `security-profile.yml` Tier-1 posture):**

- [`docs/security/cardholder-data-flow.md`](../../../security/cardholder-data-flow.md), [`cardholder-data-classification.md`](../../../security/cardholder-data-classification.md) — data flow + classification.
- [`docs/security/tokenization-and-pan-handling.md`](../../../security/tokenization-and-pan-handling.md) — PAN avoidance / tokenization decision (we don't process PAN by design; `ContentGuard` enforces).
- [`docs/security/network-segmentation.md`](../../../security/network-segmentation.md) — CDE network segmentation.
- [`docs/security/secrets-policy.md`](../../../security/secrets-policy.md) — secrets handling.
- [`docs/security/vulnerability-management-pci.md`](../../../security/vulnerability-management-pci.md) — vuln-mgmt SLA + procedure.
- [`docs/security/secure-sdlc-pci.md`](../../../security/secure-sdlc-pci.md) — secure development lifecycle.
- [`docs/security/threat-model.md`](../../../security/threat-model.md) — threat model.
- [`docs/security/incident-response-pci.md`](../../../security/incident-response-pci.md) — PCI-specific incident response.
- [`docs/security/asv-scan-plan.md`](../../../security/asv-scan-plan.md), [`penetration-test-plan.md`](../../../security/penetration-test-plan.md) — quarterly ASV scan + annual pen-test plans.
- [`docs/security/qsa-roc-readiness.md`](../../../security/qsa-roc-readiness.md) — QSA / ROC readiness.
- [`docs/security/evidence-register.md`](../../../security/evidence-register.md) — evidence register pointers.
- [`docs/security/pci-dss-control-matrix.md`](../../../security/pci-dss-control-matrix.md) — PCI DSS v4.0.1 control matrix.
- [`docs/security/compensating-controls.md`](../../../security/compensating-controls.md) — any compensating controls.
- [`docs/security/third-party-service-provider-pci.md`](../../../security/third-party-service-provider-pci.md) — third-party PCI responsibility split.
- [`docs/security/backup-recovery-pci.md`](../../../security/backup-recovery-pci.md) — backup + recovery PCI controls.
- [`docs/security/targeted-risk-analysis.md`](../../../security/targeted-risk-analysis.md) — targeted risk analyses per PCI v4 requirement.
- [`docs/security/pci-security-grill.md`](../../../security/pci-security-grill.md) — Gate-3B adversarial review.
- [`docs/security/pci-production-readiness-gate.md`](../../../security/pci-production-readiness-gate.md) — the Phase-12 readiness checklist.
- [`docs/security/secure-config-hardening.md`](../../../security/secure-config-hardening.md) — hardening baselines.

**M7 CI deliverables (from `13-C2-observability-cicd` + `13-C3-openapi-cicd`):**

- Semgrep SAST job (`.github/workflows/security.yml` §semgrep) — baseline scan + HIGH/CRITICAL findings count.
- OWASP Dependency-Check SCA job (§owasp-dc) — `--failOnCVSS 7` advisory baseline.
- Trivy filesystem + container scan (§trivy) — `severity: CRITICAL,HIGH`.
- CycloneDX SBOM (§sbom).
- GitLeaks secrets scan (§gitleaks).

**NEW dev-authored deliverable required by directive §6:**

- `docs/security/pci-dss-control-mapping.md` — PCI DSS v4.0.1 control → control-implementation mapping. Distinct from the existing `pci-dss-control-matrix.md` (which is the high-level matrix); the mapping deeply explains *how* each requirement is satisfied by code + config + procedure.

**Carry-forwards routed in from Phase 10 30-review.md §4 / §5:**

- `C1` — `idempotency-key-blocking-for-prod` (HIGH) — **routes to Phase 12** per Phase 10 30-review §2 Q3; Phase 11 bundle records the preservation only.
- `C2` — `malformed-identifier-exception-message-redaction` (LOW) — defense-in-depth source change. **CLOSED in this PR** at commit on `feature/phase-11-pci-security-readiness` (see §Decisions).
- `C3` — `oasdiff-vs-live-oas-pending-mvn` (MED) — paired with M7 Maven-in-runner. Phase 11 bundle decides: (a) provision Maven in CI; (b) maintained-baseline workflow with explicit human review on every controller change.
- `C4` — `audit-log-destination-provisioning` (MED) — Phase 11 must produce evidence of: sink choice, retention spec, integrity-protection mechanism, "destination unreachable" failover behaviour.
- `C13` + `C14` — bundle-cosmetic and naming-drift LOWs — addressed in Phase 10 hygiene commit `4e0ead2`; this prompt records closure for the audit trail.

**Carry-forward from C3 30-review F1:**

- `security-workflow-gating-activation` (MED) — flip Semgrep/OWASP-DC `continue-on-error: true → false`; flip Trivy `exit-code: '0' → '1'`; baseline scan output showing HIGH/CRITICAL = 0; exception entries in `docs/security/exceptions.md` (max 5 per G8-P1-3) if any.

## 3 — Bundle structure

Per directive §5. The `20-bundle.md` must contain:

1. Coverage map — one row per requirement across §2 source docs.
2. Decisions — non-obvious judgment calls (in particular, the gating-flag flip decision and the M7-vs-maintained-baseline decision).
3. Risks — known weaknesses; the **PCI failure modes** that the evidence does NOT yet cover.
4. Open questions — items requiring reviewer input.
5. Phase-12 hand-off list — auto-collected from BLOCKER rows.

## 4 — Phase 11 acceptance rubric

Reviewer (`30-review.md`) verifies:

- **Coverage** — every requirement in §2 has a row.
- **Quality** — each ✅ row points at a substantive artefact verified by `git show` or CI artefact inspection.
- **Mapping** — `docs/security/pci-dss-control-mapping.md` is exhaustive across PCI DSS v4.0.1 12 requirement groups; each control names the artefact that implements it.
- **CI baseline** — the security-workflow scan results at the bundle-posted commit are recorded in the bundle; if the F1 gating flip lands in this PR, a green CI run on the flipped workflow is the evidence.
- **PCI failure-mode coverage** — F-22 (HMAC key compromise / rotation), F-24 (audit-log destination unreachable), and any new failure modes the reviewer identifies under PCI scope must have explicit Decisions/Risks rows.
- **BLOCKER discipline** — every BLOCKER is justified and routed to Phase 12.
- **Higher verification bar** — the reviewer cross-checks code claims by reading source, not just trusting the bundle's text (Phase 10 30-review §F-INFO-3 playbook entry).

Outcomes per directive §4 step 3: ACCEPTED / ACCEPTED WITH CONDITIONS / GAPS_RETURNED.

## 5 — Forward-motion bias

Continues to apply per `directives/2026-05-17-forward-motion-bias.md`. Dev makes borderline calls and documents them; reviewer ratifies or amends.

## 6 — Phase 12 trigger

Phase 12 (production approval) is **NOT** a bulk-pass phase. Per directive §9, it requires multi-party human signature (architect / SRE / security / compliance) per `docs/security/change-control-pci.md §1`. Phase 11 acceptance is the precondition; Phase 12 begins with the reviewer routing the approval request to the human signatories, not with a new `00-prompt.md` from this dev-agent flow.

End of prompt.
