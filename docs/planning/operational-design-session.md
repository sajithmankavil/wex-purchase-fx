# Operational Design Session — Phase 5

> **Status:** COMPLETED — 2026-05-17
> **Verdict:** **READY FOR PHASE 6 — RELIABILITY & SCALABILITY GRILL**
> Phase 5 ratified the operational anchors that Phase 6 will adversarially attack. The case-study build runs without these ratifications materialising (the load tests are non-CI manual procedures); the production reference is internally consistent and ready for the next gate.
>
> **Linked artefacts produced by this session:**
> - [docs/operations/service-catalog.md](../operations/service-catalog.md)
> - [docs/operations/slo-sli.md](../operations/slo-sli.md) — closes OQ-014 + G-P1-2
> - [docs/operations/error-budget-policy.md](../operations/error-budget-policy.md)
> - [docs/operations/capacity-scalability-plan.md](../operations/capacity-scalability-plan.md) — closes G4-P1-21, G4-P1-25 anchor
> - [docs/operations/failure-modes-and-resilience.md](../operations/failure-modes-and-resilience.md) — closes G4-P1-17 (CB calibration)
> - [docs/operations/monitoring-alerting.md](../operations/monitoring-alerting.md) — closes G4-P1-27 (rate-orientation fail-closed threshold)
> - [docs/operations/runbook.md](../operations/runbook.md)
> - [docs/operations/oncall-escalation.md](../operations/oncall-escalation.md) — closes E1/E3/E4/E8 RACI shortfalls
> - [docs/operations/operational-readiness-gate.md](../operations/operational-readiness-gate.md)

---

## 1. Scope

Operational design for `wex-purchase-fx` running in:
- **Local / case-study mode** — single process, embedded H2 file mode, no external services. Operational design is documented but mostly inert (no on-call, no alerting destination).
- **Production-reference mode** — N stateless replicas behind internal LB, PostgreSQL, telemetry sinks, gateway-managed identity. The operational design fully applies.

Out of session scope:
- Implementation work (Phase 13).
- PCI security design (Phase 7) — referenced where it intersects (HMAC key sourcing, audit-log destination, etc.).
- Multi-region / DR (OQ-012, deferred to a future revision triggered by RTO < 30 min or read-locality requirement).

## 2. Service identity (cross-reference)

Per [service-catalog.md](../operations/service-catalog.md): Tier-2 prod / Tier-3 case-study; PCI Tier-1 hygiene (out-of-CDE); Java 21 + Spring Boot 3.x; placeholder role ownership pending pre-production hand-off.

## 3. Business-critical workflows

| Workflow | User impact if broken | Dependencies | SLI | SLO | Dashboard |
|---|---|---|---|---|---|
| Create purchase (FR-001) | Cannot record new purchases | DB | SLI-A | SLO-A 99.9 % | Service health |
| Retrieve purchase (FR-002) | Cannot read existing purchase | DB | SLI-B | SLO-B 99.9 % | Service health |
| Retrieve converted purchase (FR-003) | Cannot serve currency conversion; client retry storm if returning 503 | DB + Treasury | SLI-C | SLO-C 99.5 % | Service health + Dependency health |
| Operability — health endpoints | LB doesn't drain correctly; rolling deploys break | DB | implicit | n/a | Rollout health |
| Observability — log/metric/trace emission | Telemetry blind; alerts blind | telemetry sinks | implicit | n/a | (operator-side) |

## 4. Observability design (cross-reference)

[observability.md](../operations/observability.md) defines the contract; this Phase-5 session ratifies it without changes. The Phase-4 grill already pinned the major refinements (correlation-id server-binding, cardinality budget, HMAC key origin, `vN:` prefix).

Phase-5 additions: synthetic checks (§7 of observability.md) and warm-up specification (§8) made concrete here in [capacity-scalability-plan.md §4](../operations/capacity-scalability-plan.md#4-warm-up-job-closes-g4-p1-21).

## 5. Alerting design (cross-reference)

[monitoring-alerting.md](../operations/monitoring-alerting.md) §3 is the alert catalogue. Twenty-eight alerts (A-001..A-028) plus six burn-rate alerts. Each has severity, threshold, runbook anchor.

**Ratified at Phase 5:**
- Rate-orientation drift fail-closed threshold (G4-P1-27): SEV1 at > 5 % canary drift or 3 consecutive canary failures; SEV2 at ≥ 3 WARN/24 h.
- Alert routing: SEV1 page, SEV2 low-urgency page, SEV3 ticket, build-block alerts no page.
- Monthly alert review (§7 of monitoring-alerting.md) is owned by SRE + on-call + service owner.

## 6. Reliability design (cross-reference)

[failure-modes-and-resilience.md](../operations/failure-modes-and-resilience.md) catalogues 27 failure modes (F-01..F-27) with detection, mitigation, recovery, and test mapping.

**Ratified at Phase 5:**
- Circuit-breaker calibration (G4-P1-17): minimum-number-of-calls raised from 20 → **100**; wait-duration-in-open raised from 30 s → **5 min** for production. Rationale: low-traffic dependency (Treasury) needs larger sample for fail-rate stability; outages are minutes-to-hours, not seconds.
- Single-flight gate (Phase-4 G4-P0-1 + G4-P1-6 + G4-P1-20): all aspects in place. Bounded loser wait 200 ms + DB poll; gate-release-on-SIGTERM.
- Versioned rate persistence (D-3 / A-018): defensive design retained; empirical revision rate to be re-measured in Phase 6 if useful.
- HMAC log-hash key strength (NFR-013b): ≥ 256 bits; production refuse-to-start invariant.

## 7. Scalability design (cross-reference)

[capacity-scalability-plan.md](../operations/capacity-scalability-plan.md) pins the anchors and the load-test plan.

**Ratified at Phase 5:**
- Per-replica steady-state: 100 req/s mixed; peak burst 250 req/s for ≤ 60 s.
- Cache-hit ratio target: ≥ 99 % steady-state.
- Cluster minimum: 2 replicas in `prod`.
- Warm-up currency list (G4-P1-21): 10 currencies covering ~95 % of typical traffic; configurable.
- Load-test plan: 7 scenarios in `staging` using k6; non-CI; per-release for material changes.

## 8. Rollback and recovery (cross-reference)

[rollback-plan.md](../operations/rollback-plan.md) was authored at Phase 4; Phase 5 inherits it without modification. RTO ≤ 30 min, RPO ≤ 5 min for production-reference; the case-study mode has no DR commitments.

## 9. Architecture options considered (Phase 5 perspective)

The architecture itself is set (ADR-0001). Phase 5 considered three operational-design options:

| Option | Description | Pros | Cons | Decision |
|---|---|---|---|---|
| **Single dashboard, single severity ladder** | All alerts → one dashboard, two severities (page / no-page). | Simplest; least cognitive load. | Conflates SLO burn with resource saturation; pagers fire on noise. | Rejected. |
| **Five dashboards, three severities, multi-window burn rates** (chosen) | Service health / Dependency health / Cache / Business / Rollout dashboards; SEV1/SEV2/SEV3 ladder; SLO burn alerts use 5 min × 1 h × 6 h windows. | Aligns to Google SRE workbook; predictable on-call experience; supports the existing PCI evidence requirements. | More setup work; more JSON in `monitoring/dashboards/`. | **Accepted.** |
| **Per-currency SLO matrix** | One SLO per top-10 currency. | Granular; would catch single-currency degradations. | Cardinality explosion; doesn't fit the cardinality budget; per-currency alerts would page too often. | Rejected. Per-currency is a *histogram label* not a *SLO label*. |

| Option | Description | Decision |
|---|---|---|
| **Latency-budget mode (chosen)** | p99 latency anchors are SLO-tracked but not page-worthy on miss; only saturation alerts page. | **Accepted.** Latency degradation rarely warrants a page; saturation does. |
| Latency SLO alerting | Page on p99 > anchor for any window | Rejected. Too noisy for a latency-distribution that has long tails inherently. |

## 10. Key design decisions (Phase 5)

| Decision | Rationale | Reference |
|---|---|---|
| SLO-C bounded by Treasury × cache-hit | Phase-2 G-P1-2 root cause; closes the math problem. | [slo-sli.md §4](../operations/slo-sli.md) |
| CB calibration: 100 calls, 5 min open | Treasury low-traffic + outages-are-minutes | [failure-modes-and-resilience.md §3](../operations/failure-modes-and-resilience.md) |
| Warm-up 10 currencies, 30 s timeout | Covers top corridors; bounded by deploy-tempo | [capacity-scalability-plan.md §4](../operations/capacity-scalability-plan.md) |
| Rate-orientation fail-closed at > 5 % drift | Single-canary-drift threshold; avoids paging on noise | [monitoring-alerting.md §3.1](../operations/monitoring-alerting.md) |
| Per-class mutation thresholds (G4-P1-9) | ≥ 85 % on `RateSelectionPolicy`, `Money`; package average elsewhere | Phase-13 enforcement; recorded in [non-functional-requirements.md](../requirements/non-functional-requirements.md) NFR-021 (to be updated Phase 13) |

## 11. Security design (cross-reference)

[threat-model.md](../security/threat-model.md) is the source. Phase 5 confirms the operational implications:

- HMAC log-hash key sourcing in prod remains Phase 7 (G4-P1-11) — Phase 5 documents the runbook (§6.24).
- Audit-log destination remains Phase 7 (G4-P1-16) — Phase 5 specifies retention semantics ([service-catalog.md](../operations/service-catalog.md)).
- Vulnerability-management SLA remains Phase 7 (G4-P1-26) — Phase 5 documents the runbook for CVE rebuilds.
- App-layer rate limiting remains Phase 7 (G4-P1-15) — Phase 5 doesn't override.

## 12. Operational design (cross-reference)

- **Deployment model:** rolling with surge `maxSurge=1, maxUnavailable=0`; canary alternative for material releases ([rollback-plan.md §4.2](../operations/rollback-plan.md)).
- **Health checks:** liveness (JVM) + readiness (DB + alias-table + hot cache + pool capacity) per [component-design.md §3.4](../architecture/component-design.md).
- **Metrics, logs, traces:** per [observability.md](../operations/observability.md).
- **Alerts, dashboards:** per [monitoring-alerting.md](../operations/monitoring-alerting.md).
- **Rollback:** six classes per [rollback-plan.md](../operations/rollback-plan.md).
- **DR:** single-region v1; multi-region deferred (OQ-012).

## 13. Open risks (carried into Phase 6)

| Priority | Risk | Mitigation | Owner | Phase to close |
|---|---|---|---|---|
| P1 | Capacity anchors (100 req/s / replica, 95+ % cache hit) unverified by load test | Manual load-test plan in capacity-plan.md §5; Phase 6 ratifies anchors | SRE | Phase 6 |
| P1 | CB calibration (100 calls / 5 min open) is a working figure; not yet exercised at scale | Phase 6 chaos test under load | SRE | Phase 6 |
| P1 | Single-flight fairness under thundering herd at production scale untested | Phase 6 chaos test (failure-injection §5 of capacity-plan) | SRE | Phase 6 |
| P1 | Rate-orientation contract canary not yet implemented | Phase 13 implementation; Phase 5 documents the threshold (>5 % / 3-consecutive) | SRE + Architect | Phase 13 |
| P2 | Cache TTL: 24 h v1 / 7 d prod recommended; not yet measured | Phase 6 measurement; ADR-0001 D-10 trade-off | SRE | Phase 6 |
| P2 | Empirical Treasury availability baseline | 90 days passive observation (canary every 1 h) | SRE | Phase 6 / ongoing |
| P3 | RACI placeholder names (E1/E3/E4/E8) — real owners pending org mapping | Hand-off at pre-production cutover | Service owner | Phase 12 |

## 14. Exit-criteria checklist

| Criterion | Status |
|---|---|
| Service catalog with named ownership (placeholders OK for case study) | ✅ |
| SLIs / SLOs / error budget defined with Treasury-uptime ceiling formula | ✅ |
| Observability design ratified | ✅ (Phase 4 + cross-ref) |
| Monitoring + alerting catalogue with severity ladder + runbook anchors | ✅ |
| Dashboards specified | ✅ |
| Health + synthetic checks defined | ✅ (observability.md §7) |
| Capacity + scalability plan with load-test scenarios | ✅ |
| Failure modes catalogued with detection / mitigation / recovery / test | ✅ |
| Load / performance testing — documented manual plan (CI-out-of-scope per source) | ✅ |
| Incident + on-call process with escalation paths | ✅ |
| Runbook covering every alert in the catalogue | ✅ |
| Rollback (Phase 4 inherit) | ✅ |
| Phase-4 deferred P1s addressed (G-P1-2, G4-P1-9, G4-P1-17, G4-P1-21, G4-P1-25, G4-P1-27) | ✅ |
| E1 / E3 / E4 / E8 RACI placeholders structured | ✅ |
| OQ-014 (SLO target ratification) closed | ✅ |
| Phase-2 grill deferrals (G-P1-2) closed | ✅ |
| Verdict recorded | ✅ |

## 15. Verdict

**READY FOR PHASE 6 — RELIABILITY & SCALABILITY GRILL.**

Phase 6 adversarially attacks the anchors and calibrations Phase 5 has just pinned: 100 req/s/replica, ≥ 99 % cache-hit, CB calibration, single-flight fairness, warm-up adequacy. The grill produces P0/P1/P2 findings + chaos-test results, and either ratifies the anchors or revises them.

Phase 6 begins on explicit "proceed to Phase 6" approval. Pause cadence per D-2.
