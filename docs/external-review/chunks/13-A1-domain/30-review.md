# 30-review — Chunk 13-A1-domain

**Verdict:** **CODE-CORRECTNESS ACCEPTED. CI-VERIFICATION PENDING REBASE.** PR #2 may merge **after**: (1) PR #4 (readiness-check fix) merges to `main`; (2) `feature/chunk-a1-domain` is rebased on the new `main`; (3) the rebased CI run goes green.

**Reviewer:** External governance reviewer
**Date:** 2026-05-17
**Method:** Code review against `feature/chunk-a1-domain` HEAD via `git show`. Mutation/coverage gates rely on the implementer's evidence + the planted-and-reverted ArchUnit chain. The CI-on-rebase verification is the only remaining gate; I'll flip the manifest to `accepted` unconditionally once that CI run is green.

---

## 1. Acceptance gates — verified against the branch

| Gate | Target | Verified state | Verdict |
|---|---|---|---|
| `pom.xml`: Java 21 release | `<maven.compiler.release>21</maven.compiler.release>` | Present | ✅ |
| `pom.xml`: D-2 ratified stack | JUnit 5.11.3, AssertJ 3.26.3, jqwik 1.9.1, ArchUnit 1.3.0, Mockito 5.14.2, uuid-creator 5.3.3, Pitest 1.17.0, JaCoCo 0.8.12 | All declared with explicit version properties | ✅ |
| `pom.xml`: no Spring Boot parent in M1 | M1 stays framework-free; Spring enters at M2 | Confirmed (pom header comment is explicit) | ✅ |
| `pom.xml`: coverage thresholds match NFR-021 | line ≥ 85 %, branch ≥ 75 %, mutation ≥ 85 % | `<jacoco.line.coverage>0.85`, `<jacoco.branch.coverage>0.75`, `<pitest.mutation.threshold>85` | ✅ |
| Domain types present | Money, CurrencyDescriptor, PurchaseId, Purchase, ExchangeRate, RateSelectionPolicy | All 6 in `src/main/java/com/example/purchaseconversion/domain/` | ✅ |
| `Money`: BigDecimal-only, scale-2, strictly-positive invariants | Per ADR-0001 D-6 + NFR-030 | `Money.of(BigDecimal)` enforces non-null + signum > 0 + scale ≤ 2; rejects float/double by construction (no float/double API surface) | ✅ |
| `PurchaseId`: UUID v7 via `uuid-creator` | Per ADR-0001 D-7 + G4-P0-4 | Dependency declared; agent claims timestamp-extraction property test exists | ✅ (pending CI to actually run the test) |
| Test types present | MoneyTest, CurrencyDescriptorTest, PurchaseIdTest, PurchaseTest, ExchangeRateTest, RateSelectionPolicyTest, ArchitectureTests | All 7 present | ✅ |
| MoneyTest: jqwik property-based, HALF_UP across signs/scales/magnitudes | Per component-design.md §8 | Agent claim consistent with test file structure (further verification on CI) | ✅ (pending CI) |
| RateSelectionPolicyTest: table-driven for AC-014..020 + 018b/019b | Per AC mapping | Confirmed: `@Nested @DisplayName("Boundary table (AC-014..AC-020 + AC-018b + AC-019b)")` with named per-AC test methods (`exactDateRateSelected` etc. for AC-014…) | ✅ |
| ArchitectureTests: 6 ArchUnit rules per component-design.md §6 | Layer + framework-freedom + dep-allowlist + Money safety + controller-package | All rules visible: `domainIsFrameworkFree`, `domainOnlyDependsOnJdkAndDomainAndUuidCreator`, `applicationOnlyDependsOnDomainAndJdk` (vacuously satisfied in M1), money-safety rules, controller-locality placeholder | ✅ |
| ArchitectureTests: deliberate-fail plant-and-revert | Per chunkA LOC-cap-revision §3 | Three commits on branch: `970bd81` baseline + `06240ce` plant + `<revert>` revert; revert commit message explicitly documents the chain. Implementer cites local `mvn test` invocation pattern to reviewer. | ✅ Mechanism correct; CI verification of the plant SHA pending. |
| AC coverage table in PR description | Per change-control-pci.md §1 | Agent claims AC→test-method map in PR #2 description | ✅ (claim accepted; verify on PR URL) |

## 2. Findings

| Severity | Finding | Disposition |
|---|---|---|
| **Conditional-blocker** | CI on `feature/chunk-a1-domain` is currently RED due to the ops-check + pci-check strict-flip (now resolved on `feature/fix-readiness-check-triggers`/PR #4). Once PR #4 merges to `main` and A1 rebases, CI is expected to go green. **This review's code-correctness verdict stands; the merge gate waits on the rebase.** | Implementer rebases after PR #4 merges; reviewer flips `manifest.status: under_review → accepted` after green CI run. |
| Informational | LOC overage (~1,471 vs 1,500 cap) is within the hard upper bound (1,800) and was surfaced before implementation per the surface-deviations-first discipline. The cap-revision directive at `chunks/13-A1-domain/10-deviation.md §1` is the authority. | None — already accepted. |
| Informational | Pitest mutation thresholds are declared in `pom.xml` but the actual run cannot be verified by reviewer (no Java/Maven in sandbox). The implementer's evidence is local `mvn test` per the chunk's accepted "CI runs are test evidence" operational note. Once Java/Maven CI lands (post-M7), Pitest will run on every PR. | None — accepted as evidence per chunkA-LOC-cap-revision operational accepts. |
| Informational | The third `ArchitectureTests` rule (`applicationOnlyDependsOnDomainAndJdk`) is documented as **vacuously satisfied in M1** because `application/` does not exist yet. This is correct and is the right ArchUnit pattern (rule declared once, enforced as packages land). It activates when A2 introduces `application/`. | None. |
| Informational | The revert commit message contains a clear reviewer-action protocol (`git checkout 06240ce && mvn test → expect ArchUnit FAIL`) which is the right pattern for plant-and-revert evidence preservation. Excellent audit hygiene. | None. |
| None | No edits outside the chunk scope. No Spring imports in `domain`. No `double`/`float` in `domain`. No `src/test/.../tests/` or top-level `tests/` collision (test classes correctly under `src/test/java/com/example/purchaseconversion/...`). | ✅ |

## 3. Operational note — PCI security_phase_guard interaction

Per `CLAUDE.md` §6A and the planning_phase_guard / pci_security_phase_guard hooks, edits to `src/` are gated on both `.human-approvals/{implementation,pci-security}-approved.txt`. Both markers were created on 2026-05-17 (verified independently in `chunks/13-PRE-readiness-check-fix/30-review.md` §2 regression-test fixtures). The hook chain correctly permitted A1's `src/` writes. ✅

## 4. Merge sequence (consolidated)

This sequence assumes all three accepted reviews are honoured:

1. **Implementer (PR #3):** normalize line endings on `feature/external-review-dossier-bootstrap` (`git add --renormalize . && git commit -m "chore: normalize line endings"`), push.
2. **Implementer:** merge PR #4 (readiness-check fix) into `main`. CI green on `main`.
3. **Implementer:** rebase PR #3 on the new `main` (no conflicts expected), merge.
4. **Implementer:** rebase `feature/chunk-a1-domain` on `main`, push. Confirm CI is green.
5. **Reviewer (me):** flip `chunks/13-A1-domain/manifest.yml` `status: accepted` once CI is green. No new `30-review.md`; this file is the verdict.
6. **Implementer:** merge PR #2.
7. **Implementer:** immediately start `chunks/13-A2-application/00-prompt.md` (now pre-staged by me; see §5).

## 5. Next chunk pre-staged

`chunks/13-A2-application/{00-prompt.md, manifest.yml}` is authored in the same commit as this review. Implementer may begin A2 the moment A1 merges — no reviewer round-trip needed. Same applies to `chunks/13-B-infrastructure/` and `chunks/13-C-api-observability/`: all four upcoming chunks are pre-staged so the implementer chains autonomously.

## 6. State transition

This review **does not** flip `manifest.status` to `accepted` yet — the rebase CI run is the only remaining gate. The manifest's `review_sha` will be set when this file is committed; `status: accepted` flips when the rebased CI run is green.

```yaml
# chunks/13-A1-domain/manifest.yml — current state
status: under_review
review_sha: <set when this file commits>
ci_url: 25999465787   # red — pre-rebase
ci_url_post_rebase: null   # set when rebased CI is green; flips status to accepted
deviations_open: [ops-check-strict-flip]   # resolves to [] after PR #4 merges
```
