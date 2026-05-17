# Test Plan

> **Status:** Authored 2026-05-17 (Phase 9 M-1 closure).
> **Owner:** Architect.
> **Purpose:** Release-gate index into the substantive test plan, which is distributed across the requirements, architecture, operations, and security packages. This document does not duplicate them; it indexes them.

## 1. Authoritative sources

- [`docs/architecture/component-design.md §8`](../architecture/component-design.md#8-test-plan-by-component) — per-component test plan (unit, integration, contract, property-based, mutation, architectural).
- [`docs/requirements/acceptance-criteria.md`](../requirements/acceptance-criteria.md) — AC-001..AC-036 + AC-T-1..AC-T-6 cross-cutting test surfaces; AC-010b..e, AC-021c, AC-026b, AC-027b..e, AC-032, AC-032b.
- [`docs/requirements/non-functional-requirements.md`](../requirements/non-functional-requirements.md) — NFR-021 (mutation thresholds), NFR-003 (latency), NFR-013b / 014b / 016b / 018b (observability + logging).
- [`docs/architecture/component-design.md §6`](../architecture/component-design.md#6-archunit-rules-architectural-fitness-functions) — ArchUnit rules.
- [`docs/operations/capacity-scalability-plan.md §5`](../operations/capacity-scalability-plan.md#5-load-test-plan-closes-g4-p1-25) — load-test scenarios.
- [`docs/operations/failure-modes-and-resilience.md §1`](../operations/failure-modes-and-resilience.md#1-failure-mode-catalogue) — chaos / failure-injection scenarios.
- [`docs/security/secure-sdlc-pci.md §1`](../security/secure-sdlc-pci.md#1-required-gates-ci--pre-merge--pre-release) — SAST / SCA / DAST / secret / IaC / container scan gates.
- [`docs/security/logging-monitoring-pci.md §5`](../security/logging-monitoring-pci.md#5-redaction-policy-pci-req-34--104) — logging hygiene tests.
- [`docs/security/pci-scope-and-cde.md §4.1`](../security/pci-scope-and-cde.md#41-segmentation-validation-method) — CHD-shaped injection test set.

This document is the release-gate index. Treat the linked sections as authoritative.

## 2. Test layers — coverage matrix

| Layer | Coverage scope | Tooling | Source of truth | PR merge | Staging | Prod |
|---|---|---|---|---|---|---|
| Unit | Domain types, application services, infrastructure adapters | JUnit 5, AssertJ | component-design.md §8 | Yes | Yes | Yes |
| Property-based | `Money` HALF_UP invariants; rejects `double`/`float` | jqwik | component-design.md §8 | Yes | Yes | Yes |
| Architectural | Layer dependencies; no `double`/`float` in `domain`/`application`; controller-package locality | ArchUnit | component-design.md §6 | Yes | Yes | Yes |
| Integration | Per FR, per failure mode, per AC error code; `SingleFlightCacheConcurrencyTest`; `DurabilityRestartIT` | Testcontainers, Spring Boot Test | component-design.md §8 | Yes | Yes | Yes |
| Contract (upstream) | Treasury Fiscal Data API: happy / slow / 5xx / malformed / timeout / CB open / zero / negative / null / trailing-zero / leading-zero / high-precision / sanity-ceiling | WireMock | AC-T-3 fixtures | Yes | Yes | Yes |
| Boundary / PCI guard | CHD-shaped injection (ASCII / base64 / hex / URL-encoded / Unicode fullwidth PAN; track-data); rate-limit ordering vs ContentGuard | JUnit + Testcontainers | AC-010b/c/d/e, AC-T-6; pci-scope-and-cde.md §4.1 | Yes | Yes | Yes |
| Mutation | ≥ 70 % package average; ≥ 85 % on `RateSelectionPolicy` + `Money` | Pitest | NFR-021; component-design.md §8 | Yes | Yes | Yes |
| Content-type conformance | RFC 9457 + `application/problem+json` | JUnit, WireMock | AC-T-5 | Yes | Yes | Yes |
| Graceful shutdown | Single-flight lock release on SIGTERM; in-flight HTTP drain; readiness DOWN at SIGTERM | `GracefulShutdownIT` | G4-P1-23 | Yes (Phase 13) | Yes | Yes |
| Logging hygiene | No `description` text in logs; no PAN / CVV / track / secret in logs; HMAC `vN:` prefix | `LoggingPiiGuardTest`, `LoggingHashKeyTest`; PMD / Checkstyle | AC-032, AC-032b; logging-monitoring-pci.md §5 | Yes | Yes | Yes |
| OpenAPI lint | `oasdiff` no-breaking-changes; Spectral lint | oasdiff, Spectral | api-contracts.md §8 | Yes | Yes | Yes |
| SDLC scan | SAST + SCA + secret + IaC + container scan; SBOM (CycloneDX) | per `.github/workflows/security.yml` | secure-sdlc-pci.md §1 | Yes | Yes | Yes |
| DAST | Per release in staging | OWASP ZAP | secure-sdlc-pci.md §1 | No | Yes | Yes |
| Load / performance | 7 scenarios (steady-state 100 req/s 60/30/10 mix; burst 250 req/s ≤ 60 s; cold start; Treasury slow; Treasury 5xx; CB open; single-flight thundering-herd) | k6 (off-CI) | capacity-scalability-plan.md §5 | No | Yes (Phase 12 baseline) | Yes |
| Chaos / resilience | Failure injection per failure-modes-and-resilience.md §1 | toxiproxy, chaos-toolkit | failure-modes-and-resilience.md §1 | No | Yes (Phase 12) | Yes |
| Segmentation | Public → CDE-adjacent → CDE | per pen-test plan | penetration-test-plan.md; pci-scope-and-cde.md | No | Yes (Phase 11) | Yes |
| ASV scan | External-facing IP / hostname | PCI-DSS-approved ASV vendor | asv-scan-plan.md | No | No | Yes (quarterly, Phase 11) |

## 3. Test-case index

Per-AC test cases live in [`docs/requirements/acceptance-criteria.md`](../requirements/acceptance-criteria.md). Per-component test cases live in [`docs/architecture/component-design.md §8`](../architecture/component-design.md#8-test-plan-by-component). This file cross-references; it does not duplicate.

## 4. Promotion gates — required passing layers

| Gate | Required passing layers |
|---|---|
| PR merge | Unit, property-based, architectural, integration, contract (WireMock), boundary / PCI guard, mutation, content-type, logging hygiene, OpenAPI lint, SDLC scan |
| Staging promote | All of PR merge + DAST + graceful shutdown |
| Production promote | All of staging + load + chaos + segmentation + ASV scan (quarterly) + change-control evidence per [`change-control-pci.md`](../security/change-control-pci.md) + Phase 10 / 11 / 12 readiness markers |

## 5. Phase-13 implementation hooks

Phase 13 PRs implement test classes one milestone at a time per [`component-design.md §8`](../architecture/component-design.md#8-test-plan-by-component). Each PR description carries change-ID, requirement link, risk assessment, test evidence, approval, rollback class, deployment window, and post-deploy validation per [`docs/security/change-control-pci.md §1`](../security/change-control-pci.md#1-required-change-fields-pr--change-ticket-template).

Coverage thresholds (per NFR-021):
- Line ≥ 85 % on `domain/*`; ≥ 70 % package average elsewhere.
- Branch ≥ 75 % on `domain/*`.
- Mutation (Pitest) ≥ 85 % on `RateSelectionPolicy` + `Money`; ≥ 70 % package average elsewhere.

## 6. Out of scope for this release plan

- Test-data management for production CHD: not applicable (CHD never stored; guards reject before persistence).
- Synthetic UX testing: not applicable (server-side API only).
- Multi-region failover testing: deferred to Phase 12 (G6-P1-7).

## 7. Change history

| Date | Change | Author |
|---|---|---|
| 2026-05-17 | Authored as Phase 9 M-1 closure; consolidates the test plan distributed across `docs/architecture`, `docs/requirements`, `docs/operations`, and `docs/security`. | Architect |
