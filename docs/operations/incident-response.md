# Incident Response

> **Status:** Phase 10 (Operational Readiness), 2026-05-18. Expanded from the Phase-4 stub. Cross-references [oncall-escalation.md](oncall-escalation.md) §1 (severity ladder is canonical there), [runbook.md](runbook.md) §6 (alert-specific playbooks), and [rollback-plan.md](rollback-plan.md) §3 (rollback triggers).

---

## 1. Scope

This document defines **how the on-call engineer responds when an alert fires or a customer-visible incident is detected**. The severity ladder is owned by [oncall-escalation.md §1](oncall-escalation.md); this doc inherits that ladder by reference.

Incident response covers detection → triage → mitigation → communication → resolution → post-incident review (PIR). It does **not** cover root-cause engineering (tracked under follow-up issues opened from the PIR).

## 2. Severity ladder (reference)

The canonical severity definitions live in [oncall-escalation.md §1](oncall-escalation.md). Summary for quick reference:

| Severity | Customer impact | Response time | Communications cadence | Page who |
|---|---|---|---|---|
| **SEV1** | Production outage; conversions failing for >5 % of requests OR all `POST /purchases` unavailable | 5 min ack; 15 min mitigation start | every 15 min in incident channel; 30 min status-page updates | Primary on-call → Secondary on-call after 15 min unack → Engineering Manager after 30 min |
| **SEV2** | Significant degradation; conversions failing for 1–5 % OR latency SLO burning fast | 15 min ack; 30 min mitigation start | every 30 min in incident channel; status-page only if customer-facing | Primary on-call → Secondary on-call after 30 min unack |
| **SEV3** | Minor degradation; one currency affected; non-customer-facing metric drift | next business day | one-line update on incident close | Primary on-call (business hours) |
| **SEV4** | Informational / planned maintenance | n/a | maintenance window announcement | n/a |

Severity may be **upgraded mid-incident** by any responder if scope expands. Downgrading requires explicit sign-off from the Incident Commander.

## 3. The process

### 3.1 Detect

Alert sources (in priority order):

1. **PagerDuty alerts** wired from [monitoring-alerting.md §3](monitoring-alerting.md) — fast-burn SLO, error-rate, latency-saturation, dependency-failure, infra (Hikari pool, JVM heap, Tomcat threads).
2. **Synthetic checks** — Treasury-canary RED ([runbook §6.21](runbook.md)), warm-up RED, smoke RED.
3. **User reports** — Slack #wex-support escalation, status-page incident reports.
4. **Self-detection** — operator notices anomalous dashboard signal during routine check.

Every alert routes to PagerDuty with the on-call rotation defined in [oncall-escalation.md §2](oncall-escalation.md). Alerts include the alert name, severity, source metric, and a link to the relevant [runbook §6.x](runbook.md) playbook.

### 3.2 Triage (target: 5 min for SEV1, 15 min for SEV2)

The on-call's triage loop:

1. **Ack the page** in PagerDuty within the severity's ack target. Open `#incident-<NNN>` Slack channel (auto-created by PagerDuty integration).
2. **Open the runbook playbook** linked from the alert. Run [runbook §2 (Initial triage)](runbook.md) — the universal 5-step health check.
3. **Confirm or downgrade severity** based on actual customer impact (look at SLI-C dashboard, recent deploys, dependency status pages).
4. **Declare an Incident Commander** for SEV1/SEV2 — the on-call by default, but they can hand off to a senior responder. The IC owns coordination; everyone else owns mitigation work.
5. **Page the next responder** if mitigation will exceed the unack timeout (e.g., on-call calls Secondary at 10 min into a SEV1 to share load).

### 3.3 Mitigate (target: 15 min for SEV1)

Bias toward **time-to-mitigate**, not time-to-fix. Acceptable mitigation actions:

- **Rollback** per [rollback-plan.md](rollback-plan.md) (Class A/B/C/D depending on what changed in the last deploy).
- **Disable feature flag** (if the alert correlates with a flag flip).
- **Drain replica + restart** (for OOM, Tomcat saturation, Hikari pool stuck).
- **Manual circuit-break** (set `wex.treasury.disable=true` for the affected currency to fail-closed; serve cached rates only — see [runbook §6.7](runbook.md) rate-orientation drift playbook).
- **Scale up** (add replicas) for capacity-bound incidents.

The IC narrates each mitigation in `#incident-<NNN>` so the postmortem timeline is auto-captured.

### 3.4 Communicate (in parallel with mitigation)

| Audience | Channel | Cadence | Template |
|---|---|---|---|
| **Internal — engineering** | `#incident-<NNN>` | Continuous narration by IC | Free-form |
| **Internal — leadership** | `#oncall-leadership` Slack | every 30 min for SEV1; on resolution for SEV2 | "[SEV1] Detected at HH:MM; mitigation started HH:MM; impact: <%> of requests; ETA to mitigate: <X> min" |
| **External — customers** | Public status page | Initial post within 15 min of SEV1; updates every 30 min | "We are investigating reports of <symptom> affecting <scope>. Next update at HH:MM." |
| **External — affected enterprise** | Account email (handled by Support) | SEV1 only; on confirmation of customer-facing impact | Support owns; engineering provides scope |

The IC owns "are we communicating enough?" The default if uncertain is to over-communicate internally.

### 3.5 Resolve

An incident is **resolved** when:

1. The SLI signal that triggered the alert has returned to healthy and stayed there for at least one full alert evaluation window.
2. The smoke test in [runbook §4](runbook.md) passes end-to-end.
3. The IC announces resolution in `#incident-<NNN>`.
4. The PagerDuty incident is closed.
5. The status-page incident is closed with a "Resolved" entry.

### 3.6 Post-incident review (PIR)

**Every SEV1 and SEV2 incident requires a PIR within 5 business days.** SEV3 may get a PIR at the IC's discretion (e.g., recurrence, surprising root cause).

PIR template (§4 below) is filled by the IC; reviewed by Engineering Manager + Architect; action items tracked in the project tracker with owners and due dates.

## 4. Post-incident review (PIR) template

Create `docs/operations/postmortems/YYYY-MM-DD-<incident-id>-<slug>.md`:

```markdown
# PIR — Incident #<NNN> — <short title>

**Date:** YYYY-MM-DD
**Severity:** SEV1 / SEV2
**Duration:** detected HH:MM → mitigated HH:MM → resolved HH:MM (total: <X>m)
**IC:** <name>
**Responders:** <names>

## 1. What happened (the narrative)

One-paragraph story. What did a customer see? What did the operator see?

## 2. Impact

| Dimension | Quantity |
|---|---|
| Affected requests | N (X% of period total) |
| Affected customers | N |
| Affected currencies / endpoints | <list> |
| Estimated revenue impact | $X (if applicable) |
| SLO budget consumed | X% of monthly budget for SLO-<id> |

## 3. Timeline (UTC)

| Time | Event |
|---|---|
| HH:MM | Trigger event (e.g., deploy of v1.2.3) |
| HH:MM | First alert fired |
| HH:MM | On-call paged + acknowledged |
| HH:MM | Incident channel opened |
| HH:MM | Severity confirmed as SEV1 |
| HH:MM | Mitigation: <action> |
| HH:MM | Signal recovered |
| HH:MM | Incident closed |

## 4. Root cause

Five-whys analysis. What is the immediate cause, the contributing cause, the systemic cause? Be specific. **Blame-free** — describe the system that allowed the failure, not the person.

## 5. What went well

- Detection mechanism worked as designed (or didn't).
- On-call response time within ack target.
- Mitigation was already runbook-documented.
- ...

## 6. What went poorly

- The alert that fired was not the most actionable signal.
- The runbook playbook had a stale command.
- The rollback procedure was unfamiliar to the on-call.
- ...

## 7. Action items

| # | Action | Owner | Due | Tracking |
|---|---|---|---|---|
| 1 | <action> | <name> | YYYY-MM-DD | <issue link> |
| 2 | ... | ... | ... | ... |

Action items must have an owner and a due date. They are tracked in the project tracker and reviewed at the next PIR-readout meeting.

## 8. Lessons recorded for the playbook

If the incident reveals a process gap, capture the lesson here AND update the relevant doc:

- Lesson 1 → updated in [runbook §6.X](runbook.md)
- Lesson 2 → captured in [oncall-escalation.md §X](oncall-escalation.md)
- ...

## 9. Was this preventable?

Yes / No / Partial — with a one-paragraph justification.

End of PIR.
```

## 5. Drill cadence

To keep the response process sharp, the team runs:

- **Quarterly tabletop** — a SEV1 scenario walked through verbally, no system actions. Tests responder familiarity with the process.
- **Annual GameDay** — a controlled fault is injected into staging (Treasury-5xx, DB-pool exhausted, log-hash-key removed, etc.). Real responders, real timer. Drill log captured in `docs/operations/drills/YYYY-MM-DD-<scenario>.md`.

The Engineering Manager owns the drill schedule. Findings from drills feed back into this document, the [runbook](runbook.md), and [oncall-escalation.md](oncall-escalation.md).

## 6. Linked artefacts

- [oncall-escalation.md](oncall-escalation.md) — rotation, escalation tree, contact details.
- [runbook.md](runbook.md) — per-alert playbooks (§6.1–§6.25).
- [rollback-plan.md](rollback-plan.md) — rollback classes A–F; class-specific procedures.
- [monitoring-alerting.md](monitoring-alerting.md) — alert routing + paging policy.
- [docs/security/incident-response-pci.md](../security/incident-response-pci.md) — PCI-specific incident response (e.g., suspected cardholder-data leak); supersedes this doc for any incident involving cardholder data.
- [docs/operations/drills/](drills/) — drill logs (GameDay, tabletop).
- [docs/operations/postmortems/](postmortems/) — PIR documents.

End of incident-response.md.
