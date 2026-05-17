# Design Grill Prompt

Read:

- `CLAUDE.md`
- `docs/requirements/*`
- `docs/planning/design-session.md`
- `docs/architecture/*`
- `docs/security/*`
- `docs/operations/*`

Do not code.

Run an adversarial design grill. Be strict. The goal is to find design, security, reliability, testability, deployment, and auditability flaws before implementation.

Update:

- `docs/planning/design-grill.md`
- `docs/security/threat-model.md`
- `docs/operations/observability.md`
- `docs/operations/rollback-plan.md`
- `docs/release/test-plan.md`

Required output:

1. P0/P1/P2 findings
2. Whether any P0 blocks implementation
3. Whether any P1 requires human risk acceptance
4. Design changes required
5. Test-plan changes required
6. Verdict: BLOCKED, CONDITIONAL_PASS, or PASS_TO_IMPLEMENTATION_READINESS
