# Human Approval Markers

Claude Code must not create or edit files in this folder.

To allow implementation after planning/design/grill reviews are complete, a human should manually create:

```text
.human-approvals/implementation-approved.txt
```

The file must contain exactly:

```text
APPROVED_FOR_IMPLEMENTATION
```

Do not add production secrets or credentials here.


## PCI/security approval markers

For PCI Tier 1 projects, implementation remains blocked until a human manually creates:

```text
.human-approvals/pci-security-approved.txt
```

with exact content:

```text
APPROVED_FOR_PCI_SECURITY_IMPLEMENTATION
```

Claude Code must not create or edit this file.

Production deployment remains blocked until a human manually creates:

```text
.human-approvals/pci-production-approved.txt
```

with exact content:

```text
APPROVED_FOR_PCI_PRODUCTION_RELEASE
```

This approval should only happen after QSA/security/compliance review, PCI production readiness evidence, operational readiness, and release readiness have all passed.
