# PCI Access Control

> **Status:** Phase 7 (PCI Security Design Session), 2026-05-17.
> **Owner:** SecArch + Platform team.
>
> Records access-control posture for the service, the secrets store, the DB, the audit log, and the platform-managed plane. The service has no app-layer auth at v1 (A-007); identity is established at the gateway in non-case-study deployments. **OQ-010 (BLOCKING-for-prod)** records the gate that must close before the production hand-off (Phase 12).

---

## 1. Access principles (PCI Req 7 / 8)

- **Least privilege.** Every role gets the minimum permissions needed.
- **Need-to-know.** Production data access is granted on documented need.
- **MFA for administrative access** to the platform, secrets store, DB, and audit log destination.
- **Unique identities.** No shared accounts in `prod` / `staging`. Service accounts (non-human) have rotated credentials and are tracked.
- **Periodic access reviews.** Quarterly; documented in [evidence-register.md](evidence-register.md).

## 2. Role matrix (production-reference)

Placeholder names; pre-prod hand-off replaces with real assignments.

| Role | Systems | Privileges | Business need | MFA | Approval | Review cadence |
|---|---|---|---|---|---|---|
| **Service owner** (`@platform-team` lead) | Platform; secrets store (write); audit log (read) | Full app-config; secret rotation; runbook owner | End-to-end accountability | Yes | Compliance | Quarterly |
| **SecArch** (`@secarch`) | Secrets store (write); audit log (read); PCI evidence (write) | Key generation/rotation; security review | PCI control owner | Yes | Compliance | Quarterly |
| **SRE on-call** (`@wex-fx-oncall`) | Platform (read/write); logs/metrics/traces (read); rollback execution; readiness toggle | Incident response | Operational | Yes | Service owner | Quarterly |
| **Architect** (`@architect`) | Repo (read/write); design docs (write); ADR review | Design authority | Architectural changes | Yes | Service owner | Quarterly |
| **QA / test owner** | CI (read); test infra (write); evidence write for test suites | Test plan; mutation thresholds; chaos plan | QA accountability | Yes | Service owner | Quarterly |
| **Auditor / Compliance** | Audit log destination (read); evidence-register (read); PCI scope docs (read) | Read-only; cannot modify | PCI compliance | Yes | Service owner | Quarterly |
| **DBA / platform-DB team** | PostgreSQL (admin role) | DB administration; migrations on emergency only | DB ops | Yes | Service owner + Compliance | Quarterly |

## 3. Service accounts (non-human identities)

| Account | Purpose | Permissions | Secret storage | Rotation | Owner |
|---|---|---|---|---|---|
| `wex-purchase-fx-app` (DB user) | App runtime DB access | `SELECT, INSERT` on `purchase_transactions`, `exchange_rates`; **no `DELETE` / `DROP`** | Platform secrets store (env-mounted file) | Per platform policy (typically annual) | DBA + SecArch |
| `wex-purchase-fx-flyway` (DB user) | Flyway migrations only | `CREATE, ALTER, DROP` (DDL); used only during deploy | Platform secrets store | Per release | DBA + SecArch |
| `wex-purchase-fx` (platform identity) | The Pod's identity; reads secrets, writes telemetry | Read `WEX_LOG_HASH_KEY` + `WEX_DB_PASSWORD`; write to telemetry | Platform-managed (k8s service account / cloud IAM role) | Platform default | Platform |
| `wex-fx-canary` (Treasury canary) | Synthetic check user | None; reads-only against Treasury API (no auth) | n/a | n/a | SRE |
| `wex-fx-ci` (CI/CD) | Builds, runs tests, deploys | Push to registry; deploy to `dev` / `staging` / `prod` (via OIDC to platform IAM) | GitHub Actions secrets | Per platform policy | Platform + Architect |

**Separation of duties:** the runtime DB user (`wex-purchase-fx-app`) cannot run DDL; the migration user (`wex-purchase-fx-flyway`) is used only by the deploy job and is otherwise inaccessible. This prevents a compromised app process from altering schema.

## 4. Privileged access

- **Admin paths.** Platform UI (kubectl / cloud console) for SRE incident response; secrets store CLI for SecArch key rotation; DB admin console for the DBA. Each path requires MFA.
- **Break-glass.** No application-layer break-glass at v1 (no app-layer auth → no override surface). Platform break-glass (e.g., emergency cluster admin) follows platform policy; every use is logged + reviewed within 24 h.
- **Session logging.** Platform session logging (e.g., cloud-trail / k8s audit log) captures every admin action. Retention ≥ 1 year (NFR-016b).
- **Termination / offboarding.** On staff departure: secrets-store credentials revoked within 4 hours; platform IAM revoked; the `WEX_LOG_HASH_KEY` is rotated if the departing person held it.

## 5. Application-layer access (the OQ-010 closure direction)

**v1:** No app-layer auth (A-007). Service runs behind a trusted gateway in real deployments. Documented as **BLOCKING-for-prod**.

**Phase-12 hand-off (production deployment) options** — Phase 7 records direction; real organisational choice belongs to the consuming team:

| Pattern | When to use | What lands at the service |
|---|---|---|
| **mTLS at the gateway** | Internal service-to-service in a service mesh | Client cert SPKI in a `X-Client-Cert-SubjectDN` header from the gateway; service maps to a tenant if multi-tenant; v1 is single-tenant so the value is for audit only |
| **OIDC / JWT at the gateway** | External clients (browsers, SaaS) | `Authorization: Bearer <jwt>` validated by the gateway; gateway sets `X-User-Id` / `X-User-Roles` headers from claims |
| **SPIFFE / workload identity** | Cloud-native service mesh | SVID at gateway; gateway forwards identity headers |

**Phase 7 working position:** the service accepts an opaque `X-Request-Identity` header (string, 0-255 chars) that the gateway populates. v1 does not parse it for authorisation; it is **logged as part of every request** for audit traceability (correlation-id-style, but identity-shaped). When OQ-010 closes, the application layer can grow from "identity-aware" to "identity-enforcing."

## 6. Audit-log access

| Role | Read | Write | Delete | Notes |
|---|---|---|---|---|
| Service runtime | Write only | Yes | No | Append-only writes to the audit destination |
| SecArch | Read | No | No | Investigation + PCI evidence |
| SRE on-call | Read | No | No | Incident response |
| Auditor | Read | No | No | Quarterly review |
| Service owner | Read | No | No | Oversight |
| Platform-admin (break-glass) | Read | No (audit-destination is WORM) | No (object-lock prevents) | Even with platform-admin, the destination's object-lock prevents deletion within retention window |

**Access to the audit log is itself audit-logged** at the destination layer (per NFR-016b). The audit-of-audit destination is separately controlled and reviewed quarterly (E4 from the Phase-4 hardening).

## 7. Quarterly access review (PCI Req 7.2.4)

| Review item | Owner | Output |
|---|---|---|
| Platform IAM roster | Platform team | Confirm every user / service account still active; revoke leavers |
| Secrets-store ACL | SecArch | Confirm read/write permissions per role table |
| DB role assignments | DBA + SecArch | Confirm least-privilege; audit any role-elevation events |
| Audit-log access review | Compliance | Walk through audit-of-audit log; flag anomalies |
| Service-account credential rotation | SecArch + Platform | Confirm rotation cadence honoured |

Output is appended to [evidence-register.md](evidence-register.md) as EVD-007.

## 8. Linked artefacts

- [pci-scope-and-cde.md](pci-scope-and-cde.md) — overall scope.
- [authn-authz-design.md](authn-authz-design.md) — Phase-7 follow-on (detailed once OQ-010 closes).
- [encryption-key-management.md](encryption-key-management.md) — secrets-store IAM specifics.
- [network-segmentation.md](network-segmentation.md) — network-level access.
- [evidence-register.md](evidence-register.md) — EVD-007 / EVD-008 access reviews.
- [pci-dss-control-matrix.md](pci-dss-control-matrix.md) — Req 7 / Req 8 mappings.
