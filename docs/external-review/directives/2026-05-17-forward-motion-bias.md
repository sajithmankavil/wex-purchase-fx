# Directive — Forward-motion bias

**Date:** 2026-05-17
**Author:** External governance reviewer
**Type:** Cross-cutting operating principle. Applies to **every** chunk and every tick of the watcher.
**Trigger:** Human owner's explicit guidance — *"the bias should be to keep moving unless true blocker is found. or issues unless resolve soon can cascade to major effort later."*

---

## The principle

**Default: PROCEED.** Stop only for true blockers. Catch cascading issues at their earliest signal, before they compound.

The cost of stopping unnecessarily is wall-clock loss + context-switch churn. The cost of NOT stopping when you should is rework that grows quadratically (a wrong API shape in Chunk B forces rewrites in C, M5 wiring, M6 OpenAPI, and tests). The right calibration: **distinguish issues that cascade from issues that don't, and act on cascading ones immediately.**

## What is a true blocker (stop and surface)

| Category | Examples |
|---|---|
| **Policy / hook** | Hook denial that scope can't satisfy; attempt to edit `.human-approvals/`; attempt to mutate `docs/requirements/source-requirements.md`. |
| **PCI control** | Logging PAN / SAD / CVV / track / secret; ContentGuard wired after rate-limit instead of before (G8-P0-1 inverted); NFKC normalization missing where AC-010e requires it; secrets committed to YAML; rollback class declared lower than reality. |
| **Architectural incompatibility** | Stack pick that contradicts ADR-0001 D-2 without a new ADR; dependency direction violation (domain depending on infrastructure); `double`/`float` in domain or application. |
| **Spec ambiguity** | Two design docs disagree and the chunk needs the disagreement resolved before implementation can be correct. |
| **Capacity overage** | LOC exceeds the hard upper bound (~1,800) — soft cap is ~1,500 and overages need to be surfaced. |
| **Real test failure** | A test failure that exposes a real bug. (NOT: flaky timing, environmental setup issue, CRLF artifact.) |
| **Cascading risk** | Any issue that, if merged as-is, would force rework in a downstream chunk. (See §"How to spot cascading risk" below.) |

If you find one of these: write a `30-review.md` or `10-deviation.md` (whichever applies), flip the manifest, surface to chat. Do NOT proceed.

## What is NOT a blocker (note as follow-up, proceed)

| Category | Examples |
|---|---|
| **Style / cosmetic** | Variable naming, javadoc wording, comment density, trailing whitespace, blank-line conventions. |
| **Audit hygiene gaps that don't affect correctness** | A PR-description field could be clearer; commit message could be more specific; a follow-up TODO for `.gitattributes`. |
| **Threshold misses by small margins** | Coverage at 84.7 % vs 85 % target IF underlying test density is sound and the miss is in code paths with low complexity. (Hard miss with thin tests = blocker.) |
| **Process noise** | CRLF/LF working-tree artifacts; pytest discovery gap in CI; missing `requirements.txt` (when the gap is on a separate fix path). |
| **Defensible omissions** | A test class that doesn't exist yet but is named in a later chunk's plan; an alert spec that's pinned for the operational-readiness-gate gate not Chunk B's gate. |
| **Doc-only deviations within scope** | Wording-level changes to an in-flight `00-prompt.md` (write a `15-clarification.md` and keep moving). |

If you find one of these: log it in the appropriate `30-review.md` under "Follow-ups (not blocking)" and proceed to the next state transition. Do NOT halt.

## How to spot cascading risk

Three patterns. If you see any, treat the issue as a blocker even if the chunk in isolation looks fine:

1. **Shape-of-API risk.** An interface, port, DTO, or response schema that downstream chunks will depend on. Wrong shape here = rework in every downstream chunk that depends on it. *Example: an outbound port signature in A2 that omits a parameter B's adapter would need; better to surface and fix in A2 than to land it and patch in B.*
2. **PCI-invariant risk.** A control or boundary that, once landed wrong, is hard to fix without affecting other layers. *Example: ContentGuard order vs rate-limiter (G8-P0-1) — if it lands wrong in C, it touches the servlet pipeline, the test suite, AND the AC mapping; fix at PR time, not later.*
3. **Naming or persistence risk.** Schema column names, event names, log field names, metric names. Renaming these post-merge is high-friction because dashboards, alerts, runbooks, and downstream consumers all break. *Example: `descriptionHash` field name in audit logs — getting it wrong cascades into observability + SOC + redaction config.*

For each of these, the bias inverts: **surface early, even if the issue is small.** Cost-of-fixing curve is shallow at chunk-PR time, exponential post-merge.

## Calibration heuristics

- "Would a human Architect/SecArch reviewer treat this as a blocker, or would they ask me to ship and file a follow-up?" If the latter: follow-up.
- "Does this issue grow over time?" If yes: blocker. If no: follow-up.
- "Can I describe the fix in one sentence and is the fix scope ≤ 5 LOC?" If yes: usually follow-up. If the fix requires re-thinking a contract: blocker.
- "Has this exact pattern already been ratified by a directive or an ADR?" If the issue is rehashing a settled question: follow-up (don't reopen settled design).

## For the watcher (scheduled `wex-external-review-tick`)

When deciding a verdict on a `20-summary.md`:

1. Read the chunk's `00-prompt.md` acceptance gates.
2. Read the relevant branch code via `git show`.
3. For each gate, ask: "is this met (or met with a follow-up that doesn't cascade)?" → record verdict per gate.
4. If every gate is met or has a non-cascading follow-up → `ACCEPTED` (with follow-ups listed, but no pre-merge condition).
5. If any gate has a cascading miss → `ACCEPTED WITH PRE-MERGE CONDITION` or `REJECTED`. The pre-merge condition exists ONLY for issues that would cascade if shipped.
6. Default: lean toward acceptance. The implementer has surfaced their work; the bias is to keep them moving.

## For the implementer (dev agent)

When deciding whether to write a `10-deviation.md`:

1. Is the tension in the brief one you can resolve by picking the most defensible interpretation and documenting it? → don't write a deviation; pick + proceed + note the pick in `20-summary.md`.
2. Is the tension genuinely unresolvable without a reviewer ruling? → write the deviation, flip `status: deviation_surfaced`, wait.
3. Is the tension a LOC overage or a hook block? → write the deviation (these need explicit acceptance).

When deciding whether to surface mid-chunk for a clarification:

1. Default: don't. Pick the path that minimizes assumed scope creep and document the choice in `20-summary.md`.
2. Exception: if the path would touch out-of-chunk files OR change a PCI invariant OR introduce a new dependency → write `25-...md` or `35-...md` clarification and wait.

## Recording follow-ups

Every chunk's `30-review.md` (or `20-summary.md`) should have a "Follow-ups (not blocking)" section. Items there are tracked but do not gate any merge. They are picked up in dedicated small chunks (e.g., `13-PRE-pytest-ci-wiring`, `13-PRE-gitattributes-eol-normalize`) after the in-flight chunk closes.

If a follow-up later turns out to cascade, it's promoted to a deviation in the chunk where the cascade first manifests. That is the natural escalation path; it does not retroactively invalidate the prior acceptance.

## In one line

**Default forward. Stop only for the cascading.**
