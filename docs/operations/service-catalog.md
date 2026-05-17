# Service Catalog

> **Status:** Phase 5 (Operational Design Session), 2026-05-17.
> The minimal service identity record for the WEX Purchase Currency Conversion Service. Case-study v1 has placeholder team names; production roll-out replaces placeholders with named individuals.

---

## Service identity

| Field | Value |
|---|---|
| Service | `wex-purchase-fx` |
| Description | Stores USD purchase transactions; retrieves them converted to a target currency via the U.S. Treasury Reporting Rates of Exchange API. |
| Runtime | Java 21 · Spring Boot 3.x · embedded Tomcat |
| Repo | https://github.com/sajithmankavil/wex-purchase-fx (private) |
| Build artefact | Single executable jar (`target/wex-purchase-fx-<version>.jar`) + container image (`ghcr.io/<org>/wex-purchase-fx:<version>`) |
| Deployment target | Single host (case study) · N stateless replicas behind internal LB (production reference) |
| Data classification | Out-of-CDE (PCI Tier-1 hygiene; no payment data accepted) |
| Business criticality | **Tier-2** for production-reference deployment (degradation is recoverable; not an outage of customer-billing critical path). **Tier-3** for case study. |
| Compliance posture | PCI DSS 4.0.1 hygiene; service is out-of-CDE; evidence pending Phase 7 |

## Ownership

| Role | Holder (v1 placeholder) | Notes |
|---|---|---|
| **Service owner** | `@platform-team` (Architect) | Owns the service end-to-end. Final escalation. |
| **Backup owner** | `@platform-team` (Backend lead) | Covers vacation / out-of-band. |
| **On-call rotation primary** | `@wex-fx-oncall` (rotation) | See [oncall-escalation.md](oncall-escalation.md) for cadence. |
| **SRE lead** | `@sre` | SLO ratification + capacity plan. |
| **Security review** | `@secarch` | PCI evidence + audit log reviews. |
| **QA / test owner** | `@qa-lead` | Mutation threshold per-class; chaos plan. |
| **Product owner** | `@product` | Source-rule decisions; SLO target ratification. |
| **Auditor liaison (PCI)** | `@compliance` | QSA evidence walk-throughs (Phase 11+). |

Real names replace the `@<role>` placeholders during pre-production hand-off; the case study can ship with placeholders.

## Dependencies

| Direction | System | Type | Criticality | Failure mode | Degraded behaviour |
|---|---|---|---|---|---|
| Outbound | Treasury Fiscal Data API (`api.fiscaldata.treasury.gov`) | Public HTTPS | Critical for cache miss only | Timeout / 5xx / schema drift / orientation drift | Serve from local cache; `503 UPSTREAM_UNAVAILABLE` only if no eligible local rate (AC-022/023). |
| Outbound | PostgreSQL (prod) / H2 file (local) | Persistence | Critical (system of record for purchases) | Connection pool exhausted; DB unreachable | Readiness DOWN → LB drains → restart. |
| Outbound | Telemetry sinks (Prometheus / OTel collector / log aggregator) | Observability | Non-critical | Best-effort; loss does not affect request handling | Operate dark; alert externally on scrape gap. |
| Outbound | Secrets provider (Vault / KMS) | Startup-only | Critical for `prod`/`staging` | Refuse-to-start on missing `WEX_LOG_HASH_KEY` | Container restart loop until secret available. |
| Inbound | API clients (internal services / scripts) | HTTPS | n/a | n/a | n/a — service is the asset. |
| Inbound | Ingress / API gateway (prod) | TLS termination + identity | Critical (prod) | Service is unreachable | Pre-cutover check; readiness fails if `WEX_GATEWAY_REQUIRED=true` in non-local profile. |

## Operational windows

- **Service availability target:** 24/7 (no maintenance window). Rolling deploys zero-downtime.
- **On-call hours:** business hours v1 (case study); 24/7 once production rollout completes (Phase 12 handoff).
- **Patch cadence:** Vulnerability-management SLA (Phase 7 final) — HIGH within 7 days, CRITICAL within 24 h.
- **Treasury upstream publishing cadence:** quarterly (record_dates land on 03-31, 06-30, 09-30, 12-31). No coordinated client-side action needed; cache refreshes naturally.

## Compliance + evidence pointers

| Concern | Document |
|---|---|
| PCI scope reduction evidence | [pci-scope-and-cde.md](../security/pci-scope-and-cde.md) (Phase 7) |
| Threat model | [threat-model.md](../security/threat-model.md) |
| Audit-log retention + integrity | [logging-monitoring-pci.md](../security/logging-monitoring-pci.md) (Phase 7) |
| Vulnerability management SLA | [vulnerability-management-pci.md](../security/vulnerability-management-pci.md) (Phase 7) |
| Incident response (PCI) | [incident-response-pci.md](../security/incident-response-pci.md) (Phase 7) |

## Service-level dependencies for the readiness gate

- [slo-sli.md](slo-sli.md) — SLI/SLO definitions, Treasury-uptime ceiling formula.
- [error-budget-policy.md](error-budget-policy.md) — burn-rate semantics.
- [capacity-scalability-plan.md](capacity-scalability-plan.md) — replica band, pool sizing, load-test plan.
- [failure-modes-and-resilience.md](failure-modes-and-resilience.md) — detection + mitigation + test mapping.
- [monitoring-alerting.md](monitoring-alerting.md) — concrete alert thresholds and routing.
- [runbook.md](runbook.md) — operator step-by-step.
- [oncall-escalation.md](oncall-escalation.md) — severity ladder + escalation paths.
- [operational-readiness-gate.md](operational-readiness-gate.md) — Phase 5 exit verdict.
