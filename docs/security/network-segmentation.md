# Network Segmentation and Firewall Design

> **Status:** Phase 7 (PCI Security Design Session), 2026-05-17.
> **Owner:** SecArch + Platform team.
>
> The service is **out of CDE** — there is no CDE-to-non-CDE boundary inside this trust zone. Segmentation here is the *contract-level* boundary plus the conventional production deployment topology (ingress / egress / private network).

---

## 1. Segmentation goals

- Restrict ingress to the production service to the ingress / gateway only (no direct internet exposure).
- Restrict egress to the bounded set of upstream dependencies: Treasury API, DB, telemetry sinks, secrets store.
- Make the boundary testable and auditable; document drift detection.
- Out-of-CDE evidence rests on the *contract-level* boundary (no CHD by API design) plus the network boundary (no unsolicited inbound; no unmanaged egress).

## 2. Network zones

| Zone | Components | Trust | Ingress | Egress | Controls |
|---|---|---|---|---|---|
| **Public internet** | Anything outside the platform | None | n/a | n/a | Cannot reach `wex-purchase-fx` directly |
| **Ingress / gateway** | TLS-terminating gateway (e.g., AWS ALB / GCP HTTPS LB / nginx ingress) | High (platform-managed) | TLS 1.3 preferred; identity established at gateway (mTLS / OIDC / JWT / SPIFFE — Phase-12 OQ-010 closure) | Forwards to service over private network only | TLS termination; WAF; rate limiting; identity propagation |
| **Service private network** | `wex-purchase-fx` replicas | Medium (in-cluster) | From gateway only (no public IP) | DB, Treasury (via egress proxy), telemetry sinks, secrets store | Network policy / security groups |
| **DB subnet** | PostgreSQL (prod) / H2 file (local) | High | From service replicas only (private subnet) | None outbound | Network policy; DB-level auth |
| **Egress proxy / NAT** | Platform-managed | Medium | From service replicas only | **Allow-list:** Treasury, telemetry, secrets store. Deny everything else by default | Egress allow-list audited monthly |
| **Treasury Fiscal Data API** | External (U.S. federal government) | Low (payload-level untrusted; high TLS-identity) | n/a | n/a | TLS 1.3 / 1.2 minimum; cert validation; treated payload-untrusted |

## 3. Firewall / allowlist rules

| Rule ID | Source | Destination | Port / protocol | Purpose | Owner | Review cadence |
|---|---|---|---|---|---|---|
| **NS-001** | Public internet | Ingress / gateway | 443/TCP (HTTPS) | Inbound API traffic | Platform | Monthly |
| **NS-002** | Gateway | `wex-purchase-fx` private | 8080/TCP (HTTP — TLS terminated upstream) | Application traffic | Platform | Monthly |
| **NS-003** | `wex-purchase-fx` private | PostgreSQL subnet | 5432/TCP | DB connection | SRE | Monthly |
| **NS-004** | `wex-purchase-fx` private | Egress proxy | 443/TCP | Outbound TLS via proxy | SRE | Monthly |
| **NS-005** | Egress proxy | `api.fiscaldata.treasury.gov` | 443/TCP | Treasury API | SRE | Monthly |
| **NS-006** | Egress proxy | Telemetry sinks (Loki / Prom / OTel collector hosts) | 443/TCP | Observability | SRE | Monthly |
| **NS-007** | Egress proxy | Secrets store (Vault / cloud KMS) | 443/TCP | Startup secret retrieval | SecArch + SRE | Monthly |
| **NS-008** | Public internet | `wex-purchase-fx` private | * | **DENY** | Platform | n/a (default-deny) |
| **NS-009** | `wex-purchase-fx` private | * (anything not in NS-003..NS-007) | * | **DENY** | Platform | n/a (default-deny) |

The default-deny rules (NS-008, NS-009) are the load-bearing controls. Every allowed flow is explicit and reviewed.

## 4. Diagram

See [deployment-architecture.md §4](../architecture/deployment-architecture.md#4-production-reference-deployment) for the full topology. The PCI-relevant ingress/egress is:

```
Public internet → Gateway (TLS terminate) → Service private network → {DB, Egress proxy → Treasury, Egress proxy → Telemetry, Egress proxy → Secrets}
                                                                       (allow-listed only; default-deny everything else)
```

## 5. Validation

| Validation | Cadence | Owner |
|---|---|---|
| Segmentation test (boundary-guard probe set) | Quarterly | SRE — see [pci-scope-and-cde.md §4.1](pci-scope-and-cde.md#41-segmentation-validation-method) |
| Egress allow-list drift detection | Monthly | SRE — diff against approved baseline |
| Ingress allow-list review | Monthly | Platform |
| External pen-test | Annual + on-material-change | SecArch (external pen-test team) |
| ASV scan | Quarterly (PCI Req 11.3.2) | Compliance (Approved Scanning Vendor) |

## 6. Drift detection

- **Egress drift:** any new outbound connection from `wex-purchase-fx` to a destination not in `{Treasury, DB subnet, telemetry, secrets}` produces a platform alert. Implementation: egress proxy logs every connection; SIEM rule fires on unexpected destinations.
- **Ingress drift:** any source other than the gateway reaching the service on port 8080 produces an alert. Implementation: network-policy logs.
- **Rule-set drift:** the rule-set is source-controlled (Terraform / equivalent). PR review on changes; CI fails if the rule-set differs from the approved baseline without an associated change-control ticket ([change-control-pci.md](change-control-pci.md)).

## 7. Local / case-study mode

In `local` mode, the service runs as a single process bound to `0.0.0.0:8080` (or `127.0.0.1:8080` if the operator prefers). No gateway, no egress proxy. The case-study reviewer can reach the service directly. Treasury and the H2 file are reached over the local stack.

The PCI segmentation argument applies only to the production-reference deployment. The case-study mode is for development / review and is not the deployment posture used for live traffic.

## 8. Linked artefacts

- [deployment-architecture.md §4](../architecture/deployment-architecture.md#4-production-reference-deployment) — production topology.
- [pci-scope-and-cde.md](pci-scope-and-cde.md) — segmentation evidence requirements.
- [access-control-pci.md](access-control-pci.md) — IAM at the gateway and on the platform.
- [logging-monitoring-pci.md](logging-monitoring-pci.md) — network audit logs.
- [pci-dss-control-matrix.md](pci-dss-control-matrix.md) — Req 1 / Req 4 mappings.
