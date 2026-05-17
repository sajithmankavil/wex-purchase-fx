# Tokenization and PAN Handling

> **Status:** Phase 7 (PCI Security Design Session), 2026-05-17.
> **Owner:** SecArch.
>
> Short version: **the service neither tokenises nor handles PAN.** This document records the explicit non-position so a QSA cannot infer a missing control.

---

## 1. Default position

Per the PCI scope-reduction posture in [pci-scope-and-cde.md](pci-scope-and-cde.md), the service is **out of CDE**. There is no PAN in any flow. Tokenisation and direct PAN storage are both prohibited by the contract-level prohibition (A-017) and by the database schema (no PAN-shaped column).

## 2. Storage decision matrix

| Use case | PAN needed? | Token sufficient? | Storage decision | Scope impact | Approval |
|---|---|---|---|---|---|
| Store purchase (FR-001) | No | n/a | Store `id`, `description`, `transactionDate`, `amount_usd` only | Out-of-CDE | Phase 1 + Phase 7 scope claim |
| Retrieve purchase (FR-002) | No | n/a | Read same set | Out-of-CDE | Same |
| Retrieve converted (FR-003) | No | n/a | Read purchase + cached Treasury rates; no payment-identity data | Out-of-CDE | Same |
| **Future feature** introducing PAN/SAD | n/a | n/a | **PROHIBITED v1.** Requires a new PCI design review (re-opens this gate) before implementation. | Would change scope to in-CDE | Compliance + SecArch + service owner |

## 3. Required controls — applicability table

The bundle scaffold lists the standard tokenisation-and-PAN-handling controls. Each one is recorded here with its applicability to this service.

| Control | Applicability v1 | If a future feature introduces PAN |
|---|---|---|
| **Tokenisation model** | n/a — no PAN to tokenise | Use a payment-provider's vault (Stripe / Adyen / etc.) for any future PAN; never roll our own |
| **Detokenisation authorisation** | n/a | RBAC + audit on the provider's vault |
| **PAN display masking** | n/a | Industry pattern: first-6 + last-4 with middle masked; per Phase-N PCI design |
| **PAN logging prevention** | **In force**: the `description`-handling stack (Luhn + track + encoded-PAN guards; HMAC log digest only) ensures no PAN reaches the logs even if accidentally submitted | Same stack + provider-vault separation |
| **PAN retention and purge** | n/a — no PAN stored | n/a — provider holds; service stores token only |
| **Data discovery for PAN leakage** | Quarterly DB column-audit + log-sample review confirming no CHD-shaped data ([cardholder-data-classification.md §4](cardholder-data-classification.md#4-required-controls)) | Continue + add token-shape vs PAN-shape check |
| **Support / admin access controls** | Platform RBAC ([access-control-pci.md](access-control-pci.md)) | Same; tighten access to detokenisation surface |

## 4. Hosted-fields / SAQ-A pattern (informative)

If a future feature requires accepting payment input from a user, the recommended pattern is:

1. **Hosted fields** on the payment provider's domain — the user's PAN never touches our service. PCI SAQ-A applies.
2. The provider returns a **network token** or **provider token** identifying the saved card.
3. Our service stores only the token (which is non-CHD if implemented per network-token spec).
4. Future actions (refund, void) operate via token + provider API.

This pattern remains **out of scope v1**; documented here for completeness.

## 5. Provider AOC / scope reduction evidence

| Evidence | Status |
|---|---|
| **Provider AOC** (Attestation of Compliance) | n/a v1 — no payment provider integrated |
| **Integration diagram** (CHD flows to provider, never to us) | n/a v1 |
| **Token vault boundary** | n/a v1 |
| **No-PAN-in-app proof** | [cardholder-data-flow.md](cardholder-data-flow.md) + boundary-guard probe set ([pci-scope-and-cde.md §4.1](pci-scope-and-cde.md#41-segmentation-validation-method)) |

## 6. Linked artefacts

- [pci-scope-and-cde.md](pci-scope-and-cde.md) — the scope claim this document supports.
- [cardholder-data-classification.md](cardholder-data-classification.md) — data inventory.
- [cardholder-data-flow.md](cardholder-data-flow.md) — flow diagram.
- [logging-monitoring-pci.md](logging-monitoring-pci.md) — log redaction details.
- [pci-dss-control-matrix.md](pci-dss-control-matrix.md) — Req 3 mapping (which is "not applicable" for this service).
