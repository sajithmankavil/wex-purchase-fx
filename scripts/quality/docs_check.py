#!/usr/bin/env python3
from pathlib import Path
import sys

required = [
    "CLAUDE.md",
    "docs/requirements/source-requirements.md",
    "docs/requirements/functional-requirements.md",
    "docs/requirements/non-functional-requirements.md",
    "docs/requirements/acceptance-criteria.md",
    "docs/requirements/traceability-matrix.md",
    "docs/architecture/system-context.md",
    "docs/architecture/component-design.md",
    "docs/security/threat-model.md",
    "docs/operations/runbook.md",
    "docs/operations/rollback-plan.md",
    "docs/planning/requirements-grill.md",
    "docs/planning/design-session.md",
    "docs/planning/design-grill.md",
    "docs/planning/implementation-readiness-gate.md",
    "docs/release/release-checklist.md",
]

missing = [p for p in required if not Path(p).exists()]
if missing:
    print("Missing required docs/files:")
    for item in missing:
        print(f"- {item}")
    sys.exit(1)

print("Required enterprise docs/files exist.")
