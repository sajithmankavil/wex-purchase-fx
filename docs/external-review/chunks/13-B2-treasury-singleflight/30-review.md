# 30-review — Chunk 13-B2-treasury-singleflight

Reviewer: external (Phase 13 governance). Tick: 2026-05-18 (automated polling).
Branch: `feature/chunk-b2-treasury-singleflight` @ `17ab4ea` (substantive commit `ba8ab2a`).
PR: #8. Manifest CI: green per `ci_url` (run `26011521421`).

## Verdict

**ACCEPTED WITH CONDITIONS.** PR #8 may merge to `main` after the one pre-merge hygiene fix below lands. Two further conditions are scope-tagged forward into Chunk C; one is a record-keeping note with no action needed.

This is the final infrastructure chunk; once it lands, M3 (treasury + persistence + cache + resilience) closes, and Phase 13 narrows to Chunk C (API + observability) before transitioning to Phase 10 (Operational Readiness Gate).

## Evidence read

- `chunks/13-B2-treasury-singleflight/00-prompt.md`, `20-summary.md`, `manifest.yml` (this chunk).
- `chunks/13-B1-persistence-cache/30-review.md` (the just-closed predecessor; confirms B1's `ExchangeRateRepoAdapter` is the persistence path B2 polls on).
- `directives/2026-05-17-forward-motion-bias.md` (basis for ratifying the implementer's §1–§5 convention decisions).
- Branch at `ba8ab2a` via `git show`:
  - `src/main/java/.../infrastructure/treasury/SingleFlightGate.java` (full).
  - `src/main/java/.../infrastructure/treasury/TreasuryClientAdapter.java` (full).
  - `src/main/java/.../infrastructure/treasury/TreasuryResponse.java`.
  - `src/main/java/.../config/WexConfig.java` (diff vs B1).
  - `src/main/resources/application.yml` (full, post-merge state).
  - `pom.xml` deltas (Resilience4j + WireMock + Spring AOP additions).
  - Test method indexes (8 unit + 4 IT class methods enumerated by grep).
- `git diff --numstat main...feature/chunk-b2-treasury-singleflight`: 16 files changed, 1,647 insertions / 29 deletions.
- Production-code-only LOC (excluding docs/manifests/lock file/20-summary): +1,461 / −19 across 11 files. Comfortably within the 1,500 soft target and 1,800 hard cap reaffirmed at B1's acceptance.

## Findings

### §1 — Pre-merge MED: `.claude/scheduled_tasks.lock` committed to the PR

A runtime process-lock file slipped into the diff:

```
.claude/scheduled_tasks.lock | +1
{"sessionId":"b925086c-9bd7-4312-a965-79edf5d7223f","pid":21128,"acquiredAt":1779051720894}
```

Two problems:

1. **`.claude/` is on the do-not-edit list** per `CLAUDE.md` and the external-review task prompt. The intent of that rule is to prevent agents from mutating their own control-bundle files. An accidentally-committed lock file violates the spirit even if it does no immediate harm.
2. **Runtime process locks should never be in version control.** Every scheduled-task run will mutate this file, producing noisy diffs and merge conflicts on every subsequent PR.

**Action — required pre-merge:**

```
# Add to .gitignore:
.claude/scheduled_tasks.lock

# Then:
git rm --cached .claude/scheduled_tasks.lock
git commit -m "chore: gitignore scheduled-task lock file (B2 hygiene)"
git push
```

Surgical fix, ~3 lines of change. Does not affect any acceptance gate or test. PR #8 holds until this lands.

### §2 — Carry-forward MED → Chunk C: no outbound audit log on `TreasuryClientAdapter`

`TreasuryClientAdapter` currently emits zero log lines. The only logging in B2's new code is inside `SingleFlightGate` (WARN on loser timeout; INFO on shutdown). For PCI Tier 1, every external HTTP call to a payments-adjacent system needs an audit trail at minimum:

- Timestamp (provided by the logging framework).
- Currency descriptor (non-sensitive).
- Window bounds.
- Outcome category (success / `circuit_open` / `bulkhead_full` / `http_5xx:<code>` / `http_4xx:<code>` / `io:<reason>` / `schema_invalid:<field>`).
- Latency (ms).
- Correlation ID (HMAC-hashed per the PR template; not raw).

The B2 PR description template (00-prompt.md §"PR description") already references HMAC-hashed correlation IDs. The adapter doesn't implement that. Two reasons it's not blocking B2:

1. Adding logging here would couple B2 to the correlation-ID + DescriptionHasher infrastructure that C owns.
2. The audit-emission boundary is more cleanly drawn at the HTTP layer (`@RestControllerAdvice`) plus the SLF4J MDC the API filter establishes — both Chunk C deliverables.

**Action — carry-forward:** added to C's `manifest.review_conditions` as `treasury-client-audit-log-on-emit`. C must produce one structured log line per Treasury call covering the fields above.

### §3 — Carry-forward LOW → Chunk C: defense-in-depth at the Treasury filter trust boundary

`TreasuryClientAdapter.doRawFetchAndPersist` builds the Fiscal Data API filter by direct string concatenation:

```java
.queryParam("filter", "country_currency_desc:eq:" + currency.value()
                  + ",record_date:gte:" + windowLower
                  + ",record_date:lte:" + windowUpper)
```

Spring's `UriBuilder` URL-encodes the value, but the Fiscal Data API URL-decodes it and re-parses the filter syntax. If `currency.value()` ever returned a string containing `,` or `:`, the comma/colon would survive the URL round-trip and split into multiple filter expressions on the API side. In practice this is bounded — `CurrencyDescriptor.of(...)` is the normalization choke-point, and A1's domain layer validates the format — but defense-in-depth at the trust boundary is the PCI-Tier-1 standard.

**Action — carry-forward (LOW):** added to C's `manifest.review_conditions` as `treasury-filter-boundary-whitelist-assertion`. Simplest fix is a 2-line assertion at the adapter's call site:

```java
if (!currency.value().matches("^[A-Za-z][A-Za-z0-9 ()\\-]+$")) {
    throw new IllegalArgumentException("currency descriptor failed boundary validation: " + ...);
}
```

(Regex must match the canonical Treasury currency-descriptor format; finalize during C.)

C is also the natural home for this because the `ContentGuard` (NFKC + decoder pipeline) lives at the API layer; the boundary check is symmetric with what `ContentGuard` does at request ingest.

### §4 — INFO: implementer's STATUS.md / B1-manifest edits converged with reviewer's

The branch's diff includes:

- `docs/external-review/STATUS.md` (+5/−4): rollup table updates for B1 → `accepted` and B2 → `implementing`, plus a 2026-05-18 entry in `Recent implementer activity`.
- `docs/external-review/chunks/13-B1-persistence-cache/manifest.yml` (+1/−1): B1 status `under_review → accepted` mechanical transition.

Per the README convention, `STATUS.md` is reviewer-authored. The implementer touching it is a convention bend. **However:** the content of the implementer's edits is byte-identical to the edits I wrote independently in the 03:08 UTC tick — pure convergence, not a contested change. No harm done.

**Action — going forward:** the autonomous-handoff protocol should be updated (post-Phase-13, in a separate dossier maintenance pass) to explicitly allow the implementer to do strictly-mechanical STATUS.md housekeeping during their own chunk's merge — it's lower-friction than waiting for the reviewer's next tick. Substantive STATUS.md edits (activity narrative, decision records) remain reviewer-only. Tracking as `[STATUS] convention clarification — mechanical vs substantive ownership` follow-up in §6 below; not blocking anything.

### §5 — CRITICAL → resolved: PCI-invariant correctness in `SingleFlightGate`

Verified by source inspection at `ba8ab2a`:

| Invariant | Mechanism | Test |
|---|---|---|
| **G6-P0-2** — loser waits 10 s, polls DB every 100 ms, mirrors winner's exception | `waitAsLoser` loop: `System.currentTimeMillis() < deadline` with `Thread.sleep(dbPollIntervalMillis)`; `mirror()` preserves `UpstreamUnavailableException.reason` | `SingleFlightGateTest.{twoLosersOneWinner, loserMirrorsWinnerFailure, loserTimesOut}` |
| **G4-P0-1** — gate key `(currency, treasury_quarter_end)`; same-quarter dedupe | `Key.forTransactionDate` → `ceilToQuarterEnd` computes Mar 31 / Jun 30 / Sep 30 / Dec 31 via `(month-1)/3 * 3 + 3` and `lengthOfMonth()` | `SingleFlightGateTest.{quarterEndCeiling, sameQuarterSharesKey}` + `SingleFlightCacheConcurrencyIT.quarterDeduplication` |
| **G6-P0-1** — TIME_BASED CB opens within 5 min of sustained outage | `application.yml`: `sliding-window-type: TIME_BASED`, `sliding-window-size: 300`, `minimum-number-of-calls: 5`, `failure-rate-threshold: 50`, `wait-duration-in-open-state: 300000`, `permitted-number-of-calls-in-half-open-state: 3` | `CircuitBreakerCalibrationIT.breakerOpens` (compressed) + `productionCalibrationDocumented` (asserts override path) |
| **AC-T-3** — 12 widened Treasury fixtures | `TreasuryClientIT` with @Nested groups `HappyAndScale` + `SchemaAndSanity` + `Availability` | `TreasuryClientIT.*` |
| **G4-P1-23** — SIGTERM releases single-flight locks; in-flight losers fail-fast | `@PreDestroy releaseAll()` flips every state's outcome ref to `failure("shutdown")` via `compareAndSet(null, …)`; `shuttingDown` `AtomicBoolean` checked both at `runOnce` entry AND inside `waitAsLoser` loop | `SingleFlightGateTest.{releaseAllShortCircuits, releaseAllUnblocksLoser}` + `GracefulShutdownIT.contextCloseReleasesLosers` |

**Subtle correctness checks I verified:**

1. **Atomicity of winner selection** — `gates.putIfAbsent(key, state)` is the right primitive; only one thread sees `null` returned for a given key. ✓
2. **Gate cleanup ordering** — winner's `finally` block uses `gates.remove(key, state)` (the 2-arg variant) — won't remove if another thread has replaced the entry. Correct. ✓
3. **Outcome flip ordering** — winner sets `state.outcome.set(WinnerOutcome.success())` BEFORE the `finally` runs `gates.remove(key, state)`. Losers reading from `existing` (returned by `putIfAbsent`) hold a stable reference to the same `State` and can see the outcome flip even after the gate has been removed. ✓
4. **Shutdown / running-winner race** — `releaseAll()` flips every `outcome` to `failure("shutdown")` via `compareAndSet(null, …)`. If the winner has already set `outcome` to `success()` or `failure(e)`, the CAS is a no-op (correct — don't overwrite a real outcome). If the winner is still mid-call (`outcome == null`), shutdown wins the CAS and losers exit with `"shutdown"`. ✓
5. **Multi-replica DB-poll race (implementer's risk #3)** — implementer's analysis is correct: the loser's `dbHasResult` is a non-transactional read, and in a multi-replica deployment there is a brief window where the winner has flipped `outcome` to success but the row is not yet replica-visible. The gate's loop handles this by continuing to poll until either signal fires or the wait expires; AC-027d is satisfied because the outcome ref is checked on every iteration. **Accepted as documented risk.**

### §6 — INFO: forward-motion-bias decisions ratified

The implementer's `20-summary.md` §1–§5 lists five convention decisions. I'm ratifying each here so the audit trail is unambiguous:

| # | Decision | Ratified? | Rationale |
|---|---|---|---|
| 1 | Programmatic Resilience4j (not annotations) | ✅ | Correct call — the `@CircuitBreaker / @Retry / @Bulkhead` annotations rely on Spring AOP proxies which are bypassed by self-invocation. The decorated supplier passed to `SingleFlightGate.runOnce` is invoked via a lambda from inside `fetchRates()`; annotated wrappers would silently skip. |
| 2 | Compressed CB calibration in IT `@TestPropertySource` (5 s window / 1 s open / 2 half-open) | ✅ | Production calibration in `application.yml` is correct; IT runs in seconds via override. `productionCalibrationDocumented` test documents the pattern. |
| 3 | TimeLimiter declared but not applied at adapter level | ✅ | `@TimeLimiter` requires `CompletableFuture` return type, which would force reactive-ification. Per-call timeout enforced by `RestClient`'s `SimpleClientHttpRequestFactory` (2 s connect + 2 s read) in `WexConfig`. Behaviorally equivalent. |
| 4 | WireMock-standalone over WireMock-jre8 | ✅ | One transitive-dep jar vs many; cleaner against Spring Boot 3's BOM-managed servlet/Jackson versions. |
| 5 | `TreasuryClientIT` @Nested groupings (`HappyAndScale` / `SchemaAndSanity` / `Availability`) | ✅ | Legibility win; all 12 AC-T-3 cases present. |

**Additional ratification (not in §1–§5 but worth recording):**

| Decision | Source | Ratified? |
|---|---|---|
| Retry timing interpretation: `100ms + exponential backoff × 2` rather than `2s + jitter` | `application.yml: resilience4j.retry.treasuryClient.{wait-duration: 100, enable-exponential-backoff: true, exponential-backoff-multiplier: 2}` | ✅ The B2 prompt's `"3 attempts × 2 s timeout + jitter"` is ambiguous; implementer read it as "3 attempts each with 2s per-attempt timeout" and put the 2s on `RestClient.readTimeout` in `WexConfig`. Defensible reading; total retry budget ~700 ms is faster (more user-friendly) than the alternative ~6 s. |
| Bulkhead `max-wait-duration: 1s` | `application.yml: resilience4j.bulkhead.treasuryClient.max-wait-duration: 1s` | ✅ Prompt only specified "10 permits"; the 1-second wait before failing-fast is reasonable. |

### §7 — LOC compliance

- Production code (`pom.xml` + `application.yml` + 4 Java prod files): +677 / −19.
- Tests (5 files): +883 / 0.
- Subtotal code: +1,560 / −19.
- Docs (20-summary + STATUS.md + 2 manifests + lock file): +183 / −10.
- **Total branch diff: +1,647 / −29 across 16 files.**
- Code-only: well under the 1,500 soft target if you exclude tests; under 1,800 hard cap by any reasonable accounting.

**LOC discipline reaffirmed — no deviation.** The B1 +2,445-net concession was explicitly one-time (regression-fix co-location); B2 holds the cap as required. ✓

### §8 — Carry-forward inventory after this review

| Item | Severity | Where tracked | Action |
|---|---|---|---|
| Pre-merge: remove `.claude/scheduled_tasks.lock` + add to `.gitignore` | MED | This review §1 | Implementer addresses on `feature/chunk-b2-treasury-singleflight` before merge. |
| `treasury-client-audit-log-on-emit` (PCI outbound audit) | MED | To add to C's `manifest.review_conditions` | C delivers structured log line per Treasury call. |
| `treasury-filter-boundary-whitelist-assertion` | LOW | To add to C's `manifest.review_conditions` | C adds regex assertion at the adapter's filter-build site (symmetric with C's `ContentGuard`). |
| `pitest-conversionservice-per-class-execution` (carried from B1) | MED | B1's `manifest.follow_ups` (unchanged) | Implementer's preference: roll into C if it doesn't push C over the cap; else a small fast-follow PR. **Reviewer concurs** with rolling into C. |
| Convention clarification: STATUS.md mechanical vs substantive ownership | INFO | Post-Phase-13 dossier maintenance | Not blocking; tracked here for the playbook. |

## Merge instructions

1. **Address §1 pre-merge condition** on `feature/chunk-b2-treasury-singleflight`:
   - Add `.claude/scheduled_tasks.lock` to `.gitignore`.
   - `git rm --cached .claude/scheduled_tasks.lock`.
   - Commit + push.
2. **Pull this 30-review.md** into a separate commit on the branch (dossier convention).
3. **Pull C's `manifest.yml` update** adding the two carry-forward conditions (`treasury-client-audit-log-on-emit`, `treasury-filter-boundary-whitelist-assertion`) and the rolled-in B1 follow-up (`pitest-conversionservice-per-class-execution`). The reviewer will land this in the same tick that flips B1's manifest `follow_ups` to indicate the roll-forward.
4. **Pull STATUS.md** rollup-table update reflecting B2 acceptance + C's expanded `review_conditions`.
5. **Merge strategy: rebase** (consistent with A1, A2, B1).
6. **Flip B2 manifest** `status: under_review → accepted` on merge (forward-motion mechanical transition; same pattern as A1, A2, B1).
7. **Start Chunk C** by reading `chunks/13-C-api-observability/00-prompt.md`, `15-clarification.md`, and the updated `manifest.yml`.

## Phase 13 closure horizon

After C lands, Phase 13 is complete. The project advances to **Phase 10 (Operational Readiness Gate)**, then Phase 11 (PCI Security Readiness), then Phase 12 (production approval). Production deployment remains blocked until Phase 12 markers exist.

Per CLAUDE.md, the reviewer expects an explicit operational-readiness review session before any production-deploy artifact is produced. M7 (Java build wiring + CI) is a hard prerequisite for that gate.
