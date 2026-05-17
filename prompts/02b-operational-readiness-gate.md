# Prompt: Operational Readiness Gate

```text
Evaluate whether implementation may proceed from an operational-readiness standpoint.

Do not code.

Create/update:
- docs/operations/operational-readiness-gate.md

The gate must state exactly one:
- Status: BLOCKED
- Status: CONDITIONALLY_READY
- Status: READY_FOR_HUMAN_APPROVAL

Check evidence for:
- Observability design
- Service catalog and ownership
- SLI/SLO/error-budget definitions
- Monitoring and alert specifications
- Dashboards
- Health checks and synthetic checks
- Capacity/scalability plan
- Load/performance test plan
- Failure modes and resilience strategy
- Incident response and on-call/escalation process
- Runbook quality
- Rollback triggers and validation
- P0/P1 operational risks

Do not mark READY_FOR_HUMAN_APPROVAL if any P0 exists or if any P1 lacks a concrete mitigation.
```
