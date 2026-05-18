# Drill — <SCENARIO TITLE>

**Date:** YYYY-MM-DD
**Type:** Tabletop / GameDay / Failure-mode / Rollback-rehearsal
**Scenario reference:** F-XX from [failure-modes-and-resilience.md §1](../failure-modes-and-resilience.md) | Class X from [rollback-plan.md §2](../rollback-plan.md)
**Drill lead:** <name>
**Responders:** <name(s)> (the on-call rotation at drill time)
**Environment:** staging | local | tabletop (no environment)

---

## 1. Pre-drill state

- **Service commit:** `<sha>` (deployed YYYY-MM-DD HH:MM UTC)
- **SLI baselines (last 1 h before drill):**
  - SLI-A (POST /purchases success): `99.99 %`
  - SLI-C (GET /conversion success): `99.7 %`
  - SLI-D (POST p99 latency): `82 ms`
  - SLI-G (GET conversion cache-miss p99): `1.4 s`
- **Treasury upstream status:** healthy (Treasury status page green)
- **Dependencies:** Postgres healthy, OTel collector healthy
- **Active feature flags:** <list, or "none">

## 2. Injection method

Exact action(s) taken to introduce the fault. Be precise — someone reproducing this drill must be able to follow these steps verbatim.

Example for F-01 (Treasury timeout):
```
1. SSH to the staging WireMock host (treasury-stub-staging.internal).
2. Apply the slow-response mapping:
   curl -X POST http://localhost:8080/__admin/mappings \
        -H 'Content-Type: application/json' \
        -d @/opt/wiremock/mappings/treasury-5s-delay.json
3. Verify: curl https://treasury-stub-staging.internal/services/api/... returns after 5 s delay.
4. Start timer at HH:MM:SS UTC.
```

## 3. Detection

| Time (UTC) | Alert / signal | Source |
|---|---|---|
| HH:MM:00 | <First signal observed> | Grafana dashboard / PagerDuty page / log scan / responder noticed |
| HH:MM:00 | <Subsequent signal> | <source> |

**Did the alert fire at the expected severity?** Yes / No — and what was the gap if any.

## 4. Response (chronological)

| Time (UTC) | Action | Outcome |
|---|---|---|
| HH:MM:00 | Responder acknowledged page in PagerDuty | ✅ within ack target |
| HH:MM:00 | Opened `#incident-DRILL-NNN` Slack channel | ✅ |
| HH:MM:00 | Ran [runbook §2 (Initial triage)](../runbook.md#2-initial-triage) | Identified Treasury degraded |
| HH:MM:00 | Switched to [runbook §6.6 (Treasury degraded)](../runbook.md#66-treasury-degraded) | Confirmed CB transitioning OPEN |
| HH:MM:00 | <Mitigation action> | <Outcome> |
| HH:MM:00 | <Communication action> | <Outcome> |
| HH:MM:00 | <Resolution action> | <Outcome> |

## 5. Resolution

- **Fault removed at:** HH:MM:SS UTC
- **Signal recovered at:** HH:MM:SS UTC (time to recovery: <X> min)
- **Smoke test pass:** YYYY-MM-DD HH:MM (link to CI run or manual log)
- **System state verified by:** <responder name>

## 6. Findings

Categorize as ✅ (worked well), ⚠️ (worked but with friction), or ❌ (did not work — gap).

| # | Finding | Category | Affected artifact |
|---|---|---|---|
| 1 | Alert fired at the expected severity within 30 s of injection. | ✅ | monitoring-alerting.md §3.X |
| 2 | Runbook §6.6 command for tailing Treasury logs had a stale hostname. | ⚠️ | runbook.md §6.6 |
| 3 | <gap> | ❌ | <doc> |
| ... | ... | ... | ... |

## 7. Action items

| # | Action | Owner | Due | Tracking |
|---|---|---|---|---|
| 1 | Update [runbook §6.6](../runbook.md#66-treasury-degraded) Treasury log hostname. | <name> | YYYY-MM-DD | <issue> |
| 2 | ... | ... | ... | ... |

## 8. Lessons recorded for the playbook

Capture process lessons that the on-call should remember beyond the action-item fixes:

- Lesson 1.
- Lesson 2.

## 9. Drill cost

- **Wall time:** <X> min from injection to resolution.
- **Responder time:** <X> person-min total.
- **Customer impact:** None (drill ran in staging; no production traffic affected) | <if production exposure, quantify here>.

End of drill log.
