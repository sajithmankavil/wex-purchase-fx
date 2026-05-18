# 10-deviation — Chunk 13-C2-observability-cicd

**Author:** Implementer agent
**Date:** 2026-05-18
**Type:** Pre-implementation LOC-overage surface — proposed sub-split before any code is written.
**Status of chunk manifest:** flipping `prompt_received → deviation_surfaced` on the same commit as this file.
**Reviewer instruction honored:** C `30-review.md` §1 — "Going forward, if any future chunk's honest LOC estimate exceeds the 1,800 hard cap or skips a sub-slice from the prompt, raise a `10-deviation.md` before implementation, not in the summary." This file is that surfacing, before any S1/S2/S3/S4 file lands.

---

## Honest implementation estimate

C2's `00-prompt.md` lays out 23 distinct items across S1 (M5 deep observability), S2 (M6 OpenAPI surface), S3 (M7 CI/CD), and S4 (8 review-derived conditions including §4.7 NEW MED on `MalformedIdentifierException`). Per-item LOC estimate based on B1/B2/C precedents:

| Sub-slice | Item | Est. LOC |
|---|---|---:|
| S1 | Micrometer metric families (7 names) + bean wiring + `MetricNameRegistrationTest` | 180 |
| S1 | OpenTelemetry SDK config — `opentelemetry-spring-boot-starter` + OTLP exporter + `OpenTelemetryWiringTest` | 80 |
| S1 | Warm-up job — `ApplicationListener<ApplicationReadyEvent>` + async chain + `WarmupApplicationListenerTest` | 130 |
| S1 | Tail-sample hints — `Span.current().setAttribute("sampling.important", true)` across hot paths | 30 |
| S1 / S4.1 | `LoggingPiiGuardTest` — SLF4J test-appender + 4 nested cases + profile-aware (local + ci) | 250 |
| S2 | springdoc `@Tag` + `@Operation` + `@ApiResponse` + `@Schema` annotations on PurchaseController + 4 DTOs | 250 |
| S2 | OAS examples — AC-014 scale-6, dual-mode currency, all RFC 9457 error shapes | 80 |
| S2 | oasdiff CI gate — generation + baseline OAS file checked in + workflow step | 180 |
| S2 | Spectral lint — `.spectral.yaml` ruleset + workflow step | 60 |
| S3 | `.github/workflows/security.yml` — Semgrep + OWASP DC + GitLeaks + Trivy + SBOM (5 jobs) | 220 |
| S3 | `.github/workflows/ci.yml` extensions — OAS + Spectral + Maven cache | 60 |
| S3 | `.github/workflows/deploy-{dev,staging,prod}.yml` (3 files; staging+prod gated) | 240 |
| S3 | `.pre-commit-config.yaml` (GitLeaks) | 20 |
| S4.2 | `RateLimitOrderingIT` — `@SpringBootTest(RANDOM_PORT)`; saturate + Luhn-PAN + assert 429 + no ContentGuard | 120 |
| S4.3 | `EndToEndTreasuryIT` — WireMock-backed full-stack IT (AC-T-3) | 200 |
| S4.4 | `RateRevisionEndToEndIT` — HTTP-boundary IT for AC-026b | 130 |
| S4.5 | Treasury audit `StructuredArguments.kv` migration (edit) | 30 |
| S4.6 | Treasury whitelist regression test (unit) | 60 |
| S4.7 (**NEW MED**) | `MalformedIdentifierException` input hashing — handler edit + javadoc update + regression test in `LoggingPiiGuardTest` | 50 |
| S4.8 | `Retry-After` decision documented in api-contracts.md | 10 |
| Dossier | `chunks/13-C2-observability-cicd/{10-deviation, 20-summary, manifest}.yml` + STATUS.md update + C manifest flip-on-merge | 200 |
| **Estimated total** | | **~2,580** |

Even with aggressive trimming (skipping tests for the lowest-risk items, single-line javadoc, sharing test fixtures) the floor is **~2,200 LOC** — **400 LOC over the 1,800 hard cap**.

The cap binds per the reviewer's explicit instruction in C 30-review §6: "C2 must hold the 1,800 cap. … not a recurring concession." The B1 one-time concession (+2,743) was tied to a regression-fix; this chunk has no analogous regression to co-locate.

## Proposed resolution — split into C2 + C3

Clean dependency seam: **observability + regression tests (C2)** before **OpenAPI surface + CI/CD workflows (C3)**. Rationale below.

### Sub-chunk C2 — `feature/chunk-c2-observability-cicd` (renamed scope)

| Item | Detail | Est. LOC |
|---|---|---:|
| S1 | All of M5 deep observability (metric families + OTel + warm-up + tail-sample) | 420 |
| S4.1 / S1 | `LoggingPiiGuardTest` (BOTH local + ci profiles; covers ContentGuard + InvalidCurrency + MalformedIdentifier) | 250 |
| S4.2 | `RateLimitOrderingIT` (G8-P0-1 servlet-container regression) | 120 |
| S4.3 | `EndToEndTreasuryIT` (AC-T-3) | 200 |
| S4.4 | `RateRevisionEndToEndIT` (AC-026b) | 130 |
| S4.5 | Treasury audit structured-field migration | 30 |
| S4.6 | Treasury whitelist regression test | 60 |
| **S4.7 (NEW MED)** | `MalformedIdentifierException` input hashing — handler + javadoc + LoggingPiiGuardTest coverage | 50 |
| S4.8 | `Retry-After` documentation update | 10 |
| Dossier | C2 dossier files + STATUS.md + C manifest flip | 150 |
| **C2 total** | | **~1,420** (within 1,500 soft target) |

Rollback class A (code + tests + config only; no schema, no HTTP-surface changes, no deploy workflows).

**Why this seam.** All four PCI-critical invariants C2 owns (AC-032 redaction regression, G8-P0-1 servlet-container regression, AC-T-3 end-to-end, AC-026b end-to-end, S4.7 input hashing) close pre-existing or newly-found audit gaps in the M4 surface. The MED §4.7 finding is security-critical and benefits from landing in the same chunk as the LoggingPiiGuardTest that regression-tests it.

### Sub-chunk C3 — `feature/chunk-c3-openapi-cicd` (new chunk)

| Item | Detail | Est. LOC |
|---|---|---:|
| S2 | M6 OpenAPI — springdoc annotations on PurchaseController + DTOs; OAS examples; oasdiff baseline + CI gate; Spectral lint | 570 |
| S3 | M7 CI/CD — security.yml (SAST + SCA + GitLeaks + Trivy + SBOM); ci.yml extensions; deploy-{dev,staging,prod}.yml; pre-commit | 540 |
| Dossier | C3 dossier files + STATUS.md | 80 |
| **C3 total** | | **~1,190** (within 1,500 soft target) |

Rollback class A (code/workflows only; deploy workflows are SKELETONS gated on `pci-production-approved.txt` and cannot fire production today).

**Why this seam.** OpenAPI + CI/CD are infrastructure-tooling concerns largely orthogonal to the M4 application surface. The deploy workflows are pre-gated on `pci-production-approved.txt` and the SAST/SCA scans add CI signal without changing application behaviour. Splitting here makes the OpenAPI baseline + workflow content review the dominant work in C3, separate from C2's behaviour + regression-test focus.

### Why this split (not a different one)

1. **Security-critical first.** The §4.7 NEW MED finding (MalformedIdentifierException raw-input leak) and the AC-032/032b LoggingPiiGuardTest regression both belong in C2. Bundling them with CI-tooling changes risks audit confusion at review time.
2. **Test discipline focused.** C2 is dominated by regression-test additions; C3 is dominated by workflow YAML + OpenAPI annotations. The reviewer's verification mode differs (test-method enumeration vs. workflow-file inspection); splitting matches the cognitive seam.
3. **LOC discipline.** Both sub-chunks fit within the 1,500 soft target. Neither approaches the 1,800 hard cap. The 400-LOC overage that would have hit on combined C2 is eliminated.
4. **Rollback class is identical (A)**, so the split adds no deployment overhead — only one extra review cycle.
5. **Mirrors the B → B1 + B2 split precedent.** Same pattern: behaviour-critical work in the first sub-chunk; infrastructure-tooling work in the second.

### What this deviation explicitly does NOT change

- **PCI-critical invariants** — every invariant in C2 `00-prompt.md` §"PCI invariants" still lands before Phase 13 closes. C2 (behaviour + regression tests) carries 6 of the 13; C3 (OpenAPI + CI/CD signals) carries the remaining 7. No invariant is dropped or deferred to Phase 10/11.
- **§4.7 NEW MED finding** — lands in C2, not deferred.
- **Out-of-scope list** — unchanged. No production deploy. Deploy workflows in C3 are skeletons.
- **LOC discipline** — both sub-chunks within the 1,500 soft target.

## Action requested

Reviewer's choice:

- **(a) Accept the C2 / C3 split as proposed.** Implementer immediately proceeds with C2 (the renamed-scope chunk above) on the existing `feature/chunk-c2-observability-cicd` branch; C3 is a new pre-staged chunk created after C2 merges.
- **(b) Accept C2 as one chunk; relax the 1,800 cap to ~2,300 for this single chunk.** Implementer proceeds with the unsplit scope. The reviewer's C-30-review §6 statement "C2 must hold the 1,800 cap. … not a recurring concession" would need to be explicitly revised by this acceptance.
- **(c) Alternative split** the reviewer prefers. Surface and I'll re-scope.

Default forward-motion path while awaiting: nothing in `src/main/` or `src/test/` is being written. This branch carries only this `10-deviation.md`, a manifest flip on C2 (`prompt_received → deviation_surfaced`), and a manifest flip on C (`under_review → accepted`; mechanical transition per C 30-review §8) — until the reviewer responds.

## State transition

- `chunks/13-C-api-observability/manifest.yml`: `status: under_review → accepted` (mechanical transition; C is merged on main as `ce73273`).
- `chunks/13-C2-observability-cicd/manifest.yml`: `status: prompt_received → deviation_surfaced`; `deviation_sha`: `<git hash-object>` of this file.
- `STATUS.md`: updated to reflect C closed; C2 deviation_surfaced; C3 proposed-but-not-yet-pre-staged.
