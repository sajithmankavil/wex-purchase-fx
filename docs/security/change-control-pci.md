# PCI Change Control

> **Status:** Phase 7 (PCI Security Design Session), 2026-05-17.
> **Owner:** Architect + Service owner.
>
> Documents the change-control process applied to every production-affecting change. PCI DSS Req 6.5.

---

## 1. Required change fields (PR / change-ticket template)

Every PR that touches production-runnable surface (`src/`, `pom.xml`, `infra/`, `.github/workflows/deploy-*.yml`, `migrations/`) must include in its description:

| Field | Description |
|---|---|
| **Change ID** | Unique identifier (PR number suffices for code; ticket ID for infra changes) |
| **Requirement / defect link** | Source: requirement ID (FR-NNN / NFR-NNN / AC-NNN), grill finding ID (G6-PN-NN), or incident ID (PIR-NNN). No change without a documented driver. |
| **Risk assessment** | Likelihood × Impact rationale; reference [risk-register.md](../requirements/risk-register.md) if applicable; cite new risks introduced |
| **Security impact** | Does this change affect: authentication, authorisation, secrets, audit logs, content guards, or any control listed in [pci-dss-control-matrix.md](pci-dss-control-matrix.md)? If yes: which controls + how |
| **CDE impact** | Always "out-of-CDE" for this service; if the change would *introduce* CDE-relevant data (PAN/SAD/CHD), the PR is **blocked** pending PCI design re-review |
| **Test evidence** | New / changed tests + the test classes that ran; link to CI run; coverage delta |
| **Approval** | CODEOWNERS-required approver(s); SecArch approval for security-impact changes; Compliance approval for control-matrix changes |
| **Rollback plan** | Reference to the [rollback-plan.md](../operations/rollback-plan.md) class (A/B/C/D/E/F); specific commands or steps if non-standard |
| **Deployment window** | When the change is promoted to `prod` (timezone-explicit); coordinated with on-call if SEV1-history-bearing |
| **Post-deploy validation** | Smoke test + targeted assertions; the metric / log signal that confirms the change is healthy in production |

CI lint enforces the presence of each field as a PR description block ([secure-sdlc-pci.md §1](secure-sdlc-pci.md#1-required-gates-ci--pre-merge--pre-release)).

## 2. Categories

| Category | Definition | Approval | Deployment cadence |
|---|---|---|---|
| **Standard** | Code change with no security impact; no CDE impact; no schema migration | CODEOWNERS (≥ 1 approver) | Continuous (rolling deploy on merge) |
| **Risky** | Schema migration; new dependency; touches `domain/`, `application/security/`, `infrastructure/treasury/`, `observability/` | CODEOWNERS (≥ 2 approvers including Architect) + SRE review | Coordinated deploy window |
| **Security-impacting** | Touches threat model, content guards, audit-log emission, secret handling, error envelope, or any pci-* doc | Same as Risky + SecArch sign-off | Coordinated deploy window |
| **PCI-control-modifying** | Changes a control listed in [pci-dss-control-matrix.md](pci-dss-control-matrix.md); changes evidence collection in [evidence-register.md](evidence-register.md) | Standard + Risky + SecArch + Compliance sign-off | Coordinated deploy window + audit-log entry |
| **Emergency** | Any change deployed outside the normal cadence to address a SEV1 incident, security incident, or critical CVE within SLA | Real-time (no pre-approval); retroactive approval within 24 h; post-incident review | Immediate (with explicit operator action) |

## 3. Emergency changes

Process:

1. **Triage.** On-call primary opens the incident channel; identifies the change scope.
2. **Implementation.** Smallest possible change to address the immediate issue; full git history preserved.
3. **Deploy.** Goes through CI (cannot bypass tests / SAST / dependency scan); deploys to `prod` directly if required (skipping `staging` requires Service-owner sign-off).
4. **Post-deploy.** Validation per the field; on-call confirms metrics return to baseline.
5. **Retroactive approval.** Within 24 h: full PR description per §1; CODEOWNERS approval; SecArch review if security-impacting; Compliance review if PCI-control-modifying.
6. **PIR.** Post-incident review within 5 business days. Outputs corrective actions (tests, monitoring, runbook updates) to prevent recurrence.
7. **Evidence.** The emergency change is logged in [evidence-register.md](evidence-register.md) with `status=emergency` and the PIR link.

## 4. Change evidence register (cross-reference)

The detailed change log lives in the git history (every commit has a change-ID linkable). The [evidence-register.md](evidence-register.md) records summary-level evidence for PCI:

| What's in evidence-register | Where it lives |
|---|---|
| EVD-006 (Req 6) | SDLC / change-control evidence collection |
| Per-release change summary | git tag + release notes ([release-notes-template.md](../release/release-notes-template.md)) |
| Emergency-change retrospective | PIR document per incident |

## 5. Schema-migration changes (special case)

Per [rollback-plan.md §4.4](../operations/rollback-plan.md#44-class-c--schema-rollback-forward-only): migrations are **forward-only**. A schema "rollback" is a new forward migration.

| Migration class | PR labelling | Approval |
|---|---|---|
| Additive (new table, new column with default) | `migration:additive` | CODEOWNERS + Architect |
| Destructive (drop column, drop table, type change) | `migration:destructive` | CODEOWNERS + Architect + SecArch + Compliance + Service owner |
| Index / constraint addition | `migration:additive` (in the unlock sense) | CODEOWNERS + Architect + DBA |

Destructive migrations require:
- Written justification (typically privacy / compliance request).
- Backup of affected rows before the migration runs.
- Validation query post-migration.
- Documented rollback path (forward-only — another migration that re-adds the structure if needed).

## 6. Dependency-addition changes (special case)

A new runtime dependency requires:
1. PR description includes the dependency's name, version, license, CVE history, transitive footprint, and *why this exact version* is chosen.
2. SecArch reviews per [dependency-risk-policy.md](dependency-risk-policy.md) (Phase 7 follow-on).
3. Architect approves architectural fit.
4. The dependency is added to the SBOM tracking from the next release.

Phase-4 example: `com.github.f4b6a3:uuid-creator:5.x` (G4-P0-4) followed this process implicitly — the design grill ratified the addition before any implementation.

## 7. Configuration changes (special case)

| Config change | Class |
|---|---|
| Env-var value change (e.g., `WEX_CACHE_TTL_HOURS=24` → `=168`) | Standard (PR; rolling deploy) |
| New env var introduced | Risky (PR + Architect; documented in [deployment-architecture.md §5](../architecture/deployment-architecture.md#5-configuration-12-factor)) |
| Secret rotation | Security-impacting (SecArch executes per rotation procedure; logged) |
| Resilience-pattern tuning (CB threshold, bulkhead size, retry budget) | Risky + SRE review (Phase 5 / 6 / failure-modes refinements) |
| Alert threshold change | Risky + SRE + Service-owner review |

## 8. Linked artefacts

- [secure-sdlc-pci.md](secure-sdlc-pci.md) — CI gates that enforce change-control.
- [vulnerability-management-pci.md](vulnerability-management-pci.md) — CVE-driven emergency changes.
- [rollback-plan.md](../operations/rollback-plan.md) — six rollback classes.
- [pci-dss-control-matrix.md](pci-dss-control-matrix.md) — Req 6.5 mapping.
- [evidence-register.md](evidence-register.md) — change evidence collection.
- [release-notes-template.md](../release/release-notes-template.md) — release-note template.
