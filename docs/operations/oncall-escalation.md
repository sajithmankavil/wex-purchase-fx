# On-Call and Escalation

> **Status:** Phase 5 (Operational Design Session), 2026-05-17.
> Severity ladder, on-call rotation contract, escalation paths, and incident-response process. Closes E1 / E3 / E4 / E8 from the Phase-4 hardening pass (placeholder ownership pending real organisational mapping; the structure here is the durable record).

---

## 1. Severity ladder

| Severity | Definition | Response target | Page channel | Communication |
|---|---|---|---|---|
| **SEV1** | Major user-visible outage **or** data correctness risk. Triggers: 5xx rate > 5 % for 5 min · readiness DOWN on > 50 % replicas · suspected PCI compromise · `rate_orientation_drift_warn` ≥ Phase-5-ratified threshold · data corruption / poisoning · audit-log tamper alert. | **5 min** | Pager (PagerDuty / equivalent) | Internal incident channel within 15 min; external status page within 30 min if customer-visible. |
| **SEV2** | Material degradation or elevated error/latency. Triggers: 5xx rate > 1 % for 5 min · p99 > 2× anchor for 10 min · CB open sustained > 5 min · Treasury 5xx > 5 % over 10 min · DB pool saturated · alias-drift event. | **30 min** | Pager (low-urgency) | Internal incident channel within 60 min; no external comms unless trend persists. |
| **SEV3** | Limited or no user impact; workaround available. Triggers: hot-cache hit rate < 80 % for 30 min · single alias-drift event · individual replica restart · informational counters. | **next business day** | Ticket queue | Daily digest. |

## 2. On-call rotation

| Role | Coverage | Holder (v1 placeholder) | Tenure |
|---|---|---|---|
| Primary on-call | 24/7 (post-Phase-12); business hours (case study) | `@wex-fx-oncall-primary` (1 week rotation) | 1-week rotation, Mon 09:00 local → Mon 09:00 local. |
| Secondary on-call | Backstop primary; covers parallel SEV1 | `@wex-fx-oncall-secondary` | Same 1-week rotation, offset 1 week. |
| Manager on-call | Escalation; external comms | `@platform-team-manager` | Permanent role rotation owner. |

The case-study build ships with placeholders; real names land at pre-production hand-off. The schedule itself (1-week rotation, primary + secondary + manager-on-call) is the durable shape.

## 3. Escalation paths

```
SEV1 detection
  └─▶ Primary on-call (page) ── 5 min response ──┐
                                                 │ no ack in 5 min
                                                 ▼
                              Secondary on-call (page) ── 5 min response ──┐
                                                                            │ no ack in 5 min
                                                                            ▼
                                                Manager on-call (page) ────┘

Once acknowledged, primary owns coordination:
  ├─ Engages SRE lead (@sre) for capacity / scaling decisions
  ├─ Engages SecArch (@secarch) for security incidents
  ├─ Engages Architect (@architect) for design-level decisions
  └─ Engages Product Owner (@product) for any user-visible communication
```

SEV2: same chain but no secondary auto-page (manual escalation if primary needs help).

SEV3: ticket-based; no page.

## 4. Functional escalation roles (E1 / E3 / E4 closures)

| Scenario | Primary owner | Backup | Escalation |
|---|---|---|---|
| **E1** — Quarterly restore-to-staging drill (RTO/RPO validation) | Platform team (`@platform-team`) | SRE (`@sre`) | Architect on drill failure. |
| **E3** — Daily alias-drift triage (`currency_alias.drift.detected.count > 0`) | On-call primary | On-call secondary | Architect if drift indicates Treasury schema change requiring PR. |
| **E4** — Quarterly audit-log access review | Compliance (`@compliance`) | SecArch (`@secarch`) | Service owner if anomalies. |
| **E8** — On-call schedule owner | Service owner (`@platform-team`) | Manager on-call | n/a — this is the rotation owner itself. |

## 5. Incident process

Standard six-phase loop:

| Phase | Owner | Output |
|---|---|---|
| **Detection** | Alert system (or external report) | Page → ack within response target. |
| **Triage** | On-call primary | SEV assignment, scope, blast radius; opens incident record. |
| **Mitigation** | On-call primary (with engaged roles) | Rollback / config revert / scale-out / disable feature flag. Reference [rollback-plan.md](rollback-plan.md). |
| **Communication** | On-call primary (or Manager-on-call for external) | Internal channel updated every 30 min during SEV1; external status page on customer impact. |
| **Resolution** | On-call primary | Confirm metrics return to baseline; smoke tests pass; close incident. |
| **Post-incident review (PIR)** | On-call primary + Service owner | Within 5 business days for SEV1; within 10 for SEV2. Blameless template. Actions feed into backlog / runbook / monitoring updates. |

Templates and conventions:

- **Incident record:** title prefix `[SEV<n>] <symptom> — <date>`; channel `#incident-wex-fx-<yyyymmdd>`; recording in [incident-response.md](incident-response.md) (template; Phase 7 PCI overlay in `incident-response-pci.md`).
- **PIR format:** what happened / impact / detection / response / root cause / 5 whys / corrective actions / lessons. No individual blame.
- **Corrective actions:** every action has a ticket, an owner, and a due date. Tracked through to closure.

## 6. Communications policy

| Audience | Channel | When |
|---|---|---|
| Internal — engineering | `#incident-wex-fx-*` channel + status update every 30 min during SEV1 | Always for SEV1; SEV2 if it persists > 1 h. |
| Internal — leadership | Manager-on-call to leadership directly | SEV1 only. |
| External — affected client teams (prod) | Status page + email if customer-visible ≥ 1 min | SEV1 with customer impact. |
| External — public | Public status page (if it exists for this service) | SEV1 with customer impact; per platform policy. |
| Auditor / compliance | Compliance role notified for any SEV1 with PCI implications | Every PCI-touching SEV1. |

Content for customer-visible incidents must **not** include:
- Specific technical hints that aid attackers (CB calibration, single-flight gate state, etc.).
- Internal identifiers (replica IDs, trace IDs from internal infrastructure).
- Specific timing or trigger logic that could enable replay.

Include:
- User-visible symptom (in non-technical terms).
- Current mitigation status.
- Estimated time to resolution if known.
- Next update time.

## 7. Linked artefacts

- [service-catalog.md](service-catalog.md) — ownership roles cross-referenced.
- [runbook.md](runbook.md) — operator-facing step-by-step for common scenarios.
- [monitoring-alerting.md](monitoring-alerting.md) — alert routing into this severity ladder.
- [rollback-plan.md](rollback-plan.md) — six rollback classes.
- [incident-response.md](incident-response.md) — full incident template (Phase 5 / 7 overlay).
- [incident-response-pci.md](../security/incident-response-pci.md) — PCI overlay (Phase 7).
- [error-budget-policy.md](error-budget-policy.md) — burn-rate triggers feed into SEV1/SEV2.
