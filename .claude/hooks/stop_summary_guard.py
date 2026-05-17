#!/usr/bin/env python3
"""
Stop hook reminder.

This is intentionally non-blocking. It nudges the agent to provide audit-style
completion output.
"""
print("""
Enterprise completion checklist:
- Summary
- Files changed
- Tests run
- Requirement coverage
- Risks
- Follow-ups
""".strip())
