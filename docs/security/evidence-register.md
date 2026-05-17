# PCI Evidence Register

> **Status:** Phase 7 (PCI Security Design Session), 2026-05-17.
> **Owner:** SecArch + Compliance.
>
> Central index of every piece of evidence the QSA, internal audit, or post-incident review may want. Each row points at the artefact, the cadence, the retention, and the most recent collection date (populated at Phase 12+ once production data exists).

---

| Evidence ID | PCI Req | Evidence description | Location | Owner | Frequency | Retention | Last collected | Status |
|---|---|---|---|---|---|---|---|---|
| **EVD-001** | Req 1 (network controls) | Egress allow-list audit; ingress allow-list audit; default-deny rule confirmation | `evidence/network/egress-audit-YYYYMM.json` + ingress audit; configuration-as-code git history | Platform + SRE | Monthly | 1 year | n/a (pre-prod) | designed |
| **EVD-002** | Req 2 (secure config) | CIS-equivalent benchmark report per replica image; refuse-to-start invariant tests (`LoggingHashKeyStartupTest`, AliasTableStartupIT); configuration drift detection | `evidence/config-baseline/` | SRE + SecArch | Per release | 1 year | n/a | designed |
| **EVD-003** | Req 3 (stored account data — n/a) | DB column-audit confirming no PAN-shaped column; cardholder-data-flow walk-through; boundary-guard probe set results | `evidence/cdh-evidence/` (DB schema dumps + flow review + probe-set output) | SecArch | Quarterly + per release with schema change | 1 year | n/a | designed |
| **EVD-004** | Req 4 (transmission crypto) | TLS scan reports (inbound + outbound); cert validity inventory; outbound TLS test in CI; CA bundle pin verification | `evidence/tls-scans/` | Platform + SecArch | Per release + quarterly | 1 year | n/a | designed |
| **EVD-005** | Req 5 (malware protection) | Provider AOC for platform malware protection; container image scan reports per release | `evidence/image-scans/` + TPSP AOC archive | Platform + SecArch | Per release | 1 year | n/a | designed |
| **EVD-006** | Req 6 (secure SDLC) | SAST + SCA + secret-scan + DAST scan reports; SBOM per release; change-control records (PR descriptions); image-signature verification logs; emergency-change retrospectives | `evidence/sdlc/` (multiple subdirs) | SecArch + Architect | Per PR + per release | 1 year (scans), per repo lifetime (PR records) | n/a | designed |
| **EVD-007** | Req 7 (need-to-know access) | Quarterly access-review records (platform IAM, secrets-store ACL, DB roles, audit-log access) | `evidence/access-reviews/YYYY-QN/` | SecArch + Compliance | Quarterly | 1 year | n/a | designed |
| **EVD-008** | Req 8 (identity + authn) | Platform MFA enforcement evidence; service-account credential rotation log; gateway authentication logs (post-Phase 12); OQ-010 closure record | `evidence/identity/` | SecArch + Platform | Per quarter + per significant change | 1 year | n/a (Phase 12 dependency) | designed (BLOCKING-for-prod via OQ-010) |
| **EVD-009** | Req 9 (physical access) | Cloud provider AOCs (current; ≤ 12 months old) | `evidence/tpsp-aocs/` | Compliance | Annually | Per contract lifetime | n/a | designed |
| **EVD-010** | Req 10 (logging + monitoring) | Audit-log destination access logs; sample audit-event walk-through (1000 lines/month); log-redaction tests (AC-032 / AC-032b); time-sync drift alerts; log-hash-key rotation log | `evidence/audit-logs/` + audit-destination metadata | SecArch + SRE | Monthly (sample) + per release (tests) + continuous (time-sync) | 1 year (PCI minimum); 3 months online | n/a | designed |
| **EVD-011** | Req 11 (security testing) | ASV scan reports (quarterly); internal vulnerability scan reports (quarterly); pen-test report (annual + material change); segmentation test results (quarterly) | `evidence/security-tests/` (multiple subdirs) | SecArch + Compliance | Per cadence above | 1 year (scans), 3 years (pen-test reports — PCI standard) | n/a | designed |
| **EVD-012** | Req 12 (security program) | Security policies; risk-register snapshots; threat-model versions; TPSP AOCs; incident-response post-mortems; security-awareness-training records (org-level) | `evidence/security-program/` | SecArch + Compliance + Service owner | Annual + per incident | 3 years (per PCI standard) | n/a | designed |

## Evidence collection automation (Phase-13 task)

Each row above will be automated where feasible. Phase 13:

- CI workflows publish per-release evidence to `evidence/<row>/` (or platform-equivalent durable storage with the same WORM / retention properties as the audit-log destination).
- A monthly evidence-collection job (cron) aggregates EVD-001, EVD-007, EVD-010 sample evidence.
- A quarterly review job summarises status of every EVD-* row for the Compliance review.

## Status definitions

| Status | Meaning |
|---|---|
| **designed** | Phase-7 artefact exists; cadence + retention + owner defined; evidence-collection mechanism designed; **implementation lands in Phase 13** |
| **collected** | Phase-12 / production state — at least one cycle of evidence collected and reviewed |
| **gap** | Evidence-collection mechanism missing or broken; SecArch must restore before next Compliance review |
| **n/a** | The PCI control does not apply; the evidence above is the "n/a justification" |

The Phase-7 status for every row is **designed**. Status moves to **collected** in Phase 12 / 13.

## Linked artefacts

- [pci-dss-control-matrix.md](pci-dss-control-matrix.md) — the requirement-to-control map.
- [logging-monitoring-pci.md](logging-monitoring-pci.md) — audit-log destination details.
- [vulnerability-management-pci.md](vulnerability-management-pci.md) — scan cadence + SLA.
- [secure-sdlc-pci.md](secure-sdlc-pci.md) — CI evidence pipeline.
- [access-control-pci.md](access-control-pci.md) — IAM review evidence.
- [third-party-service-provider-pci.md](third-party-service-provider-pci.md) — TPSP AOC inventory.
