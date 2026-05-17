# Prompt: Reliability and Scalability Grill

```text
Act as a principal SRE, distributed-systems architect, production incident commander, and capacity engineer.

Do not code.

Attack the design for reliability, scalability, and operability weaknesses.

Create/update:
- docs/planning/reliability-scalability-grill.md
- docs/operations/failure-modes-and-resilience.md
- docs/operations/capacity-scalability-plan.md
- docs/operations/monitoring-alerting.md

Review:
- Single points of failure
- Bottlenecks and saturation risks
- Traffic spikes and load-shedding behavior
- Data growth and migration risks
- Dependency failures and retry storms
- Queue buildup and backpressure
- Cache failure/staleness
- Partial outages and degraded mode
- Observability blind spots
- Alert noise and missing user-impact alerts
- Rollback and recovery weaknesses
- RTO/RPO gaps
- Cost explosion risks

Return P0/P1/P2 findings and required design changes.
Implementation is blocked while any P0 or unresolved P1 exists.
```
