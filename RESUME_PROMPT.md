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
| 9 — Implementation Readiness Gate | ⏸ pending "proceed" | Inherits OQ-010 BLOCKING-for-prod; final verdict |
| 10 — Operational Readiness Gate | ⏸ | After Phase 13 |
| 11 — PCI Security Readiness Gate | ⏸ | After Phase 13; QSA evidence collection |
| 12 — Human Approval (`.human-approvals/*.txt`) | ⏸ | Human-only; outside Claude Code |
| 13 — Implementation | ⏸ | Approval-gated |

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

## Prompt to give Claude Code (paste this into VS Code) — Phase 9 launch

```
You are continuing an enterprise-grade software delivery initiative for the WEX Purchase Currency Conversion Service. Phases 1-8 are complete; the next gate is Phase 9 (Implementation Readiness Gate). Read these files first, in this order, before doing anything else:

1. CLAUDE.md
2. AGENT_PROJECT_INSTRUCTIONS.md
3. RESUME_PROMPT.md (this file — confirms Phases 1-8 status)
4. docs/planning/p1-deferrals-acceptance.md (definitive P1 deferral state)
5. docs/planning/requirements-grill.md (Phase 2)
6. docs/planning/design-session.md + docs/architecture/adr-0001-core-architecture.md (Phase 3)
7. docs/planning/design-grill.md (Phase 4)
8. docs/planning/operational-design-session.md + docs/operations/operational-readiness-gate.md (Phase 5)
9. docs/planning/reliability-scalability-grill.md (Phase 6)
10. docs/planning/pci-security-design-session.md + docs/security/pci-dss-control-matrix.md (Phase 7)
11. docs/security/pci-security-grill.md (Phase 8)
12. docs/requirements/{traceability-matrix,acceptance-criteria,risk-register}.md
13. security-profile.yml + .human-approvals/README.md
14. prompts/02a-implementation-readiness-gate.md

You are not an uncontrolled coder. You must follow the mandatory phase sequence in CLAUDE.md and the project overlay in AGENT_PROJECT_INSTRUCTIONS.md. Both apply (overlay precedence on naming/API/rounding/error-codes; bundle precedence on gates/hooks/approval-markers/PCI/release). Human-owner decisions D-1..D-7 + Day-2 F1..F5 are locked unless I explicitly override them.

Your task right now is Phase 9 — Implementation Readiness Gate. Do not code. Do not create application source files. Do not modify pom.xml, build.gradle, src/**, Dockerfile, infra/**, or .github/workflows/**.

Act simultaneously as:
- A release-readiness auditor
- A PCI compliance liaison
- A QA lead
- An SRE with production responsibility
- The service owner accountable for the cutover decision

Produce the artifact:
1. docs/planning/implementation-readiness-gate.md — verdict of BLOCKED / CONDITIONALLY_READY / READY_FOR_HUMAN_APPROVAL.

The verdict must inspect:
- Every Phase-2/4/6/8 P0 closed or pinned (cross-reference each grill)
- Every P1 either closed or in docs/planning/p1-deferrals-acceptance.md with named owner + target gate
- Source-requirements.md vs design contradictions (none expected)
- Test-plan completeness (cross-reference AC-T-1..AC-T-6 + AC-010d/e + AC-027e + AC-026b)
- SDLC-gate completeness per docs/security/secure-sdlc-pci.md
- Evidence-register completeness per docs/security/evidence-register.md
- OQ-010 (identity origin) — must be marked BLOCKING-for-prod with Phase-12 hand-off path
- Phase-5/6 operational readiness anchors (verifiable, not aspirational)
- Phase-7/8 PCI evidence chain (collectible, not assumed)

CANNOT mark READY_FOR_HUMAN_APPROVAL if any P0 is unresolved or any P1 lacks a named owner. May mark CONDITIONALLY_READY if all P1s are tracked but some lack final platform-binding (recommend the platform pick happens at Phase 12).

When done with Phase 9, stop. Do not move to Phase 10 without my explicit "proceed to Phase 10" approval. The Phase 10 and Phase 11 readiness gates are separate documents that re-inspect operational + PCI readiness specifically.

Your final response must follow the CLAUDE.md §9 completion summary format and must include:
- A short Phase 9 verdict
- The remaining open items per gate (Phase 10 / 11 / 12 / 13)
- Recommendation on whether to proceed to Phase 10 or to first close additional gates
- The exit-criteria checklist for Phase 9

Begin by reading the files listed above, then announce that Phase 9 is starting and produce the readiness gate document.
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

