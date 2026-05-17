Before starting, verify `.human-approvals/implementation-approved.txt` exists and contains `APPROVED_FOR_IMPLEMENTATION`. If absent, stop and return to planning gates.

# Prompt: Implement Milestone

```text
Implement the approved milestone only.

Rules:
- Create or use a feature branch.
- Keep changes scoped to the milestone.
- Add or update tests.
- Do not touch secrets.
- Do not modify production deployment files unless explicitly required.
- Run the appropriate quality checks.
- If checks fail, fix the issue instead of weakening tests.
- Produce a PR-style summary.

Follow CLAUDE.md completion format.
```
