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

## What's complete

- ✅ Phase 1 — Requirements Ingestion (reconciled with `AGENT_PROJECT_INSTRUCTIONS.md` on 2026-05-14).
- ✅ Phase 1 reconciliation pass (endpoint/field/error-code renames, RFC 9457 problem details, PAN-pattern content guard, `record_date` canonical, dual-mode currency input).

**Phase 1 artifacts (authoritative):**
- `docs/requirements/source-requirements.md` (verbatim source — never overwritten)
- `docs/requirements/functional-requirements.md` (FR-001..FR-006)
- `docs/requirements/non-functional-requirements.md` (NFR-001..NFR-035)
- `docs/requirements/acceptance-criteria.md` (AC-001..AC-036 + AC-T-1..AC-T-4)
- `docs/requirements/assumptions-and-open-questions.md` (A-001..A-016, OQ-001..OQ-017)
- `docs/requirements/open-questions.md` (focused OQ register)
- `docs/requirements/risk-register.md` (R-001..R-020 + W-001..W-005)
- `docs/requirements/requirements-analysis.md` (consolidated executive analysis)
- `docs/requirements/traceability-matrix.md` (source ↔ FR ↔ AC ↔ tests ↔ NFRs ↔ risks)

## What's next

Phase 2 — **Requirements Grill** (adversarial). The prompt below kicks this off.

After Phase 2 finishes, the sequence is: Phase 3 (Architecture & Design Session) → Phase 4 (Design Grill) → Phase 5 (Operational Design Session) → Phase 6 (Reliability & Scalability Grill) → Phase 7 (PCI Security Design Session) → Phase 8 (PCI Adversarial Security Grill) → Phase 9 (Implementation Readiness Gate) → Phase 10 (Operational Readiness Gate) → Phase 11 (PCI Security Readiness Gate) → Phase 12 (Human Approval — you manually create `.human-approvals/implementation-approved.txt` and `pci-security-approved.txt`) → Phase 13 (Implementation in small reviewable branches).

Pause after each phase; do not batch.

---

## Prompt to give Claude Code (paste this into VS Code)

```
You are continuing an enterprise-grade software delivery initiative for the WEX Purchase Currency Conversion Service. Read these files first, in this order, before doing anything else:

1. CLAUDE.md
2. AGENT_PROJECT_INSTRUCTIONS.md
3. RESUME_PROMPT.md
4. docs/requirements/source-requirements.md
5. docs/requirements/requirements-analysis.md
6. docs/requirements/functional-requirements.md
7. docs/requirements/non-functional-requirements.md
8. docs/requirements/acceptance-criteria.md
9. docs/requirements/assumptions-and-open-questions.md
10. docs/requirements/open-questions.md
11. docs/requirements/risk-register.md
12. docs/requirements/traceability-matrix.md
13. security-profile.yml
14. .claude/settings.json
15. .human-approvals/README.md
16. prompts/00a-requirements-grill.md

You are not an uncontrolled coder. You must follow the mandatory phase sequence in CLAUDE.md and the project overlay in AGENT_PROJECT_INSTRUCTIONS.md. Both apply. Where they conflict, AGENT_PROJECT_INSTRUCTIONS.md takes precedence on naming, API shape, rounding, and error-code conventions; CLAUDE.md takes precedence on gate sequence, hook-enforced phase guards, human-approval markers, PCI posture, and CI/CD release rules. The human-owner decisions D-1 through D-7 in RESUME_PROMPT.md are locked unless I explicitly override them.

Phase 1 (Requirements Ingestion) is complete. Do not regenerate Phase 1 artifacts. Treat the docs/requirements/* files as authoritative.

Your task right now is Phase 2 — Requirements Grill (adversarial). Do not code. Do not create application source files. Do not modify pom.xml, build.gradle, src/**, Dockerfile, infra/**, or .github/workflows/**.

Act simultaneously as:
- A skeptical enterprise architecture review board
- A production SRE who has been paged at 2 a.m. for currency-conversion regressions
- A Java principal engineer who has seen money-arithmetic bugs ship before
- A security reviewer with a PCI background
- A QA lead who insists on testable, unambiguous acceptance criteria
- A business stakeholder who wants the right answer faster, not a longer document

Aggressively challenge:
- Contradictions across the Phase 1 docs
- Vague or untestable acceptance criteria
- Hidden operational risks
- Reliability/resilience gaps
- Scalability assumptions
- Treasury API dependency risks (especially: which rate field, tie-breaking on duplicates, rate-direction orientation, schema drift)
- Rounding/precision risks (HALF_UP is locked but record the counter-argument and any places HALF_UP behaves surprisingly)
- Validation gaps (especially the PAN-pattern content guard's false-positive profile)
- Error-handling gaps (especially the boundary between CONVERSION_RATE_NOT_AVAILABLE and UPSTREAM_UNAVAILABLE)
- Test-coverage gaps
- Local-runnable-assignment risks (single-jar, H2 file mode)
- Production-readiness gaps
- Security / PCI-scope risks (the out-of-CDE claim must be adversarially attacked)
- The NFR latency/SLO anchors — they are PROPOSED figures and must be either ratified or rejected
- Each of the 17 open questions (OQ-001..OQ-017) — for each one, decide BLOCKING / IMPORTANT / NICE for the next phase

Produce these artifacts:
1. docs/planning/requirements-grill.md  — P0 / P1 / P2 findings; per-finding: title, observation, evidence (cite FR/NFR/AC/A/OQ/R IDs), risk, recommended fix, owner, target gate. End with a proceed/block recommendation and the exit-criteria checklist that must be satisfied before Phase 3 begins.
2. Update docs/requirements/* in place if the grill finds ambiguities you can pin down to a concrete clarification. Preserve IDs; never rewrite source-requirements.md.
3. Update docs/requirements/traceability-matrix.md so every grill finding traces to one or more existing IDs.

When you are done with Phase 2, stop. Do not move to Phase 3 without my explicit "proceed to Phase 3" approval.

Your final response must follow the CLAUDE.md §9 completion summary format and must include:
- A short Phase 2 verdict: PROCEED / BLOCK / CONDITIONALLY PROCEED
- The top three P0 findings
- Any new OQs or risks discovered
- Recommended improvements to AGENT_PROJECT_INSTRUCTIONS.md or CLAUDE.md, if any
- The exit-criteria checklist for Phase 2

Begin by reading the files listed above, then announce that Phase 2 is starting and produce the grill.
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

