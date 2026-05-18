# 00-prompt — Phase 10 (Operational Readiness Gate)

**Authored:** 2026-05-18 — by the dev agent under "proceed thru next phases to next HITL" authorization. **The reviewer normally pre-stages this file per `directives/2026-05-18-phase-10-11-bulk-pass-protocol.md` §4 step 1.** This is a forward-motion-bias bend: the directive activated at 13-C2 merge (now done); rather than block on a reviewer round-trip, the dev pre-stages from the directive's contract. Reviewer is free to amend this prompt on the next tick before the bundle review.
**Phase:** 10 — Operational Readiness Gate
**Trigger:** Merge of `13-C2-observability-cicd` (`c92e146`) + `13-C3-openapi-cicd` (`d06b552`), 2026-05-18.

---

## 1 — Purpose

Phase 10 is the **evidence gate** that the service can be operated safely in production. The dev agent compiles all evidence into a single `20-bundle.md`; the reviewer signs off (or returns gaps) in one deep pass per the bulk-pass protocol.

This phase is NOT an implementation phase — there is no new application behaviour to ship. The job is to demonstrate that the artifacts the design phases promised actually exist, are coherent, and are sufficient for a real on-call rotation to operate the service.

## 2 — Source documents (the evidence inventory)

Every requirement in every section of every file below is in scope. The bundle's coverage map must reference each file by name + section.

**Core five (minimum per directive §6):**

- [`docs/operations/observability.md`](../../../operations/observability.md) — SLI definitions, dashboards, structured-log contract, trace propagation, event taxonomy.
- [`docs/operations/capacity-scalability-plan.md`](../../../operations/capacity-scalability-plan.md) — DB pool sizing, replica strategy, request budgets at p99 load, growth runway.
- [`docs/operations/failure-modes-and-resilience.md`](../../../operations/failure-modes-and-resilience.md) — circuit breaker calibration, retry envelopes, single-flight gate, graceful shutdown, idempotency, bulkheads.
- [`docs/operations/incident-response.md`](../../../operations/incident-response.md) — severity levels, response process, paging integration, post-incident review template.
- [`docs/operations/rollback-plan.md`](../../../operations/rollback-plan.md) — per-rollback-class procedures (A/B/C/D); verified rollback evidence per class.

**Operational-context (also in scope — referenced by CLAUDE.md Gate 3A):**

- [`docs/operations/service-catalog.md`](../../../operations/service-catalog.md) — service owner, criticality, upstream/downstream dependencies.
- [`docs/operations/monitoring-alerting.md`](../../../operations/monitoring-alerting.md) — alert conditions, paging policy.
- [`docs/operations/slo-sli.md`](../../../operations/slo-sli.md) — SLOs with measurable targets, error-budget thresholds.
- [`docs/operations/error-budget-policy.md`](../../../operations/error-budget-policy.md) — budget burn thresholds, freeze triggers, policy escalations.
- [`docs/operations/oncall-escalation.md`](../../../operations/oncall-escalation.md) — rotation, contact tree, paging tool.
- [`docs/operations/runbook.md`](../../../operations/runbook.md) — operational procedures (deploy, rollback, common failure responses).
- [`docs/operations/operational-readiness-gate.md`](../../../operations/operational-readiness-gate.md) — the gate's own self-check.

**Carry-forwards from Phase 13 reviews that route to Phase 10** (per `chunks/13-C3-openapi-cicd/manifest.yml::follow_ups`):

- `oasdiff-vs-live-oas-pending-mvn` — paired with M7 Maven-runner provisioning; document the deferral or close.
- `rate-revision-end-to-end-it-cache-invalidation-variant` — optional second test variant exercising cache-invalidation through the HTTP boundary.
- `malformed-identifier-exception-message-redaction` — defense-in-depth: drop raw input from `MalformedIdentifierException.getMessage()`.

(Note: the C3 30-review's F2 part 1 — `spectral-lint-node-setup-unconditional` — was closed pre-merge in the C3 hygiene commit `5ca15e6`. It is NOT a Phase 10 carry-forward.)

## 3 — Bundle structure

Per directive §5. The `20-bundle.md` must contain:

1. A **coverage-map** table with one row per requirement across the source docs above. Status column uses ✅ / ⚠️ / `BLOCKER: <reason>`.
2. A **Decisions** section listing non-obvious judgment calls the dev made while compiling evidence.
3. A **Risks** section listing known weaknesses in the evidence that reviewer should evaluate.
4. An **Open questions** section listing items needing reviewer input before phase can close.

Artifacts live in their natural homes — `docs/operations/runbooks/<scenario>.md`, `docs/operations/drills/<date>-<scenario>.md`, `infra/dashboards/<dashboard>.json`, etc. The bundle is the *index*, not the storage.

## 4 — Phase 10 acceptance rubric (the reviewer's bar)

The reviewer (`30-review.md`) verifies:

- **Coverage** — every requirement in the source docs has a row in the coverage map.
- **Quality** — for each ✅, the linked artifact is substantive (not a stub) and matches the requirement.
- **BLOCKER discipline** — every BLOCKER is justified (genuinely requires resources unavailable pre-production) and routed to either Phase 11 or Phase 12 follow-up.
- **Decisions/Risks honesty** — judgment calls and risks are surfaced in the bundle, not hidden in artifact bodies.
- **Drill evidence** — at minimum, the failure-modes doc's CB calibration drill and one rollback drill have completed-drill logs at `docs/operations/drills/` (production-equivalent or WireMock-injected).

Outcome enum per directive §4 step 3:
- **ACCEPTED** — every requirement covered; quality bar met.
- **ACCEPTED WITH CONDITIONS** — every requirement covered; non-blocking quality gaps tracked as follow-ups.
- **GAPS_RETURNED** — uncovered requirement(s) or substantively-insufficient artifact(s); dev addresses all gaps in one revision cycle (`25-bundle-revision.md`).

## 5 — Forward-motion bias

Per `directives/2026-05-17-forward-motion-bias.md` and the bulk-pass directive §7, the dev makes borderline judgment calls and documents them under "Decisions". Reviewer ratifies or amends. Bias toward shipping the bundle with explicit risks documented over delaying for borderline polish.

## 6 — Phase 11 pre-stage trigger

Phase 11 (PCI Security Readiness) pre-staging is triggered when Phase 10 status flips to `accepted`. Until then, the reviewer is not expected to author Phase 11's `00-prompt.md`.

End of prompt.
