# Drill logs

This folder holds the audit trail of failure-mode drills and incident GameDay exercises. Each drill produces a markdown file named `YYYY-MM-DD-<scenario>.md` following the template in [`TEMPLATE-drill.md`](TEMPLATE-drill.md).

## What counts as a drill

A drill is a **controlled exercise** that exercises the response process and/or a specific failure-mode mitigation. Drills are distinct from real incidents (which produce PIR documents in [`../postmortems/`](../postmortems/)).

Types:

- **Tabletop** — a SEV1 scenario walked through verbally; no system actions. Quarterly cadence per [incident-response.md §5](../incident-response.md).
- **GameDay** — a controlled fault injected into staging (e.g., Treasury-5xx via WireMock, DB-pool exhausted, log-hash-key removed). Annual cadence.
- **Failure-mode drill** — an exercise of one specific failure-mode from [failure-modes-and-resilience.md §1](../failure-modes-and-resilience.md) (e.g., F-01 Treasury timeout, F-05 rate orientation drift). Per-release or per-quarter cadence.
- **Rollback rehearsal** — an exercise of one rollback class from [rollback-plan.md §2](../rollback-plan.md) (A–F). Cadence per [rollback-plan §7](../rollback-plan.md).

## Required artifacts per drill

Each drill log captures:

1. **Pre-drill state** — what was the system state immediately before the drill (commit SHA, deploy time, SLI baselines).
2. **Injection method** — exact action taken to introduce the fault (e.g., "set WireMock stub to return 503 for 5 minutes" or "applied chaos toolkit `kill -9` to one pod").
3. **Detection** — which alert fired, at what time, and what the responder saw.
4. **Response** — what the responder did, in chronological order.
5. **Resolution** — when the system returned to healthy and how that was verified.
6. **Findings** — what the drill revealed (gaps in the runbook, alert tuning issues, response-process friction).
7. **Action items** — follow-up work created from the drill, with owners and due dates.

The drill log IS the audit evidence — referenced from `phases/<phase-id>/20-bundle.md` coverage maps.

## Filing convention

`docs/operations/drills/YYYY-MM-DD-<scenario-slug>.md`

Examples:
- `docs/operations/drills/2026-06-15-treasury-5xx-circuit-breaker.md`
- `docs/operations/drills/2026-07-22-tabletop-pan-disclosure-incident.md`
- `docs/operations/drills/2026-08-10-class-b-config-rollback-rehearsal.md`
