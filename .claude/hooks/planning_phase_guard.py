#!/usr/bin/env python3
"""
Claude Code Write/Edit planning phase guard.

Until a human creates `.human-approvals/implementation-approved.txt` containing
`APPROVED_FOR_IMPLEMENTATION`, this hook blocks edits outside planning/design
documentation. This prevents accidental implementation before requirements,
design session, design grill, and human approval are complete.
"""
import json
import os
from pathlib import Path
import sys

APPROVAL_FILE = Path(".human-approvals/implementation-approved.txt")
APPROVAL_TOKEN = "APPROVED_FOR_IMPLEMENTATION"

def approved() -> bool:
    try:
        return APPROVAL_FILE.exists() and APPROVAL_TOKEN in APPROVAL_FILE.read_text(errors="ignore")
    except Exception:
        return False

def load_payload():
    raw = sys.stdin.read()
    try:
        return json.loads(raw) if raw.strip() else {}
    except Exception:
        return {}

def extract_paths(obj, parent_key=""):
    paths = []
    if isinstance(obj, dict):
        for k, v in obj.items():
            key = str(k).lower()
            if key in {"file_path", "filepath", "path", "target_file", "filename"} and isinstance(v, str):
                paths.append(v)
            paths.extend(extract_paths(v, key))
    elif isinstance(obj, list):
        for item in obj:
            paths.extend(extract_paths(item, parent_key))
    return paths

def normalize(path: str) -> str:
    path = path.replace("\\", "/").strip()
    while path.startswith("./"):
        path = path[2:]
    return path

ALLOWED_BEFORE_APPROVAL = (
    "docs/requirements/",
    "docs/planning/",
    "docs/architecture/",
    "docs/security/",
    "docs/operations/",
    "docs/release/",
)

if approved():
    sys.exit(0)

payload = load_payload()
paths = [normalize(p) for p in extract_paths(payload)]

# If the schema changes and no path can be extracted, do not block blindly.
# Other guards still apply.
if not paths:
    sys.exit(0)

for path in paths:
    if path.startswith(".human-approvals/"):
        print("BLOCKED: Claude Code must not create or edit human approval markers.", file=sys.stderr)
        sys.exit(2)
    if not path.startswith(ALLOWED_BEFORE_APPROVAL):
        print("BLOCKED: implementation is not approved yet.", file=sys.stderr)
        print("Complete requirements grill, design session, design grill, and implementation-readiness gate first.", file=sys.stderr)
        print("Then a human must manually create .human-approvals/implementation-approved.txt with APPROVED_FOR_IMPLEMENTATION.", file=sys.stderr)
        print(f"Attempted path: {path}", file=sys.stderr)
        sys.exit(2)

sys.exit(0)
