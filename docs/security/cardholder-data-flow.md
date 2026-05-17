# Cardholder Data Flow

Status: BLOCKED
Owner:

## Data types
- PAN:
- CHD:
- SAD:
- CVV/CVC:
- Track data:
- Tokens/network tokens:

## Flow inventory
| Flow ID | Ingress | Processing component | Storage/logging | Egress | Data elements | Encryption | Owner | Scope impact |
|---|---|---|---|---|---|---|---|---|
| FLOW-001 | TBD | TBD | TBD | TBD | PAN/CHD/SAD/token TBD | TBD | TBD | TBD |

## Diagram
Add a data-flow diagram showing browser/client, API, services, queues, databases, logs, analytics, monitoring, backups, admin tools, and third parties.

## Prohibited flows
- Sensitive authentication data storage after authorization is prohibited.
- PAN in logs, traces, support tickets, metrics, data lake, or analytics is prohibited unless explicitly approved and controlled.

## Evidence
- Data discovery scan:
- Log redaction test:
- Telemetry redaction test:
- Backup content validation:
