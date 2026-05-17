# Secure SDLC for PCI Tier 1

> **Status:** Phase 7 (PCI Security Design Session), 2026-05-17.
> **Owner:** SecArch + Architect.
> Closes **G4-P1-1** (logging-hygiene PMD/Checkstyle ruleset) at the **policy** level; Phase 13 implements the lint rules.
>
> Records the gated SDLC controls: review, SAST, SCA, secret scanning, IaC scan, container scan, DAST/API testing, change control. Many of these are pre-existing in the project scaffold (`.github/workflows/`, CI gates) and Phase 13 wires the concrete tools.

---

## 1. Required gates (CI / pre-merge / pre-release)

| Gate | Tool / process (Phase-7 recommended) | Blocking? | Evidence | Owner |
|---|---|---|---|---|
| **Threat model review** | [threat-model.md](threat-model.md) walk-through on any architectural change | Yes | ADR + threat-model.md diff | SecArch |
| **Secure design review** | PR-level review by Architect on PRs that touch `api/`, `domain/`, `infrastructure/treasury/` | Yes | PR review comments | Architect |
| **Code review** | GitHub PR review via [CODEOWNERS](../../CODEOWNERS); minimum 1 approval (2 for `domain/` + `security/` paths) | Yes | PR approval log | Service owner |
| **SAST** (Static Application Security Testing) | **SonarQube Cloud** (recommended) or **Semgrep** (lightweight alternative); rules tuned to OWASP Top 10 + Java-specific (CWE-89 SQL injection, CWE-200 info disclosure, etc.) | Yes — HIGH blocks merge; MEDIUM blocks release | Scan reports archived in `evidence/sast/` | SecArch |
| **SCA / dependency scan** | **OWASP Dependency-Check** + **Snyk** (or GitHub's Dependabot for the simpler subset); Maven `<dependency-verification>` for checksum pinning (Phase-4 G4-P0-4 / TM-T-008) | Yes — Critical/High block merge per [vulnerability-management-pci.md](vulnerability-management-pci.md) | Scan reports archived | SecArch |
| **Secret scan** | **GitLeaks** + **TruffleHog**; runs on every PR + nightly on the full history | Yes — any finding blocks merge | Scan reports archived | SecArch |
| **IaC scan** | Once IaC lands (Phase 13): **Checkov** or **tfsec** on Terraform / Kubernetes manifests | Yes — Critical/High block merge | Scan reports archived | SRE + SecArch |
| **Container scan** | **Trivy** (recommended) or **Grype** on every container image build; both OS-package CVE + Java-dep CVE coverage | Yes — Critical/High block release | Image scan reports archived | SRE + SecArch |
| **Image signing** | **cosign / sigstore** on every release image; verified at admission control / pull (Phase-4 carry-forward closed here) | Yes for production deploys | Signature verification log | SRE |
| **SBOM generation** | **CycloneDX Maven plugin** producing SBOM on every release; published alongside the container image | Yes for production releases | SBOM file archived per release (TM-T-008 mitigation) | SecArch |
| **DAST / API security test** | OWASP ZAP scan (passive + targeted active) against `staging` per release; full active scan annually + on-material-change | Yes for production releases | Scan report archived | SecArch |
| **Change control** | Per [change-control-pci.md](change-control-pci.md); every PR has change-ID + risk-assessment + rollback-plan in the description | Yes | PR description + CI gate check | Architect |

## 2. Secure-coding requirements (Phase-7 ratified)

| Requirement | Source / mechanism |
|---|---|
| **Input validation** | Server-side, deny-by-default; strict typing; bounded length; Bean Validation annotations enforced via `@Valid` on every controller method (NFR-014) |
| **Authorisation checks** | n/a v1 (A-007 — no app-layer authn). Gateway-side identity propagation lands in Phase 12 (OQ-010) and authorisation logic land alongside |
| **Safe errors** | Centralised `ProblemDetailsExceptionHandler`; no stack traces in API responses; RFC 9457 + `Content-Type: application/problem+json` (AC-T-5) |
| **Dependency approval** | New runtime dependency requires architect + SecArch approval; documented in [dependency-risk-policy.md](dependency-risk-policy.md) (Phase 7 follow-on) |
| **Security regression tests** | Every error code has a negative-path integration test (AC-T-2); every content-guard reason has a test (AC-010b/c/d); every PCI-relevant audit event has an integration assertion |
| **Logging hygiene (G4-P1-1 closure at policy level)** | PMD / Checkstyle rule blocks log statements that include `description` directly as an argument; SAST rules catch `Logger.*(\".*description.*\", description)` patterns; `LoggingPiiGuardTest` is the integration-level enforcement (AC-032) |
| **No `double` / `float` for money** | ArchUnit rule enforces; CI fails on violation ([component-design.md §6](../architecture/component-design.md#6-archunit-rules-architectural-fitness-functions)) |
| **No framework imports in `domain/`** | ArchUnit rule enforces |
| **No new `TODO` without a tracked issue link** | Checkstyle rule (NFR-033) |
| **`@SuppressWarnings` requires justification comment** | Checkstyle rule |

## 3. Phase-13 implementation order

When Phase 13 implementation begins, the CI hardening should land in this order:

1. **Pre-merge gates** (block PR merge): code review, basic CI (build + unit test), Checkstyle/PMD lint, SAST, dependency scan, secret scan, ArchUnit tests.
2. **Pre-release gates** (block container build): image scan, SBOM generation, image signing.
3. **Pre-deploy gates** (block production deploy): integration tests against `staging`, DAST scan, smoke tests, the operational-readiness gate from [operational-readiness-gate.md](../operations/operational-readiness-gate.md).
4. **Post-deploy gates** (block release acceptance): SLO burn-rate within budget for 24 h post-deploy; no SEV1 alerts; smoke + canary green.

Each gate has a `.github/workflows/*.yml` file driving it. The scaffolds at `.github/workflows/ci.yml` / `security.yml` / `deploy-{dev,staging,prod}.yml` exist; Phase 13 fills them.

## 4. PCI DSS Req 6 mapping

| Req | What it asks | Where it's addressed |
|---|---|---|
| 6.2 | Bespoke and custom software is developed securely | This document + [threat-model.md](threat-model.md) + the Phase 1-6 design process |
| 6.3 | Vulnerabilities are identified | [vulnerability-management-pci.md](vulnerability-management-pci.md) |
| 6.3.2 | Bespoke software inventory | [cardholder-data-flow.md](cardholder-data-flow.md) + [pci-dss-control-matrix.md](pci-dss-control-matrix.md) |
| 6.3.3 | Security patches | [vulnerability-management-pci.md §3](vulnerability-management-pci.md#3-remediation-sla) |
| 6.4 | Public-facing web applications protected | Gateway WAF (platform-level); content guards (service-level) |
| 6.5 | Changes to all system components managed securely | [change-control-pci.md](change-control-pci.md) |

## 5. Evidence requirements

| Evidence | Frequency | Location | Retention |
|---|---|---|---|
| Threat-model review records | On every architectural change | PR records + threat-model.md diffs | Per repo lifetime |
| Code-review approvals | On every PR | PR records | Per repo lifetime |
| SAST scan reports | Per PR + per release | `evidence/sast/` (archived) | 1 year |
| SCA scan reports | Per PR + per release | `evidence/sca/` | 1 year |
| Secret-scan reports | Per PR + nightly | `evidence/secrets/` | 1 year |
| Container scan reports | Per release | `evidence/image-scans/` | 1 year |
| SBOM | Per release | `evidence/sbom/` + published with image | Per release retention |
| DAST scan reports | Per release | `evidence/dast/` | 1 year |
| Penetration-test reports | Annual + on-material-change | [penetration-test-plan.md](penetration-test-plan.md) | 3 years (PCI standard) |
| ASV scan reports | Quarterly | [asv-scan-plan.md](asv-scan-plan.md) | 1 year |

## 6. Linked artefacts

- [pci-scope-and-cde.md](pci-scope-and-cde.md) — overall scope.
- [vulnerability-management-pci.md](vulnerability-management-pci.md) — CVE remediation SLA.
- [change-control-pci.md](change-control-pci.md) — change-control process.
- [threat-model.md](threat-model.md) — STRIDE catalogue.
- [dependency-risk-policy.md](dependency-risk-policy.md) — dependency-approval process (Phase 7 follow-on).
- [secure-config-hardening.md](secure-config-hardening.md) — container image hardening (Phase 7 follow-on).
- [pci-dss-control-matrix.md](pci-dss-control-matrix.md) — Req 6 mapping.
- [evidence-register.md](evidence-register.md) — EVD-006 SDLC evidence.
