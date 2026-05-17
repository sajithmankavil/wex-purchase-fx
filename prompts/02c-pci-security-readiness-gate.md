Run the PCI Tier 1 security readiness gate.

Do not code.

Read all docs/security/*.md and docs/operations/*.md.

Create/update:
- docs/security/pci-production-readiness-gate.md
- docs/security/backup-recovery-pci.md
- docs/security/secure-config-hardening.md

The gate must state exactly one:

```text
Status: BLOCKED
Status: CONDITIONALLY_READY
Status: READY_FOR_HUMAN_PCI_SECURITY_APPROVAL
```

Return GO/NO-GO. Do not mark ready if any P0 exists or if any P1 lacks owner/date/mitigation.

Required checks:
- CDE scope defined and reviewed
- Data flows documented
- PAN/SAD storage and logging controls defined
- PCI DSS Req 1-12 mapped to owners/evidence
- Threat model complete
- Segmentation and firewall approach testable
- Key management and encryption defined
- Access control and MFA defined
- Secure SDLC gates defined
- ASV, vulnerability, penetration, and segmentation testing planned
- Audit logging, SIEM, alerting, time sync, and retention defined
- Incident response and breach/escalation process defined
- Third-party responsibility matrix and AOC process defined
- Evidence register ready for QSA/ROC
