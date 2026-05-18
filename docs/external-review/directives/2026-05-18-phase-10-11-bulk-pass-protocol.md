# Directive — Bulk-pass review protocol for Phases 10 + 11

**Author:** External governance reviewer
**Date:** 2026-05-18
**Applies to:** Phase 10 (Operational Readiness Gate) and Phase 11 (PCI Security Readiness Gate)
**Does NOT apply to:** Phase 13 (still in-flight; per-chunk protocol stands) or Phase 12 (production approval — separate procedure, see §9 below).
**Supersedes:** Nothing. This is an addition to the dossier convention spec in `docs/external-review/README.md`.

## 1 — Why

Phase 13 was code in small chunks. Each chunk demonstrably touched application behaviour; tight per-PR reviewer loops earned their cost.

Phases 10 + 11 are **evidence gates**, not implementation phases. The dev agent produces artifacts (capacity-plan validation reports, failure-mode drill evidence, runbooks, SLO dashboards, PCI-DSS control mappings, audit-log retention proofs, encryption-at-rest evidence, vulnerability scan output, etc.) and the reviewer signs off that the bundle meets a predefined bar. Iterating per-artifact would compound round-trip latency without commensurate quality gain — the gate's evidence inventory is already enumerated in `docs/operations/*` and `docs/security/*`; the reviewer's job is to verify coverage and quality, not to discover scope.

This directive defines a **bulk-pass protocol**: dev produces all evidence for a phase in one cohesive submission; reviewer does one deep read; one verdict.

## 2 — Layout

Parallel to `chunks/`, a new directory `phases/`:

```
docs/external-review/
├── chunks/                          (Phase 13 — existing)
│   └── 13-*/
└── phases/                          (NEW)
    ├── 10-operational-readiness/
    │   ├── 00-prompt.md             ← reviewer-authored; references docs/operations/* requirements
    │   ├── 20-bundle.md             ← implementer-authored; index + pointers to all evidence artifacts
    │   ├── 30-review.md             ← reviewer-authored; verdict + gap list (if any)
    │   └── manifest.yml             ← state pointer
    └── 11-pci-security-readiness/
        ├── 00-prompt.md
        ├── 20-bundle.md
        ├── 30-review.md
        └── manifest.yml
```

Authorial limits are identical to the chunk convention (see README §"Authorial limits"). The only file-naming change is `20-bundle.md` instead of `20-summary.md` — semantically the bundle is an *index* of evidence artifacts that live elsewhere in the repo, not a summary of a single code-chunk's changes.

## 3 — State machine

Reuses the chunk state enum from `README.md` §"State machine". Mapping:

| Phase status | Set by | Meaning |
|---|---|---|
| `prompt_received` | reviewer (at phase creation) | `00-prompt.md` exists; dev hasn't begun compiling evidence |
| `bundling` | dev | Dev is producing artifacts |
| `bundle_posted` | dev | `20-bundle.md` written; all referenced artifacts present in repo; reviewer cue |
| `under_review` | dev (after bundle ready) | Reviewer is reading the bundle + cross-checking artifacts |
| `gaps_returned` | reviewer | `30-review.md` lists missing or insufficient artifacts; dev must address before re-submitting |
| `accepted` | reviewer | `30-review.md` says gate passed |
| `rejected` | reviewer | Used only for irrecoverable failures (e.g., scope misunderstanding requiring re-scoping) |

`implementing` and `deviation_surfaced` from the chunk enum are not used for phases — the gate's evidence inventory is fixed by the predefined documents, so there is no "deviation from prompt" path. If the dev finds a gate requirement that cannot be met (e.g., a missing tool), they raise it inline in `20-bundle.md` as a `BLOCKER:` entry and flip status to `bundle_posted` anyway; the reviewer treats it as a gap.

## 4 — The protocol

### Step 1 — Reviewer pre-stages the phase

Triggered: when the preceding phase's final chunk merges. For Phase 10, the trigger is the merge of `13-C2-observability-cicd`. For Phase 11, the trigger is acceptance of Phase 10.

Reviewer writes:

- `phases/<phase-id>/00-prompt.md` — short kickoff (typically ≤ 100 lines):
  - Lists the source documents that define the evidence requirements (e.g., `docs/operations/observability.md`, `docs/operations/capacity-scalability-plan.md`, `docs/operations/failure-modes-and-resilience.md`, `docs/operations/incident-response.md`, `docs/operations/rollback-plan.md` for Phase 10).
  - Any phase-specific guidance not in the source docs (e.g., "drill the failure-mode scenarios under Postgres-timeout + Treasury-5xx; capture screenshots; persist drill log to `docs/operations/drills/2026-MM-DD-<scenario>.md`").
  - The bundle's required structure (see §5 below).
  - The reviewer's success criteria (the rubric used in `30-review.md`).
- `phases/<phase-id>/manifest.yml` — initial state `prompt_received`.
- An entry in `STATUS.md` rollup table for the phase.

### Step 2 — Dev compiles the bundle

Dev reads the phase's `00-prompt.md` + the source documents it references. Dev produces:

1. Every artifact called for by the source documents. Artifacts live in their natural home (`docs/operations/runbooks/<scenario>.md`, `docs/operations/drills/...md`, dashboards as JSON in `infra/dashboards/`, etc.) — **not** inside `phases/<phase-id>/`. The phase folder is the *index*, not the storage.
2. `phases/<phase-id>/20-bundle.md` — the index. See §5 for required structure.
3. Updates `manifest.status` to `bundling` during work; flips to `bundle_posted` on completion.

**One single submission.** No partial drops. If dev hits a true blocker (missing tool, missing input, irreconcilable requirement), they document it as a `BLOCKER:` line in `20-bundle.md` and submit the bundle anyway with that gap explicit.

### Step 3 — Reviewer does one deep pass

Reviewer reads `20-bundle.md` end-to-end, then opens each linked artifact and verifies it against the source documents' requirements. The pass is sequential through every requirement, not random-sample.

Reviewer writes `phases/<phase-id>/30-review.md` with one of three outcomes:

- **ACCEPTED** — every requirement covered; bundle quality meets the bar; phase passes.
- **ACCEPTED WITH CONDITIONS** — every requirement covered; some artifacts have non-blocking quality issues tracked as `follow_ups` (e.g., dashboard layout polish, runbook prose tightening). Phase passes; conditions tracked for a follow-up dossier item.
- **GAPS_RETURNED** — one or more requirements not covered, or coverage is insufficient. `30-review.md` enumerates every gap with the source-document reference, the artifact that's missing or insufficient, and what would close it. Phase does NOT pass. Manifest flips to `gaps_returned`. Dev addresses all gaps in one revision round (writes `25-bundle-revision.md` indexing the new/updated artifacts; flips manifest back to `bundle_posted`). Reviewer does a second pass writing `30-review-v2.md`.

**No per-artifact ping-pong.** If the reviewer would have found 5 gaps in one pass, they list all 5 in `30-review.md`. The dev addresses all 5 before re-submitting. This collapses what would otherwise be 5 round-trips into 2 (initial submit → gaps → revised submit → accept).

### Step 4 — Phase closes

On `accepted`: reviewer updates `STATUS.md` to reflect the phase pass; the project advances to the next phase. For Phase 10 acceptance → trigger Phase 11 pre-staging. For Phase 11 acceptance → trigger the Phase 12 procedure (which is **out of scope of this directive**; see §9).

## 5 — `20-bundle.md` required structure

The bundle is a structured index, not free-form prose. Required sections:

```markdown
# 20-bundle — Phase <id>

## Coverage map

| Source doc + section | Required artifact | Delivered artifact (link) | Status |
|---|---|---|---|
| docs/operations/observability.md §3 | SLO dashboard JSON for `purchase.create.latency` | infra/dashboards/slo-purchase-create.json | ✅ |
| docs/operations/observability.md §3 | SLO dashboard for `treasury.client.outcome` | infra/dashboards/slo-treasury.json | ✅ |
| docs/operations/capacity-scalability-plan.md §2 | DB pool sizing analysis at p99 load | docs/operations/capacity/2026-MM-DD-db-pool.md | ✅ |
| docs/operations/failure-modes-and-resilience.md §3 | CB calibration drill evidence | docs/operations/drills/2026-MM-DD-treasury-5xx.md | ✅ |
| docs/operations/incident-response.md §1 | On-call rotation + escalation tree | docs/operations/oncall.md | ✅ |
| ... | ... | ... | ✅ / ⚠️ / BLOCKER |
```

Every requirement in every source document referenced by `00-prompt.md` must have a row. Status options:

- `✅` — artifact present, dev believes it meets the bar.
- `⚠️` — artifact present but dev flags a known quality issue (e.g., "screenshot is from staging, not prod-equivalent").
- `BLOCKER: <reason>` — dev could not produce the artifact; reviewer must decide what to do.

After the coverage map:

- **Decisions** — any non-obvious calls dev made while compiling evidence (e.g., "drilled CB calibration against a WireMock-injected 5xx; production Treasury endpoint not used because no prod deploy exists yet").
- **Risks** — known weaknesses in the evidence that reviewer should evaluate (e.g., "capacity plan validation is theoretical, not load-tested; no load-test infrastructure available pre-Phase-12").
- **Open questions** — items dev needs reviewer input on before phase can close.

## 6 — Reviewer's evidence-bar source documents

**Phase 10 (Operational Readiness Gate).** The `00-prompt.md` for Phase 10 will reference at minimum:

- `docs/operations/observability.md` — SLOs, SLIs, dashboards, traces, structured-log fields.
- `docs/operations/capacity-scalability-plan.md` — DB pool sizing, replica count, request budgets.
- `docs/operations/failure-modes-and-resilience.md` — CB calibration, retry envelopes, single-flight gate, graceful shutdown drills.
- `docs/operations/incident-response.md` — on-call rotation, escalation paths, paging integration, post-incident review templates.
- `docs/operations/rollback-plan.md` — per-class (A/B/C/D) rollback procedures with verified evidence per class.

**Phase 11 (PCI Security Readiness Gate).** The `00-prompt.md` for Phase 11 will reference at minimum:

- `docs/security/pci-scope-and-cde.md` — CDE vs connected-to categorization; cardholder-data flow diagram.
- `docs/security/change-control-pci.md` — change-control procedure evidence (every Phase-13 PR's change-id audit trail).
- `docs/security/logging-monitoring-pci.md` — audit-log destination, immutability, retention, access control.
- `docs/security/encryption-key-management.md` — encryption-in-transit + at-rest evidence; key rotation procedure.
- `docs/security/access-control-pci.md` — least-privilege evidence; access audit trail.
- M7 deliverables (from 13-C2): SAST report (Semgrep), SCA report (OWASP Dependency-Check), container scan (Trivy), SBOM (CycloneDX), secrets-scan (GitLeaks).
- PCI DSS v4.0.1 control mapping document (to be authored under `docs/security/pci-dss-control-mapping.md`; bundle must verify it exists and is complete).

If any of the above documents do not yet exist, they are **dev's responsibility to author** as part of the phase's bundle. The reviewer's `00-prompt.md` will name them; dev produces them; reviewer's `30-review.md` verifies them.

## 7 — Forward-motion bias still applies

The directive `directives/2026-05-17-forward-motion-bias.md` continues to govern. Specifically:

- If the dev judges a borderline call in compiling evidence (e.g., "is a runbook stub enough, or does it need full prose?") they make the call, document it under "Decisions" in `20-bundle.md`, and proceed. Reviewer ratifies or amends in `30-review.md`.
- Bias toward shipping the bundle with explicit risks documented, over delaying the bundle while the dev tries to resolve every borderline judgment.

## 8 — Edge cases

**What if the reviewer finds a Phase-13 defect during the Phase-10 pass?** Open a separate dossier item under `chunks/13-POST-<topic>` to fix the defect; don't entangle it with the phase pass. The phase can be `gaps_returned` or `accepted with conditions` depending on whether the defect blocks the phase's evidence (rare).

**What if the dev cannot meet a gate requirement at all?** The blocker goes in `20-bundle.md`. Reviewer's `30-review.md` either (a) waives the requirement with documented reason (only if the requirement is truly inapplicable), (b) defers it to a follow-up dossier item with a tracked exception, or (c) returns `gaps_returned` if it's a true blocker requiring resolution before phase pass. Waivers and exceptions must be re-evaluated at Phase 12 and may block production approval.

**Concurrent reviewer ticks** (per the scheduled-task polling architecture): both ticks read `manifest.status`. If status is `bundle_posted`, the tick that opens `30-review.md` first wins; the other tick sees the file exists on disk and exits. Same convention as Phase-13 chunks.

**Re-submission after `gaps_returned`:** dev writes `25-bundle-revision.md` indexing the new/updated artifacts (not a full re-write of `20-bundle.md`; immutability rule per README §"Immutability rule"). Coverage-map rows that changed get re-asserted; unchanged rows are referenced by pointer. Reviewer writes `30-review-v2.md`.

## 9 — Phase 12 is out of scope

Phase 12 (production approval) is **not** a bulk-pass phase. It produces `.human-approvals/pci-production-approved.txt` (and any sibling marker files) and requires multi-party human approval per `docs/security/change-control-pci.md §1`:

- Architect signature
- SRE signature
- Security signature
- Compliance signature

The reviewer's role at Phase 12 is to verify all upstream phase markers exist and are recent, then route the approval request to the human signatories. No bulk-pass; no per-tick automation. The marker file is produced only when all four signatures are present.

Production deploy remains blocked until the marker exists. No exceptions.

## 10 — When this directive activates

This directive is **drafted now** (during Phase 13, before 13-C2 merges) but **activates at 13-C2 merge**. Reviewer will draft `phases/10-operational-readiness/{00-prompt.md, manifest.yml}` at 13-C2 merge per Step 1 above.

Until then, this directive is a forward-looking commitment. No state changes triggered by its authoring.

## 11 — Revising this directive

The bulk-pass protocol is itself a convention experiment. If it doesn't work in practice (e.g., if `gaps_returned` round-trips multiply because the gate's evidence inventory turns out to be too ambiguous to bulk-pass cleanly), the reviewer is empowered to revise this directive by writing `directives/YYYY-MM-DD-phase-bulk-pass-revision.md`. Per README §"Cross-cutting directives", directives are the source of truth for cross-cutting rulings; revisions are new files, not edits.
