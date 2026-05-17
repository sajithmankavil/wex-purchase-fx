# Third-Party Service Provider PCI Responsibility Matrix

> **Status:** Phase 7 (PCI Security Design Session), 2026-05-17.
> **Owner:** SecArch + Compliance + Service owner.
>
> Records every external dependency, its CDE/CHD impact, and the shared-responsibility model. PCI DSS Req 12.8 / 12.9.

---

## 1. Service providers

| Provider | Service | CDE/CHD impact | AOC required? | Responsibilities | Monitoring | Owner |
|---|---|---|---|---|---|---|
| **U.S. Treasury Fiscal Data API** (`api.fiscaldata.treasury.gov`) | Public exchange-rate dataset (read-only consumption) | **None** — public open-data; no CHD; we are an anonymous consumer | No — see formal applicability decision below (Phase-8 G8-P0-4 closure) | Treasury publishes the data; we treat their response payload as untrusted at the schema level (validated + sanity-checked); we treat the TLS identity as trusted | Treasury upstream canary every 1 h ([observability.md §7](../operations/observability.md#7-synthetic-checks)) + orientation canary every 24 h ([monitoring-alerting.md §3.1](../operations/monitoring-alerting.md#31-rateorientationcontractcheck-calibration-g4-p1-27-closure)) | Architect + SRE |
| **Cloud platform** (AWS / GCP / Azure — platform-dependent) | Compute, network, ingress, secrets store, audit-log destination, DB (Postgres), telemetry sinks | **Security-impacting** for all PCI controls; runs the substrate the service depends on | **Yes** — current AOC required before production roll-out | Platform IAM, network policy, default encryption-at-rest, key-management infrastructure, audit-log immutability, time-sync | Platform-managed SLA + alarms | Service owner + Platform team |
| **Container image base** (Eclipse Temurin JRE 21 / Distroless option) | Runtime JRE | Security-impacting (CVE surface in the base image) | No AOC; relies on vulnerability-management process | Provide patched base image releases | CVE feeds + Trivy image scan per release ([vulnerability-management-pci.md](vulnerability-management-pci.md)) | SecArch + SRE |
| **GitHub** (source-control + Actions CI) | Source-control + CI/CD pipeline | Security-impacting (build-pipeline integrity) | **Yes** — GitHub has a SOC 2 report; SOC 2 attestation accepted in lieu of AOC for CI/CD provider | Provide access control, secret management for Actions, audit log of repo events | GitHub audit log (read by SecArch quarterly); secret-scan results | Service owner + SecArch |
| **CVE / vulnerability data sources** | NVD, GitHub Advisories, Snyk vuln-DB, etc. | Security-impacting (drives patch decisions) | n/a (public/commercial data feeds) | Provide CVE data on schedule | Daily ingestion check (Phase 13) | SecArch |
| **External pen-test vendor** (engaged annually + on material change) | Independent security testing | Security-impacting (touches all controls during testing) | **Yes** — vendor's own SOC 2 + signed engagement contract | Pen-test execution; report; remediation tracking | Pen-test contract terms; SLAs in [penetration-test-plan.md](penetration-test-plan.md) | SecArch + Compliance |
| **Approved Scanning Vendor (ASV)** | Quarterly external scans | Security-impacting (PCI Req 11.3.2) | **Yes** — ASV must be PCI Council-listed | ASV scans; pass/fail reports; remediation evidence | Per [asv-scan-plan.md](asv-scan-plan.md) | Compliance |
| **QSA** (Qualified Security Assessor — for ROC if/when scope warrants) | Annual PCI compliance assessment | Read-only access to evidence | n/a (QSA *issues* AOCs; not a TPSP) | Conduct assessment per PCI DSS testing procedures | Per [qsa-roc-readiness.md](qsa-roc-readiness.md) | Compliance |

## 1a. Treasury — formal applicability decision (Phase-8 G8-P0-4 closure)

Treasury Fiscal Data API is *not* a Third-Party Service Provider under PCI DSS 4.0.1 Req 12.8 for the following reasons:

1. Treasury does not receive, store, process, transmit, or otherwise have access to cardholder data on our behalf.
2. Treasury does not authenticate clients on our behalf; we are anonymous to them (no API key, no contract).
3. Treasury's service interruption affects our SLOs (SLO-C is bounded by Treasury availability) and audit-event volume (drives `treasury_api_failure` counters) but does not affect any PCI-DSS-relevant control (CHD protection, access control, audit-log integrity, etc.).
4. Treasury operates under U.S. federal-government open-data publication; no contractual relationship exists or is required.
5. Compensating controls for Treasury risk are *operational* (single-flight cache + CB + local rate fallback) and are documented in [failure-modes-and-resilience.md](../operations/failure-modes-and-resilience.md); they are not PCI-DSS controls.

**Treasury is therefore an upstream open-data dependency, not a TPSP.** If a future change to Treasury's terms or our integration model introduces CHD flow, contract, or authentication, this applicability decision is re-opened.

## 2. Required evidence

For every TPSP (rows where AOC is required):

| Evidence item | Frequency | Retention |
|---|---|---|
| **Current AOC or compliance attestation** | Annually (PCI requires the AOC be no more than 12 months old) | Per release retention; archived in `evidence/tpsp-aocs/` |
| **Responsibility matrix** | At onboarding + per contract renewal | Per contract lifetime |
| **SLA / security obligations** | At onboarding + per contract renewal | Per contract lifetime |
| **Incident-notification obligations** | At onboarding | Per contract lifetime |
| **Access controls** (who at provider can access our data; how they're authenticated) | At onboarding + annual review | Annual |
| **Data-processing and retention terms** (what data the provider sees, retention, deletion) | At onboarding | Per contract lifetime |

## 3. Shared-responsibility notes

### 3.1 Cloud platform (most important)

| Responsibility | Provider | Us |
|---|---|---|
| Physical security of data centres | Provider | — |
| Hypervisor / host OS patching | Provider | — |
| Network infrastructure (cables, switches, regional networking) | Provider | — |
| TLS termination at load balancer | Provider | We configure (cert auto-rotation; ciphers; min TLS version) |
| Encryption at rest (block storage) | Provider (default) | We verify enabled |
| IAM roles + policies | Provider provides; we configure | We configure least-privilege; quarterly access review |
| Secret encryption (KMS / Vault / Secrets Manager) | Provider | We configure rotation, access policies |
| Network policy (VPC / security groups) | Provider provides; we configure | We configure default-deny + explicit allow-list ([network-segmentation.md](network-segmentation.md)) |
| Container runtime patching | Provider | We update base image per release |
| Application binaries | — | Us |
| Application configuration | — | Us |
| Application logs / metrics / traces emission | — | Us; provider hosts the sinks |
| Audit-log destination (WORM) | Provider provides Object Lock / equivalent | We configure retention + access |
| DB platform (PostgreSQL) | Provider (managed) | We configure schema, roles, backups, connections |

### 3.2 Treasury Fiscal Data API

Special case: Treasury is a **public open-data provider**, not a TPSP in the PCI sense.
- No AOC required (no CHD; no contract).
- No service-level obligation from Treasury (anonymous public API; no published SLA).
- Our responsibility: treat the payload as untrusted (validated + sanity-bounded + orientation-checked); treat the TLS identity as trusted (signed by a public CA).
- If Treasury changes the API contract (schema drift, orientation flip), we detect via canaries and patch via the change-control process.

### 3.3 GitHub (CI/CD)

| Responsibility | GitHub | Us |
|---|---|---|
| Source-control integrity (auditable history; signed commits where configured) | GitHub | We enable required-status checks + branch protection |
| Actions runner security | GitHub | We minimise the runner permissions (least-privilege OIDC roles) |
| Secret storage for Actions | GitHub (encrypted at rest) | We classify which secrets live in Actions secrets vs platform secrets-store; rotate per policy |
| Repo IAM | GitHub provides | We configure (CODEOWNERS, branch protection, required reviewers) |
| Repository content | — | Us |

## 4. TPSP audit cadence

| Activity | Cadence | Owner |
|---|---|---|
| AOC freshness check (each TPSP requiring AOC) | Annually | Compliance |
| **GitHub SOC 2 freshness check (Phase-8 G8-P1-6)** | Annually; raise P2 incident if attestation > 12 months old; mitigation = escalate to GitHub Enterprise or switch provider | Compliance |
| TPSP IAM access review | Quarterly | SecArch + Compliance |
| Contract / DPA review | Annually + on renewal | Compliance + legal |
| Incident-notification drill (verify provider's notification SLA is real) | Annually | SecArch |

## 5. Onboarding a new TPSP

Before engaging a new TPSP (e.g., a payment provider in a future feature):

1. **Scope review.** Does the new TPSP touch CHD? If yes, the project re-opens its PCI scope (currently out-of-CDE).
2. **AOC review.** Current AOC validated by Compliance.
3. **Responsibility matrix.** Drafted + signed.
4. **SLA + IR commitments.** In contract.
5. **Onboarding ticket** through change-control ([change-control-pci.md](change-control-pci.md)) with security-impacting class.
6. **Update this document** to add the new row.

## 6. Linked artefacts

- [pci-scope-and-cde.md](pci-scope-and-cde.md) — overall scope.
- [pci-dss-control-matrix.md](pci-dss-control-matrix.md) — Req 12.8 / 12.9 mappings.
- [evidence-register.md](evidence-register.md) — EVD-012 TPSP evidence.
- [vulnerability-management-pci.md](vulnerability-management-pci.md) — TPSP CVE handling.
- [change-control-pci.md](change-control-pci.md) — TPSP-change process.
- [qsa-roc-readiness.md](qsa-roc-readiness.md) — QSA engagement model.
- [penetration-test-plan.md](penetration-test-plan.md) / [asv-scan-plan.md](asv-scan-plan.md) — pen-test + ASV plans.
