# Monitoring and Alerting

Status: NOT_ASSESSED

## Alert philosophy
Alerts must be actionable, tied to user impact or imminent risk, and linked to a runbook.

## Alert catalog
| Alert | Severity | Threshold | Window | Owner | Runbook | User impact | Auto-remediation |
|---|---|---|---|---|---|---|---|
| High error rate | SEV2 | | | | | | |
| Latency SLO burn | SEV2 | | | | | | |
| Dependency failure | SEV2 | | | | | | |
| Resource saturation | SEV2 | | | | | | |
| Data correctness anomaly | SEV1/SEV2 | | | | | | |

## Burn-rate alerts
Define fast-burn and slow-burn SLO alerts.

## Noise controls
Define deduplication, grouping, maintenance windows, and alert ownership.
