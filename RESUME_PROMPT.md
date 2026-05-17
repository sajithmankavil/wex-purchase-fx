# Resume Prompt — WEX Purchase Currency Conversion Service

> Paste the **"Prompt to give Claude Code"** block at the bottom of this file into Claude Code (VS Code) as your first message in the new session. Everything Claude Code needs to pick up cleanly is either in that prompt or in the files it tells Claude Code to read first.
>
> This file is durable; refer to it any time you start a new Claude Code session in this project.

---

## Where we are

**Project root:** `C:\Users\sajit\OneDrive\Documents\Claude\Projects\WEX Case Study\wex-purchase-fx\`

**Governance documents (both in force):**
- `CLAUDE.md` — enterprise control bundle (planning gates, hooks, PCI, human approval markers, completion summary format).
- `AGENT_PROJECT_INSTRUCTIONS.md` — project-specific overlay (REST shape, naming, rounding, error codes). Takes precedence over the bundle on naming and API shape; the bundle takes precedence on gate sequence and approval markers.

**Source requirement:** `docs/requirements/source-requirements.md` (verbatim WEX requirements).

## Decisions already made by the human owner

| # | Decision | Rationale |
|---|---|---|
| D-1 | **PCI posture:** out-of-CDE; apply Tier-1 hygiene. Scope-reduction evidence is a deliverable (Phase 7). | The source contains no PAN/SAD/CHD; treating it as a CDE would be ceremony, but throwing PCI rigor away loses the enterprise dimension. |
| D-2 | **Cadence:** pause after each major gate; do not batch phases. | Maximises rigor; lets the human review each artifact before the next gate begins. |
| D-3 | **Currency input format:** accept **both** Treasury `country_currency_desc` (e.g., `Canada-Dollar`) **and** ISO 4217 codes (e.g., `CAD`) via a curated alias table. The response always returns the canonical `country_currency_desc`. | Client ergonomics + Treasury fidelity. |
| D-4 | **Gate depth:** keep **all** bundle gates as separate documents (operational design, reliability/scalability grill, PCI design, PCI grill, separate operational-readiness and PCI-security-readiness gates), not the lighter set in the project guideline. | Matches the bundle's hook scripts; higher production fidelity. |
| D-5 | **Rounding mode:** `HALF_UP` to scale 2 for `convertedAmount`. HALF_EVEN trade-off documented in ADR-0001. | Per `AGENT_PROJECT_INSTRUCTIONS.md` §5. |
| D-6 | **Stack (proposed, to be ratified in ADR-0001):** Java 21 · Spring Boot 3.x · Maven · embedded H2 (file mode) · Flyway · Resilience4j · springdoc-openapi · Micrometer + OpenTelemetry · JUnit 5 + AssertJ + Mockito + WireMock + Pitest + ArchUnit + jqwik · SLF4J/Logback (JSON). PostgreSQL-compatible production profile. | Satisfies "no separate DB/web server/servlet container"; modern enterprise default. |
| D-7 | **Project layout:** bundle scaffolded into `WEX Case Study/wex-purchase-fx/` as the project root. | Persists on the user's drive across sessions. |

## What's complete (updated 2026-05-17, end of Phase 8)

| Phase | Status | Headline |
|---|---|---|
| 1 — Requirements Ingestion | ✅ | FR/NFR/AC/A/OQ/R registers populated; reconciled with overlay 2026-05-14 |
| 2 — Requirements Grill | ✅ | 5 P0 + 11 P1 + 10 P2 findings; 12 new ACs; bundle/overlay edits applied at Phase-4 hardening |
| 3 — Architecture & Design Session | ✅ | ADR-0001 D-1..D-14 + 5 architecture docs; Treasury rate orientation empirically verified |
| 4 — Design Grill | ✅ | 5 P0 (single-flight, cache key, scale-6, UUID-v7, encoded-PAN) + 20 P1 + 10 P2 |
| 4-hardening pass | ✅ | Bundle/overlay drift closed; P1 deferrals acceptance doc; Day-2 ratifications F1–F5; 5 new threat-model entries; NFR-013b HMAC ≥ 256 bits |
| 5 — Operational Design Session | ✅ | 10 deliverables; SLO-C bounded by Treasury × cache-hit; CB calibration; warm-up; rate-orientation fail-closed threshold |
| 6 — Reliability & Scalability Grill | ✅ | 4 P0 (CB TIME_BASED, loser-wait 10 s, p99 1500 → 3000 ms, DB pool 10 → 20) + 12 P1 |
| 7 — PCI Security Design Session | ✅ | 13 security docs + meta; D-15..D-20 (rate-limiter, HMAC sourcing, audit destination, vuln SLA, encoded-PAN, PMD policy) |
| 8 — PCI Adversarial Security Grill | ✅ | 4 P0 (decoder ordering, audit re-categorisation, Unicode NFKC, Treasury TPSP) + 12 P1; AC-010e + AC-T-6 + R-038..R-041 |
| 9 — Implementation Readiness Gate | ✅ | Verdict: **READY_FOR_HUMAN_APPROVAL** with Phase-12 conditions; OQ-010 BLOCKING-for-prod tracked; first milestone M1 defined (~500 LOC domain layer) |
| 10 — Operational Readiness Gate | ⏸ | After Phase 13 (load-test execution + named individuals + alert wiring) |
| 11 — PCI Security Readiness Gate | ⏸ | After Phase 13; QSA evidence collection per evidence-register.md EVD-001..012 |
| 12 — Human Approval (`.human-approvals/*.txt`) | ⏸ | Human-only; outside Claude Code. Implementation approval can be created **now** based on Phase-9 verdict; PCI-security + PCI-production approvals wait for Phases 10/11 |
| 13 — Implementation | ⏸ | Unblocks when `.human-approvals/implementation-approved.txt` + `pci-security-approved.txt` are created |

**Repo:** [github.com/sajithmankavil/wex-purchase-fx](https://github.com/sajithmankavil/wex-purchase-fx) (private; initial commit Phase 4)

**Core artefacts (authoritative):**
- `docs/requirements/source-requirements.md` (verbatim source — never overwritten)
- `docs/requirements/functional-requirements.md` (FR-001..FR-006 + decision tables)
- `docs/requirements/non-functional-requirements.md` (NFR-001..NFR-035 + NFR-013b/014b/016b/018b)
- `docs/requirements/acceptance-criteria.md` (AC-001..AC-036 + AC-T-1..AC-T-6 + 14 Phase-2/4/8 additions)
- `docs/requirements/assumptions-and-open-questions.md` (A-001..A-022; OQ-001..OQ-023; closures Phase-2/3/4/6/7/8)
- `docs/requirements/risk-register.md` (R-001..R-041)
- `docs/requirements/traceability-matrix.md` (bidirectional + per-gate finding maps)
- `docs/planning/{requirements-grill,design-session,design-grill,operational-design-session,reliability-scalability-grill,pci-security-design-session}.md`
- `docs/planning/{day-1-ratifications,phase-3-prototype-log,p1-deferrals-acceptance}.md`
- `docs/architecture/{adr-0001-core-architecture,system-context,component-design,data-model,api-contracts,deployment-architecture}.md`
- `docs/operations/` (10 deliverables: service-catalog, slo-sli, error-budget-policy, capacity-scalability-plan, failure-modes-and-resilience, monitoring-alerting, observability, runbook, oncall-escalation, operational-readiness-gate, rollback-plan, incident-response)
- `docs/security/` (14 Phase-7 deliverables + threat-model.md + pci-security-grill.md from Phase 8)

## What's next

**Phase 9 — Implementation Readiness Gate.** Single deliverable: `docs/planning/implementation-readiness-gate.md`. Verdict will be one of:

```
Status: BLOCKED
Status: CONDITIONALLY_READY
Status: READY_FOR_HUMAN_APPROVAL
```

Phase 9 inspects:
- Every Phase-2/4/6/8 P0 closed or pinned ✅ (already done)
- Every P1 either closed or in `docs/planning/p1-deferrals-acceptance.md` with named owner + target gate ✅
- OQ-010 (identity origin) — **still BLOCKING-for-prod**; Phase 9 records this for Phase 12 hand-off
- No source-rule conflicts; no spec-vs-design contradictions
- Test-plan completeness; SDLC-gate completeness; evidence-register completeness

Phase 9 cannot mark "READY_FOR_HUMAN_APPROVAL" if any P0 is unresolved or any P1 lacks a named owner.

After Phase 9: Phase 10 (operational readiness) → Phase 11 (PCI security readiness) → Phase 12 (human approval markers, human-only) → Phase 13 (implementation in small reviewable branches).

Pause after each phase; do not batch (D-2).

---

## Prompt to give Claude Code (paste this into VS Code) — Phase 10 launch

```
You are continuing an enterprise-grade software delivery initiative for the WEX Purchase Currency Conversion Service. Phases 1-9 are complete; the next gate is Phase 10 (Operational Readiness Gate). Read these files first, in this order, before doing anything else:

1. CLAUDE.md
2. AGENT_PROJECT_INSTRUCTIONS.md
3. RESUME_PROMPT.md (this file — confirms Phases 1-9 status)
4. docs/planning/implementation-readiness-gate.md (Phase 9 verdict — READY_FOR_HUMAN_APPROVAL with Phase-12 conditions)
5. docs/planning/p1-deferrals-acceptance.md (definitive P1 deferral state with Phase-9 acceptance section)
6. docs/operations/operational-readiness-gate.md (Phase-5 scaffold; Phase-10 re-inspects)
7. docs/operations/slo-sli.md + docs/operations/error-budget-policy.md (Phase 5)
8. docs/operations/capacity-scalability-plan.md (load-test plan and anchors)
9. docs/operations/failure-modes-and-resilience.md (CB calibration; F-01..F-27)
10. docs/operations/monitoring-alerting.md (alerts; A-001..A-028 + burn-rate alerts)
11. docs/operations/runbook.md (operator step-by-step)
12. docs/operations/oncall-escalation.md (severity ladder; rotations; RACI; named individuals)
13. docs/planning/reliability-scalability-grill.md (Phase 6 — anchor math + corrections)
14. docs/requirements/{risk-register,traceability-matrix}.md
15. security-profile.yml + .human-approvals/README.md
16. prompts/02b-operational-readiness-gate.md

You are not an uncontrolled coder. You must follow the mandatory phase sequence in CLAUDE.md. Phase 9 said READY_FOR_HUMAN_APPROVAL for implementation, not for production deployment. Phase 10 is the **operational** readiness gate that re-inspects whether the production-reference deployment can sustain production load with the documented SLOs, alerts, runbook, and on-call.

Your task right now is Phase 10 — Operational Readiness Gate. Do not code. Do not modify implementation files. Phase 10 may re-affirm or revise the existing operational design; it produces a final verdict on operational-readiness specifically.

Act simultaneously as:
- A platform SRE leader signing off on production take-over
- The on-call who will be paged at 2 a.m.
- The capacity planner who has to ratify the load-test numbers
- The incident commander responsible for SEV1 response
- The auditor verifying SLO/SLI evidence

Produce the artifact:
1. docs/operations/operational-readiness-gate.md — Go/No-Go for operational readiness.

The verdict must verify:
- Capacity load-tests EXECUTED (not just planned) — capacity-plan §5 scenarios run in staging
- CB calibration tested under chaos (failure-injection §5)
- SLO baseline measured (not anchored) — 90 days of canary + SLI data ideally, or first 30 days as bootstrap
- Alert routing wired with concrete destinations (pager + ticket + channel)
- On-call rotation NAMED with real individuals (replace E1/E3/E4/E8 placeholders)
- Dashboards JSON or dashboard-as-code committed
- Synthetic checks deployed
- Warm-up job deployed and observed
- Incident-response procedure rehearsed (tabletop or live drill)
- Rollback rehearsed (Class A and Class C minimum)

For a case-study posture (no real production load), the verdict can be CONDITIONAL_PASS with explicit "Phase-12 cutover requires the items above." For a production-cutover posture, hard requirements apply.

When done with Phase 10, stop. Do not move to Phase 11 without my explicit "proceed to Phase 11" approval. Phase 11 (PCI Security Readiness Gate) is separate and re-inspects PCI evidence collection.

Your final response must follow the CLAUDE.md §9 completion summary format.

Begin by reading the files listed above, then announce that Phase 10 is starting and produce the operational-readiness-gate document.
```

---

## Bootstrap reminders for the new session

- **Working directory** when Claude Code opens VS Code: `C:\Users\sajit\OneDrive\Documents\Claude\Projects\WEX Case Study\wex-purchase-fx\`. The hooks in `.claude/settings.json` (`planning_phase_guard.py`, `pci_security_phase_guard.py`, `protected_file_guard.py`, etc.) run automatically and enforce the gates — if Claude Code tries to write to `src/**` before approval, the hook will refuse. That is intended.
- **`.human-approvals/` is human-only.** When you (the human) are satisfied with the planning gates and ready to permit implementation, you create those marker files manually with the exact contents shown in `.human-approvals/README.md`. Claude Code must never create them.
- **Pause cadence.** After each Phase, Claude Code is expected to stop and wait for your "proceed to Phase N+1" before continuing. The prompt above enforces this for Phase 2; replicate the pattern for subsequent phases.
- **If you want to resume Phase 2 directly without the read-everything preamble** (e.g., in a follow-up message after the first session loads context), just say: *"continue Phase 2 — Requirements Grill, following the rules I gave you, and produce docs/planning/requirements-grill.md."*

## When you reach Phase 13 (implementation)

Before pasting the implementation prompt, manually create:

```
.human-approvals/implementation-approved.txt   (single line: APPROVED_FOR_IMPLEMENTATION)
.human-approvals/pci-security-approved.txt     (single line: APPROVED_FOR_PCI_SECURITY_IMPLEMENTATION)
```

Until those exist, the `planning_phase_guard.py` and `pci_security_phase_guard.py` hooks will refuse all writes outside `docs/**`, `prompts/**`, `README.md`, `CLAUDE.md`, and `AGENT_PROJECT_INSTRUCTIONS.md`.

