#!/usr/bin/env python3
"""
Claude Code Stop hook: warns when implementation approval exists but operational
readiness artifacts are missing or incomplete. This does not replace CI; CI is
the hard release gate.
"""
from pathlib import Path
import sys

approval = Path('.human-approvals/implementation-approved.txt')
if not approval.exists():
    sys.exit(0)

required = [
    'docs/planning/operational-design-session.md',
    'docs/planning/reliability-scalability-grill.md',
    'docs/operations/service-catalog.md',
    'docs/operations/observability.md',
    'docs/operations/monitoring-alerting.md',
    'docs/operations/slo-sli.md',
    'docs/operations/error-budget-policy.md',
    'docs/operations/capacity-scalability-plan.md',
    'docs/operations/failure-modes-and-resilience.md',
    'docs/operations/runbook.md',
    'docs/operations/oncall-escalation.md',
    'docs/operations/operational-readiness-gate.md',
]
missing = [p for p in required if not Path(p).exists()]
if missing:
    print('WARNING: operational readiness is incomplete. Missing:', file=sys.stderr)
    for p in missing:
        print(f'- {p}', file=sys.stderr)
    sys.exit(0)
print('Operational readiness artifact check: required files exist.', file=sys.stderr)
