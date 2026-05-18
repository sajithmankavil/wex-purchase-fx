# 30-review — Chunk 13-C2-observability-cicd

Reviewer: external (Phase 13 governance). Tick: 2026-05-18 (automated polling).
Branch: `feature/chunk-c2-observability-cicd` @ `091b4a5` (substantive commit `091b4a5`).
PR: not yet opened at review time per manifest (`pr: null`, `ci_url: null`). Reviewer is operating on branch state, not PR diff.

## Verdict

**ACCEPTED WITH CONDITIONS.** All 8 narrowed-scope `review_conditions` are addressed in this PR. The §4.7 NEW MED on `MalformedIdentifierException` input hashing — the security-critical condition routed forward from C 30-review — is fully closed. LOC discipline holds at +1,448/-22 ≈ +1,426 net, comfortably within the 1,500 soft target and the 1,800 hard cap. The implementer correctly surfaced the C2/C3 split pre-implementation via `10-deviation.md` — protocol fully internalized.

**Two MED carry-forwards routed to C3** (see §3 + §4 below). Both stem from partial-closure transparency the implementer flagged in `20-summary.md`. **Three procedural / INFO observations** also recorded.

After PR #10 opens, runs CI green, and merges, C3 (already pre-staged + already started in parallel per implementer's manifest update — see §5 INFO) is the **final Phase-13 chunk**.

## Evidence read

- `chunks/13-C2-observability-cicd/00-prompt.md`, `10-deviation.md`, `15-clarification.md`, `20-summary.md`, `manifest.yml` (this chunk).
- `chunks/13-C-api-observability/30-review.md` §4 (carry-forward source — 8 items routed here).
- `directives/2026-05-18-phase-10-11-bulk-pass-protocol.md` (relevant — C3 is the last chunk before activation).
- Branch at `091b4a5` via `git show` / `git diff`:
  - `src/main/java/.../api/advice/ProblemDetailExceptionHandler.java` (S4.7 closure diff).
  - `src/main/java/.../application/exception/MalformedIdentifierException.java` (javadoc + audit-trail diff).
  - `src/main/java/.../infrastructure/treasury/TreasuryClientAdapter.java` (S4.5 audit migration diff).
  - `src/main/java/.../observability/{MetricsCatalog.java, WarmupApplicationListener.java}` (S1 full reads).
  - Test class method indexes (9 new + 2 modified; enumerated by grep).
- `git diff --numstat main...feature/chunk-c2-observability-cicd`: 24 files changed, 1,448 insertions / 22 deletions (production: 11 files; tests: 9 files; docs/dossier: 4 files).

## Findings

### §1 — CRITICAL → resolved: S4.7 NEW MED (MalformedIdentifierException) fully closed

Verified directly at `091b4a5`. The closure mirrors A2 §5 / InvalidCurrencyException exactly:

```java
// ProblemDetailExceptionHandler.onMalformedId — post-fix:
String hashed = hasher.hash(e.getInput());
int length = e.getInput() == null ? 0 : e.getInput().length();
LOG.warn("malformed_identifier.detected idHash={} idLength={}", hashed, length);
Map<String, Object> idDetails = new LinkedHashMap<>();
idDetails.put("hash", hashed);
idDetails.put("length", length);
return respond(HttpStatus.BAD_REQUEST, "MALFORMED_IDENTIFIER",
        "Purchase identifier is not a valid UUID v7",
        Map.of("reason", "malformed-uuid", "id", idDetails), null);
```

The raw `e.getInput()` no longer reaches log emission or response body. `MalformedIdentifierException`'s javadoc was updated to drop the "not subject to redaction" claim and explicitly reference C 30-review §4.7 for the audit trail. Three test surfaces cover the closure:

1. `LoggingPiiGuardTest.LocalProfile.malformedIdentifierLogsHashedOnly` (cross-profile redaction).
2. `ProblemDetailExceptionHandlerTest.malformedId` (handler unit — hashed response shape).
3. `PurchaseControllerWebMvcTest.retrieveMalformedHashesInput` (controller-slice E2E).

**Closed.** The most security-critical condition from C 30-review is resolved.

### §2 — Closure verification on remaining 7 conditions

| Condition | Mechanism | Status |
|---|---|---|
| `logging-pii-guard-test-regression` (§4.1 MED) | New `LoggingPiiGuardTest` with @Nested groups (LocalProfile, CiProfile, CrossLevel) covering ContentGuard + InvalidCurrency + MalformedIdentifier under both profile families | ✅ |
| `rate-limit-ordering-integration-test` (§4.2 MED) | New `RateLimitOrderingIT` with `@SpringBootTest(webEnvironment = RANDOM_PORT)`; saturates limiter then submits Luhn-PAN payload; asserts 429 (limiter wins, ContentGuard never invoked) | ✅ |
| `end-to-end-treasury-it` (§4.3 MED) | New `EndToEndTreasuryIT` (166 LOC) with full HTTP → Spring → WireMock-backed Treasury stack; covers happy + 5xx + sanity-ceiling | ✅ |
| `rate-revision-end-to-end-it` (§4.4 LOW) | New `RateRevisionEndToEndIT` (135 LOC) exercising AC-026b at the HTTP boundary: revision between two GETs returns consistent outcome | ✅ |
| `treasury-audit-structured-fields` (§4.5 LOW) | `TreasuryClientAdapter::audit` migrated to `StructuredArguments.kv(...)` for `currency`, `windowLower`, `windowUpper`, `outcome`, `latencyMs`, `errClass` — emitted as top-level JSON fields. **See §3** for partial-closure note on ProblemDetailExceptionHandler. | ⚠️ partial — see §3 |
| `treasury-filter-whitelist-regression-test` (§4.6 LOW) | New `TreasuryFilterWhitelistTest` (121 LOC) with parameterised reject (10 cases) + accept (6 cases) coverage of `DESCRIPTOR_WHITELIST` | ✅ |
| `malformed-identifier-input-hashing` (§4.7 MED) | See §1 above | ✅ |
| `rate-limit-retry-after-header-value` (§4.8 LOW) | `WexRateLimiterFilter` constructor now takes `retryAfter` env-tunable; default `1` (matches Resilience4j 1s refresh window); documented in `docs/architecture/api-contracts.md` §7 | ✅ |

7 of 8 fully closed; 1 (§4.5) partially closed with implementer-surfaced transparency.

### §3 — Carry-forward MED → C3: §4.5 ProblemDetailExceptionHandler StructuredArguments migration

The implementer's `20-summary.md` Risks section honestly flags that §4.5's migration was applied only to `TreasuryClientAdapter::audit`, not to the other structured-log emission sites in `ProblemDetailExceptionHandler`:

- `currency_alias.drift.detected currencyHash={} currencyLength={}` (in `onInvalidCurrency`)
- `malformed_identifier.detected idHash={} idLength={}` (in `onMalformedId`, newly added in this PR)

Both are still positional SLF4J args, not `StructuredArguments.kv(...)`. The **redaction is correct** — `LoggingPiiGuardTest` verifies no raw PII at any log level — but the **field shape** is positional, which means downstream log parsers (Loki, Splunk, ELK) need regex extraction rather than direct field consumption.

**Severity: MED carry-forward** (not pre-merge). Reasoning:

- PCI Tier 1 audit log requirements are met (no raw PII).
- The structured-field shape is an *ingestion ergonomics* concern, not a security or correctness concern.
- The migration is a ~10-LOC edit on two methods; trivial to land alongside C3's OpenAPI work.

**Action:** added to C3's `manifest.review_conditions` as `problem-detail-handler-structured-arguments-migration`. Implementer addresses in C3.

### §4 — Carry-forward MED → C3: MetricsCatalog increments not wired through adapters

The implementer's convention decision §1 explicitly states: *"MetricsCatalog is a thin name registry — increments are NOT wired through every adapter (would cascade through ITs and inflate LOC). The contract is the metric names + tags."*

This is a partial S1 closure. The reasoning has a flaw: **a metric that's never incremented is not queryable in Prometheus.** PromQL against `treasury.client.requests` returns no time series until at least one sample is published. Dashboards built against these names will display "no data" rather than "0 traffic" — and the difference is not distinguishable to an operator looking at a panel.

The current state:

- `MetricsCatalog` has 7 metric-name constants ✓
- `MetricsCatalog` has 4 increment methods (`purchaseValidationError`, `treasuryRequest`, `singleFlightLoserOutcome`, `aliasDriftDetected`) ✓
- **No call sites** invoke any of those methods. Verified by skim of `TreasuryClientAdapter`, `SingleFlightGate`, `ProblemDetailExceptionHandler`, `ConversionService` post-merge.

**Severity: MED carry-forward.** Reasoning:

- Phase 10 (Operational Readiness Gate) bulk-pass will require evidence that metrics actually produce data; the `MetricsCatalog` names-only state will fail that gate.
- Wiring is straightforward (~30-50 LOC across 3-4 call sites): `treasuryRequest(outcome)` in `TreasuryClientAdapter::audit`; `singleFlightLoserOutcome(type)` on each loser exit path in `SingleFlightGate`; `aliasDriftDetected()` in `ProblemDetailExceptionHandler::onInvalidCurrency`.
- The implementer explicitly asked for reviewer guidance on this in convention decision §1 ("Service-side increments will be added in a small follow-up if reviewer prefers"). **Reviewer prefers C3** rather than a separate follow-up — C3 has LOC headroom (~1,190 est. vs 1,500 soft target).

**Action:** added to C3's `manifest.review_conditions` as `metrics-catalog-increment-wiring-through-adapters`.

C2 ships with names-and-methods-only; C3 wires them. C3 is also the natural home for the dashboard + alert artifacts that consume these metrics (since C3 builds the OAS + CI/CD baseline).

### §5 — INFO: implementer started C3 in parallel off the C2 branch

The C3 `manifest.yml` (modified during this reviewer tick, per system notification) now reads `status: implementing`, `depends_on_satisfied: in-review` (a new enum-extension value the implementer introduced), with a note: *"branched off c2 branch for foundation; will rebase on main once C2 merges."*

Strict reading of the dossier convention: a chunk with `depends_on_satisfied: false` should not start. The implementer flipped the value to a novel `in-review` and proceeded.

**Disposition:** ratified as a forward-motion-bias extension, with three caveats:

1. **C3's substantive work is largely orthogonal to C2** — OpenAPI annotations on the controller, springdoc OAS generation, CI workflow YAML, deploy workflow scaffolding. None of these conflict with C2's M5/S4 changes. The rebase will be clean.
2. **C3 inherits C2's working tree** — meaning if C2 is rejected or revised in this 30-review, C3's foundation is invalidated. This is the real risk. Mitigation: C2 IS being accepted here (with MED carry-forwards routed to C3), so C3's foundation stands.
3. **The `in-review` value should be a one-time appearance.** Future chunks should use the existing `prompt_received` + `implementing` states and the dependency block should be honored. If parallel work on dependent chunks becomes a recurring pattern, propose a directive amendment rather than introducing ad-hoc enum extensions.

**Action — INFO; no rework required.** Recorded for the playbook. The novel `in-review` value is allowed to stand on the C3 manifest for now; the implementer should flip it to `prompt_received` (then `implementing`) after the C2 merge + rebase, per normal state-machine progression.

### §6 — INFO: PR # and CI URL not yet set at review time

C2's `manifest.yml` shows `pr: null` and `ci_url: null` at the time of this review. The implementer flipped `status: summary_posted` and wrote `20-summary.md` before opening the PR. This is **a minor protocol bend** — per the chunk convention §"Authoring + ping protocol" step 3, the PR is opened (and `pr` + `ci_url` set) at the same time as `status: summary_posted`.

**Disposition:** I'm reviewing the branch state directly (`git show feature/chunk-c2-observability-cicd:<path>`); the CI run will land separately. The verdict in this `30-review.md` is contingent on **CI green at PR open + merge time**. If CI surfaces a failure I haven't seen (e.g., a JaCoCo gate fails because the new tests don't cover S1 sufficiently), the verdict reverts to GAPS_RETURNED and the implementer addresses before merge.

**Action:** before merging C2, the implementer must (a) open the PR (`gh pr create`), (b) set `manifest.pr` and `manifest.ci_url` accordingly, (c) confirm CI green. If CI surfaces blockers, write `25-update.md` and flip `status: under_review`.

### §7 — INFO: forward-motion-bias decisions ratified

The implementer's `20-summary.md` §1–§5 decisions:

| # | Decision | Ratified? |
|---|---|---|
| 1 | MetricsCatalog as thin name registry (no call-site wiring) | ⚠️ — see §4 (routed to C3, not accepted as final) |
| 2 | Warm-up via `ForkJoinPool.commonPool()` (no dedicated executor) | ✅ Acceptable for fire-and-forget pre-fetch; promote to scheduled executor only if observability needs per-warmup metrics |
| 3 | OpenTelemetry via `micrometer-tracing-bridge-otel` (not full otel-spring-boot-starter) | ✅ Lighter Spring Boot 3 idiom; auto-instrumentation for MVC + JDBC + RestClient comes free |
| 4 | `MalformedIdentifierException` javadoc updated + audit-trail reference | ✅ Correctly references C 30-review §4.7 |
| 5 | `Retry-After: 1` default in `docs/architecture/api-contracts.md` §7 | ✅ Matches Resilience4j 1s refresh; env-tunable per `wex.ratelimit.retry-after-seconds` |

### §8 — LOC compliance

- Production code (pom.xml + 7 src files): +320 / −14 net.
- Tests (9 new + 2 modified): +873 / 0.
- Docs (api-contracts.md): +1 / 0.
- Dossier (manifests, deviation, clarification, summary, C3 prompt, bulk-pass directive): +254 / −8.
- **Total branch diff: +1,448 / −22 across 24 files.**
- **Code-only: ~+1,193 net** (within 1,500 soft target).

**LOC discipline reaffirmed.** The B1 one-time concession + C's one-time concession are not transitive; C2 honored the cap from the start by pre-implementation deviation surfacing. Excellent execution.

### §9 — Carry-forward inventory after this review

| Item | Severity | Where tracked | Action |
|---|---|---|---|
| `problem-detail-handler-structured-arguments-migration` | MED | C3 `manifest.review_conditions` | C3 migrates the 2 ProblemDetailExceptionHandler log emissions to `StructuredArguments.kv` |
| `metrics-catalog-increment-wiring-through-adapters` | MED | C3 `manifest.review_conditions` | C3 wires `treasuryRequest()` / `singleFlightLoserOutcome()` / `aliasDriftDetected()` into the relevant call sites |
| C2 protocol bend (`pr: null` at `summary_posted`) | INFO | This review §6 | Implementer opens PR + sets manifest fields + confirms CI green before merge |
| C3 novel `in-review` enum value | INFO | This review §5 | Reset to standard enum values after C2 merge + rebase |

## Merge instructions

1. **Open PR #10 on GitHub** (`gh pr create`). Set `manifest.pr` and `manifest.ci_url` accordingly.
2. **Confirm CI green** on the PR. If failures surface, write `25-update.md` and flip `status: under_review`. Do NOT merge with CI failures.
3. **Pull this 30-review.md** into a separate commit on the branch.
4. **Pull C3 manifest update** (adding 2 new `review_conditions` per §3 + §4 — to be applied by the reviewer in the same tick as this review).
5. **Pull STATUS.md** updates reflecting C2 acceptance + C3's expanded conditions.
6. **Merge strategy: rebase** (consistent with A1, A2, B1, B2, C).
7. **Flip C2 manifest** `status: under_review → accepted` on merge (mechanical transition; same pattern as B1, C).
8. **Rebase C3 on main** post-C2-merge; flip C3 manifest's novel `in-review` value back to `implementing`.
9. **Start completing C3** — note the 2 new review_conditions added in this tick.

## Phase 13 closure horizon (refined)

After C3 merges, **Phase 13 is complete**. The `phases/10-operational-readiness/` folder will be pre-staged by the reviewer at C3 merge time per the bulk-pass directive (`directives/2026-05-18-phase-10-11-bulk-pass-protocol.md`). The dev compiles the Phase 10 evidence bundle as a single submission; reviewer does one deep pass.

**Production deployment remains blocked until Phase 12 markers exist.** No exceptions.
