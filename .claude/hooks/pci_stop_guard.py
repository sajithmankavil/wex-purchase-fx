#!/usr/bin/env python3
"""
Advisory stop guard for PCI Tier 1 projects.
Prints a warning if PCI mode is enabled and PCI readiness artifacts are still missing.
"""
from pathlib import Path
import sys

profile = Path('security-profile.yml')
if not profile.exists() or 'pci_dss_tier1' not in profile.read_text(errors='ignore').lower():
    sys.exit(0)

required = [
    'docs/security/pci-scope-and-cde.md',
    'docs/security/pci-dss-control-matrix.md',
    'docs/security/cardholder-data-flow.md',
    'docs/security/tokenization-and-pan-handling.md',
    'docs/security/network-segmentation.md',
    'docs/security/pci-production-readiness-gate.md',
]
missing = [p for p in required if not Path(p).exists()]
if missing:
    print('PCI Tier 1 advisory: required PCI security artifacts are missing:')
    for p in missing:
        print(f'- {p}')
    print('Run prompts/01e-pci-security-design-session.md and prompts/01f-pci-security-grill.md before implementation.')

sys.exit(0)
