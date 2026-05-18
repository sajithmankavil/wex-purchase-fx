# 15-clarification — Chunk 13-C2-observability-cicd (scope narrowing)

**Author:** External governance reviewer
**Date:** 2026-05-18
**Type:** Reviewer response to `chunks/13-C2-observability-cicd/10-deviation.md`. Narrows C2's scope per implementer's proposed C2/C3 split. The original `00-prompt.md` remains immutable; this file is the binding scope amendment.

---

## Verdict on `10-deviation.md`: ACCEPT option (a) — C2 / C3 split

The implementer's analysis is sound; the proposed seam is the correct one. Accepting option (a) verbatim.

**Recognition.** This is the first deviation surfaced **pre-implementation** since the dossier convention was introduced. That is exactly the protocol C 30-review §1 asked for ("Going forward, if any future chunk's honest LOC estimate exceeds the 1,800 hard cap or skips a sub-slice from the prompt, raise a `10-deviation.md` before implementation, not in the summary"). Forward-motion bias rewarded.

## Reasoning for accepting the split

1. **LOC discipline holds.** 2,200–2,580 LOC over a 1,800 cap is a real overage — well beyond the rounding-tolerance band the B1 one-time concession occupied. Splitting both halves within the 1,500 soft target preserves the discipline.
2. **Security-critical lands first.** S4.7 (NEW MED — `MalformedIdentifierException` input hashing) and the AC-032/032b `LoggingPiiGuardTest` regression both go in C2. Bundling these with CI-workflow YAML risks confusion at review time and delays the security-critical close.
3. **Cognitive seam matches reviewer mode.** C2 = test-method enumeration + behavior verification. C3 = workflow-file inspection + OpenAPI annotation review. Two different verification modes; splitting the chunks matches that.
4. **Rollback class identical (A).** No deployment-coordination overhead from the split.
5. **Precedent.** B → B1+B2 is the same pattern. Same forward-motion pattern, same outcome.

## C2's narrowed scope (binding for this chunk)

C2's `00-prompt.md` is **partially superseded** by this clarification. The binding C2 scope is now:

### In scope for C2

| Item | Source | Est. LOC |
|---|---|---:|
| S1 — Micrometer metric families (7 names) + bean wiring + `MetricNameRegistrationTest` | C2 00-prompt §S1 | 180 |
| S1 — OpenTelemetry SDK config (`opentelemetry-spring-boot-starter` + OTLP exporter) + `OpenTelemetryWiringTest` | C2 00-prompt §S1 | 80 |
| S1 — Warm-up job (`ApplicationListener<ApplicationReadyEvent>` + async chain) + `WarmupApplicationListenerTest` | C2 00-prompt §S1 | 130 |
| S1 — Tail-sample hints across hot paths | C2 00-prompt §S1 | 30 |
| S1 / S4.1 — `LoggingPiiGuardTest` (SLF4J test-appender; local + ci profiles; ContentGuard + InvalidCurrency + **MalformedIdentifier**) | C2 00-prompt §S4.1 + S4.7 absorbed | 250 |
| S4.2 — `RateLimitOrderingIT` (G8-P0-1 servlet-container regression; `@SpringBootTest(RANDOM_PORT)`) | C2 00-prompt §S4.2 | 120 |
| S4.3 — `EndToEndTreasuryIT` (WireMock-backed full-stack; AC-T-3) | C2 00-prompt §S4.3 | 200 |
| S4.4 — `RateRevisionEndToEndIT` (HTTP-boundary IT; AC-026b) | C2 00-prompt §S4.4 | 130 |
| S4.5 — Treasury audit `StructuredArguments.kv` migration | C2 00-prompt §S4.5 | 30 |
| S4.6 — Treasury whitelist regression test (unit) | C2 00-prompt §S4.6 | 60 |
| **S4.7 NEW MED — `MalformedIdentifierException` input hashing (handler edit + javadoc + LoggingPiiGuardTest coverage)** | C 30-review §4.7 (carried forward to C2 via `review_conditions: [malformed-identifier-input-hashing]`) | 50 |
| S4.8 — `Retry-After` documentation update in api-contracts.md | C2 00-prompt §S4.8 | 10 |
| Dossier — `20-summary.md` + `manifest.yml` updates + STATUS.md + C manifest flip + B2 manifest flip | (this clarification + scope narrowing) | 150 |
| **C2 total** | | **~1,420** |

### Deferred to new chunk `13-C3-openapi-cicd`

| Item | Notes |
|---|---|
| S2 — M6 OpenAPI: springdoc `@Tag` + `@Operation` + `@ApiResponse` + `@Schema` on `PurchaseController` + DTOs; OAS examples (AC-014 scale-6, dual-mode currency, all RFC 9457 error shapes); oasdiff baseline + CI gate; Spectral lint + ruleset | Full ownership → C3 |
| S3 — M7 CI/CD: `.github/workflows/security.yml` (Semgrep + OWASP DC + GitLeaks + Trivy + SBOM); `ci.yml` extensions; `deploy-{dev,staging,prod}.yml`; `.pre-commit-config.yaml` | Full ownership → C3 |

C3's `00-prompt.md` is being pre-staged in this same reviewer commit.

## Updated acceptance gates for C2 (narrowed scope)

The C2 00-prompt's acceptance gates apply only to the items in the narrowed scope above. Specifically:

- **LOC:** ≤ 1,500 (soft target) / ≤ 1,800 (hard upper bound). Estimated ~1,420. No further overages tolerated this phase.
- **Mutation gates on touched classes:** unchanged — Pitest ≥ 70% package, ≥ 80% per-class on the LoggingPiiGuardTest hook + `MalformedIdentifierException` handler edits.
- **Coverage:** unchanged — line ≥ 0.75 / branch ≥ 0.65 on new code (api.* + config.* gate from C still applies; new observability.* code under same rule).
- **ArchUnit:** all A1+A2+B1+B2+C rules still pass.
- **Out-of-scope confirmation:** no OpenAPI annotations, no CI workflow edits, no deploy workflow scaffolding. These all live in C3.

## Reviewer's expectations on `20-summary.md`

When C2 lands, its `20-summary.md` must:

1. Confirm the narrowed scope (per this clarification) is what shipped.
2. Confirm S4.7 (NEW MED) is closed — `MalformedIdentifierException.getInput()` is no longer reachable in audit logs or response bodies in plaintext; `LoggingPiiGuardTest` includes a regression case asserting this.
3. Confirm `LoggingPiiGuardTest` covers all three input-hashing surfaces (ContentGuard, InvalidCurrency, MalformedIdentifier) under both `local` and `ci` profiles.
4. Confirm `RateLimitOrderingIT` exercises the full chain end-to-end with a real servlet container (the existing `WexRateLimiterFilterTest` is the isolated unit; this IT closes C 30-review §7.2 carry-forward).
5. Map each PCI-critical invariant to a test method (table format).
6. PR title prefix: `feat(Chunk C2)` per the existing convention.

## Reviewer's expectations on `30-review.md`

I will verify, in addition to the standard PCI invariants table:

- That **all 13 review_conditions** in C2's manifest (originally 8 from C 30-review §3 + 5 newly-derived) are addressed in this PR. Specifically S4.7 MUST close in C2; do not roll it forward to C3 absent a new pre-implementation deviation.
- That the narrowed scope holds and no S2/S3 items leak into C2.
- That the C2 LOC actual is within the 1,500 soft target.
- That the implementer's response to C 30-review §1 (the "INFO procedural irregularity" on post-hoc deviation surfacing) is honored — which it is: this very `10-deviation.md` is the proof. No further reviewer action on §1.

## State transition

- `chunks/13-C2-observability-cicd/manifest.yml`: `status: deviation_surfaced → implementing` (deviation resolved by this clarification; clearance to proceed). `deviations_open: []`. `review_conditions` updated to remove S2/S3-scoped items (they migrate to C3) and add `malformed-identifier-input-hashing` (S4.7 carry-forward).
- `chunks/13-C3-openapi-cicd/manifest.yml`: new chunk created with `status: prompt_received`. `depends_on: [13-C2-observability-cicd]`.
- `chunks/13-B2-treasury-singleflight/manifest.yml`: `status: under_review → accepted` (mechanical transition; B2 was merged at `1b02dcf` + hygiene `c59b9cf`; this housekeeping was overlooked).
- `STATUS.md`: rollup updated to reflect C accepted+merged, B2 accepted+merged, C2 scope narrowed + implementing, C3 prompt_received.

## Forward-motion expectations

Implementer may proceed with C2 implementation on `feature/chunk-c2-observability-cicd` immediately. No further reviewer round-trip required for kickoff. C3's `00-prompt.md` is ready for the implementer to read at any time, but C2 must merge first.

After C2 merges, C3 is the **final Phase 13 chunk** before transition to the bulk-pass protocol for Phases 10 + 11 (`directives/2026-05-18-phase-10-11-bulk-pass-protocol.md`).
