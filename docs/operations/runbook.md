# Runbook — wex-purchase-fx

> **Status:** Phase 5 (Operational Design Session), 2026-05-17.
> Operator-facing step-by-step for the alerts catalogued in [monitoring-alerting.md](monitoring-alerting.md). Read alongside the [oncall-escalation.md](oncall-escalation.md) severity ladder and [rollback-plan.md](rollback-plan.md) six classes.

---

## 1. Service summary

| Field | Value |
|---|---|
| Service | `wex-purchase-fx` |
| Owner | `@platform-team` (v1 placeholder; see [service-catalog.md](service-catalog.md)) |
| Backup owner | `@platform-team` (Backend lead) |
| Criticality | Tier-2 (prod) / Tier-3 (case study) |
| Dashboard | Grafana → `wex-fx / Service health` (default) + four siblings (see [monitoring-alerting.md §5](monitoring-alerting.md#5-dashboards)) |
| Logs | Log aggregator → `service=wex-purchase-fx` filter |
| Traces | OTel backend (Tempo / Jaeger) → `service=wex-purchase-fx` |
| Escalation | [oncall-escalation.md §3](oncall-escalation.md#3-escalation-paths) |

## 2. Initial triage (every incident starts here)

Run this loop **before** drilling into the alert-specific section. It typically takes 60–90 seconds.

1. **User impact?** Check the `Business workflow` dashboard — is conversion rate dropping? Errors visible to clients? If yes, you have a real incident; if no, may be infrastructure noise.
2. **SLO burn rate?** Check the `Service health` dashboard. SLO-C fast burn = SEV1 candidate. SLO-A/B fast burn = SEV1 candidate. Slow burn = SEV2.
3. **Recent deploys or config changes?** Check the deployment timeline (last 30 min). If a deploy is in-flight, rollback per [rollback-plan.md §4.2](rollback-plan.md) Class A.
4. **Dependency health?** Check Treasury canary + DB health on the `Dependency health` dashboard. Treasury RED → see §6.6; DB RED → see §6.11.
5. **Logs and traces** by `correlationId` for a recent failed request. Filter on `event=…_failed` for the affected endpoint.
6. **Decide mitigation:** rollback (most common), config revert, scale-out, feature-flag disable, or escalate.

Communicate progress every 30 min in the incident channel.

## 3. Quick-action cheat sheet

| Action | Command (production-reference placeholder; platform-specific in Phase 12) |
|---|---|
| Rollback last deploy | `kubectl rollout undo deployment/wex-purchase-fx -n wex-fx` |
| Restart all pods | `kubectl rollout restart deployment/wex-purchase-fx -n wex-fx` |
| Force readiness DOWN | `kubectl set env deployment/wex-purchase-fx WEX_FORCE_READINESS_DOWN=true -n wex-fx` |
| Scale to N replicas | `kubectl scale deployment/wex-purchase-fx --replicas=N -n wex-fx` |
| View live logs | `kubectl logs -f deployment/wex-purchase-fx -n wex-fx --tail=200` |
| Trigger warm-up manually | `curl -X POST http://<endpoint>/actuator/warmup` (Phase 13 endpoint) |
| Inspect circuit breaker state | `curl http://<management-port>/actuator/circuitbreakers` |
| Inspect Hikari pool | `curl http://<management-port>/actuator/metrics/hikaricp.connections.active` |
| Smoke test | See [rollback-plan.md §4.1](rollback-plan.md#41-smoke-test) |

## 4. Smoke test (always run after mitigation)

```
# Health
curl -fsS http://<endpoint>/actuator/health/readiness
# Create
curl -fsS -X POST http://<endpoint>/api/v1/purchases \
  -H 'Content-Type: application/json' \
  -d '{"description":"smoke","transactionDate":"'$(date -u +%F)'","amountUsd":"10.00"}'
# Convert (capture id from previous response)
curl -fsS "http://<endpoint>/api/v1/purchases/<id>/conversion?currency=CAD"
```

Pass criteria: 200 from all three; convertedAmount within ±1 cent of expected (≈ 13.93 for CAD at 1.393).

## 5. Post-mitigation validation (cross-reference)

See [rollback-plan.md §5](rollback-plan.md#5-post-rollback-validation) for the standard validation table (health, smoke, RED, latency, Treasury, cache, audit).

## 6. Alert-specific playbooks

### 6.1 Fast-burn on SLO-A or SLO-B

(A-SLO-A-fast, A-SLO-B-fast)

**Likely causes:** bad deploy (most common); DB pool exhaustion (§6.10); DB unreachable (§6.11).

1. Check Service health dashboard — is the burn correlated with a recent deploy timeline?
2. If yes → rollback (Class A, [rollback-plan §4.2](rollback-plan.md#42-class-a--code-rollback)).
3. If no → check DB indicators (§6.10, §6.11).
4. If neither → check `purchase.create.validation_error.count{reason}` for input-validation spikes (possible client misbehaviour, not our fault — investigate but don't rollback).
5. Smoke test post-mitigation. Open PIR.

### 6.2 Fast-burn on SLO-C

(A-SLO-C-fast)

**Likely causes:** Treasury degraded with cache-miss path (§6.6); DB pool (§6.10); orientation drift (§6.7).

1. **First check:** Treasury canary on Dependency dashboard. RED canary → Treasury-side; we are bounded by their uptime ([error-budget-policy.md §6](error-budget-policy.md#6-slo-c-specific-behaviour)). No service-side action; document and ride it out.
2. Canary GREEN but our `treasury.api.failure.count` is high → DNS / egress / cert issue. Investigate egress; escalate to platform.
3. Canary GREEN, Treasury success-rate normal, but `503 UPSTREAM_UNAVAILABLE` is climbing → check cache-hit ratio (A-011). Low cache-hit indicates cold-cache cliff; trigger manual warm-up (§3 cheat sheet).
4. Smoke test post-mitigation. Open PIR. Quarterly trend goes into the monthly SLO report.

### 6.3 Elevated 5xx rate

(A-001)

1. Distinguish 5xx by `errorCode`: `INTERNAL_ERROR` (bug); `UPSTREAM_UNAVAILABLE` (Treasury — §6.6); `UPSTREAM_BAD_RESPONSE` (Treasury schema drift — §6.8).
2. If predominantly `INTERNAL_ERROR`, this is a bug. Roll back the last deploy (Class A). File a bug.
3. Smoke test. PIR.

### 6.4 Latency saturation

(A-002, A-003, A-004)

1. Distinguish which endpoint and (for conversion) cache hit vs miss path.
2. Check JVM heap / GC pause (§6.12), Tomcat threads (§6.13), bulkhead (§6.14).
3. Check recent capacity changes (replica count) and config drift (timeouts).
4. Mitigate: scale out (+1 replica), or rollback if config-related.
5. Smoke test. PIR.

### 6.5 Treasury CB open

(A-005)

1. Expected behaviour when Treasury is degraded. CB will half-open after `wait_duration_in_open_state` (5 min default).
2. Confirm via `treasury.api.failure.count{reason}` what tripped it.
3. If CB is open + cache-miss-path traffic is high → users see `503`. No service-side mitigation; communicate.
4. If CB is open + cache hits are serving the bulk of traffic → degraded mode is working as designed. No action.
5. Treasury recovery → CB half-opens; if first 5 calls succeed, CB closes. Verify `resilience4j.circuitbreaker.state` returns to `closed`.

### 6.6 Treasury degraded

(A-006, A-007)

1. Treasury canary (Dependency dashboard) — RED confirms upstream-side, GREEN means us.
2. RED: nothing to do; CB will protect us. Watch SLO-C; document for monthly report.
3. GREEN but our failure rate is high: likely egress / TLS / DNS issue. Verify TLS cert validity. Verify egress IP isn't blocked by Treasury (anonymous API but they can rate-limit by IP).
4. If you suspect rate-limit, lower request frequency: increase cache TTL via env, increase warm-up coverage. Long-term fix Phase 5/6.

### 6.7 Rate-orientation drift

(A-008)

**This is SEV1 if the canary drift threshold is hit. Silent 1/x correctness risk.**

1. Identify the affected currency from the alert label.
2. **Immediately:** feature-flag-disable conversions for that currency (Phase 13 plumbing; v1 case study has no flag — escalate to Architect).
3. Run the prototype script from [phase-3-prototype-log.md](../planning/phase-3-prototype-log.md) to confirm Treasury's current convention for the currency.
4. Compare to the fixture in `src/test/resources/treasury/fixtures/orientation/`.
5. If Treasury flipped: update the fixture; ship a hotfix; update ADR-0001 §D-4.
6. If our fixture was wrong all along: same patch + audit any past conversions for the currency (incident).
7. Re-enable currency. Smoke. PIR.

### 6.8 Treasury schema drift

(A-009, A-010)

1. Inspect the failing Treasury response (logs with `event=treasury_api_failure{reason=malformed}`).
2. Compare to the JSON schema in `src/main/resources/treasury-schema.json`.
3. If a field renamed: update schema; ship hotfix; update contract tests.
4. If a field type changed: same.
5. If `rate_sanity` triggered: confirm with raw response that the rate is genuinely outside bounds (very rare). If bound is too tight for a legitimate currency, raise per G4-P1-3 process.

### 6.9 Cache-hit degraded

(A-011)

1. Most common cause: recent rolling deploy → cold replicas.
2. Verify `warmup_completed` log event landed for each replica.
3. If warm-up didn't run, trigger manually (§3 cheat sheet).
4. If warm-up ran but hit-rate still low, check if traffic distribution is unusual (lots of cold currencies). Consider extending `WEX_WARMUP_CURRENCIES`.
5. SEV3 — informational; not a page.

### 6.10 DB pool saturated

(A-012)

1. **First:** scale out replicas (one more is usually enough at 100 req/s/replica).
2. Investigate if a slow query is holding connections: check `db.statement` traces.
3. If sustained, check Hikari pool size config. Increase via `WEX_DB_POOL_SIZE` env (default 10).
4. If query-related, may need a hotfix or index addition (Phase 13).
5. Smoke test. PIR.

### 6.11 DB unreachable

(A-013)

1. Check DB platform status. If DB-side outage, escalate to platform team.
2. Service readiness will flip DOWN; LB drains automatically. No additional action.
3. On recovery, readiness returns UP. Smoke test.
4. PIR if outage > 15 min.

### 6.12 JVM heap saturation

(A-014, A-015)

1. Check for memory leak pattern: heap doesn't return to baseline after GC.
2. If leak suspected: roll back recent deploy (Class A).
3. If steady-state grew (more replicas needed), scale out.
4. If GC pauses spiking: investigate config drift on JVM flags.
5. PIR.

### 6.13 Tomcat threads saturated

(A-016)

1. Distinguish: slow-loris attack vs legitimate load spike vs slow downstream (DB, Treasury).
2. If load spike: scale out.
3. If slow-loris: gateway/WAF should be filtering; escalate to platform.
4. If slow downstream: address that root cause (§6.10 or §6.6).

### 6.14 Bulkhead saturated

(A-017)

1. Treasury bulkhead at 50 concurrent permits. Saturation means many concurrent first-fetches.
2. Check `single_flight.loser.polled.count` — gate working as expected?
3. If gate is deduping but bulkhead still saturated, increase bulkhead size (Phase 5/6 capacity decision; immediate mitigation: scale out replicas).

### 6.15 Readiness flapping

(A-018)

1. Inspect `readiness_state_changed` events for the failing indicator.
2. If DB: §6.11. If alias-table: check the table file integrity. If pool: §6.10.
3. Force readiness DOWN on the affected replica (§3 cheat sheet), let LB drain, restart, force UP.
4. Smoke test. PIR.

### 6.16 Container restart loop

(A-019)

1. Inspect last few container logs *before* restart. Look for `ApplicationContextException` or `OutOfMemoryError`.
2. If `WEX_LOG_HASH_KEY` absent (prod): restore the env (§6.24). This is a common cause.
3. If migration failure: roll back (Class C — [rollback-plan §4.4](rollback-plan.md#44-class-c--schema-rollback-forward-only)).
4. If image-CVE rebuild bad: roll back image.

### 6.17 Alias drift

(A-020)

1. The audit event includes `{ resolvedCanonical, treasuryReturned }` (for AC-021c) or `{ resolvedCanonical }` (for AC-021b).
2. Open a PR updating `infrastructure/resources/currency-aliases.json`.
3. Merge + deploy. Smoke test.
4. SEV3 — daily digest is sufficient unless drift is widespread.

### 6.18 Content-guard spike

(A-021)

1. Inspect `purchase_validation_failed{reason}` audit events. The rejected payload is **not** in the log; only `description.length` and `description.hash`.
2. If spike correlated with a single client / source IP: gateway-level block.
3. If spike across many clients: investigate whether a legitimate use case is hitting false positives (PR may be needed to expand allow-list, Phase 7 decision).
4. Escalate to SecArch.

### 6.19 Single-flight loser timeout

(A-022)

1. Most common cause: Treasury slow + single-flight winner takes too long.
2. Check `wex.gate.wait_ms` p99.
3. Mitigation: increase the loser bounded-wait via `WEX_SINGLE_FLIGHT_WAIT_MS` (default 200 ms).
4. If sustained, consider larger Treasury bulkhead or pre-warm coverage.

### 6.20 Warm-up failed

(A-023)

1. Check pod logs around `service_started` for absent / failed `warmup_completed`.
2. Common causes: Treasury timeout during warm-up (replica still serves; first user requests trigger natural fetches).
3. Manually trigger warm-up (§3 cheat sheet).
4. If chronic, expand `WEX_WARMUP_TIMEOUT_SECONDS` (default 30 s).

### 6.21 Treasury canary RED

(A-024)

1. Check Treasury's status page (if any) or fiscaldata.treasury.gov directly via browser.
2. If genuinely down: nothing to do — wait. SLO-C burn is bounded.
3. If canary fails but main path succeeds: canary infrastructure issue; investigate but don't act on service.

### 6.22 Audit-event spike

(A-025)

1. Same as §6.18 but specifically for PCI-relevant rejections (PAN / track / encoded-PAN).
2. Escalate to SecArch.
3. May trigger Phase-7 incident-response procedure if pattern suggests real attack.

### 6.23 Service start failure

(A-026)

1. Container restart loop indicates startup-time failure.
2. Most common: missing env (`WEX_LOG_HASH_KEY`, DB credentials, etc.).
3. Check pod logs for `ApplicationContextException`.
4. Mitigate per the specific cause; restart.

### 6.24 Log-hash key issue

(A-027)

1. If digests are emitting `v0:` in production → `WEX_LOG_HASH_KEY` is unset or the no-op fallback is being used. This is a compliance violation.
2. Check the platform secrets-store binding for the pod.
3. Set the env from the secrets store; restart pod.
4. **PCI evidence:** open an incident; document detection time, exposure window, audit-log impact.
5. Investigation: were any digests emitted with `v0:` to the prod log? If yes, audit-log retention policy must be applied to those entries (treated as unhashed-equivalent because `v0` is documented no-op).

### 6.25 OpenAPI breaking change

(A-028)

1. CI `oasdiff` gate fails on the PR.
2. Inspect the diff. If the change is intentional, follow the API-versioning process: bump to `/api/v2/`; add deprecation window; new ADR.
3. If the change is accidental, revert it.

## 7. Linked artefacts

- [monitoring-alerting.md](monitoring-alerting.md) — alert catalogue with severities + this runbook's anchors.
- [oncall-escalation.md](oncall-escalation.md) — severity ladder + escalation paths.
- [rollback-plan.md](rollback-plan.md) — six rollback classes (A=code, B=config, C=schema, D=data PITR, E=orientation flip, F=security).
- [failure-modes-and-resilience.md](failure-modes-and-resilience.md) — full failure-mode catalogue.
- [error-budget-policy.md](error-budget-policy.md) — what burn rates mean for release velocity.
- [observability.md](observability.md) — metric / event / span definitions.
- [service-catalog.md](service-catalog.md) — ownership and dependencies.
- [incident-response.md](incident-response.md) — Phase 5 / 7 overlay.
- Phase-3 [phase-3-prototype-log.md](../planning/phase-3-prototype-log.md) — for rate-orientation drift triage (§6.7).
