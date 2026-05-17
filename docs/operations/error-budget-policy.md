# Error Budget Policy

> **Status:** Phase 5 (Operational Design Session), 2026-05-17.
> Defines how reliability targets in [slo-sli.md](slo-sli.md) constrain release velocity and operator action. Applies to production-reference deployments; case-study build is informational.

---

## 1. What an error budget is, here

For each availability SLO, the error budget is the **allowed downtime / failed-fraction in the window**.

| SLO | Target | 30-day budget |
|---|---|---|
| SLO-A (POST /purchases) | 99.9 % | 43 m 12 s |
| SLO-B (GET /purchases/{id}) | 99.9 % | 43 m 12 s |
| SLO-C (GET /…/conversion) | 99.5 % | 3 h 36 m |

The budget is a number, not a moral judgment. Spending it on a successful deploy is fine. Spending it on a Treasury outage is fine. Burning through it for *avoidable* reasons (a bad release, an un-rolled-back config change, a contended deploy window) triggers the policies below.

## 2. Burn rate

**Burn rate** = (rate at which the budget is being consumed) / (rate that would consume the budget exactly at SLO-target).

A burn rate of `1.0` means we are exactly meeting the SLO. A burn rate of `14.4` means we will exhaust the 30-day budget in 30 / 14.4 ≈ 2 days. Burn rates are computed over multiple windows simultaneously (the "multi-window multi-burn-rate" pattern from Google SRE workbook chapter 5).

| Window | Burn-rate trigger | Time to exhaust 30-day budget | Severity |
|---|---|---|---|
| **5 min** | 14.4× | 2.08 days | SEV1 (page) |
| **1 h** | 6× | 5 days | SEV2 (page low-urgency) |
| **6 h** | 1× | 30 days | SEV3 (informational; investigate) |

These map directly to the alerts in [monitoring-alerting.md](monitoring-alerting.md). Fast-burn alerts page; slow-burn alerts ticket.

## 3. Policy by burn condition

| Condition | Owner action | Service-level effect | Approver |
|---|---|---|---|
| **Fast burn (5 min @ 14.4×)** | On-call primary pages; investigates immediately; mitigation = rollback / config revert / scale-out / degrade. | If the cause is a recent release, the release is **rolled back**. | On-call primary. |
| **Slow burn (1 h @ 6×)** | On-call primary opens an investigation ticket; reviews recent deploys, capacity, dependency-health. | If trend continues for ≥ 4 h, escalate to SEV1 fast-burn even without the 14.4× trigger. | On-call + SRE lead. |
| **6 h @ 1× sustained, with no incident attribution** | SRE leads a root-cause analysis. | Investigation ticket; no service action. | SRE lead. |
| **Budget < 25 % remaining at any time in window** | Service owner reviews release-velocity policy (§4). | Release freeze for risky changes; reliability work prioritised. | Service owner + Product owner. |
| **Budget exhausted (0 % remaining)** | Hard release freeze on non-reliability changes (§4). | Only fixes that close the burn are merged. | Service owner. |

## 4. Release-velocity policy

The error budget constrains releases. The policy is intentionally graduated, not binary.

| Budget remaining (30-day window) | Allowed release types | Notes |
|---|---|---|
| **> 75 %** | All releases. | Normal cadence. |
| **50 %–75 %** | All releases; SRE review on releases tagged `risky`. | Risky = ADR-bearing, schema-changing, or touching the Treasury client. |
| **25 %–50 %** | Standard releases only; risky releases require Service-owner sign-off. | Architect can override for legal / security urgency. |
| **< 25 %** | **Release freeze** on risky releases. Standard releases continue. Reliability fixes always allowed. | Service owner publishes the freeze; lifts when burn rate drops. |
| **0 % (exhausted)** | **Hard freeze.** Only fixes that close the burn. | Service owner + Product owner sign-off required for *anything else*; reasoned exception process per §5. |

Architect or Service owner can override freezes for **legally or security-required** changes (e.g., a CVE patch). Override goes on the audit log.

## 5. Exceptions

A freeze override requires:

1. Written justification (incident channel post; one paragraph).
2. Counter-signature from Service owner **and** SRE lead (or, for security urgency, SecArch).
3. Post-merge plan: how the change will be validated; what monitoring will detect regression.
4. Entry in the audit-log destination (Phase 7).

Exceptions are reviewed monthly. A pattern of overrides triggers an SLO re-ratification — the target is either too tight or the system is genuinely under-resourced.

## 6. SLO-C-specific behaviour

SLO-C (`GET /…/conversion`) is the only SLO sensitive to Treasury. From [slo-sli.md](slo-sli.md) §3:

- `503 UPSTREAM_UNAVAILABLE` consumes budget — this is our fault by classification, even when the *cause* is Treasury (we should have a cached eligible rate).
- `422 CONVERSION_RATE_NOT_AVAILABLE` does **not** consume budget — the rule was applied correctly.

If a Treasury outage drives SLO-C burn past 25 %, we do **not** automatically freeze releases. Instead:

- Verify the cache-hit ratio is at or above design (≥ 99 %).
- If yes, the budget consumption is bounded by Treasury and there is no service-side action available beyond waiting and the Phase-5/6 cache-strategy improvements.
- If no (cache-hit ratio degraded), the freeze applies because we have a *fixable* problem (likely warm-up gap, cache eviction tuning, or rolling-deploy churn).

This nuance is captured in [runbook.md](runbook.md) §6.

## 7. Reporting + review

| Cadence | Output |
|---|---|
| Real-time | Burn-rate dashboards (per SLO). |
| Weekly | On-call hand-off note: this week's burn delta + open incidents touching budget. |
| Monthly | Service-owner report: full budget consumption by SLO, attribution by cause (deploy / capacity / dependency / etc.), freeze status, release-velocity implications. |
| Quarterly | SLO target review — are the SLOs still appropriate? Treasury-uptime baseline refresh per [slo-sli.md](slo-sli.md) §4.1. |

## 8. Linked artefacts

- [slo-sli.md](slo-sli.md) — SLI/SLO definitions and the Treasury-uptime ceiling formula.
- [monitoring-alerting.md](monitoring-alerting.md) — concrete multi-window multi-burn-rate alerts.
- [runbook.md](runbook.md) — operator action for burn-rate alerts.
- [oncall-escalation.md](oncall-escalation.md) — SEV ladder that the burn-rate alerts feed into.
- [rollback-plan.md](rollback-plan.md) — first-line mitigation for most fast-burn causes.
