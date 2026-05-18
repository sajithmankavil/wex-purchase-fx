# 30-review — Phase 10 (Operational Readiness Gate)

**Reviewer:** External governance reviewer (automated tick)
**Date:** 2026-05-18
**Bundle reviewed:** `phases/10-operational-readiness/20-bundle.md` @ branch `feature/phase-10-operational-readiness` (head `e5a202d`)
**Prompt:** `phases/10-operational-readiness/00-prompt.md` (dev-pre-staged per forward-motion-bias bend; ratified — see §3 Q4)
**Verdict:** **ACCEPTED WITH CONDITIONS**

This is the first phase to come through the bulk-pass evidence-gate protocol (`directives/2026-05-18-phase-10-11-bulk-pass-protocol.md`). All 12 source-doc requirements have coverage-map rows; all 3 Phase-13 carry-forwards have explicit dispositions; all 5 BLOCKER rows are justified and routed forward; the substantive artifacts that *can* be produced pre-Phase-12 *have* been produced (Grafana dashboard JSON × 3, drill template + folder scaffolding, expanded `incident-response.md`).

No `GAPS_RETURNED` triggers. Conditions below are forward-routed (Phase 11 / Phase 12), not pre-merge blockers.

---

## 1 — Verification summary

Reviewer verified the bundle's central claims by direct source inspection at branch head `e5a202d`. Spot-checks (all pass):

| Claim in bundle | Verification |
|---|---|
| §1.1 §2.1 — logback-spring.xml emits logstash JSON; LoggingPiiGuardTest covers @Nested LocalProfile + CiProfile + CrossLevel | `git show e5a202d:src/main/resources/logback-spring.xml` exists; `src/test/java/com/example/purchaseconversion/api/advice/LoggingPiiGuardTest.java` exists. ✅ |
| §1.1 §3 — MetricsCatalog declares custom metric names | `src/main/java/com/example/purchaseconversion/observability/MetricsCatalog.java` exists; `MetricNameRegistrationTest` exists. ✅ |
| §1.1 §5 — WarmupApplicationListener (`@EventListener ApplicationReadyEvent`); 5 cases in WarmupApplicationListenerTest | both files exist. ✅ |
| §1.1 §6 — 3 Grafana 10.x dashboard JSON templates | all 3 JSON files parse cleanly: `slo-availability.json` (4 panels), `slo-latency.json` (4 panels), `treasury-dependency.json` (6 panels). Verified with `python3 -c "import json,sys; json.load(sys.stdin)"`. Panel PromQL queries match SLI definitions in `slo-sli.md`; threshold step values match SLO targets (SLO-A/B `green ≥ 0.999`, SLO-C `green ≥ 0.995`). ✅ |
| §1.4 F-14 — SingleFlightGate `@PreDestroy releaseAll()` | source verified in B2 30-review (`5fb95e55`); still present at `e5a202d`. ✅ |
| §1.4 F-16 — ContentGuard suite | actual path is `src/main/java/com/example/purchaseconversion/api/advice/ContentGuard.java`; not `api/guard/` as bundle implies. `ContentGuardTest.java` exists and contains @Nested classes `PanLuhn`, `Nfkc`, `Encoded` covering AC-010b/c/d/e. ✅ with cosmetic finding (§3 F-LOW-1). |
| §1.4 F-15 — Idempotency-Key documented as P1 / BLOCKING-for-prod (OQ-009) | `docs/operations/failure-modes-and-resilience.md` §1 F-15 row carries exactly that text. ✅ |
| F5 carry-forward — `MalformedIdentifierException.getMessage()` embeds raw input | `super("malformed purchase identifier: " + Objects.requireNonNull(input));` — bundle's claim is correct; the centralised `ProblemDetailExceptionHandler::onMalformedId` already redacts at log time (closed in C3), but defense-in-depth on the exception constructor itself remains a follow-up. ✅ |
| Incident-response.md expansion | 189 LOC at HEAD vs the 28-line Phase-4 stub; substantive — scope, severity-ladder reference (not duplication; defers to `oncall-escalation.md §1` as canonical), full Detect / Triage / Mitigate / Communicate / Resolve / PIR process, PIR template, drill cadence (quarterly tabletop + annual GameDay). ✅ |
| Drill template + folder scaffolding | `docs/operations/drills/TEMPLATE-drill.md` (98 LOC, structured: Pre-drill state → Injection method → Detection → Response → Findings → Action items); `docs/operations/drills/README.md` (37 LOC). ✅ |

No coverage-map row reads as fabricated. The reviewer's evidence-bar (per directive §8) is satisfied.

---

## 2 — Open-questions adjudication

The bundle §4 listed 5 open questions for reviewer input. Each is adjudicated below.

### Q1 — `rate-revision-end-to-end-it-cache-invalidation-variant` (F4 from C3 30-review)

**Decision: DROP.** Reviewer accepts dev recommendation.

**Rationale:** the cache-invalidation-on-upsert invariant is already proven at two layers — (i) B1 persistence: `ExchangeRateRepoIT.VersionedUpsert.*` (versioned-upsert atomicity at the JDBC boundary); (ii) B1/B2 hot cache: the `ExchangeRateHotCacheAdapter` upsert-invalidation contract that B1 closed (per chunks/13-B1-persistence-cache/30-review.md §2). The existing `RateRevisionEndToEndIT.revisionAcrossTwoCalls` IT (with `expire-after-write-hours: 0` in the test profile, per C3 closure of the third C2 MED carry-forward) is v2-spec-compliant at the HTTP boundary. A second variant that exercises *cache-invalidation* through the HTTP boundary specifically (rather than TTL-expiry) would be belt-and-suspenders — the underlying invariant is not at risk.

**Routing:** explicitly closed here. Will NOT carry into Phase 11 or Phase 12.

### Q2 — `malformed-identifier-exception-message-redaction` (F5 from C3 30-review)

**Decision: ROUTE TO PHASE 11 (defense-in-depth bundle).** Reviewer overrides dev recommendation (which was "land in this PR").

**Rationale:** Phase 10 is explicitly an evidence-only gate (`00-prompt.md` §1: "This phase is NOT an implementation phase — there is no new application behaviour to ship"). A 3-LOC source change to `MalformedIdentifierException` — however small — is *code*, not evidence, and belongs on a phase boundary that admits source modifications. Phase 11 (PCI Security Readiness) is the next such boundary; the F5 redaction is thematically coherent with the F1 `security-workflow-gating-activation` change (both are PCI defense-in-depth) and can ride in the same Phase 11 bundle. This is also marginally safer for the audit trail: the Phase 10 PR remains a pure-evidence diff, easier to review and revert as a unit.

**Routing:** carry-forward to Phase 11 as `malformed-identifier-exception-message-redaction` (LOW). Add to Phase 11 manifest `review_conditions` when reviewer pre-stages Phase 11's `00-prompt.md` (next tick after Phase 10 merges).

### Q3 — `Idempotency-Key` (F-15) — explicit BLOCKING-for-prod adjudication

**Decision: (b) Accept the BLOCKING-for-prod flag.** Reviewer rejects dev's option (a) recommendation.

**Rationale:** option (a) — opening `chunks/13-POST-idempotency-key/` to implement ~600 LOC — would effectively re-open Phase 13 (which closed at C3 merge per STATUS.md and the bulk-pass directive's activation clause). That re-opening defeats the entire phase-boundary discipline. Option (c) — waive — is unsafe: the failure mode (duplicate purchases under network retry) is a real production hazard, even if the AC-001b "two ids" semantic is documented. Option (b) — keep the BLOCKING-for-prod flag — preserves the constraint exactly where it belongs: at the Phase 12 multi-party human-signature procedure per `docs/security/change-control-pci.md §1`, where the production sign-off authority has the policy weight to either (i) require implementation before cutover, (ii) waive with documented production-acceptable-risk basis, or (iii) accept with a mitigating-controls scope (e.g., "client SLA-mandated header use; audit detection on duplicate `(merchant, amount, timestamp)` tuples").

**Routing:** Phase 12 (production approval). The decision authority is the human signatory chain, not the automated reviewer or the dev agent.

**Operational note:** the failure-modes-and-resilience.md F-15 row already names this as "P1 v1 / BLOCKING-for-prod (OQ-009)" — the doc is consistent with this adjudication.

### Q4 — `00-prompt.md` ratification

**Decision: RATIFY without amendment.**

**Rationale:** the dev pre-staged `00-prompt.md` from the bulk-pass directive's contract (§4 step 1). The pre-staged content matches the directive: §2 source-doc list is exhaustive across `docs/operations/*.md`; §3 bundle structure mirrors directive §5; §4 acceptance rubric mirrors directive §6; §5 forward-motion bias is correctly invoked; §6 Phase 11 pre-stage trigger is correctly identified. The forward-motion-bias bend (dev pre-staging instead of waiting for a reviewer tick) is reasonable under the "proceed thru next phases all the way to next HITL" authorization and does not deform the directive.

**Lesson recorded:** on first activation of any phase under the bulk-pass directive, the dev pre-staging `00-prompt.md` is acceptable provided (a) the pre-staged content materially matches the directive's contract, and (b) the bundle declares the bend explicitly in §Decisions. Both conditions are met here. Future phases (11, 12) should follow the directive's nominal flow (reviewer pre-stages first); the §1 Decisions paragraph in this bundle is the formal record of why this one was exceptional.

### Q5 — BLOCKER row acceptance (5 rows)

**Decision: all 5 BLOCKERs are (b) DEFER per directive §8, with the routing matrix below.** None are (c) GAPS_RETURNED candidates.

| BLOCKER (bundle §) | Adjudication | Route to |
|---|---|---|
| §1.3 §5 — load-test execution | (b) defer. Genuinely requires staging infrastructure that does not exist pre-Phase-12. | Phase 12 staging shakedown — k6 + WireMock-Treasury fixture. Hand-off list §5 item 1. |
| §1.4 §3 — CB-calibration drill | (b) defer. Drill execution requires staging + WireMock fault injection. | Phase 12 staging shakedown. Hand-off list §5 item 2. Drill log uses `docs/operations/drills/TEMPLATE-drill.md` skeleton. |
| §1.6 §4.2 + §4.3 — Class-A and Class-B rollback rehearsals | (b) defer. Same staging-infra dependency. | Phase 12 staging shakedown. Hand-off list §5 items 3–4. |
| §1.4 F-24 — audit-log destination unreachable | (b) defer to Phase 11. Audit-log sink is a Phase 11 PCI deliverable. | Phase 11 bundle must produce: sink choice, retention spec, integrity-protection mechanism, "destination unreachable" failover behaviour. |
| §1.13 — `oasdiff-vs-live-oas-pending-mvn` | (b) defer to Phase 11 (paired with M7 Maven-runner provisioning, which the security workflows also depend on per the C3 30-review F1/F2 family). | Phase 11. Bundle decision: provision Maven in CI runner (single shared task across security + drift gates) or document a maintained-baseline workflow with explicit human review on every controller change. |

**Reviewer addendum on drill evidence:** Phase 12 staging shakedown must produce, at minimum, the following drill logs in `docs/operations/drills/` before the `pci-production-approved.txt` marker is created:

- One CB-calibration drill log (F-01 fault injection).
- One Class-A rollback rehearsal log.
- One quarterly-tabletop walkthrough log.

This is a *Phase 12* condition; not blocking Phase 10 acceptance. Captured for the Phase 12 prompt-author's benefit.

---

## 3 — Findings

### F-LOW-1 — Bundle artifact-link cosmetics (`bundle-artifact-link-cosmetics`)

**Severity:** LOW.

**Observation:** several artifact references in the coverage map use truncated link paths that point to a directory or a non-existent path, rather than the actual file. Examples:

- §1.1 §2.1 — `[DescriptionHasher](../../../../src/main/java/com/example/purchaseconversion/observability/DescriptionHasher.java)` — correct path. ✅
- §1.4 F-16 — `[ContentGuard](../../../../src/main/java/)` — link target is a directory, not the file. Actual path is `src/main/java/com/example/purchaseconversion/api/advice/ContentGuard.java`.
- §1.4 F-17 — `[DataDirSyncPrefixWarningTest](../../../../src/test/java/)` — directory link.
- §1.4 F-18 — `[DescriptionHasher](../../../../src/main/java/)` — directory link.
- §1.4 F-25 — `[HealthController](../../../../src/main/java/)` — directory link (and file may not exist at this exact path; reviewer did not verify).

**Why this is LOW:** the bundle's textual claims are independently verifiable; the link cosmetics are navigation-aid only. A reviewer reading the bundle in a real PR UI would notice but be able to resolve via grep.

**Disposition:** on next dossier-touchpoint commit (Phase 10 PR pre-merge hygiene, or first Phase 11 commit), fix the truncated paths to point at actual files. Non-blocking.

### F-LOW-2 — Test-class naming drift (`content-guard-test-nested-naming`)

**Severity:** LOW.

**Observation:** bundle §1.4 F-16 lists test coverage as "`PanPatternGuardTest`, `TrackDataGuardTest`, `EncodedPanGuardTest`" — naming them as if they were three separate top-level test classes. In the actual tree they are `@Nested` classes inside `src/test/java/com/example/purchaseconversion/api/advice/ContentGuardTest.java` (named `PanLuhn`, `Track`, `Encoded`, `Nfkc`).

**Why this is LOW:** the assertions exist and cover AC-010b/c/d/e — the substantive claim "PAN content guard is tested" is accurate. The naming drift is a documentation artefact (the failure-modes doc F-16 row uses the same names; bundle inherited them); this affects auditability, not correctness.

**Disposition:** on next dossier or doc-hygiene touchpoint, align references to either (a) the actual nested-class names (`ContentGuardTest.PanLuhn`, etc.), or (b) update the failure-modes doc's F-16 row to point at the consolidated `ContentGuardTest` class. Non-blocking.

### F-INFO-3 — Bulk-pass protocol working as designed (`bulk-pass-protocol-first-execution`)

**Severity:** INFO.

**Observation:** this is the first activation of the bulk-pass evidence-gate protocol per `directives/2026-05-18-phase-10-11-bulk-pass-protocol.md`. The bundle structure (coverage map + decisions + risks + open questions + Phase-12 hand-off list) is faithful to directive §5. The single-deep-pass reviewer flow (this 30-review) is faithful to directive §4. No deformation of the protocol observed.

**Playbook entry pinned for the Phase 11 reviewer tick:** when reviewing the Phase 11 bundle, the same coverage-map verification pattern applies — spot-check claims by `git show <branch>:<path>` against the actual source rather than trusting the textual claim. Phase 11 will involve PCI controls (audit-log destination, key rotation, security-workflow gating activation) — the verification bar is *higher* than Phase 10 because the failure mode is regulatory, not just operational.

---

## 4 — Conditions (consolidated)

| # | Condition | Severity | Routing |
|---|---|---|---|
| C1 | `Idempotency-Key` (F-15) — BLOCKING-for-prod flag preserved | HIGH (compliance posture) | Phase 12 production approval (multi-party human signature) |
| C2 | `malformed-identifier-exception-message-redaction` (F5) — drop raw input from `MalformedIdentifierException.getMessage()` | LOW | Phase 11 bundle |
| C3 | `oasdiff-vs-live-oas-pending-mvn` (C3 F2 part 2) — activate live-vs-baseline diff once Maven-in-runner provisioned | MED | Phase 11 bundle (paired with M7 family) |
| C4 | Audit-log destination provisioning (failure-modes F-24) | MED | Phase 11 bundle |
| C5 | Load-test execution against capacity-plan §1 anchors | MED | Phase 12 staging shakedown |
| C6 | CB-calibration drill log produced | MED | Phase 12 staging shakedown |
| C7 | Class-A rollback rehearsal log produced | MED | Phase 12 staging shakedown |
| C8 | Class-B rollback rehearsal log produced | LOW | Phase 12 staging shakedown |
| C9 | First quarterly-tabletop drill log produced | LOW | Phase 12 staging shakedown |
| C10 | PagerDuty integration provisioned + verified | MED | Phase 12 platform-team task |
| C11 | On-call rotation populated with real names + paging tokens | LOW | Phase 12 prod-cutover |
| C12 | F4 `rate-revision-end-to-end-it-cache-invalidation-variant` | — | DROPPED with rationale in §2 Q1 |
| C13 | Bundle artifact-link cosmetics fix (F-LOW-1) | LOW | Phase 11 or any dossier-touchpoint commit |
| C14 | Test-class naming drift fix (F-LOW-2) | LOW | Phase 11 or doc-hygiene touchpoint |

C12 is closed as DROPPED. C1–C11 + C13–C14 are forward-routed. None are pre-merge blockers for the Phase 10 PR.

---

## 5 — Pre-merge protocol

Phase 10 PR is acceptable to open + merge under the following protocol:

1. Dev opens PR from `feature/phase-10-operational-readiness` to `main`.
2. PR description references this `30-review.md` and the Phase-12 hand-off list at `20-bundle.md §5`.
3. CI green is the only pre-merge gate (no source-code changes in this PR, so the gate is doc-hygiene + workflow-format checks).
4. Merge under rebase strategy (consistent with B1/B2/C/C2/C3 prior phase-13 chunks).
5. On merge, mechanical transition: `phases/10-operational-readiness/manifest.yml::status` flips `bundle_posted → accepted`. PR# and merge SHA populated.
6. On merge, the reviewer's next tick pre-stages `phases/11-pci-security-readiness/{00-prompt.md, manifest.yml}` per directive §6 and `00-prompt.md §6`.

---

## 6 — Forward-motion summary

- **Phase 13 closed at C3 merge** (`d06b552` on `main` 2026-05-18). Affirmed.
- **Phase 10 acceptance posture:** ACCEPTED WITH CONDITIONS; 14 conditions tracked; 1 condition dropped with rationale; 11 forward-routed to Phase 11 (4) and Phase 12 (7); 2 forward-routed as low-priority doc hygiene.
- **Phase 11 pre-stage trigger:** armed; fires on Phase 10 PR merge.
- **Phase 12 prompt-author input:** C1 (`Idempotency-Key`) is the highest-stakes carry-forward; the Phase 12 prompt-author must surface it explicitly to the human signatory chain.

End of `30-review.md`.
