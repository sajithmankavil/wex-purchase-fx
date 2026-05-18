# External-Review Status

> Auto-target: `make status` once Java/Make wired (post-M7). Hand-maintained until then.

| Chunk ID | Status | PR | Merged | Next |
|---|---|---|---|---|
| 13-PRE-dossier-bootstrap | accepted | #3 (squash) | `b762bec` | — closed |
| 13-PRE-readiness-check-fix | accepted | #4 (squash) | `fdfe8e9` | — closed |
| 13-A1-domain | accepted | #2 (rebase) | `04f19aa` (plant `3e03380` + revert `04f19aa` preserved) | — closed |
| 13-A2-application | accepted | #5 (rebase) | `48dc656` + dossier follow-ups | — closed |
| 13-B-infrastructure | superseded | — | — | superseded by `13-B1-persistence-cache` + `13-B2-treasury-singleflight` per `chunks/13-B-infrastructure/30-review.md` |
| 13-B1-persistence-cache | accepted | #7 (rebase) | `d96e08b` (+ `44346f3` manifest, `bd57073` 30-review) | — closed; 1 MED follow-up tracked on B1 manifest (`pitest-conversionservice-per-class-execution`) |
| 13-B2-treasury-singleflight | implementing | — | — | implementer started on `feature/chunk-b2-treasury-singleflight` off `main`; depends_on B1 satisfied |
| 13-C-api-observability | prompt_received | — | — | starts after B2 merges; intake condition in `chunks/13-C-api-observability/15-clarification.md` |

Status enum: `prompt_received | deviation_surfaced | implementing | summary_posted | under_review | accepted | rejected | superseded`.

## Phase 13 chunk plan (live)

```
13-PRE-dossier-bootstrap        ← accepted, merged b762bec
13-PRE-readiness-check-fix      ← accepted, merged fdfe8e9
13-A1-domain                    ← accepted, merged 04f19aa (plant+revert preserved)
13-A2-application               ← accepted, merged 48dc656
13-B-infrastructure             ← superseded by B1+B2 (LOC overage 2,800 vs hard 1,800 — split accepted)
13-B1-persistence-cache         ← accepted, merged d96e08b (PR #7 rebase; +2,743/−51 actual; one-time LOC concession ratified)
13-B2-treasury-singleflight     ← implementing — ~1,400 LOC est., rollback class A, 1,800 LOC cap reaffirmed
13-C-api-observability          ← prompt_received (pre-staged) — depends on B2 — final chunk
```

## Autonomous-handoff protocol (added 2026-05-17)

All four upcoming chunks (A1 merge, A2, B, C) have their `00-prompt.md` and `manifest.yml` pre-staged. The implementer reads `STATUS.md` + the next-pending chunk's `00-prompt.md` at the start of every session — no reviewer round-trip is required to start the next chunk after the previous one merges. The reviewer's role is per-PR `30-review.md` writing, which is triggered by `manifest.status: summary_posted` on any chunk.

True file-watch ("watch for each other and go") requires a GitHub Action on `docs/external-review/**` push events. Proposed but deferred (post-M7); see `docs/external-review/README.md` §"Authoring + ping protocol".

## Cross-cutting directives in force

- [`directives/2026-05-17-ops-check-and-pci-check-strict-flip.md`](directives/2026-05-17-ops-check-and-pci-check-strict-flip.md) — defers strict mode in `ops-check` + `pci-check` to the production-approval marker. Implemented by `13-PRE-readiness-check-fix` (accepted 2026-05-17).

## Recent reviewer activity

- 2026-05-18 — Reviewed `13-B1-persistence-cache` (PR #7). Verdict: **ACCEPTED WITH CONDITIONS** in `chunks/13-B1-persistence-cache/30-review.md`. (1) Closed both A2 intake items §1 + §2 carried as `review_conditions`; §2 (hot-cache upsert-invalidation contract) is fully closed with adapter + port javadoc + 2 IT methods. (2) One MED residual on §1 sub-condition (iii) — `<mutationThreshold>` is package-aggregate, not per-class, so `ConversionService` per-class ≥ 80 % is not enforced by the package-scope execution; tracked as `follow_ups: [pitest-conversionservice-per-class-execution]` with option (a) (~15-line pom edit) preferred. Not blocking PR #7 merge. (3) Verified the **A2 regression-fix discovery+fix** in B1: A2 `48dc656`'s `.gitignore` had unscoped `out/` that gitignore-matched `application/port/out/` and dropped 6 ports; B1 rescopes to `/out/` and re-adds the ports. `main` was non-compilable from `48dc656` until B1 merges (masked by M7 Java-build carry-forward). A2 acceptance stands; this 30-review is the canonical audit trail. (4) Accepted LOC overage (~2,445 net new vs 1,800 hard cap) as **one-time concession** driven by regression-fix co-location + prompt-mandated test density; B2 must hold the 1,800 cap. (5) Ratified 5 convention decisions (port-javadoc bend, TreasuryClientPort stub, Liquibase YAML, `*IT.java` naming, `purchase_transactions` table name). Lesson recorded for the reviewer playbook: while M7 mask is in effect, always `git show --name-only` the implementer's merge commit against the claimed file list before granting acceptance.
- 2026-05-17 — Accepted Option (a) on `chunks/13-B-infrastructure/10-deviation.md` (B1/B2 capacity-overage split). `chunks/13-B-infrastructure/30-review.md` records the supersession; B is now `status: superseded` with `superseded_by: [13-B1-persistence-cache, 13-B2-treasury-singleflight]`. Created two new chunk dossiers: `chunks/13-B1-persistence-cache/{00-prompt.md, manifest.yml}` (absorbs both A2 intake items inline; ~1,300 LOC, rollback class C) and `chunks/13-B2-treasury-singleflight/{00-prompt.md, manifest.yml}` (~1,400 LOC, rollback class A, depends on B1). Rewired `chunks/13-C-api-observability/manifest.yml` `depends_on` from `[13-B-infrastructure]` to `[13-B2-treasury-singleflight]`. Dev agent may start B1 immediately.
- 2026-05-17 — Wrote `chunks/13-B-infrastructure/15-clarification.md` (2 intake items: pom coverage/mutation extension MED + hot-cache upsert-invalidation contract LOW) and `chunks/13-C-api-observability/15-clarification.md` (1 intake item: API-layer currency-input hashing LOW). The two B-scoped items have now been absorbed inline into `chunks/13-B1-persistence-cache/00-prompt.md` since the original B chunk is superseded; the C-scoped item remains active. Dev agent reads B1's prompt directly — no reference into the superseded folder required.
- 2026-05-17 — Reviewed `13-A2-application` (PR #5). Verdict: ACCEPTED WITH CONDITIONS. `chunks/13-A2-application/30-review.md`. Three conditions surfaced and scope-tagged: (1) MED — `pom.xml` JaCoCo `check` and Pitest `<targetClasses>` do not cover `application/*`, so the A2 prompt's NFR-021 gates are declared but not build-enforced; mandatory for Chunk B intake. (2) LOW — Hot-cache window-completeness contract risk: `ConversionService` treats cache hits as authoritative, but per-(currency, recordDate) cache cannot guarantee window-completeness — Chunk B's upsert adapter must invalidate per-key on every upsert (or grow a bulk-invalidate). (3) LOW — `InvalidCurrencyException` carries a raw currency string; Chunk C's `@RestControllerAdvice` must hash via `DescriptionHasher` before emission. PR #5 may merge as-is; conditions are intake items, not pre-merge gates.
- 2026-05-17 — Withdrew §5 line-ending finding on `13-PRE-dossier-bootstrap` after implementer's `35-clarification.md` showed PR diff is `+790/-0` (pure additions, no churn); my original `git diff` was working-tree-vs-index on Windows-CRLF + LF-index, not the actual PR diff. See `chunks/13-PRE-dossier-bootstrap/30-review-v2.md`. PR #3 now has zero pre-merge conditions.
- 2026-05-17 — Pre-staged `13-A2-application` + `13-B-infrastructure` + `13-C-api-observability` (00-prompt + manifest each). Implementer can chain through all four remaining chunks without further reviewer round-trips for kickoff.
- 2026-05-17 — Code-correctness accepted `13-A1-domain` (PR #2). `chunks/13-A1-domain/30-review.md`. CI-verification awaits rebase-on-main post-PR-#4 merge.
- 2026-05-17 — Accepted `13-PRE-readiness-check-fix` (PR #4). Verdict: `chunks/13-PRE-readiness-check-fix/30-review.md`. Independent pytest 7/7 pass.
- 2026-05-17 — Accepted `13-PRE-dossier-bootstrap` (PR #3) with one pre-merge condition (line-ending normalization). Verdict: `chunks/13-PRE-dossier-bootstrap/30-review.md`.

## Recent implementer activity

- 2026-05-18 — Shipped `13-B1-persistence-cache` (PR #7, rebase). Substantive commit `d96e08b feat(Chunk B1): persistence + cache infrastructure (M3 partial) + A2 regression fix`; dossier follow-ups `44346f3` (manifest pr=7 + ci_url) and `bd57073` (30-review landed). Discovered + fixed A2 `48dc656` `.gitignore` regression (unscoped `out/` had matched `application/port/out/` and dropped 6 ports — `main` was non-compilable from A2 merge until B1 merge, masked by M7 Java-build carry-forward). B1 actual diff `+2,743 / −51` over 31 files; reviewer accepted as one-time concession driven by co-located regression-fix + prompt-mandated test density. Immediately started B2 on `feature/chunk-b2-treasury-singleflight` off `main`; B2 manifest flipped `prompt_received → implementing`.
- 2026-05-17 — Surfaced LOC-overage deviation on `13-B-infrastructure` before implementation began. Honest estimate ~2,800 LOC across 5 adapters + Liquibase + 12 ITs + 2 A2 intake items vs the 1,800 hard upper bound. Proposed B1/B2 split along the persistence/concurrency seam in `chunks/13-B-infrastructure/10-deviation.md`. Manifest flipped to `deviation_surfaced`. Awaiting reviewer (a) accept-split / (b) accept-unsplit-with-relaxed-cap / (c) alternative-split.
- 2026-05-17 — Merged PR #5 (A2) rebase strategy; commit `48dc656` on main. A2 manifest flipped `summary_posted → accepted` once reviewer 30-review.md landed; pulled reviewer's STATUS.md + manifest updates + 30-review.md + B/C 15-clarification.md files into the PR before merge per dossier convention.
- 2026-05-17 — Ran full merge sequence per `30-review-v2.md`: PR #4 → PR #3 → rebase A1 → PR #2. All four merged. A1's `manifest.status` flipped to `accepted` under forward-motion bias (mechanical transition; substantive review was `30-review.md` §1-§4). Started A2 on `feature/chunk-a2-application`; flipped A2 manifest to `implementing`.
- 2026-05-17 — Pushed back on the §5 line-ending finding in `chunks/13-PRE-dossier-bootstrap/30-review.md`. Actual PR #3 diff on origin is `+790 / -0` across 13 new files (no churn). Evidence in `chunks/13-PRE-dossier-bootstrap/35-clarification.md`. Reviewer withdrew the finding in `30-review-v2.md`.

## Pending follow-ups (non-blocking)

- After Chunk A1 closes: open a separate dossier chunk to wire `scripts/tests/` into CI (`requirements.txt` + `scripts/quality/test.sh` discovery + pip-install step in the workflow). Tracked in `chunks/13-PRE-readiness-check-fix/30-review.md` §4.
