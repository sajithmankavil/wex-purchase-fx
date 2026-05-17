# PCI DSS v4.0.1 Control Matrix

> **Status:** Phase 7 (PCI Security Design Session), 2026-05-17.
> **Owner:** SecArch + Compliance.
>
> Implementation ownership and evidence for PCI DSS Requirements 1–12, framed for this service's **out-of-CDE** posture. Each row records design + evidence + owner + status + open gaps. This is not a replacement for QSA testing procedures; it's the project's internal map.

---

## How to read this matrix

- **Status `n/a`** means the requirement does not apply because the service is out-of-CDE and the data class doesn't exist here (e.g., Req 3 — protect stored account data — applies to PAN; we have none).
- **Status `applicable + designed`** means the control applies in our context and is designed (artefacts exist), with implementation realised in Phase 13.
- **Status `applicable + Phase-7 in-design`** means design is in this document set; concrete implementation lands in Phase 13.
- **Status `BLOCKED`** would mean a P0 gap; **there are none at the close of Phase 7**.

| PCI area | Requirement focus | In-scope components | Control design | Evidence | Owner | Status |
|---|---|---|---|---|---|---|
| **Req 1** | Install and maintain network security controls | Gateway, egress proxy, service private network, DB subnet | Default-deny everywhere; explicit allow-list per [network-segmentation.md §3](network-segmentation.md#3-firewall--allowlist-rules); monthly review | Egress rule audit; ingress allow-list; segmentation test (quarterly) | Platform + SRE | applicable + Phase-7 in-design |
| **Req 2** | Apply secure configurations to all system components | Container image; JVM flags; Spring Boot config; DB role; secrets-store policy; gateway config | Hardening per [secure-config-hardening.md](secure-config-hardening.md) (Phase 7 follow-on); refuse-to-start invariants on missing secrets ([encryption-key-management.md](encryption-key-management.md)); least-privilege DB role ([access-control-pci.md §3](access-control-pci.md#3-service-accounts-non-human-identities)) | CIS-equivalent benchmark report; configuration-as-code review (PR history); refuse-to-start integration test | SRE + SecArch | applicable + Phase-7 in-design |
| **Req 3** | Protect stored account data | **n/a — no PAN/SAD stored.** API contract prohibits ([pci-scope-and-cde.md](pci-scope-and-cde.md)); schema cannot hold it ([data-model.md](../architecture/data-model.md)); boundary guards detect-and-reject (AC-010b/c/d) | Schema review confirms no PAN-shaped column; boundary-guard probe set passes ([pci-scope-and-cde.md §4.1](pci-scope-and-cde.md#41-segmentation-validation-method)) | SecArch | **n/a (out-of-CDE)** — evidence required to defend the n/a claim |
| **Req 4** | Protect cardholder data with strong cryptography during transmission over open public networks | Inbound HTTPS (gateway); outbound HTTPS to Treasury; outbound to secrets/telemetry sinks | TLS 1.3 preferred / TLS 1.2 minimum (NFR-011/012; Phase-4 G4-P1-12); pinned CA bundle; hostname verification; cert chain validation. **No CHD in transit by design** — control applies to general traffic | TLS scan reports (per release); outbound TLS test in CI; cert validity inventory | Platform + SecArch | applicable + Phase-7 in-design |
| **Req 5** | Protect all systems and networks from malicious software | Container hosts; CI runners; secrets store; DB | Platform-managed malware protection (provider responsibility); image scanning per release ([vulnerability-management-pci.md](vulnerability-management-pci.md)) | Trivy / Grype scan reports per release; platform malware-protection AOC | Platform + SecArch | applicable + Phase-7 in-design |
| **Req 6** | Develop and maintain secure systems and software | Full SDLC: design, code, dependency, deploy | Threat modelling (Phase 4 / 7); SAST + SCA + secret-scan + container-scan + DAST in CI per [secure-sdlc-pci.md](secure-sdlc-pci.md); change control per [change-control-pci.md](change-control-pci.md); vulnerability SLA per [vulnerability-management-pci.md](vulnerability-management-pci.md) | CI scan reports; PR review records; vulnerability backlog | SecArch + Architect | applicable + Phase-7 in-design |
| **Req 7** | Restrict access to system components and cardholder data by business need to know | Platform IAM; DB roles; secrets-store IAM; audit-log destination IAM | Least-privilege per [access-control-pci.md §2](access-control-pci.md#2-role-matrix-production-reference); quarterly access review | Quarterly access-review records; IAM-policy review; DB role audit | SecArch + Platform | applicable + Phase-7 in-design |
| **Req 8** | Identify users and authenticate access to system components | Platform (engineers); gateway (clients); service-to-service (mTLS at gateway, Phase 12) | **Phase-7 working position:** identity is established at the gateway (OQ-010 BLOCKING-for-prod); v1 service has no app-layer authn (A-007); the service logs an opaque `X-Request-Identity` header from the gateway for audit traceability. Platform-side MFA on admin access | Platform IAM logs; gateway access logs; OQ-010 closure record (Phase 12) | SecArch + Platform | applicable + **BLOCKING-for-prod via OQ-010** |
| **Req 9** | Restrict physical access to cardholder data | Data centres; CI runner hardware | Platform-managed (cloud provider's physical security per their AOC); no on-prem CHD storage | Cloud provider AOC ([third-party-service-provider-pci.md](third-party-service-provider-pci.md)) | Platform | applicable + provider AOC |
| **Req 10** | Log and monitor all access to system components and cardholder data | All in-scope sources per [logging-monitoring-pci.md §2](logging-monitoring-pci.md#2-log-sources) | Audit-log destination is WORM / signed / append-only (Phase-7 G4-P1-16 closure); ≥ 1 year retention; 3 months online; time-sync via NTP; access-of-audit-log itself audit-logged | Audit destination access logs; sample audit-event walk-through; log-redaction tests (AC-032 / AC-032b); time-sync alerts | SecArch + SRE | applicable + Phase-7 in-design |
| **Req 11** | Test security of systems and networks regularly | ASV scans; internal scans; pen tests; segmentation tests | ASV quarterly + on material change ([asv-scan-plan.md](asv-scan-plan.md)); internal vulnerability scan quarterly; segmentation test quarterly; external pen-test annual + on material change ([penetration-test-plan.md](penetration-test-plan.md)) | Scan reports; pen-test report; segmentation-test evidence | SecArch + Compliance | applicable + Phase-7 in-design |
| **Req 12** | Support information security with organisational policies and programs | All; org-level | Policies in `docs/security/*.md`; security-incident-response in [incident-response-pci.md](incident-response-pci.md); security-awareness training (org-level); risk-assessment via [threat-model.md](threat-model.md) + [risk-register.md](../requirements/risk-register.md); TPSP management per [third-party-service-provider-pci.md](third-party-service-provider-pci.md) | Policy documents; training records (org); risk-register; TPSP AOCs | SecArch + Compliance + Service owner | applicable + Phase-7 in-design |

## Control evidence rules

- **Evidence must be reproducible, dated, owned, and retained.** Every row in [evidence-register.md](evidence-register.md) records the collection cadence + retention.
- **No control may be marked complete with only narrative evidence.** Each control has a concrete artefact (scan report, audit log, test class, configuration file).
- **P0 / P1 findings must include owner, remediation path, and due date.** Tracked in [risk-register.md](../requirements/risk-register.md) and [p1-deferrals-acceptance.md](../planning/p1-deferrals-acceptance.md).
- **n/a claims require evidence-of-n/a.** For us, Req 3 is the big "n/a" — the evidence is the schema review + boundary-guard probe set + cardholder-data-flow diagram.

## Open gaps (P1 carry-forward)

| Gate | Item | Phase to close |
|---|---|---|
| **Req 8** | Identity origin (OQ-010) | Phase 12 pre-prod hand-off |
| **Req 6** | SAST tool concrete pick | Phase 13 (recommendation: SonarQube Cloud or Semgrep — [secure-sdlc-pci.md §2](secure-sdlc-pci.md#2-tooling--phase-7-recommended-picks)) |
| **Req 6** | SBOM generation toolchain integration | Phase 13 (recommendation: CycloneDX Maven plugin) |
| **Req 6** | Image signing chain | Phase 13 (recommendation: cosign / sigstore) |
| **Req 11** | ASV vendor engagement | Phase 12 |
| **Req 11** | Pen-test vendor engagement | Phase 12 |
| **Req 10** | Audit-log destination concrete pick (platform-dependent) | Phase 12 (recommendations in [logging-monitoring-pci.md §3](logging-monitoring-pci.md#3-audit-log-destination--phase-7-ratified)) |
| **Req 3 (n/a)** | Encoded-PAN guard implementation (AC-010d) | Phase 13 |
| **Req 6** | App-layer rate-limiter (G4-P1-15) | **Phase-7 ratified position: YES** — add Resilience4j RateLimiter at controller layer; default disabled; env-var `WEX_RATE_LIMIT_RPM` enables. See [pci-security-design-session.md](../planning/pci-security-design-session.md) decision log |

## Linked artefacts

All other security documents in `docs/security/`. The control matrix is the index; each requirement above links to its detailed document.
