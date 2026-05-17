# PCI Logging and Monitoring

> **Status:** Phase 7 (PCI Security Design Session), 2026-05-17.
> **Owner:** SecArch + SRE.
> Closes **G4-P1-16** (audit-log destination + tamper-evidence mechanism).
>
> The application-layer log/metric/trace contract is in [observability.md](../operations/observability.md). This document overlays PCI DSS Req 10 controls on top: audit destination, tamper-evidence, retention, redaction, time sync, access-of-audit logging.

---

## 1. Logging rules (PCI Req 10.2)

What must be logged (PCI-relevant):

- **Audit events** with `event=purchase_validation_failed{reason=…}` for every content-guard rejection (luhn / luhn-encoded / track1 / track2 / length / etc.).
- **`exchange_rate_revision_persisted`** events when Treasury republishes a rate (G-P0-4 / AC-026b).
- **Treasury circuit-breaker state changes** (`treasury_circuit_open` / `_half_open` / `_closed`).
- **Alias-drift events** (`currency_alias_drift_detected`).
- **Rate-orientation drift warnings** (`rate_orientation_drift_warn`).
- **Readiness state transitions** (`readiness_state_changed`).
- **Service start / stop** (`service_started`, `service_stopping`).
- **Log-hash-key rotation observed** (`log_hash_key_rotation_detected`).
- **All authentication events** at the gateway (Phase-12 OQ-010 closure) — gateway-layer concern, not service-layer.
- **All admin/config changes** to the secrets store / DB / platform — platform-layer audit logs, not service-layer.

What must **never** be logged:
- PAN, SAD, CVV/CVC, track data, PIN, payment tokens, session tokens, passwords, API keys.
- Verbatim `description` field content.
- Rejected payload content on a content-guard trip — the *fact* of the rejection (with `reason`) is logged, never the payload.
- Full request bodies on validation failures.
- Stack traces in API response bodies (logged internally is acceptable; never echoed to the client).

## 2. Log sources

| Source | Events | PAN redaction? | Audit destination | Retention | Owner |
|---|---|---|---|---|---|
| `wex-purchase-fx` app | Audit events per §1 + standard request/response logs | n/a (no PAN by design; `description` is hashed) | Audit-log destination (§3) for audit events; regular log sink for non-audit | Audit ≥ 1 year (3 months online); non-audit 30 days | Service owner |
| Gateway / ingress | Authentication events; rate-limit hits; TLS errors | Platform-managed | Platform audit log | ≥ 1 year per PCI Req 10.5.1 | Platform |
| PostgreSQL | Connection events; admin commands; failed auth | Platform-managed | DB audit log | ≥ 1 year | DBA + SecArch |
| Secrets store | Read access (every read of any key) | n/a | Secrets-store audit log | ≥ 1 year | SecArch |
| CI/CD | Deploy events; secret-environment-binding | n/a | CI audit log | ≥ 1 year per PCI Req 10.5.1 | Platform |
| Audit-log destination itself | Read access (audit-of-audit) | n/a | Separate audit-log destination (different IAM scope) | ≥ 1 year | SecArch + Compliance |

## 3. Audit-log destination — Phase-7 ratified

**Closes G4-P1-16.** The destination must be append-only / signed / WORM in production. Platform-dependent picks:

### Production-reference options (any is acceptable; Phase 12 picks based on platform)

| Option | Mechanism | Retention | Notes |
|---|---|---|---|
| **AWS S3 + Object Lock (compliance mode)** | Audit events written via Kinesis Firehose to S3; Object Lock with `compliance` mode prevents deletion (even by root account) during the retention window; SSE-S3 encryption at rest | 1 year retention; 3 months in standard tier, then transitions to Glacier Deep Archive | Recommended for AWS deployments |
| **GCP Cloud Logging + Log Buckets (immutable)** | Log buckets with `_Required` bucket setting; immutable retention enforced | 1 year retention | Recommended for GCP |
| **Azure Storage with Immutable Blob Storage** | Append-only blob with legal hold + time-based retention | 1 year retention | Recommended for Azure |
| **Self-hosted WORM** (e.g., MinIO with object-lock, immudb) | Same semantics | 1 year retention | For on-prem deployments |

**The actual pick is platform-dependent and made at Phase 12 (pre-prod hand-off).** All four options satisfy PCI Req 10.5.2 ("audit log files are protected from modifications").

### Required properties of the chosen destination

| Property | Requirement | PCI Req |
|---|---|---|
| Append-only | Yes — deletes are blocked within retention window | 10.5.2 |
| Signed or hash-chained | Yes — destination provides cryptographic integrity (object-lock + SSE) | 10.5.2 |
| Synchronised timestamps | Yes — destination uses platform-managed NTP; audit records carry RFC 3339 UTC timestamps from the source | 10.6 |
| Tamper-evident | Yes — modification attempts fail; access attempts log to a separate sink | 10.5.2 |
| Retention | ≥ 1 year (3 months online + 9 months in cold storage) | 10.5.1 |
| Read access logged | Yes (audit-of-audit) | 10.2 |
| Authorised destruction at retention end | Yes — after 1 year, the destination retention policy purges old records | 10.5.1 |

### Case-study local mode

Local mode writes audit events to stdout (captured by the operator's terminal or log aggregator). There is no WORM in local mode. The PCI claim applies only to production-reference deployment.

## 4. Time synchronisation (PCI Req 10.6)

- The service uses **UTC** everywhere (Phase-1 A-014).
- The host's clock is synchronised via platform-managed NTP (or chronyd / equivalent).
- Audit events carry `timestamp` in RFC 3339 UTC microsecond precision (NFR-017).
- Time-drift > 1 s between service replicas is a platform alert (out-of-service-scope).

## 5. Redaction policy (PCI Req 3.4 / 10.4)

| Data type | Redaction in logs |
|---|---|
| `description` field content | **Never logged plaintext.** Logged only as `description.length` (integer) and `description.hash` (HMAC-SHA-256 with `vN:` version prefix, ≥ 256-bit key per NFR-013b). |
| Rejected-payload content (on content-guard trip) | **Never logged.** The reject path emits only the `event=purchase_validation_failed{reason=…}` audit record; the payload itself is dropped. |
| Stack traces in API responses | **Never** (centralised `ProblemDetailsExceptionHandler`; AC-T-2 + AC-T-5 assert). |
| Secret values | **Never** (refuse-to-start invariant on missing secret; never echoed in error responses). |
| Request bodies on validation failures | **Never logged in full.** The `details.errors[].field` plus `code` is the most we expose. |
| `currency` query-string value in access logs | Length-bounded by Bean Validation (≤ 64 chars); platform-policy access log redaction (TM-I-009 residual). |

### Redaction enforcement

- **Logging hygiene rule** (Phase 13): PMD / Checkstyle rule blocks `log.*("...description...", description, ...)` patterns. Closes G4-P1-1.
- **Integration test:** `LoggingPiiGuardTest` greps all emitted log lines from a full test run for the verbatim `description` value injected by test fixtures; assertion fails if any line contains it (AC-032).
- **Sampling review:** monthly, on-call samples 1 000 production log lines and confirms none contain plaintext `description` content.

## 6. Alerts (PCI Req 10.7)

All defined in [monitoring-alerting.md §3](../operations/monitoring-alerting.md#3-alert-catalogue-everything-else). PCI-relevant subset:

| Alert | Triggers on | PCI Req | Severity |
|---|---|---|---|
| A-021 Content-guard fires | `description.content_guard.fired.count > 10/5min` | 10.7 + 8 (suspicious activity) | SEV2 |
| A-025 Audit-event spike (PCI rejections) | `purchase_validation_failed{reason=pan_pattern\|luhn-encoded\|track1\|track2} > 100/1h` | 10.7 | SEV2 |
| A-027 Log-hash key issue | digest emits `v0:` in prod | 10.5.2 | SEV1 |
| A-008 Rate-orientation drift | per [monitoring-alerting.md §3.1](../operations/monitoring-alerting.md#31-rateorientationcontractcheck-calibration-g4-p1-27-closure) | n/a (correctness) | SEV1/SEV2 |
| Audit-destination outage | platform-managed | 10.5 | SEV1 |
| Time-drift alert | platform-managed (NTP) | 10.6 | SEV2 |

## 7. Required tests

| Test | What it asserts | Cadence |
|---|---|---|
| `LoggingPiiGuardTest` | No `description` plaintext in any log line | Per release (AC-032) |
| `LoggingHashKeyTest` | Refuse-to-start on missing `WEX_LOG_HASH_KEY` in `prod`/`staging`; `vN:` prefix appears on digests | Per release (AC-032b) |
| Boundary-guard probe | Inject CHD-shaped strings via API; assert rejection + no payload logged | Quarterly (segmentation test per §4.1 of pci-scope-and-cde.md) |
| Log-sampling review | 1 000 lines sampled; no plaintext `description`; no secrets; no stack traces | Monthly |
| SIEM ingestion validation | All audit events from a 24-h window arrive at the audit destination | Weekly |
| Time-sync validation | All replicas within 1 s of NTP | Continuous (platform) |

## 8. Audit-log retention purge

- After **1 year**, audit-log destination retention policy purges expired records.
- Purge events are themselves logged (to the audit-of-audit destination).
- HMAC log-hash key for the corresponding `vN:` version is destroyed at the same time (the digests become uninvertible by design after key destruction; the records themselves remain readable as opaque hex).

## 7b. Mitigating argument for connected-to categorisation (Phase-8 G8-P0-2)

The audit-log destination contains `description.hash` (HMAC-SHA-256 with `vN:` prefix) + `description.length`. With both the HMAC key and audit-log read access, an attacker could dictionary-attack short / low-entropy descriptions. PCI DSS 4.0.1 therefore classifies this as **connected-to** rather than out-of-scope.

The mitigating argument for why this is *operationally* acceptable:

1. **Dual-IAM compromise required.** The HMAC key lives in the secrets store; the audit log lives in a different destination with different IAM. An attacker must compromise *both* IAM zones (different principals, different rotation cadences, different audit-of-audit destinations).
2. **What's recoverable is not CHD.** Even with both compromised, the inverted output is `description` text. By API contract (A-017), this never contains CHD; the boundary guards (Phase-4 + Phase-8 NFKC pre-pass + encoded-PAN decoder) ensure CHD-shaped content is rejected *before* logging.
3. **Rejected payload is never logged.** The reject path emits only `event=purchase_validation_failed{reason}` — the offending bytes never reach the audit log.
4. **Rotation limits the dictionary-attack window.** Quarterly key rotation (per [encryption-key-management.md §3.3](encryption-key-management.md#33-key-rotation)) means each `vN:` prefix carries at most 90 days of digests; cryptographic erasure (G8-P2-1) destroys the key 1 year later.
5. **Length side-channel** is a documented residual (TM-I-002 / G4-P2-4) — at v1 volume, not material.

Consequence-of-compromise is operational privacy risk (description-text leak), **not** CHD leak. PCI-applicable controls (Req 10.5 access control on the audit destination; Req 3.5 key management) are in force and proportionate.

## 9. Linked artefacts

- [observability.md](../operations/observability.md) — application-layer log/metric/trace contract.
- [pci-scope-and-cde.md](pci-scope-and-cde.md) — overall scope.
- [encryption-key-management.md](encryption-key-management.md) — HMAC key lifecycle.
- [access-control-pci.md](access-control-pci.md) — IAM on the audit destination.
- [evidence-register.md](evidence-register.md) — EVD-010 audit-log evidence.
- [pci-dss-control-matrix.md](pci-dss-control-matrix.md) — Req 10.x mappings.
- [non-functional-requirements.md NFR-016b / NFR-017 / NFR-018](../requirements/non-functional-requirements.md) — audit + log NFRs.
