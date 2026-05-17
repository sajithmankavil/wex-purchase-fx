# Rollback Plan

> What constitutes a rollback, what triggers it, how to execute it, and how to validate that the system is back to a known-good state. Builds on [deployment-architecture.md](../architecture/deployment-architecture.md) §9 (which gives the elevator pitch); this document is the operator's reference.
> **Status:** Phase 4 (Design Grill), 2026-05-17. Concrete platform-specific commands (kubectl / helm / runbook integrations) land in Phase 5.

---

## 1. Rollback strategy (one-paragraph elevator pitch)

The default deploy strategy is **rolling with surge** (`maxSurge=1, maxUnavailable=0`); the default rollback strategy is the **inverse rolling deploy to the previous image tag** with no surge, again zero-downtime. Database schema is **forward-only Flyway**; a logical "schema rollback" is a new forward migration that reverts the intent. Data rollback (corruption / poisoning) is **Postgres point-in-time recovery**. Configuration rollback is a **platform env-var revert** plus pod restart. Rate-cache state is non-durable; it is repopulated on demand.

The plan is rehearsed in `staging` quarterly; failure to validate a rollback is a P1 incident, blocking the next release.

---

## 2. Rollback classes

| Class | Trigger | Action | Validation | Typical RTO |
|---|---|---|---|---|
| **A. Code rollout** | New image fails health checks during rollout; SLO burn-rate breach; spike of `purchase_conversion_failed{error_code=UPSTREAM_UNAVAILABLE}` not attributable to Treasury. | `kubectl rollout undo` (or platform equivalent) to the previous image tag. Replicas roll back one at a time; readiness governs traffic. | Smoke (§4.1) + watch RED for 15 min. | ≤ 5 min |
| **B. Configuration** | New env-var rollout introduces a regression (e.g., timeout too low; alias-table path wrong; warm-up list too aggressive). | Platform env-var revert; restart the affected pods. | Smoke (§4.1) + the metric the config drives. | ≤ 2 min |
| **C. Schema migration** | Flyway migration failed in pre-prod *or* prod migration introduces an unexpected lock or wrong shape. | Migrations are forward-only by policy. Author a new `V<N+1>__revert_<reason>.sql` that compensates the change (e.g., drop the new column, restore the index). Deploy via the same rolling code path. Destructive backouts (drop existing data) require change-control approval. | Schema-shape assertion query + smoke. | ≤ 30 min (incl. RFC if destructive) |
| **D. Data corruption** | Bulk wrong rate persisted via Treasury bug; security incident; accidental data deletion; column-corruption-at-rest. | Postgres point-in-time recovery to the latest pre-incident point. Affected replicas re-read after restore. | Targeted SELECT for the affected `(currency, record_date, effective_date)` rows; smoke. | ≤ 30 min (NFR-007) |
| **E. Treasury orientation flip** | `rate_orientation_drift_warn` events ≥ Phase-5 threshold or a real Treasury convention change. | **Not a rollback.** Open a P1 incident; freeze new conversions for the affected currency (feature flag, Phase-5); update the fixture set; ship a hotfix that records the new convention in ADR-0001. | Rate-orientation canary green. | Hours; controlled, not panic. |
| **F. Security incident** | Suspected compromise of HMAC log key, DB credentials, or container image. | Rotate the affected secret; force a rolling restart picking up the new env; consider rolling back image if it is suspect. Run the incident-response runbook (Phase 7). | Per the incident type. | Per the incident SLA. |

---

## 3. Rollback triggers (concrete signals)

Phase 5 ratifies thresholds; the anchors below feed the alerts in [observability.md](observability.md) §6.

### 3.1 Auto-rollback signals (rolling deploy in progress)

| Signal | Threshold | Action |
|---|---|---|
| New replicas fail readiness for ≥ 90 s | platform-level | Roll forward stalls automatically (no surge replaces failing pod). Operator decides revert vs hold. |
| 5xx rate > 1 % | over a 5 min window after the first new pod takes traffic | Page on-call; recommended: roll back. |
| p99 latency > 2× pre-deploy baseline | over a 10 min window | Investigate; rollback at operator's discretion. |
| Per-version error-rate diverges | new version error_rate > old version × 2 over 10 min | Recommended: rollback. |

### 3.2 Triggers outside a deploy window

| Signal | Anchor threshold | Class |
|---|---|---|
| `purchase.conversion.failure.count{error_code=UPSTREAM_UNAVAILABLE}` > 1 % over 5 min **AND** Treasury canary is GREEN | — | A (code rolled out before, recent dep change) |
| Readiness flapping > 3 transitions in 5 min on > 1 replica | — | A or B |
| Treasury canary returns *wrong* rate for the recorded fixture | drift > 5 % vs fixture | E |
| Audit-event count anomaly (e.g., `description.content_guard.fired.count` spike) | > 100 in 5 min | F (suspected probing/attack) |
| DB integrity-violation rate spike | > 5 / min for the upsert path | C or D |

### 3.3 Triggers that explicitly do **not** justify rollback

- Single `UPSTREAM_UNAVAILABLE` burst correlated with Treasury canary RED — *Treasury is down, not us*.
- Single replica failing readiness while the others are UP — *normal LB drains, no action*.
- `currency_alias.drift.detected.count > 0` — *open a PR; do not roll back*.

---

## 4. Procedures

### 4.1 Smoke test (every rollback class validates against this)

```
# Health
curl -fsS http://<endpoint>/actuator/health/readiness
# Create
curl -fsS -X POST http://<endpoint>/api/v1/purchases \
  -H 'Content-Type: application/json' \
  -d '{"description":"smoke","transactionDate":"<today UTC>","amountUsd":"10.00"}'
# Convert
curl -fsS "http://<endpoint>/api/v1/purchases/<id>/conversion?currency=CAD"
```

Expected: `200` from every probe; converted amount within ±1 cent of the latest Treasury rate × 10.00.

The smoke set runs automatically as part of `dev` deploys; for `staging` / `prod` it is invoked by the on-call runbook.

### 4.2 Class A — Code rollback

1. **Confirm trigger** — RED graphs, recent deploy timeline.
2. **Hold deploy** — pause any in-flight rollout (`kubectl rollout pause` or platform equivalent).
3. **Issue revert** — `kubectl rollout undo deployment/wex-purchase-fx`.
4. **Watch** — readiness comes back UP on all replicas; RED returns to baseline within 5 min.
5. **Smoke** — §4.1.
6. **Post-rollback** — file an incident, link to logs/metrics; do not redeploy until root cause is understood.

### 4.3 Class B — Configuration rollback

1. Identify the changed env var (`kubectl set env --from` or platform diff tool).
2. Revert to the previous value via the platform.
3. Restart affected pods (`kubectl rollout restart`).
4. Validate: the metric that this config drives.
5. Smoke § 4.1.

### 4.4 Class C — Schema rollback (forward-only)

Migrations are forward-only. To "roll back" a schema change:

1. Author `V<N+1>__revert_<reason>.sql` that compensates the previous change. Examples:
   - Previous migration added a column → revert drops the column (only safe if no app traffic depends on it).
   - Previous migration added a constraint that is too strict → revert drops the constraint.
   - Previous migration changed a column type → revert may require data copy.
2. Test in `dev` and `staging`.
3. Deploy via the same rolling code path; Flyway picks up the revert migration on application startup.
4. Verify schema-shape:
   ```sql
   SELECT column_name, data_type, is_nullable
   FROM information_schema.columns
   WHERE table_name = 'exchange_rates'
   ORDER BY ordinal_position;
   ```
5. Smoke § 4.1.

**Destructive backouts** (drop existing rows or columns containing user data) require a written RFC + change-control approval before execution. Document the operation in the incident record.

### 4.5 Class D — Data rollback (PITR)

For Postgres-backed `prod` / `staging`:

1. **Identify the target time** — the latest time before the incident. Use audit logs + metric anomalies to pin.
2. **Freeze writes** — set the service replicas to readiness DOWN by setting `WEX_FORCE_READINESS_DOWN=true` (Phase-5 control) **OR** drop traffic at the gateway.
3. **Restore** — platform-managed PITR to the target time. Restored cluster becomes the new primary; the original is archived for forensic review.
4. **Re-read** — restart service replicas; they will re-read from the restored DB.
5. **Validate** — targeted SELECT for the affected rows / range; smoke § 4.1.
6. **Communicate** — clients may need to retry any conversion responses they served between the incident time and the restore time.

For H2-backed `local`:
- Stop the service; replace `${WEX_DATA_DIR}/wex.mv.db` with the most recent backup file; restart.

### 4.6 Class E — Treasury orientation flip (incident, not rollback)

This is **not** a code/data rollback in the conventional sense. The architecture's `RateOrientationContractCheck` is the early warning (Phase-3 D-9; Phase-5 ratifies the fail-closed threshold per G4-P1-27). When it fires beyond the threshold:

1. Open a P1 incident.
2. Confirm the drift against a manual Treasury fetch (run the prototype script in `docs/planning/phase-3-prototype-log.md`).
3. Feature-flag-disable conversions for the affected currency (Phase-5 feature-flag plumbing).
4. Patch the fixture set in `src/test/resources/treasury/fixtures/`.
5. Update ADR-0001 §D-4 with the new convention and the date of observation.
6. Ship a hotfix; verify with the canary.

Risk class is correctness, not availability.

### 4.7 Class F — Security incident

Defer to `docs/security/incident-response-pci.md` (Phase 7 deliverable). High-level shape:

1. Rotate the compromised secret (`WEX_LOG_HASH_KEY`, DB credentials, container image signing key).
2. Force a rolling restart picking up the new env.
3. Audit the audit log; investigate scope of compromise.
4. If the image is suspect, roll back per Class A; tag a forensic image for IR.
5. Notify per the IR playbook (Phase 7).

---

## 5. Post-rollback validation

Every rollback ends with the following:

| Step | What to verify |
|---|---|
| Health checks | All replicas readiness UP; aggregated `/actuator/health` 200. |
| Smoke | §4.1 succeeds. |
| RED | `http.server.requests` error rate < 0.5 % over 10 min. |
| Latency | p99 within NFR-001/002/003 anchors over 15 min. |
| Treasury | `treasury.client.duration` p95 within 2× pre-rollback baseline. |
| Cache | `exchange_rate.cache.hit / total` returning to ≥ 80 % within 30 min. |
| Audit | Incident record updated; post-mortem scheduled within 5 business days. |

---

## 6. Communication plan

| Audience | Channel | When |
|---|---|---|
| On-call team | Pager / IR chat channel | Immediately on trigger. |
| Service owners | IR chat channel | On rollback decision. |
| Affected client teams (prod only) | Status page / email | On rollback completion if customer-visible impact ≥ 1 min. |
| Engineering org | Daily incident digest | Within 24 h. |
| Auditor / PCI evidence (if any change-control implications) | Change-control system | Per platform SOP. |

Communication content for customer-visible incidents must avoid technical details that could aid an attacker (e.g., don't disclose CB calibration in a public status page).

---

## 7. Rehearsal cadence

| Rehearsal | Frequency | What is exercised |
|---|---|---|
| Class A (code rollback) | Quarterly in `staging` | Roll out a no-op image; roll back; validate. |
| Class C (schema rollback) | Per release that includes a migration | Apply migration; apply revert in `staging`. |
| Class D (PITR) | Quarterly (platform-managed) | Restore-to-staging from production snapshots. |
| Class F (security incident) | Annually | Tabletop with the IR playbook. |

Failure of any rehearsal is a P1 issue: the next prod release is blocked until the rehearsal passes.

---

## 8. Forward-looking gaps (Phase 5 / 7 to close)

- **Concrete platform commands.** Phase 5 picks the platform (Kubernetes, ECS, Cloud Run, etc.) and replaces the `kubectl …` examples with the canonical commands.
- **Feature-flag plumbing** for currency-specific freezes (Class E). Phase 5.
- **`WEX_FORCE_READINESS_DOWN` env var.** Phase 5 plumbing.
- **Audit-log integration for change-control evidence.** Phase 7.
- **Status-page template.** Phase 5.

## 9. Linked artefacts

- [docs/architecture/deployment-architecture.md](../architecture/deployment-architecture.md) §9 — high-level rollback summary.
- [docs/planning/design-grill.md](../planning/design-grill.md) — Phase-4 findings; many P1 items here close in Phase 5 / 7 along with rollback rehearsal.
- [docs/operations/observability.md](observability.md) — triggers + telemetry that drive rollback decisions.
- [docs/operations/runbook.md](runbook.md) — Phase-5 deliverable (operator step-by-step including this plan's procedures).
- [docs/operations/incident-response.md](incident-response.md) — Phase-5 deliverable.
- [docs/security/incident-response-pci.md](../security/incident-response-pci.md) — Phase-7 deliverable; Class F defers here.
