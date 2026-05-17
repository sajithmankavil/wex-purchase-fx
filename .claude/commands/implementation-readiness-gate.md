# Implementation Readiness Gate Prompt

Read:

- `CLAUDE.md`
- `docs/requirements/*`
- `docs/planning/requirements-grill.md`
- `docs/planning/design-session.md`
- `docs/planning/design-grill.md`
- `docs/architecture/*`
- `docs/security/*`
- `docs/operations/*`
- `docs/release/test-plan.md`

Do not code.

Update:

- `docs/planning/implementation-readiness-gate.md`
- `docs/release/test-plan.md` if gaps remain

Required output:

1. Whether requirements evidence is sufficient
2. Whether design evidence is sufficient
3. Whether grill findings are resolved
4. Whether first implementation milestone is small/testable
5. Remaining risks
6. Readiness status: BLOCKED, CONDITIONALLY_READY, or READY_FOR_HUMAN_APPROVAL

Important:
Even if status becomes `READY_FOR_HUMAN_APPROVAL`, do not implement. Wait for a human to manually create `.human-approvals/implementation-approved.txt`.
