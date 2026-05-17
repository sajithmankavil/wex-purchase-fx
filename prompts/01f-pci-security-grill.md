Run the PCI Tier 1 adversarial security grill.

Do not code.

Review the design as:
1. PCI QSA-style reviewer
2. Application security architect
3. Cloud security engineer
4. SRE/security monitoring reviewer
5. Evidence auditor
6. Incident responder

Create/update:
- docs/security/pci-security-grill.md
- docs/security/pci-dss-control-matrix.md
- docs/security/penetration-test-plan.md
- docs/security/asv-scan-plan.md
- docs/security/targeted-risk-analysis.md
- docs/security/compensating-controls.md
- docs/security/incident-response-pci.md
- docs/security/qsa-roc-readiness.md

Rules:
- Any unclear PAN/SAD handling is P0.
- Any missing CDE boundary or segmentation validation is P0.
- Any missing logging redaction strategy is P0.
- Any missing access-control/MFA/least-privilege model for CDE is P0.
- Any unowned PCI control is P1 or P0 depending on impact.
- Any production release without ASV/pen-test/segmentation/evidence plan is P0.
