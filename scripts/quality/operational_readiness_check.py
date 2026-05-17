#!/usr/bin/env python3
"""
Operational readiness CI gate.

This gate becomes strict once a Phase-12 human owner has created
.human-approvals/pci-production-approved.txt with content
APPROVED_FOR_PCI_PRODUCTION_RELEASE. Until then it runs in advisory mode:
it prints the list of missing required files but exits 0 so implementation
work can proceed.

The gate prevents production-bound work from passing CI with vague or
missing observability, reliability, scalability, and operations artifacts.
"""
from pathlib import Path
import sys

PROD_APPROVAL = Path('.human-approvals/pci-production-approved.txt')

strict = PROD_APPROVAL.exists()

required_files = {
    'docs/planning/operational-design-session.md': [
        'Status:', 'SLI', 'SLO', 'dashboard', 'alert', 'runbook', 'capacity', 'rollback'
    ],
    'docs/planning/reliability-scalability-grill.md': [
        'P0', 'P1', 'P2', 'load', 'failure', 'scale', 'bottleneck'
    ],
    'docs/operations/service-catalog.md': [
        'Owner', 'Criticality', 'Dependencies', 'Runtime', 'Data classification'
    ],
    'docs/operations/observability.md': [
        'Logs', 'Metrics', 'Traces', 'Correlation', 'Dashboards'
    ],
    'docs/operations/monitoring-alerting.md': [
        'Alert', 'Threshold', 'Severity', 'Runbook', 'Owner'
    ],
    'docs/operations/slo-sli.md': [
        'SLI', 'SLO', 'Error budget', 'Measurement', 'Window'
    ],
    'docs/operations/error-budget-policy.md': [
        'Burn rate', 'Freeze', 'Escalation', 'Policy'
    ],
    'docs/operations/capacity-scalability-plan.md': [
        'Expected load', 'Peak load', 'Growth', 'Bottleneck', 'Scale strategy', 'Load test'
    ],
    'docs/operations/failure-modes-and-resilience.md': [
        'Failure mode', 'Impact', 'Detection', 'Mitigation', 'Recovery'
    ],
    'docs/operations/runbook.md': [
        'Triage', 'Rollback', 'Dashboard', 'Logs', 'Escalation'
    ],
    'docs/operations/oncall-escalation.md': [
        'Severity', 'Primary owner', 'Escalation', 'Response target'
    ],
    'docs/operations/operational-readiness-gate.md': [
        'Status:', 'Go/No-Go', 'Evidence', 'P0', 'P1'
    ],
}

if not strict:
    print('Operational readiness check: advisory mode. No production-approval marker present yet.')
    missing = [p for p in required_files if not Path(p).exists()]
    if missing:
        print('Advisory missing files:')
        for p in missing:
            print(f'- {p}')
    sys.exit(0)

failures = []
for rel, tokens in required_files.items():
    path = Path(rel)
    if not path.exists():
        failures.append(f'Missing required file: {rel}')
        continue
    text = path.read_text(errors='ignore')
    for token in tokens:
        if token.lower() not in text.lower():
            failures.append(f'{rel} is missing required content marker: {token}')
    vague_markers = ['TODO', 'TBD', 'to be defined', 'fill this in', 'placeholder']
    for marker in vague_markers:
        if marker.lower() in text.lower():
            failures.append(f'{rel} contains unresolved placeholder marker: {marker}')

# Production readiness must not be GO if operational gate is blocked.
org = Path('docs/operations/operational-readiness-gate.md')
if org.exists():
    text = org.read_text(errors='ignore').lower()
    if 'status: blocked' in text or 'status: not_ready' in text or 'no-go' in text:
        failures.append('Operational readiness gate is blocked/no-go.')

if failures:
    print('Operational readiness check failed:')
    for f in failures:
        print(f'- {f}')
    sys.exit(1)

print('Operational readiness check passed.')
