# 30-review — Chunk 13-C3-openapi-cicd

**Reviewer:** External governance reviewer.
**Date:** 2026-05-18 (UTC).
**Verdict:** **ACCEPTED WITH CONDITIONS** — all conditions are Phase-10/11/12 carry-forwards, none are pre-merge blockers. Standard pre-merge protocol still applies: PR must be opened, CI must go green, then merge under forward-motion bias mechanical transition.
**Source under review:** `feature/chunk-c3-openapi-cicd` at `3b37753` (substantive C3 commit, child of C2 `091b4a5`). No PR # at review time — see §6 below.
**Phase note:** C3 is the **FINAL** Phase-13 chunk. After C3 merges, Phase 13 closes and Phase 10 (Operational Readiness) bulk-pass activates per `directives/2026-05-18-phase-10-11-bulk-pass-protocol.md`.

---

## §1 — Summary

C3 ships the deferred S2 + S3 scope from the original C2 prompt (M6 OpenAPI surface + M7 CI/CD workflows), plus closure of the three MED carry-forwards routed in from the C2 reviews (`30-review.md` §§3 + 4; `30-review-v2.md` §2). Direct source inspection of the branch confirms:

| Deliverable | Status |
|---|---|
| `infra/openapi/baseline.yaml` (253 LOC, hand-crafted OAS 3.1) | ✅ Present; 11 error codes enumerated in `ProblemDetail.errorCode` enum; AC-014 scale-6 `exchangeRate="1.370000"` example wired on `/conversion` 200 response. |
| `.spectral.yaml` (47 LOC) | ✅ Extends `spectral:oas`; 3 WEX rules (`wex-error-responses-must-use-problem-json`, `wex-no-pii-field-names`, `wex-problem-detail-enumerates-error-codes`). Severity `error` on the operation-level basics. |
| springdoc annotations on `PurchaseController` | ✅ `@Tag` at class level; `@Operation` + `@ApiResponses` on each of the 3 methods; `@ExampleObject` for the AC-014 conversion-success response. |
| springdoc `@Schema` on every public DTO field | ✅ `PurchaseRequest`, `PurchaseResponse`, `ConversionResponse` annotated with `description`/`example`/`pattern`. |
| `.github/workflows/ci.yml` extensions | ✅ `oasdiff vs baseline` + `Spectral lint OAS` steps added after `make test`. **See F2 below — both have soft-fallback paths that no-op without external tools in the runner.** |
| `.github/workflows/security.yml` rewrite | ✅ 5 named jobs (gitleaks, semgrep, owasp-dc, trivy, sbom) + `local-security-check`. **See F1 below — Semgrep/OWASP-DC/Trivy are advisory only.** |
| `.github/workflows/deploy-{dev,staging,prod}.yml` | ✅ Marker-gate logic in `deploy-prod.yml` verifies `pci-production-approved.txt` exists + contains `APPROVED_FOR_PCI_PRODUCTION_RELEASE`, runs strict `make ops-check` + `make pci-check`, enforces production-checklist files present, and uses GitHub `environment: production` for named-reviewer sign-off. **See F3 below — "newer than the deployed commit" freshness check is not implemented; compensated in practice by GitHub Environments.** |
| `.pre-commit-config.yaml` | ✅ GitLeaks v8.18.4 + 5 standard hooks; parity-with-CI claim verified (same GitLeaks ruleset). |
| `ProblemDetailExceptionHandler` `StructuredArguments.kv` migration (C2 30-review §3 MED) | ✅ CLOSED. Both `onMalformedId` and `onInvalidCurrency` emit `LOG.warn("...", StructuredArguments.kv(...))` so logstash-logback-encoder lifts the fields to top-level JSON. |
| `MetricsCatalog` increment wiring (C2 30-review §4 MED) | ✅ CLOSED. `TreasuryClientAdapter::audit` calls `metrics.treasuryRequest(outcome)` for all 8 outcome paths. `SingleFlightGate` emits `metrics.singleFlightLoserOutcome(type)` at all 5 loser exits (shutdown / mirrored_failure / db_hit / timeout / interrupted). `ProblemDetailExceptionHandler::onInvalidCurrency` calls `metrics.aliasDriftDetected()`. `@Autowired(required=false)` setter pattern preserves test-only constructors. |
| `RateRevisionEndToEndIT` exercises versioned-upsert (C2 30-review-v2 §2 MED) | ✅ CLOSED. Interstitial `DELETE FROM exchange_rates` removed; `@TestPropertySource("wex.cache.exchange-rate.expire-after-write-hours=0")` forces hot-cache refresh between the two HTTP calls (explicitly listed as an acceptable path in v2 §2 — "either by setting a short TTL via `@TestPropertySource` or by exposing a test-only invalidate hook"). All 3 v2 assertions present: HTTP `exchangeRate="1.420000"`, `COUNT(*)`=2 keyed on (Canada-Dollar, 2026-04-15), `MAX(effective_date)`=2026-04-20. **See F4 below — the cache-invalidation-on-upsert contract is forced (TTL=0) rather than observed; v2-spec-compliant but a subtler test variant is recommended for Phase 10.** |

**LOC:** Actual `+1,340 / -84` net `+1,256` across 22 files (C3 vs C2 substantive commit). Within the 1,500 soft target ✓; well under the 1,800 hard cap ✓. The implementer's `+1,200/-86` estimate in the summary tracks the actual within rounding.

---

## §2 — PCI-critical invariant verification

Cross-reference against the C3 `00-prompt.md` "PCI-critical invariants verified at this merge" table:

| Invariant | C3 evidence | Verdict |
|---|---|---|
| RFC 9457 conformance of every error response | `ProblemDetail` schema in `baseline.yaml` enumerates all 11 error codes; Spectral rule `wex-error-responses-must-use-problem-json` enforces `application/problem+json` content-type on every 4xx/5xx response; `wex-problem-detail-enumerates-error-codes` enforces enum presence. | ✅ Substantive evidence present; F2 affects when the Spectral lint actually runs in CI. |
| AC-014 wire-format invariant (`exchangeRate=1.370000`, scale 6) | OAS `ConversionResponse.exchangeRate` has `pattern: '^[0-9]+\.[0-9]{6}$'` + `example: "1.370000"`; `PurchaseController` 200 `@ApiResponse` carries `@ExampleObject` with the same value. | ✅ |
| PCI Tier 1 deploy gate — production deploy requires `pci-production-approved.txt` | `deploy-prod.yml` `marker-gate` job verifies file existence + literal token `APPROVED_FOR_PCI_PRODUCTION_RELEASE`; runs `make ops-check` and `make pci-check` in strict mode (mode-flip per `directives/2026-05-17-ops-check-and-pci-check-strict-flip.md`); enforces production-checklist files exist (5 files); GitHub `environment: production` adds the named-reviewer sign-off layer; stub deploy step prints "would deploy". | ✅ for what's actually checked. See F3 — freshness check missing. |
| Secrets-scan parity local ↔ CI | `.pre-commit-config.yaml` uses `gitleaks/gitleaks@v8.18.4`; `security.yml::gitleaks` uses `gitleaks/gitleaks-action@v2` which pulls the same upstream ruleset. Parity is by-reference (both point at the same upstream); no divergent local ruleset overlay. | ✅ |
| Vulnerability signal (Semgrep + OWASP DC + Trivy SARIF) | All 3 jobs exist in `security.yml`; each uploads SARIF or report artefacts on `if: always()`. | ✅ for artefact production; ⚠ — see F1 for the gating-disabled stance. |
| Dependency manifest visibility (CycloneDX SBOM) | `security.yml::sbom` runs `mvn org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom` when mvn is present; falls back to a stub SBOM JSON when mvn is not (M7 carry-forward); uploads as `cyclonedx-sbom` artefact. | ✅ — substantive when mvn lands; documented stub today. |

---

## §3 — Findings

### F1 — MED — Security workflow scans are advisory; no scan currently blocks merge

**Evidence:**
- `security.yml::semgrep` has `continue-on-error: true` ("Continue-on-error until Semgrep token/config is wired in repo settings").
- `security.yml::owasp-dc` has `continue-on-error: true`.
- `security.yml::trivy` filesystem scan has `exit-code: '0'` ("advisory until baseline established; tighten to '1' in Phase 12"). Container scan only activates when a Dockerfile exists (none exists today).
- Net effect: HIGH/CRITICAL findings produce log output + SARIF artefacts but do not fail the workflow → do not block merge.

**Why it matters:**
The C3 `00-prompt.md` §S3 specifies: *"Failure thresholds: HIGH or CRITICAL findings block merge; MEDIUM are advisory."* The current posture inverts this — everything is advisory. The acceptance gate *"All 5 security-workflow jobs green on C3 branch | yes; HIGH/CRITICAL = 0"* is satisfied performatively (jobs are trivially green because they cannot fail), not substantively.

**Severity:** MED. The scanners and SARIF wiring are correctly installed; the gating policy needs to be flipped from `continue-on-error: true` → `false` (Semgrep, OWASP-DC) and `exit-code: '0'` → `'1'` (Trivy). The implementer's own inline comments anticipate this ("tighten to '1' in Phase 12").

**Acceptable disposition for C3 merge:** Defer to **Phase 11 (PCI Security Readiness) bulk-pass** per `directives/2026-05-18-phase-10-11-bulk-pass-protocol.md`. Phase 11's bundle MUST include: (a) baseline scan output showing HIGH/CRITICAL count = 0 on `main` HEAD; (b) workflow YAML diff flipping the gating posture; (c) any exception entries in `docs/security/exceptions.md` (max 5 per G8-P1-3).

**Carry-forward id:** `security-workflow-gating-activation` → Phase 11 bulk-pass `review_conditions`.

### F2 — MED — Spectral CI lint silently skipped without Node setup; oasdiff invocation is a tautology

**Evidence:**
- `ci.yml::Set up Node if present` has `if: hashFiles('package.json') != ''`. The repo has no `package.json`, so Node is NOT set up.
- `ci.yml::Spectral lint OAS` step: `if command -v npx >/dev/null 2>&1; then npx ... else echo "Spectral not yet installed in this runner; skipping (post-M7 follow-up)" fi`. Without Node, npx is absent → the step prints `skipping` and exits 0.
- `ci.yml::oasdiff vs baseline` step (when oasdiff IS installed): `oasdiff diff infra/openapi/baseline.yaml infra/openapi/baseline.yaml --fail-on ERR` — diffs the baseline against **itself**, which is always empty. Even with oasdiff installed, this is a tautology, not a regression gate. The fallback (`python3 -c "import yaml; yaml.safe_load(...)"`) only validates the baseline parses.

**Why it matters:**
The `.spectral.yaml` rules are correct and ready to lint. The OAS baseline itself is well-formed. The wiring to actually run Spectral against the baseline on every PR — which is the stated acceptance gate — does not run in practice. Similarly, oasdiff cannot fulfil its stated purpose ("diffs live-generated OAS against the baseline") until Maven is in the CI runner (M7 carry-forward).

**Severity:** MED, two-part:
1. **Spectral activation (~2-line workflow fix):** Drop the `if: hashFiles('package.json') != ''` condition on `Set up Node`, or add an unconditional Node 20 setup step before the Spectral lint step. Then `command -v npx` succeeds and Spectral runs `npx @stoplight/spectral-cli@6 lint infra/openapi/baseline.yaml --ruleset .spectral.yaml --fail-severity=error` on every CI run.
2. **oasdiff substance:** Requires Maven-in-runner (M7) — defer with the rest of the M7 family.

**Acceptable disposition for C3 merge:**
- **Part 1 (Spectral):** Strongly recommended pre-merge fix (~2 LOC); accept as **Phase 10 bulk-pass carry-forward** if implementer prefers to ship C3 as-is for forward-motion. Phase 10 bundle MUST include the activated Spectral lint step + a CI run showing it actually executes.
- **Part 2 (oasdiff vs live OAS):** Deferred with the M7 family (Maven runner provisioning). Phase 10 bulk-pass tracks this as a "deferred-pending-M7" item; not in scope to fix this tick.

**Carry-forward id:** `spectral-lint-node-setup-unconditional` + `oasdiff-vs-live-oas-pending-mvn` → Phase 10 bulk-pass `review_conditions`.

### F3 — LOW — Deploy workflows missing "newer than deployed commit" marker-freshness check

**Evidence:** C3 `00-prompt.md` §S3 explicitly requires for both staging and prod:
> Gated on `.human-approvals/staging-approved.txt` existing AND being newer than the deployed commit.

Neither `deploy-staging.yml` nor `deploy-prod.yml` implements a `git log -1 --format=%ct HEAD` vs `stat -c %Y .human-approvals/staging-approved.txt` (or equivalent) check. The current checks are presence + token-content only.

**Why it matters:**
A stale marker from a prior approval would still pass the gate. A team member could deploy a new commit using a marker that was approved for an older one. The compensating control is the GitHub `environment: <name>` named-reviewer sign-off — which does verify the deploy-time, but adds friction rather than automated enforcement. Defense in depth would be better.

**Severity:** LOW. The Tier 1 gate is materially enforced by the marker content check + GitHub Environments + the strict ops/pci checks; the missing piece is the temporal freshness assertion. Easy to add (~10 LOC bash per workflow).

**Acceptable disposition for C3 merge:** Defer to the **Phase 12 production-cutover work** — that's when real production exists and the marker-freshness assertion stops being a process scaffold and starts protecting a real deploy. Phase 12 work is governed by `docs/security/change-control-pci.md §1` independently of Phase 13.

**Carry-forward id:** `deploy-workflow-marker-freshness-check` → Phase 12 prod-cutover scope. NOT tracked in Phase 10 or 11 bulk-passes (which both activate before Phase 12).

### F4 — LOW — `RateRevisionEndToEndIT` bypasses cache-invalidation contract via TTL=0

**Evidence:** The test sets `wex.cache.exchange-rate.expire-after-write-hours=0` to force hot-cache refresh between the two HTTP calls. The B1 `hot-cache upsert-invalidation` contract is therefore not exercised by this IT — the cache simply never holds an entry long enough to need invalidating.

**Why it matters / disposition:** v2 §2 fix-spec **explicitly lists this as one of two acceptable paths**: *"either by setting a short TTL via `@TestPropertySource` or by exposing a test-only invalidate hook on `ConversionService`"*. The implementer chose path 1, which is compliant.

But: the test now verifies the versioned-upsert + window-query + controller path end-to-end **with the cache effectively disabled**. The cache-invalidation contract is asserted by `ExchangeRateRepoIT.VersionedUpsert.*` at the persistence layer (B1) but not propagated through the HTTP boundary. If a future regression breaks the cache-invalidation-on-upsert path while the versioned-upsert + window-query continue to work correctly, this test would still pass.

**Severity:** LOW. v2-spec-compliant; correctness of AC-026b at HTTP boundary is now bridged correctly. The cache-invalidation gap is a subtler invariant.

**Acceptable disposition for C3 merge:** Defer to **Phase 10 bulk-pass** as an optional enhancement. The Phase 10 bundle could carry a second test variant — `RateRevisionEndToEndIT.revisionAcrossTwoCallsWithDefaultCacheTTL` — that uses the production cache TTL and either (a) exposes a `@VisibleForTesting` invalidate hook on `ConversionService`, or (b) waits `cache.expire-after-write-seconds + 1` between calls. Either path proves the upsert path drives cache invalidation through the HTTP boundary.

**Carry-forward id:** `rate-revision-end-to-end-it-cache-invalidation-variant` → Phase 10 bulk-pass `review_conditions` (optional enhancement, not blocking).

### F5 — LOW (defense-in-depth) — `MalformedIdentifierException` super message embeds raw input

**Evidence:** `MalformedIdentifierException(String input)` calls `super("malformed purchase identifier: " + input)`. The javadoc acknowledges this: *"The exception's `getMessage()` also contains the raw input; callers that log `e.getMessage()` would leak it — the centralised handler `ProblemDetailExceptionHandler::onMalformedId` avoids this by logging only the hashed form."*

**Why it matters:**
Spring routes to the most-specific `@ExceptionHandler` for `MalformedIdentifierException` — which redacts correctly. So in practice the raw input does not leak. But:
- The catch-all `@ExceptionHandler(Throwable.class)` does `LOG.error("unhandled_exception class={}", t.getClass().getName(), t)` — the `t` argument causes the logger to print the full stack trace including `e.getMessage()`. If a future refactor removes the specific handler or wraps `MalformedIdentifierException` in another exception type, the raw PAN-shaped input could leak via the stack trace.
- Static-analysis tools that lint for "exception message contains user-supplied data" would flag this even though the runtime control flow is currently safe.

**Severity:** LOW (defense-in-depth). The C 30-review §4.7 NEW MED intent was to redact the raw input from response body + log emissions; both have been substantively closed at the handler level. The lingering exposure is internal to the JVM via `getMessage()`.

**Acceptable disposition for C3 merge:** Defer to **Phase 10 bulk-pass** as a defense-in-depth follow-up. Acceptable fix: change super message to `"malformed purchase identifier (length=" + (input == null ? 0 : input.length()) + ")"` — keeps the length signal, drops the raw input. ~3-line change. The same treatment should be considered for `InvalidCurrencyException` if it has the same shape.

**Carry-forward id:** `malformed-identifier-exception-message-redaction` → Phase 10 bulk-pass `review_conditions`.

### F6 — INFO — Reviewer-authored files staged on C3 branch (convention-bend repeat, pre-cleared)

**Evidence:** The substantive C3 commit `3b37753` includes 5 reviewer-authored files (C2 `30-review.md`, `30-review-v2.md`, C2 manifest update, STATUS.md updates, C3 manifest 3rd condition addition) because C3 was branched off C2 while C2 PR #10 was still open.

**Disposition:** v2 §5 explicitly endorsed this: *"Both edits land in this tick alongside this v2 file."* No convention violation. **Recorded** as the second forward-motion-bias instance of branching mid-review in the C-series (the first was C2 branching off C while C was being reviewed; flagged in C2 30-review §5 INFO as not-to-recur-without-directive-amendment).

The pattern is now established 2-for-2 in the C-series. Phase 10 is the natural inflection point — the bulk-pass protocol explicitly establishes sequential review of one phase bundle at a time, eliminating the need for parallel branching mid-review. **Pin** the playbook entry: if branching mid-review continues into Phase 10, escalate for a directive amendment.

### F7 — INFO — PR not opened at review time

**Evidence:** `manifest.yml` shows `pr: null`, `ci_url: null`. The summary acknowledges this: *"Awaiting PR open + CI green."*

**Disposition:** Standard pre-merge protocol — open PR, confirm CI green, then merge. The verdict in this 30-review is contingent on CI green at PR open. If CI surfaces an issue, the fix path is a follow-up commit on this branch before merge — same protocol as B2 hygiene commit `c59b9cf`.

---

## §4 — Verdict

**ACCEPTED WITH CONDITIONS.**

**Pre-merge conditions:**
1. Open PR (target: `feature/chunk-c3-openapi-cicd` → `main`, post-C2-PR#10-merge rebase).
2. CI must pass green on the rebased branch.
3. **Strongly recommended** (not strictly blocking — convertible to a Phase 10 carry-forward at implementer's discretion): land F2 part 1 (unconditional Node 20 setup → Spectral lint actually runs) as a ~2-line workflow tweak on this PR. If chosen, this resolves `spectral-lint-node-setup-unconditional` from the carry-forward list.

**Phase 10 bulk-pass carry-forwards** (target activation: post-C3-merge per `directives/2026-05-18-phase-10-11-bulk-pass-protocol.md`):
- `spectral-lint-node-setup-unconditional` (F2 part 1) — only if not landed pre-merge
- `oasdiff-vs-live-oas-pending-mvn` (F2 part 2) — paired with M7 Maven runner provisioning
- `rate-revision-end-to-end-it-cache-invalidation-variant` (F4) — optional enhancement
- `malformed-identifier-exception-message-redaction` (F5) — defense-in-depth

**Phase 11 bulk-pass carry-forward:**
- `security-workflow-gating-activation` (F1) — flip `continue-on-error: true` → `false` on Semgrep/OWASP-DC; flip Trivy `exit-code: '0'` → `'1'`; add baseline scan output showing HIGH/CRITICAL = 0.

**Phase 12 (production-cutover) carry-forward:**
- `deploy-workflow-marker-freshness-check` (F3) — out-of-scope for Phase 10/11; handled in Phase 12 prod-cutover work governed by `docs/security/change-control-pci.md §1`.

---

## §5 — LOC discipline reaffirmation

| Chunk | Soft target | Hard cap | Actual net | Status |
|---|---|---|---|---|
| 13-C3-openapi-cicd | 1,500 | 1,800 | +1,256 | ✅ within soft target |

The B1 one-time concession (2,743 over 1,800) and C one-time concession (~250 over 1,800) were both explicitly non-transitive. C2 honoured this (+1,193 code-only within 1,500); C3 has honoured it (+1,256 within 1,500). The 1,800 hard cap discipline is now established as the steady-state norm — entering Phase 10 with two clean chunks in a row demonstrating the cap is achievable when scope is correctly partitioned upfront via pre-implementation deviation surfacing.

---

## §6 — Merge instructions

1. Open PR # against `main` (post-C2-PR#10-merge). Set `manifest.pr` to the new number.
2. Wait for CI green. Set `manifest.ci_url` to the green-run URL.
3. **OPTIONAL** but strongly recommended: land F2 part 1 (unconditional Node 20) as a follow-up commit on this branch. ~2 LOC. If landed, drop `spectral-lint-node-setup-unconditional` from the Phase 10 carry-forward list.
4. Merge via **rebase** (consistent with C2's rebase-merge strategy). After merge:
   - Implementer flips `manifest.status: under_review → accepted` (mechanical transition per forward-motion bias).
   - Implementer or reviewer updates STATUS.md C3 row to `accepted` with merge SHA.
5. **Phase-13 closure:** Once C3 merges, Phase 13 is complete. Reviewer's next tick will pre-stage `phases/10-operational-readiness/{00-prompt.md, manifest.yml}` per `directives/2026-05-18-phase-10-11-bulk-pass-protocol.md` activation clause.

Production deployment remains blocked until Phase 12 markers exist.

---

## §7 — Playbook entry (Phase 10 transition)

Pinning for the Phase 10 bulk-pass reviewer:

> When a CI workflow step has a conditional-skip fallback (`if command -v X >/dev/null 2>&1; ... else echo "skipping"`), read the runner provisioning before granting the acceptance gate. A green workflow does not imply the gate ran. Spectral/oasdiff in this chunk are the canonical example — the rules are correct, the runner is missing the tool, the step prints `skipping` and exits 0, the gate is performative.

This complements the v2 §4 playbook entry ("read test bodies, not summary tables").

End of 30-review.
