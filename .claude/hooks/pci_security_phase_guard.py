#!/usr/bin/env python3
"""
PCI Tier 1 security phase guard.

When security-profile.yml declares pci_dss_tier1 mode, this hook blocks Claude Code
from editing implementation/runtime/infrastructure paths until a human-created PCI
security approval marker exists. It also blocks production-sensitive paths unless a
separate production approval marker exists.
"""
import json
from pathlib import Path
import sys

PROFILE = Path('security-profile.yml')
SECURITY_APPROVAL = Path('.human-approvals/pci-security-approved.txt')
SECURITY_TOKEN = 'APPROVED_FOR_PCI_SECURITY_IMPLEMENTATION'
PROD_APPROVAL = Path('.human-approvals/pci-production-approved.txt')
PROD_TOKEN = 'APPROVED_FOR_PCI_PRODUCTION_RELEASE'

PCI_MODE_MARKERS = ['pci_dss_tier1', 'pci-tier1', 'tier1']

DOC_ALLOWED_BEFORE_SECURITY_APPROVAL = (
    'docs/',
    'prompts/',
    '.claude/',
    '.github/workflows/',
    'scripts/',
    'tests/README.md',
    'README.md',
    'BUNDLE_MANIFEST.md',
    'CLAUDE.md',
    'Makefile',
    'CODEOWNERS',
    '.gitignore',
    '.env.example',
    'security-profile.yml',
)

IMPLEMENTATION_OR_RUNTIME_PREFIXES = (
    'src/', 'app/', 'apps/', 'services/', 'packages/', 'lib/', 'api/', 'backend/', 'frontend/',
    'server/', 'client/', 'workers/', 'jobs/', 'cmd/', 'internal/', 'pkg/', 'infra/',
    'k8s/', 'helm/', 'terraform/', 'pulumi/', 'docker/', 'Dockerfile', 'compose.yml', 'docker-compose.yml'
)

PROD_SENSITIVE_PREFIXES = (
    'infra/prod/', 'k8s/prod/', 'helm/prod/', 'terraform/prod/', '.github/workflows/deploy-prod.yml'
)

def pci_mode_enabled() -> bool:
    if not PROFILE.exists():
        return False
    text = PROFILE.read_text(errors='ignore').lower()
    return any(marker in text for marker in PCI_MODE_MARKERS)

def marker_ok(path: Path, token: str) -> bool:
    try:
        return path.exists() and token in path.read_text(errors='ignore')
    except Exception:
        return False

def load_payload():
    raw = sys.stdin.read()
    try:
        return json.loads(raw) if raw.strip() else {}
    except Exception:
        return {}

def extract_paths(obj):
    paths = []
    if isinstance(obj, dict):
        for k, v in obj.items():
            key = str(k).lower()
            if key in {'file_path', 'filepath', 'path', 'target_file', 'filename'} and isinstance(v, str):
                paths.append(v)
            paths.extend(extract_paths(v))
    elif isinstance(obj, list):
        for item in obj:
            paths.extend(extract_paths(item))
    return paths

def norm(path: str) -> str:
    p = path.replace('\\', '/').strip()
    while p.startswith('./'):
        p = p[2:]
    return p

def allowed_doc_path(path: str) -> bool:
    return path.startswith(DOC_ALLOWED_BEFORE_SECURITY_APPROVAL) or path in DOC_ALLOWED_BEFORE_SECURITY_APPROVAL

def is_impl_path(path: str) -> bool:
    return path.startswith(IMPLEMENTATION_OR_RUNTIME_PREFIXES) or path in IMPLEMENTATION_OR_RUNTIME_PREFIXES

def is_prod_sensitive(path: str) -> bool:
    return path.startswith(PROD_SENSITIVE_PREFIXES) or path in PROD_SENSITIVE_PREFIXES

if not pci_mode_enabled():
    sys.exit(0)

payload = load_payload()
paths = [norm(p) for p in extract_paths(payload)]
if not paths:
    sys.exit(0)

security_approved = marker_ok(SECURITY_APPROVAL, SECURITY_TOKEN)
prod_approved = marker_ok(PROD_APPROVAL, PROD_TOKEN)

for path in paths:
    if path.startswith('.human-approvals/'):
        print('BLOCKED: Claude Code must not create or edit PCI/human approval markers.', file=sys.stderr)
        sys.exit(2)
    if is_prod_sensitive(path) and not prod_approved:
        print('BLOCKED: PCI Tier 1 production-sensitive path requires human PCI production approval.', file=sys.stderr)
        print('Required marker: .human-approvals/pci-production-approved.txt with APPROVED_FOR_PCI_PRODUCTION_RELEASE.', file=sys.stderr)
        print(f'Attempted path: {path}', file=sys.stderr)
        sys.exit(2)
    if is_impl_path(path) and not security_approved:
        print('BLOCKED: PCI Tier 1 implementation/runtime/infra edits require PCI security approval first.', file=sys.stderr)
        print('Complete PCI security design, PCI grill, PCI readiness gate, and human security approval.', file=sys.stderr)
        print('Required marker: .human-approvals/pci-security-approved.txt with APPROVED_FOR_PCI_SECURITY_IMPLEMENTATION.', file=sys.stderr)
        print(f'Attempted path: {path}', file=sys.stderr)
        sys.exit(2)
    if not security_approved and not allowed_doc_path(path):
        print('BLOCKED: PCI Tier 1 mode allows only docs/prompts/hooks/CI/security scaffolding before PCI security approval.', file=sys.stderr)
        print(f'Attempted path: {path}', file=sys.stderr)
        sys.exit(2)

sys.exit(0)
