#!/usr/bin/env python3
"""
PCI Tier 1 readiness CI gate.

Advisory before code/security approval. Strict after implementation code exists,
PCI security approval exists, or production approval is requested.
"""
from pathlib import Path
import sys

PROFILE = Path('security-profile.yml')
SECURITY_APPROVAL = Path('.human-approvals/pci-security-approved.txt')
PROD_APPROVAL = Path('.human-approvals/pci-production-approved.txt')
CODE_MARKERS = [Path('src'), Path('app'), Path('apps'), Path('services'), Path('packages'), Path('infra')]

if not PROFILE.exists() or 'pci_dss_tier1' not in PROFILE.read_text(errors='ignore').lower():
    print('PCI readiness check skipped: security-profile.yml is not in pci_dss_tier1 mode.')
    sys.exit(0)

def has_non_placeholder_files(root: Path) -> bool:
    if not root.exists():
        return False
    ignored = {'README.md', '.gitkeep'}
    for item in root.rglob('*') if root.is_dir() else []:
        if item.is_file() and item.name not in ignored:
            return True
    return root.is_file()

strict = SECURITY_APPROVAL.exists() or PROD_APPROVAL.exists() or any(has_non_placeholder_files(p) for p in CODE_MARKERS)

required_files = {
    'docs/security/pci-scope-and-cde.md': [
        'Status:', 'CDE', 'in-scope', 'connected-to', 'out-of-scope', 'segmentation', 'data flow', 'owner'
    ],
    'docs/security/pci-dss-control-matrix.md': [
        'Req 1', 'Req 2', 'Req 3', 'Req 4', 'Req 5', 'Req 6', 'Req 7', 'Req 8', 'Req 9', 'Req 10', 'Req 11', 'Req 12', 'Evidence', 'Owner', 'Status'
    ],
    'docs/security/cardholder-data-flow.md': [
        'PAN', 'CHD', 'SAD', 'token', 'ingress', 'egress', 'storage', 'logs', 'diagram'
    ],
    'docs/security/cardholder-data-classification.md': [
        'PAN', 'CHD', 'SAD', 'CVV', 'track', 'retention', 'redaction', 'masking'
    ],
    'docs/security/tokenization-and-pan-handling.md': [
        'PAN', 'tokenization', 'storage decision', 'masking', 'logging', 'retention', 'scope reduction'
    ],
    'docs/security/encryption-key-management.md': [
        'encryption', 'key management', 'rotation', 'HSM', 'KMS', 'separation of duties', 'crypto inventory'
    ],
    'docs/security/network-segmentation.md': [
        'CDE', 'segmentation', 'firewall', 'allowlist', 'ingress', 'egress', 'validation', 'diagram'
    ],
    'docs/security/secure-sdlc-pci.md': [
        'secure SDLC', 'code review', 'threat model', 'SAST', 'DAST', 'dependency', 'change control'
    ],
    'docs/security/vulnerability-management-pci.md': [
        'ASV', 'vulnerability', 'patching', 'severity', 'SLA', 'scan', 'remediation'
    ],
    'docs/security/logging-monitoring-pci.md': [
        'audit log', 'CDE', 'PAN redaction', 'SIEM', 'alert', 'retention', 'time synchronization'
    ],
    'docs/security/access-control-pci.md': [
        'least privilege', 'MFA', 'role', 'service account', 'review', 'privileged access'
    ],
    'docs/security/incident-response-pci.md': [
        'incident', 'payment brand', 'acquirer', 'forensic', 'P0', 'evidence', 'notification'
    ],
    'docs/security/change-control-pci.md': [
        'change control', 'approval', 'rollback', 'testing', 'risk', 'emergency change'
    ],
    'docs/security/evidence-register.md': [
        'Evidence ID', 'Control', 'Owner', 'Location', 'Frequency', 'Retention'
    ],
    'docs/security/qsa-roc-readiness.md': [
        'QSA', 'ROC', 'AOC', 'sampling', 'evidence', 'gap', 'owner'
    ],
    'docs/security/asv-scan-plan.md': [
        'ASV', 'quarterly', 'external scan', 'scope', 'remediation', 'evidence'
    ],
    'docs/security/penetration-test-plan.md': [
        'penetration test', 'segmentation test', 'CDE', 'scope', 'remediation', 'retest'
    ],
    'docs/security/targeted-risk-analysis.md': [
        'targeted risk analysis', 'frequency', 'risk', 'control', 'rationale', 'approval'
    ],
    'docs/security/compensating-controls.md': [
        'compensating control', 'constraint', 'objective', 'risk', 'validation', 'approval'
    ],
    'docs/security/third-party-service-provider-pci.md': [
        'service provider', 'responsibility matrix', 'AOC', 'SLA', 'monitoring', 'shared responsibility'
    ],
    'docs/security/secure-config-hardening.md': [
        'hardening', 'baseline', 'default password', 'configuration', 'drift', 'evidence'
    ],
    'docs/security/backup-recovery-pci.md': [
        'backup', 'restore test', 'encryption', 'retention', 'CDE', 'access'
    ],
    'docs/security/pci-production-readiness-gate.md': [
        'Status:', 'GO/NO-GO', 'P0', 'P1', 'QSA', 'ROC', 'AOC', 'ASV', 'evidence'
    ],
}

if not strict:
    print('PCI readiness check: advisory mode. No implementation/security approval/app code detected yet.')
    missing = [p for p in required_files if not Path(p).exists()]
    if missing:
        print('Advisory missing PCI files:')
        for p in missing:
            print(f'- {p}')
    sys.exit(0)

failures = []
for rel, tokens in required_files.items():
    path = Path(rel)
    if not path.exists():
        failures.append(f'Missing required PCI file: {rel}')
        continue
    text = path.read_text(errors='ignore')
    for token in tokens:
        if token.lower() not in text.lower():
            failures.append(f'{rel} is missing required content marker: {token}')
    vague_markers = ['TODO', 'TBD', 'to be defined', 'fill this in', 'placeholder']
    for marker in vague_markers:
        if marker.lower() in text.lower():
            failures.append(f'{rel} contains unresolved placeholder marker: {marker}')

prod_gate = Path('docs/security/pci-production-readiness-gate.md')
if prod_gate.exists():
    text = prod_gate.read_text(errors='ignore').lower()
    if 'status: blocked' in text or 'status: not_ready' in text or 'no-go' in text:
        failures.append('PCI production readiness gate is blocked/no-go.')

if failures:
    print('PCI Tier 1 readiness check failed:')
    for f in failures:
        print(f'- {f}')
    sys.exit(1)

print('PCI Tier 1 readiness check passed.')
