# PCI Logging and Monitoring

Status: BLOCKED
Owner:

## Logging rules
- Audit log all CDE security events, auth events, admin actions, access to CHD/PAN, configuration changes, deployment changes, and security-control changes.
- Never log PAN, SAD, CVV/CVC, track data, tokens, passwords, session cookies, or secrets.
- Logs must include correlation/request IDs and synchronized timestamps.

## Log sources
| Source | Events | PAN redaction? | SIEM destination | Retention | Owner | Evidence |
|---|---|---|---|---|---|---|
| TBD | TBD | TBD | TBD | TBD | TBD | TBD |

## Alerts
| Alert | Condition | Severity | Runbook | Owner | Evidence |
|---|---|---|---|---|---|
| TBD | TBD | TBD | TBD | TBD | TBD |

## Required tests
- PAN redaction unit/integration tests:
- Log sampling review:
- SIEM ingestion validation:
- Time synchronization validation:
