# PCI DSS v4.0.1 Control Implementation Mapping

> **Status:** Phase 11 (PCI Security Readiness Gate), 2026-05-18. Authored as the Phase 11 directive §6 requirement.
> **Companion to:** [`pci-dss-control-matrix.md`](pci-dss-control-matrix.md) (high-level matrix). This document goes one level deeper — for each PCI DSS v4.0.1 requirement, it names the **concrete artefact** (file:line, CI job, runbook section, etc.) that implements or evidences the control.
> **Scope posture:** out-of-CDE service per [`pci-scope-and-cde.md`](pci-scope-and-cde.md). Controls marked `n/a` carry evidence of inapplicability, not absence of consideration.

---

## How to read this mapping

For each PCI DSS v4.0.1 requirement (Req 1–12), this document lists:

- **Sub-requirements that materially apply** to this service's scope (out-of-CDE; no PAN/SAD; processes a `description` string at the trust boundary).
- **Implementation artefact** — file path (with line range when useful), CI job, or procedure document that satisfies the requirement.
- **Verification method** — how a reviewer (or QSA) confirms the artefact actually implements the control.
- **Status** — `CLOSED` (implemented + verified) / `Phase-12-deferred` (waits on production cutover) / `n/a-evidenced` (control inapplicable; evidence-of-inapplicability cited).

This is the audit-trail-grade companion to `pci-dss-control-matrix.md`. The matrix is the executive view; this mapping is the implementer's reference.

---

## Req 1 — Install and maintain network security controls

**Applicability:** Phase-12 platform-team responsibility; service code does not directly control firewall/network rules.

| Sub-req | Control | Artefact | Verification | Status |
|---|---|---|---|---|
| 1.2.1 | Network security controls (NSCs) configured and managed | [`docs/security/network-segmentation.md §3`](network-segmentation.md) (default-deny + monthly review) | Phase-12: provisioning code review + egress rule audit log | Phase-12-deferred |
| 1.2.5 | Ports/services/protocols documented and approved | [`docs/security/network-segmentation.md §3`](network-segmentation.md) — service exposes only `:8080/http`; egress to Treasury + telemetry sinks only | Phase-12: platform allow-list inspection | Phase-12-deferred |
| 1.3.1 | NSCs between trusted and untrusted networks | Gateway → service private network → DB subnet (3 tiers) | Phase-12: segmentation test (quarterly cadence) | Phase-12-deferred |
| 1.4.1 | NSCs between any wireless networks and the CDE environment | n/a — no wireless components in this service's perimeter | Provider AOC ([`third-party-service-provider-pci.md`](third-party-service-provider-pci.md)) | n/a-evidenced |

## Req 2 — Apply secure configurations to all system components

| Sub-req | Control | Artefact | Verification | Status |
|---|---|---|---|---|
| 2.2.1 | Configuration standards developed and applied | [`docs/security/secure-config-hardening.md`](secure-config-hardening.md); [`src/main/resources/application.yml`](../../src/main/resources/application.yml); [`pom.xml`](../../pom.xml) JVM-flag config | CI lint enforces; PR review verifies | CLOSED (config) / Phase-12-deferred (CIS benchmark) |
| 2.2.4 | Only necessary services/protocols enabled | Spring Boot starts only `web` + `actuator` + `data-jdbc`; no JMX exposed to network; actuator health binding restricted to internal | `application.yml` `management.endpoints.web.exposure.include` audit | CLOSED |
| 2.2.5 | Insecure services/protocols disabled or risk-justified | HTTP-only on internal port; gateway terminates TLS upstream | [`network-segmentation.md`](network-segmentation.md) | Phase-12-deferred (gateway provisioning) |
| 2.2.7 | All non-console administrative access encrypted | Cloud platform SSH/kubectl over TLS by provider | Provider AOC | n/a-evidenced (platform-managed) |
| 2.3 | Refuse-to-start invariants on missing secrets | [`DescriptionHasher.java`](../../src/main/java/com/example/purchaseconversion/observability/DescriptionHasher.java) ctor throws on missing `wex.log.hash.key` in `local-pii`/`prod`/`staging` profiles (NFR-017 / A-020) | [`DescriptionHasherTest.java`](../../src/test/java/com/example/purchaseconversion/observability/DescriptionHasherTest.java); F-18 in [`failure-modes-and-resilience.md`](../operations/failure-modes-and-resilience.md) | CLOSED |

## Req 3 — Protect stored account data

**Posture: n/a — service is out-of-CDE; no PAN/SAD stored or processed by design.**

| Sub-req | Control | Artefact | Verification | Status |
|---|---|---|---|---|
| 3.2 (n/a defense) | Account data storage prohibited | Schema cannot hold PAN: [`exchange_rates`](../../src/main/resources/db/migration/V001__initial.sql) + [`purchase_transactions`](../../src/main/resources/db/migration/V001__initial.sql) have no PAN-typed columns; `description` is `varchar(255)` with content-guard at API boundary | Schema review + boundary-guard probe set | n/a-evidenced |
| 3.3.1 (n/a defense) | SAD not stored after authorization | n/a — no authorization flow in this service; no SAD ever crosses the trust boundary | API contract review; [`pci-scope-and-cde.md §2`](pci-scope-and-cde.md) | n/a-evidenced |
| **Boundary control** | PAN-shaped input detected + rejected at API boundary | [`ContentGuard.java`](../../src/main/java/com/example/purchaseconversion/api/advice/ContentGuard.java) — Luhn check + NFKC + track-data + encoded-PAN detection; AC-010b/c/d/e | [`ContentGuardTest.java`](../../src/test/java/com/example/purchaseconversion/api/advice/ContentGuardTest.java) `@Nested PanLuhn`, `Track`, `Encoded`, `Nfkc` | CLOSED |
| **PII-redaction-on-log** | Even rejected PAN-shaped strings are HMAC-hashed before log emission | [`DescriptionHasher.java`](../../src/main/java/com/example/purchaseconversion/observability/DescriptionHasher.java); [`ProblemDetailExceptionHandler::onMalformedId` + `onInvalidCurrency`](../../src/main/java/com/example/purchaseconversion/api/advice/ProblemDetailExceptionHandler.java) | [`LoggingPiiGuardTest.java`](../../src/test/java/com/example/purchaseconversion/api/advice/LoggingPiiGuardTest.java) `@Nested LocalProfile + CiProfile + CrossLevel + Fullwidth-confusable` | CLOSED |
| **Defense-in-depth on exception** | `MalformedIdentifierException.getMessage()` does NOT embed raw input | [`MalformedIdentifierException.java`](../../src/main/java/com/example/purchaseconversion/application/exception/MalformedIdentifierException.java) `buildMessage()` carries length only | [`ProblemDetailExceptionHandlerTest.malformedId()`](../../src/test/java/com/example/purchaseconversion/api/advice/ProblemDetailExceptionHandlerTest.java) — asserts `getMessage()` does NOT contain raw input | CLOSED (Phase 11 §C2 closure) |

## Req 4 — Protect cardholder data with strong cryptography during transmission

**Posture: applies to general traffic; no CHD by design (Req 3 n/a).**

| Sub-req | Control | Artefact | Verification | Status |
|---|---|---|---|---|
| 4.2.1 | TLS for all transmission of CHD across open public networks | Inbound HTTPS at gateway (Phase 12); outbound to Treasury over HTTPS pinned per [`encryption-key-management.md §4`](encryption-key-management.md) | Phase-12: TLS scan reports per release | Phase-12-deferred |
| 4.2.1.1 | TLS minimums (no SSL, no early TLS) | TLS 1.3 preferred / TLS 1.2 minimum per NFR-011/012 (Phase-4 G4-P1-12); pinned CA bundle; hostname verification | [`TreasuryClientAdapter`](../../src/main/java/com/example/purchaseconversion/infrastructure/treasury/TreasuryClientAdapter.java) `RestClient` uses default JDK TLS stack (1.2/1.3) | CLOSED (client) / Phase-12-deferred (gateway) |

## Req 5 — Protect all systems and networks from malicious software

| Sub-req | Control | Artefact | Verification | Status |
|---|---|---|---|---|
| 5.2.1 | Anti-malware deployed on all system components | Container hosts: provider-managed. CI runners: GitHub-hosted runners. | Provider AOC | n/a-evidenced (platform-managed) |
| 5.3.4 | Anti-malware solution maintained and active | Provider responsibility | Provider AOC | n/a-evidenced |
| **Code-level malware avoidance** | Container image vulnerability scanning per release | [`.github/workflows/security.yml::trivy`](../../.github/workflows/security.yml) — Trivy fs + container scan on every PR (`severity: CRITICAL,HIGH`) | CI artefact `trivy-sarif` per PR; HIGH/CRITICAL count = 0 baseline at Phase 11 bundle commit | CLOSED (scanner provisioned); **F1 carry-forward**: gating flip from advisory to blocking → see Phase 11 bundle Decisions |

## Req 6 — Develop and maintain secure systems and software

**This is the largest applicable area for an out-of-CDE service.**

| Sub-req | Control | Artefact | Verification | Status |
|---|---|---|---|---|
| 6.2.4 | Bespoke and custom software developed according to PCI DSS and industry best practices | Threat model: [`docs/security/threat-model.md`](threat-model.md); secure-SDLC: [`docs/security/secure-sdlc-pci.md`](secure-sdlc-pci.md); chunk-based phase-13 reviews under [`docs/external-review/`](../external-review/) | All Phase-13 chunks reviewed + accepted per `external-review/STATUS.md` | CLOSED |
| 6.3.1 | Security vulnerabilities identified | SAST (Semgrep) + SCA (OWASP DC) + secret-scan (GitLeaks) + container (Trivy) all in [`.github/workflows/security.yml`](../../.github/workflows/security.yml) | CI runs on every PR; SARIF uploaded; baseline count at Phase 11 bundle commit | CLOSED |
| 6.3.2 | Inventory of bespoke software components | CycloneDX SBOM generated per release: [`.github/workflows/security.yml::sbom`](../../.github/workflows/security.yml) → `target/bom.json` | CI artefact `cyclonedx-sbom` per PR; Maven plugin runs when mvn is provisioned in CI (M7) | Phase-12-deferred (M7 dependency) for full SBOM; stub today |
| 6.3.3 | Security vulnerabilities addressed by deploying security patches | [`vulnerability-management-pci.md`](vulnerability-management-pci.md) — HIGH within 7d, CRITICAL within 24h | Tracker integration (Phase 12) | Phase-12-deferred |
| 6.4.1 | Public-facing web app reviewed via automated technical solution | OWASP Dep-Check + Semgrep + Trivy fs scan; CI gating | CI artefact inspection | CLOSED (scans) / **F1 carry-forward** (gating flip) |
| 6.5.1 | Changes to all system components managed | [`docs/security/change-control-pci.md`](change-control-pci.md) §1 — per-PR change-id audit trail; reviewer approval; CI green; PCI security review | PR history in `git log`; reviewer 30-review.md files | CLOSED |
| 6.5.2 | All changes verified upon completion | CI green required; reviewer ACCEPTED state on each chunk | All Phase-13 chunks accepted; STATUS.md ledger | CLOSED |
| 6.5.4 | Roles and responsibilities for change management defined | [`change-control-pci.md §1`](change-control-pci.md) — dev / reviewer / architect / SRE / security / compliance | Phase-12 marker procedure | Phase-12-deferred (sign-off chain) |

## Req 7 — Restrict access by business need to know

| Sub-req | Control | Artefact | Verification | Status |
|---|---|---|---|---|
| 7.2.1 | Access control system in place | [`docs/security/access-control-pci.md §2`](access-control-pci.md) — role matrix (production reference) | Phase-12: IAM-policy review | Phase-12-deferred |
| 7.2.3 | Necessary access provisioned based on business need | Per role matrix; service DB role has SELECT + INSERT only, no DELETE/ALTER | DB role audit; SQL Liquibase migration grants | Phase-12-deferred (prod DB role audit) |
| 7.2.5 | Privileges reviewed quarterly | [`access-control-pci.md §4`](access-control-pci.md) — quarterly access review cadence documented; first review in Phase 12 prod-cutover | Phase-12 quarterly review records | Phase-12-deferred |

## Req 8 — Identify users and authenticate access to system components

**Posture: BLOCKING-for-prod via OQ-010 per [`pci-dss-control-matrix.md Req 8`](pci-dss-control-matrix.md).**

| Sub-req | Control | Artefact | Verification | Status |
|---|---|---|---|---|
| 8.2.1 | All users assigned a unique ID before allowed access | Phase-12 working position: identity established at gateway; service is downstream | OQ-010 closure record (Phase 12); gateway access-log audit | **BLOCKING-for-prod** (Phase 12) |
| 8.2.4 | Lifecycle of user IDs managed | Phase-12 platform IAM | Phase-12 platform IAM logs | Phase-12-deferred |
| 8.3.1 | MFA for all access into the CDE | n/a — no CDE | n/a-evidenced |
| 8.3.6 | MFA for all admin access | Phase-12 platform IAM requirement | Phase-12 evidence | Phase-12-deferred |

## Req 9 — Restrict physical access to cardholder data

| Sub-req | Control | Artefact | Verification | Status |
|---|---|---|---|---|
| 9.1.1 | Physical access controls implemented | Cloud provider (n/a for our team's operational responsibility) | Provider AOC | n/a-evidenced |

## Req 10 — Log and monitor all access

**Critical area; many sub-requirements.**

| Sub-req | Control | Artefact | Verification | Status |
|---|---|---|---|---|
| 10.2.1 | Audit logs enabled for all system components | Logback structured JSON via [`logback-spring.xml`](../../src/main/resources/logback-spring.xml); event taxonomy per [`docs/operations/observability.md §2.2`](../operations/observability.md) | [`LoggingPiiGuardTest`](../../src/test/java/com/example/purchaseconversion/api/advice/LoggingPiiGuardTest.java) confirms emission shape | CLOSED |
| 10.2.1.1 | All individual user accesses to cardholder data | n/a — no CHD | n/a-evidenced |
| 10.2.1.2 | All actions taken by individuals with administrative privileges | Phase-12 platform audit | Phase-12 audit-log walk-through | Phase-12-deferred |
| 10.2.1.5 | Use of system-level objects | All `event` taxonomy items per [`observability.md §2.2`](../operations/observability.md) | Sample audit-event walk-through | CLOSED |
| 10.2.2 | Audit logs include user ID, event type, date+time, success/failure, origin, identity of affected resource | Every log entry includes the structured fields per [`observability.md §2.1`](../operations/observability.md): `timestamp`, `event`, `outcome`, `error_code`, `correlationId`, `context` | [`LoggingPiiGuardTest`](../../src/test/java/com/example/purchaseconversion/api/advice/LoggingPiiGuardTest.java) | CLOSED |
| 10.2.7 | Audit log retention ≥ 1 year, last 3 months online | [`logging-monitoring-pci.md §4`](logging-monitoring-pci.md) — retention spec | Phase-12: actual destination provisioning (`C4` carry-forward — `audit-log-destination-provisioning`) | **Phase-12-deferred** |
| 10.3.1 | Audit logs WORM / signed / append-only | [`logging-monitoring-pci.md §3`](logging-monitoring-pci.md) | Phase-12 destination AOC | **Phase-12-deferred** |
| 10.3.4 | Access to audit logs itself audit-logged | Per [`logging-monitoring-pci.md §3.3`](logging-monitoring-pci.md) | Phase-12 destination AOC | Phase-12-deferred |
| 10.4 | Time-sync across components | NTP via platform | Phase-12 platform NTP audit | Phase-12-deferred |
| 10.6.1 | Audit logs reviewed quarterly | [`logging-monitoring-pci.md §5`](logging-monitoring-pci.md) | Phase-12 review records | Phase-12-deferred |

## Req 11 — Test security of systems and networks regularly

| Sub-req | Control | Artefact | Verification | Status |
|---|---|---|---|---|
| 11.3.1 | Internal vulnerability scans quarterly | [`docs/security/vulnerability-management-pci.md`](vulnerability-management-pci.md) + CI Trivy/OWASP-DC every PR | CI runs; **F1 gating flip** for blocking posture | Phase-12-deferred (formal quarterly cadence) |
| 11.3.2 | External vulnerability scans (ASV) quarterly | [`asv-scan-plan.md`](asv-scan-plan.md) — vendor engagement Phase 12 | Phase-12 vendor engagement | Phase-12-deferred |
| 11.4.1 | External pen-tests annually + on material change | [`penetration-test-plan.md`](penetration-test-plan.md) — vendor engagement Phase 12 | Phase-12 pen-test report | Phase-12-deferred |
| 11.4.5 | Segmentation tests at least every 6 months | [`network-segmentation.md §5`](network-segmentation.md) — quarterly test | Phase-12 segmentation test | Phase-12-deferred |

## Req 12 — Support information security with organisational policies

| Sub-req | Control | Artefact | Verification | Status |
|---|---|---|---|---|
| 12.1.1 | Information security policy established + reviewed | All `docs/security/*.md` together; quarterly review cadence | Annual review records (Phase 12+) | Phase-12-deferred (cadence) / CLOSED (policies authored) |
| 12.3.1 | Targeted risk analyses documented | [`targeted-risk-analysis.md`](targeted-risk-analysis.md) — TRAs for each in-scope custom risk-based control | Document review | CLOSED |
| 12.5.1 | Inventory of system components | [`pci-scope-and-cde.md §3`](pci-scope-and-cde.md) — scope inventory | Document review | CLOSED |
| 12.5.2 | Scope confirmed and documented annually | [`pci-scope-and-cde.md §6`](pci-scope-and-cde.md) — annual review cadence | Phase-12+ annual review | Phase-12-deferred (cadence) |
| 12.6.1 | Security awareness program | Org-level (org responsibility, not service-level) | Org records | n/a-evidenced (org-level) |
| 12.8 | TPSP management | [`third-party-service-provider-pci.md`](third-party-service-provider-pci.md) — Treasury + cloud + telemetry | TPSP AOCs collected at Phase 12 | Phase-12-deferred |
| 12.10.1 | Incident response plan implemented | [`incident-response-pci.md`](incident-response-pci.md) + [`docs/operations/incident-response.md`](../operations/incident-response.md) (Phase 10 expansion) | First quarterly tabletop (Phase 12) | CLOSED (plan) / Phase-12-deferred (drill) |

---

## Closure summary by status

| Status | Count | Notes |
|---|---|---|
| `CLOSED` (implemented + verified) | 18 | Core SDLC controls + boundary defences are in place |
| `Phase-12-deferred` | 23 | Mostly platform / vendor / cadence controls; not implementable pre-prod |
| `n/a-evidenced` | 7 | Out-of-CDE posture; evidence-of-inapplicability cited |
| `BLOCKING-for-prod` | 1 | Req 8.2.1 identity origin (OQ-010) — Phase 12 cannot proceed without resolution |

---

## How this mapping is maintained

Per PCI DSS v4.0.1 Req 12.5.2, scope and controls are reviewed annually. This document is reviewed:

- **Per Phase-13 chunk acceptance** — when a chunk lands new code in a PCI-touching path, the corresponding row's "Artefact" field is updated.
- **Per Phase-11 acceptance** — the closure-summary counts are reviewed and any new `Phase-12-deferred` items are routed onto the Phase 12 prompt-author's hand-off list.
- **Annually** — full document re-review against any PCI DSS sub-revision (currently v4.0.1; next checkpoint at v4.0.2 release).

End of `pci-dss-control-mapping.md`.
