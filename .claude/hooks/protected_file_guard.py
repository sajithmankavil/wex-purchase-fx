#!/usr/bin/env python3
"""
Claude Code Write/Edit guard for protected files.

Blocks edits to secrets and sensitive production deployment paths unless
the human explicitly changes this guardrail.
"""
import json
import re
import sys

raw = sys.stdin.read()
try:
    payload = json.loads(raw) if raw.strip() else {}
except Exception:
    payload = {}

def collect_strings(obj):
    if isinstance(obj, str):
        yield obj
    elif isinstance(obj, dict):
        for value in obj.values():
            yield from collect_strings(value)
    elif isinstance(obj, list):
        for value in obj:
            yield from collect_strings(value)

text = "\n".join(collect_strings(payload))

protected_patterns = [
    r"(^|/)\.human-approvals/",
    r"(^|/)\.env(\.|$)",
    r"\.pem$",
    r"\.key$",
    r"\.p12$",
    r"\.pfx$",
    r"(^|/)secrets/",
    r"(^|/)credentials/",
    r"service-account.*\.json$",
    r"infra/prod/",
    r"\.kube/config",
]

for pattern in protected_patterns:
    if re.search(pattern, text, flags=re.IGNORECASE | re.MULTILINE):
        print(f"BLOCKED by protected-file guardrail: matched {pattern}", file=sys.stderr)
        print("Ask the human to make or explicitly approve this sensitive change.", file=sys.stderr)
        sys.exit(2)

sys.exit(0)
