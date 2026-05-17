# PCI Scope and CDE Definition

> **Status:** Phase 7 (PCI Security Design Session), 2026-05-17.
> **Owner:** SecArch (placeholder @secarch); Compliance liaison (@compliance).
> **Posture:** PCI DSS 4.0.1 hygiene; service is **out-of-CDE**.
>
> Primary purpose: the foundational evidence that this service does not store, process, transmit, secure, or impact cardholder data. This is the core artefact a QSA will read to accept the scope-reduction claim. The PCI Security Grill (Phase 8) attacks every claim made here.

---

## 1. The scope claim, in one paragraph

`wex-purchase-fx` stores USD purchase transactions (description, transaction date, amount in USD) and retrieves them re-expressed in a target currency using publicly published Treasury exchange rates. **The service receives no PAN, no SAD, no CVV/CVC, no track data, no PIN, no session tokens, no payment-network identifiers.** The API contract prohibits payment data; the implementation enforces it via boundary content guards (AC-010b/c/d). The only data flow that *could* carry cardholder data is the free-text `description` field, which is governed by the Luhn + track-data + encoded-PAN guard pipeline at the API boundary. The service is therefore **out of CDE scope** for PCI DSS 4.0.1.

This claim is load-bearing on three things:
1. The contract-level prohibition (A-017) — primary control.
2. The boundary guards (AC-010b, AC-010c, AC-010d) — detection-and-alert, defense-in-depth.
3. The audit trail (`purchase_validation_failed{reason}` events) — evidence that the guards fire and that payload was never persisted.

If any of those weaken, scope review re-opens.

## 2. Scope model

| Category | Components | Why in scope | Owner | Evidence |
|---|---|---|---|---|
| **CDE** (Cardholder Data Environment) | None. | No component of `wex-purchase-fx` stores, processes, or transmits CHD. | n/a | This document + [cardholder-data-flow.md](cardholder-data-flow.md) + [cardholder-data-classification.md](cardholder-data-classification.md) |
| **Connected-to** | None. | No network or auth connectivity to a CDE — the service is its own trust zone. | n/a | [network-segmentation.md](network-segmentation.md) |
| **Connected-to** *(Phase-8 G8-P0-2 re-categorisation)* | **Audit-log destination.** | Contains `description.hash` + length data which, with the HMAC key (held separately), is dictionary-attackable for short descriptions. Re-categorised from "security-impacting" to "connected-to" to match PCI DSS 4.0.1 semantics. IAM and controls unchanged — see [logging-monitoring-pci.md §7](logging-monitoring-pci.md) for the mitigating-argument detail. | SecArch | [logging-monitoring-pci.md](logging-monitoring-pci.md) |
| **Security-impacting** | Metrics / trace destinations; secrets store. | Operational telemetry without reversible-to-CHD content (metric labels enum-bound; trace span attributes never include description content; secrets store holds key material, not data). | SecArch | [encryption-key-management.md](encryption-key-management.md) + [observability.md](../operations/observability.md) |
| **Out-of-scope** | `wex-purchase-fx` (the service itself), PostgreSQL (when used), Treasury Fiscal Data API (public). | No CHD path; the contract prohibits payment data; the guards detect and reject; segmentation is by design (separate network zone). | Service owner | All of `docs/security/` |

## 3. Required decisions — Phase-7 ratified

### 3.1 In-scope services
**None.** `wex-purchase-fx` does not handle CHD; it is out-of-scope by API contract.

### 3.2 In-scope databases / storage
**None.** `purchase_transactions` (DB table) stores `id`, `description`, `transactionDate`, `amount_usd`, `created_at`, `updated_at`. **`description` is a free-text field with bounded length (≤ 50 chars) and is governed by the API-boundary content guards.** The DB schema by design cannot store PAN/SAD/CHD — there is no column shaped for them. `exchange_rates` stores public Treasury rate data; never CHD.

### 3.3 In-scope queues / events
**None.** The service publishes no messages; consumes no queues. Audit-events are emitted as structured logs (a one-way logging destination, not a queue).

### 3.4 In-scope logs / telemetry
**Treated as security-impacting** rather than in-scope.
- Logs: structured JSON; `description` field is **never** logged plaintext (NFR-017; AC-032). Only `description.length` and `vN:<hex>` HMAC digest. Audit-events are logged at INFO with content-guard `reason` labels; rejected payloads are *not* logged (AC-010b/c/d).
- Metrics: bounded cardinality (NFR-018b); no label carries `description` content; all label values are well-known enum values.
- Traces: OTel span attributes never include `description` content.

### 3.5 Admin / support tools
**None bundled with the service.** Operator access in production is via the platform (kubectl / cloud-console / etc.) governed by the platform's RBAC.

### 3.6 CI/CD and deployment systems
GitHub Actions workflows. These can impact production by deploying new binaries; access to the repo and the deploy workflow is **security-impacting**. PCI controls apply (signed commits, branch protection, OIDC for deploy authentication — Phase 12 hand-off).

## 4. Segmentation proof

This service is its own trust zone. There is no "CDE" to segment from — the service handles no CHD by design. Segmentation evidence is therefore evidence of the **API contract**, not network topology.

| Segmentation evidence | Location |
|---|---|
| Network diagram showing service trust boundary | [deployment-architecture.md §4](../architecture/deployment-architecture.md#4-production-reference-deployment) |
| Firewall / security-group policy (production) | Platform-managed; documented in `secure-config-hardening.md` (Phase 7 follow-on) |
| Ingress allow-list | TLS-terminating gateway only; service listens on private network only |
| Egress allow-list | Outbound only to `api.fiscaldata.treasury.gov:443` + DB + telemetry sinks; everything else denied at egress proxy |
| **Contract-level prohibition** (the primary control) | [api-contracts.md §3.1](../architecture/api-contracts.md#3-post-apiv1purchases--create) — `description` field rules; OpenAPI documents the same |
| **Boundary guards** (defense in depth) | AC-010b (Luhn), AC-010c (track), AC-010d (encoded); [component-design.md §3.5](../architecture/component-design.md#35-contentguard-phase-4-refinement-g4-p0-5) |
| **Detection metrics** | `description.content_guard.fired.count{reason}` ([monitoring-alerting.md §3](../operations/monitoring-alerting.md)) |
| **Audit evidence** | `purchase_validation_failed{reason}` audit events ([logging-monitoring-pci.md](logging-monitoring-pci.md)) |

### 4.1 Segmentation validation method

**Annual + on-material-change.** The PCI security grill (Phase 8) is the first run; subsequent runs follow PCI DSS 4.0.1 Req 11.4.4 cadence.

Validation tests:
1. Inject Luhn-valid card numbers via the API; assert `400 PAN_PATTERN_DETECTED` with `reason=luhn`. (AC-010b)
2. Inject track-data-shaped strings; assert `400 PAN_PATTERN_DETECTED` with `reason=track1` / `reason=track2`. (AC-010c)
3. Inject base64 / hex / URL-encoded card numbers; assert `400 PAN_PATTERN_DETECTED` with `reason=luhn-encoded`. (AC-010d)
4. Inspect logs from those rejections; assert no plaintext payload appears. (AC-032 + audit evidence)
5. Inspect DB after rejections; assert no row was persisted. (Persistence layer test)
6. Sample 1 % of production audit events for `reason=luhn|luhn-encoded|track1|track2` and review for unexpected sources / patterns.

### 4.2 Pen-test / segmentation-test cadence

| Test type | Cadence | Owner |
|---|---|---|
| Internal pen-test (against the API contract) | Annually + on-material-change | SecArch (engages an external pen-test team) |
| Segmentation test (boundary-guard probe set) | Quarterly | SRE (runs the test suite in [§4.1](#41-segmentation-validation-method)) |
| ASV scan (external) | Quarterly per PCI DSS Req 11.3.2 | Compliance (engages an Approved Scanning Vendor) |

Detail in [penetration-test-plan.md](penetration-test-plan.md) and [asv-scan-plan.md](asv-scan-plan.md) (Phase 7 follow-ons; placeholders authored elsewhere in Phase 7).

## 5. Open issues

| Priority | Issue | Owner | Due date | Mitigation |
|---|---|---|---|---|
| P1 | **OQ-010** Identity origin for non-case-study deployments | Platform security | Phase 12 pre-prod hand-off | Working: trusted gateway with mTLS / OIDC / SPIFFE; no app-layer auth (A-007). |
| P1 | **G4-P0-5 closure** — encoded-PAN guard implementation | SecArch + Architect | Phase 13 | Phase-4 direction pinned; AC-010d documents; implementation in Phase 13 (`ContentGuard` decoder pipeline). |
| P1 | **Multi-encoding chain residual** (AB-014) — URL-encoded base64 of a PAN | SecArch | Phase 8 (PCI grill) | Accepted residual at v1; Phase-8 PCI grill re-attacks. |
| P2 | Unicode confusables in `description` (e.g., `4` → `４` fullwidth) | SecArch | Phase 8 / 13 | Accepted residual; Phase-8 PCI grill considers Unicode normalisation. |

## 6. What this document does NOT do

- Document specific QSA findings — those land in [qsa-roc-readiness.md](qsa-roc-readiness.md) (Phase 11 hand-off).
- Replace the formal PCI DSS testing procedures — those are QSA-led.
- Lock in network topology — production-reference is in [deployment-architecture.md](../architecture/deployment-architecture.md).
- Document control evidence in detail — that's [evidence-register.md](evidence-register.md).

## 7. Linked artefacts

- [pci-dss-control-matrix.md](pci-dss-control-matrix.md) — full 12-requirement breakdown.
- [cardholder-data-flow.md](cardholder-data-flow.md) — the *no-CHD* flow diagram.
- [cardholder-data-classification.md](cardholder-data-classification.md) — data inventory.
- [tokenization-and-pan-handling.md](tokenization-and-pan-handling.md) — neither tokenisation nor PAN handling.
- [threat-model.md](threat-model.md) — STRIDE catalogue.
- [evidence-register.md](evidence-register.md) — central evidence index.
- [pci-security-grill.md](pci-security-grill.md) — Phase 8 adversarial review.
