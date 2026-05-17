#!/usr/bin/env python3
"""
Claude Code PreToolUse Bash guard.

This script is intentionally conservative and robust to input schema changes.
It reads hook JSON from stdin when available, extracts a command-like string,
and blocks obviously dangerous commands.
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

blocked_patterns = [
    r"\brm\s+-rf\s+/",
    r"\brm\s+-rf\s+~",
    r"\bterraform\s+apply\b",
    r"\bterraform\s+destroy\b",
    r"\bkubectl\s+apply\b",
    r"\bkubectl\s+delete\b",
    r"\baws\s+secretsmanager\s+get-secret-value\b",
    r"\bgcloud\s+secrets\s+versions\s+access\b",
    r"\baz\s+keyvault\s+secret\s+show\b",
    r"\bprintenv\b",
    r"(^|\s)env($|\s)",
    r"\bcat\s+\.env\b",
    r"\becho\s+\$[A-Z0-9_]*(TOKEN|SECRET|KEY|PASSWORD)",
]

for pattern in blocked_patterns:
    if re.search(pattern, text, flags=re.IGNORECASE | re.MULTILINE):
        print(f"BLOCKED by enterprise guardrail: command matched unsafe pattern: {pattern}", file=sys.stderr)
        sys.exit(2)

sys.exit(0)
