# 00-prompt — Chunk 13-C3-openapi-cicd

> Canonical kickoff for sub-chunk C3 of the original C2 scope. Created 2026-05-18 by external reviewer alongside `chunks/13-C2-observability-cicd/15-clarification.md`. See that clarification for the supersession rationale; see `chunks/13-C-api-observability/30-review.md` §3 for the parent scope-deviation acceptance.

## Prerequisite

C2 (`13-C2-observability-cicd`, narrowed scope per `chunks/13-C2-observability-cicd/15-clarification.md`) merged to `main`. C3 cannot start before C2 closes — `LoggingPiiGuardTest` from C2 is the integration touchstone for several CI quality gates here.

## Read first

0. `docs/external-review/` — every file under `chunks/13-C-api-observability/` and `chunks/13-C2-observability-cicd/`. Specifically:
   - `STATUS.md`
   - `directives/2026-05-17-forward-motion-bias.md`
   - `directives/2026-05-18-phase-10-11-bulk-pass-protocol.md` (relevant — C3 is the last chunk before this directive activates)
   - `chunks/13-C-api-observability/30-review.md` §3 (scope-deviation ratification — C3 inherits the deferred items)
   - `chunks/13-C2-observability-cicd/15-clarification.md` (the binding C2/C3 split)
1. `CLAUDE.md`.
2. `docs/architecture/adr-0001-core-architecture.md` D-11 (RFC 9457), D-13 (detection-and-alert guards).
3. `docs/architecture/api-contracts.md` §1 + §5 — scale-6 wire format; RFC 9457 error envelope; AC-014 example `exchangeRate=1.370000`.
4. `docs/security/change-control-pci.md` §1 — PR template; deploy workflows must reference `pci-production-approved.txt` gate.
5. `docs/security/pci-scope-and-cde.md` — connected-to vs CDE; relevant for the security workflow's scope.
6. `docs/operations/observability.md` — context for what M5 deliverables in C2 produced; C3 doesn't add to it.
7. `pom.xml` (post-C2-merge state).
8. C's `PurchaseController.java` + DTOs + `ProblemDetailExceptionHandler.java` (post-merge state) — the surface annotated by S2 below.
9. C2's `LoggingPiiGuardTest.java` (post-merge state) — referenced by S3's CI workflow as a quality gate.

## Scope — exact and bounded

### S2 — M6 OpenAPI surface (~570 LOC)

| Component | Detail |
|---|---|
| springdoc annotations on `PurchaseController` | `@Tag("Purchases")` at class level; `@Operation` + `@ApiResponse` on each of the 3 methods. Each `@ApiResponse` enumerates the RFC 9457 error codes that endpoint can produce. |
| springdoc annotations on DTOs (`PurchaseRequest`, `PurchaseResponse`, `ConversionResponse`) | `@Schema` on each field with `description`, `example`, `pattern` where applicable. Currency examples cover both dual-mode forms (canonical Treasury descriptor + alias). |
| OAS examples | Per `docs/architecture/api-contracts.md`: AC-014 (`exchangeRate=1.370000`); a successful create response; a successful conversion response; one example per RFC 9457 error shape (VALIDATION_FAILED, PAN_PATTERN_DETECTED, FUTURE_DATE, PURCHASE_NOT_FOUND, MALFORMED_IDENTIFIER, INVALID_CURRENCY, CONVERSION_RATE_NOT_AVAILABLE, UPSTREAM_UNAVAILABLE, UPSTREAM_BAD_RESPONSE, RATE_LIMITED, INTERNAL_ERROR). |
| OAS baseline file | Generated OAS at `infra/openapi/baseline.yaml` (checked in). Updated by an explicit `mvn -Pgenerate-oas` profile or equivalent. |
| oasdiff CI gate | New workflow step that diffs the live-generated OAS against `infra/openapi/baseline.yaml`. Diff must be either empty (OK) or explicitly annotated as a breaking-change ADR (fails CI otherwise). |
| Spectral lint | `.spectral.yaml` ruleset enforcing: `operation-tag-defined`, `operation-operationId`, `operation-description`, RFC 9457 fields present, no PII field names. Workflow step runs Spectral against the generated OAS. |

### S3 — M7 CI/CD (~540 LOC)

| Component | Detail |
|---|---|
| `.github/workflows/security.yml` | 5 jobs: Semgrep (SAST), OWASP Dependency-Check (SCA), GitLeaks (secrets), Trivy (container scan once a Dockerfile exists; if no Dockerfile, SARIF-no-op acceptable for v1), CycloneDX SBOM. Each emits SARIF where possible; uploads as artifact. Failure thresholds: HIGH or CRITICAL findings block merge; MEDIUM are advisory. |
| `.github/workflows/ci.yml` extensions | Add OAS generation + oasdiff + Spectral steps; add Maven dependency cache; ensure JaCoCo + Pitest results upload as artifacts. The existing scripts/quality test step must still run. |
| `.github/workflows/deploy-dev.yml` | Manual `workflow_dispatch`; deploys to `dev`. No gating beyond CI green. **No production deploy from this workflow.** |
| `.github/workflows/deploy-staging.yml` | Manual `workflow_dispatch`; deploys to `staging`. Gated on `.human-approvals/staging-approved.txt` existing AND being newer than the deployed commit. |
| `.github/workflows/deploy-prod.yml` | Manual `workflow_dispatch`; deploys to `prod`. Gated on `.human-approvals/pci-production-approved.txt` existing AND being newer than the deployed commit. Workflow fails fast if the marker is missing or stale. The deploy step itself is a stub (`echo "would deploy <sha> to prod"`) — there is no real prod infrastructure to deploy to yet. The gating and marker-check logic is what's being verified at this chunk. |
| `.pre-commit-config.yaml` | Installs GitLeaks as a pre-commit hook locally. Hook config is the single source for the secrets-scan rule shared with the security workflow. |

### Out of scope for C3

- No application code changes (S1-style observability is fully owned by C2; M4 surface is owned by C).
- No new tests beyond OAS-generation smoke tests and workflow-validation tests (e.g., `actionlint` config). The workflows themselves are the deliverables; their test is "do they run green on the C3 branch".
- No `docs/security/pci-dss-control-mapping.md` — that's a Phase 11 bundle artifact, not a Phase 13 deliverable. C3's security workflows produce inputs that Phase 11's bundle references.
- No real production deploy. The prod workflow is a marker-gated stub.

## PCI-critical invariants verified at this merge

| Invariant | Test / Evidence |
|---|---|
| RFC 9457 conformance of every error response | OAS examples cover all 11 error codes; Spectral rules enforce envelope shape; oasdiff fails on accidental drift |
| AC-014 wire-format invariant (`exchangeRate=1.370000`, scale 6) | OAS example for the conversion-success response |
| PCI Tier 1 deploy gate — production deploy requires `pci-production-approved.txt` | `deploy-prod.yml` fails fast if marker missing or stale; reviewer reads the workflow YAML |
| Secrets-scan parity local ↔ CI | `.pre-commit-config.yaml` GitLeaks rule matches `security.yml` GitLeaks rule (single ruleset reference) |
| Vulnerability signal | Semgrep + OWASP DC + Trivy SARIF artifacts produced on every PR |
| Dependency manifest visibility | CycloneDX SBOM artifact on every release |

PR description **must** include this table mapped to the deliverable artifact + workflow file path.

## Acceptance gates

| Gate | Target |
|---|---|
| OAS baseline checked in + oasdiff workflow green | yes |
| Spectral ruleset present + workflow green on the generated OAS | yes |
| All 5 security-workflow jobs green on C3 branch | yes; HIGH/CRITICAL = 0 (or explicitly waived in this PR's description) |
| Deploy-prod workflow marker-gate logic | manually tested by running `act` (or equivalent) with + without a stale/fresh marker file; evidence in `20-summary.md` |
| ArchUnit | All A1+A2+B1+B2+C+C2 rules still pass |
| `application/*` + `infrastructure/*` + `api.*` + `config.*` JaCoCo + Pitest gates (from earlier chunks) | Still pass on this branch |
| LOC | ≤ 1,500 (target) / ≤ 1,800 (hard upper bound). Estimated ~1,190. **No exceptions; the cap is reaffirmed and the B1/C concessions are not transitive.** |

## Branching, PR, CI

- Branch: `feature/chunk-c3-openapi-cicd` off `main` (post-C2-merge).
- One PR. Estimated ~1,190 LOC.

## PR description (change-control-pci.md §1)

```
change-id: WEX-CHUNK-C3-openapi-cicd
requirement link: M6 (OpenAPI/OAS/oasdiff/Spectral); M7 (SAST/SCA/secrets/container/SBOM/deploy workflows);
                  RFC 9457 (D-11) via OAS; PCI Tier 1 deploy gating via .human-approvals/*.txt
risk assessment: Low — no application code changes. Risks: (a) workflow misconfiguration could
                 false-pass CI, (b) deploy workflows could be triggered without intended gates.
                 Mitigations: oasdiff baseline locks the API contract; deploy workflows are
                 manually-triggered and marker-gated; the prod deploy step is a stub.
security impact: low (positive) — adds SAST/SCA/Trivy/SBOM scanning to every PR; adds secrets
                 scanning local + CI; adds the production deploy marker-gate.
CDE impact: connected-to (audit destination only via security workflow SARIF artifacts); no CHD path.
test evidence:
  - All 5 security-workflow jobs green: <CI URLs>
  - oasdiff diff vs baseline: empty: <CI URL>
  - Spectral lint green: <CI URL>
  - deploy-prod.yml marker-gate test (with + without stale marker): <evidence>
  - PCI-invariants table: <inline>
approval: <Architect + SRE + Security>
rollback class: A (workflow rollback; revert + re-CI)
deployment window: continuous
post-deploy validation: trigger deploy-dev once; verify SBOM artifact exists; verify oasdiff baseline
                        unchanged after the deploy
```

## Workflow

Per CLAUDE.md §2 + the directive `directives/2026-05-17-forward-motion-bias.md`. Implement in order:

1. S2 first (OAS surface) — springdoc annotations + DTO schemas + examples + baseline OAS generation + oasdiff gate + Spectral lint.
2. S3 after S2 — security workflow + ci.yml extensions + deploy workflows + pre-commit.
3. Land each sub-slice with its own commits inside the single PR so the diff is reviewable by sub-slice.

If LOC estimate creeps above 1,500 mid-implementation, surface a `10-deviation.md` BEFORE the cap is hit (per C 30-review §1 / C2's `10-deviation.md` precedent). The 1,800 hard cap is non-negotiable this phase.

## Completion summary

Write to `chunks/13-C3-openapi-cicd/20-summary.md`. CLAUDE.md §9 format. Flip `manifest.status` accordingly.

## Phase 13 closure note

After C3 merges, **Phase 13 is complete**. The next pre-staged artifact is `phases/10-operational-readiness/{00-prompt.md, manifest.yml}` — the reviewer will create those at C3-merge per the bulk-pass directive activation clause.
