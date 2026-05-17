# Threat Model

> STRIDE-style threat model for the WEX Purchase Currency Conversion Service. Treated as the Phase-4 design-grill output for the security lens; refined in Phase 7 (PCI security design) and challenged in Phase 8 (PCI security grill).
> **Status:** Phase 4 (Design Grill), 2026-05-17. Working document.
> **Phase-4 hardening pass additions (H6 / H8 / H9, same day):** five new STRIDE entries (TM-T-008, TM-T-009, TM-D-008, TM-I-009, AB-014); Phase-7 carry-forwards section (§8); residuals §7 refreshed.

---

## 1. Scope

In scope: the `wex-purchase-fx` Spring Boot service, its persistence (H2 local / PostgreSQL prod), its HTTP listener, its outbound Treasury client, the structured-log / metrics / trace emission paths, and the secrets / configuration plane that feeds it.

Out of scope (covered by other documents or out of scope v1):
- Ingress / gateway / WAF posture (platform responsibility; OQ-010).
- Treasury Fiscal Data API itself (we treat it as untrusted at the payload level and trusted at the TLS-identity level).
- Platform secrets backend, container-image signing, supply-chain CI hardening (Phase 7).
- Audit-log destination internals (Phase 7).

## 2. Assets

| Asset | Sensitivity | Threats | Controls |
|---|---|---|---|
| **Stored purchases** (`purchase_transactions`) | Low — non-financial metadata; no payment data. The `description` field is the only attack vector for accidental cardholder data. | Tampering, mass exfiltration, repudiation. | DB integrity constraints; PAN-Luhn / track-data / encoded-PAN guards at the API boundary (AC-010b/c/d). |
| **Stored exchange rates** (`exchange_rates`) | Public; sourced from a U.S. government open dataset. | Tampering (introduce wrong rate → wrong `convertedAmount`). | Schema validation + sanity bounds at fetch time; versioned persistence preserves audit trail; conversion result computed at retrieval time, never cached. |
| **HMAC log-hash key** (`WEX_LOG_HASH_KEY`) | High. Plain compromise lets an attacker invert `description.hash` for short / low-entropy descriptions. | Secret exfiltration. | Env-only at v1; platform secrets store (Vault / KMS) in production; refuse-to-start on absence in `prod`/`staging`; rotation produces `vN:` version tag so old and new digests are distinguishable. |
| **DB credentials** (`WEX_DB_*`) | High. | Secret exfiltration; lateral movement. | Env-only; least-privilege DB role; split env vars (Phase-4 G4-P1-14) so the password is never embedded in a URL. |
| **Audit events** | Medium. Used in incident investigation, PCI evidence. | Tampering, deletion. | Emitted to external sink with append-only / signed / WORM properties (NFR-016b; concrete destination Phase 7); access to the audit log is itself audit-logged. |
| **Correlation identifiers** | Low individually; medium in aggregate (correlation graph). | Confusion (client-controlled re-use), unbounded series cardinality. | Server-side prefix binding (Phase-4 G4-P1-13); cardinality budget. |
| **TLS server certificate** (prod ingress) | High. | Stolen cert → MITM. | Managed by platform; rotation policy per platform. |

## 3. Trust boundaries

```
                            ┌─────────────────────── External boundary ────────────────────────┐
                            │                                                                   │
                            │   API client (untrusted: payload-level)                           │
                            │      │                                                            │
                            │      │ HTTPS (TLS 1.3 preferred, 1.2 minimum)                     │
                            │      ▼                                                            │
                            │   Gateway / Ingress (prod only)                                   │
                            │      • Terminates TLS                                             │
                            │      • Establishes identity (mTLS / OIDC / JWT / SPIFFE — Phase 7)│
                            │      • Rate-limits (gateway)                                      │
                            │      │                                                            │
                            │      │ HTTP/2 over loopback (in-cluster)                          │
                            │      ▼                                                            │
                            │   wex-purchase-fx (trust boundary: this service is the asset)     │
                            │      • Validates every payload byte at the API layer              │
                            │      • Content guards (Luhn, track, encoded)                      │
                            │      • Centralised RFC-9457 exception mapper                      │
                            │      • HMAC-hashes `description` before logging                   │
                            │                                                                   │
                            │   ──┬───────────────────────────────────┬──                       │
                            │     │                                   │                         │
                            │     ▼                                   ▼                         │
                            │   PostgreSQL (managed)               Treasury Fiscal Data API    │
                            │      • Service-account login            (untrusted: payload-level│
                            │      • Least-privilege role              trusted: TLS identity)  │
                            │      • PITR, snapshots                  • Schema-validated       │
                            │                                          • Sanity-bound          │
                            │                                          • Single-flight gated   │
                            │                                                                   │
                            └───────────────────────────────────────────────────────────────────┘
                                       ▲                       ▲                       ▲
                                       │                       │                       │
                                  Secrets store           Telemetry sinks         Audit-log sink
                                  (Vault / KMS)           (Loki / Prom / OTel)    (WORM / signed)
```

## 4. STRIDE catalogue

Threats indexed `TM-S-…` (Spoofing) `TM-T-…` (Tampering) `TM-R-…` (Repudiation) `TM-I-…` (Information disclosure) `TM-D-…` (Denial of service) `TM-E-…` (Elevation of privilege). Severity = L (likelihood 1-5) × I (impact 1-5); class Low/Med/High/Critical per Phase-2 risk matrix.

### 4.1 Spoofing (identity)

| ID | Threat | L | I | Sev | Controls (existing) | Gap / required action |
|---|---|---|---|---|---|---|
| TM-S-001 | API client impersonates another client (no app-layer auth in v1). | 4 | 3 | 12 (Med) | A-007: service deployed behind a trusted gateway only. Readiness refuses UP in non-local profiles if `WEX_GATEWAY_REQUIRED=true` (Phase-7 control). | Phase 7: identity origin (OQ-010). v1 case-study: accepted residual. |
| TM-S-002 | Caller spoofs a `traceparent` / `X-Correlation-Id` to misattribute actions. | 3 | 2 | 6 (Low) | Server prefixes the correlation id with a server-instance tag (Phase-4 G4-P1-13). | Pinned in observability.md. |
| TM-S-003 | Treasury Fiscal Data API impersonated by MITM. | 1 | 5 | 5 (Low) | TLS 1.3 preferred / 1.2 minimum; full chain validation; hostname verification; pinned CA bundle (NFR-012). | None additional. |
| TM-S-004 | Secrets backend impersonated. | 1 | 5 | 5 (Low) | Platform mTLS to secrets store; CSI driver or sidecar (Phase 7). | Phase 7. |

### 4.2 Tampering (integrity)

| ID | Threat | L | I | Sev | Controls (existing) | Gap / required action |
|---|---|---|---|---|---|---|
| TM-T-001 | Treasury API returns malicious / corrupted rate data. | 2 | 5 | 10 (Med) | Schema validation; sanity bounds 0 < rate ≤ 10³⁰ (data-model.md §2; AC-024b widened by G4-P1-3); `RateOrientationContractCheck` WARN on convention drift. | Phase 5: promote contract check to fail-closed at threshold (G4-P1-27). |
| TM-T-002 | Treasury API replays an old rate with current `fetched_at`. | 1 | 3 | 3 (Low) | Versioned persistence stores `effective_date`; existing rows preserved (AC-026b). | None. |
| TM-T-003 | Database tampering directly via DB credentials theft. | 2 | 5 | 10 (Med) | Least-privilege DB role; secrets in platform store; row-level audit by DB-engine logs (Postgres). | Phase 7: read-only role for the app + scoped write role for migrations only. |
| TM-T-004 | Audit-log tampering after the fact. | 2 | 4 | 8 (Med) | Append-only / signed / WORM destination (NFR-016b). | Phase 7: pick the destination. |
| TM-T-005 | `description` payload smuggles CHD that bypasses the content guards (base64 / hex / URL / Unicode confusables). | 3 | 4 | 12 (Med) | Decoder pipeline + Luhn / track checks (AC-010d, Phase-4 G4-P0-5). | Phase 7: Unicode-confusable handling; split-field handling reserved for when a second free-text field lands. |
| TM-T-006 | An attacker poisons the alias table via PR review compromise. | 1 | 4 | 4 (Low) | CODEOWNERS gating on `currency-aliases.json`; daily reconciliation job emits `currency_alias.drift.detected` for unexpected canonicals (R-014 mitigation). | Phase 7: add ownership review cadence. |
| TM-T-007 | Conversion-result tampering (clients re-using older response). | 1 | 1 | 1 (Low) | Out of scope — clients are responsible for their own state. | None. |
| TM-T-008 | Supply-chain compromise of a build-time dependency (e.g., `uuid-creator` v7 generator added Phase-4 G4-P0-4; Spring Boot transitive deps; Resilience4j). A malicious release ships with every deployed binary. | 2 | 5 | 10 (Med) | Pin to exact version + SHA-256 checksum in `pom.xml`; Maven's `<dependency-verification>` mode enabled in Phase 13; CI dependency-scan (Snyk / OWASP DC) on every PR; image scan (Trivy / Grype) on every build. | Phase 7: produce SBOM on every release; Phase 13: enable Maven dependency verification. |
| TM-T-009 | Time-of-check vs time-of-use on the currency alias table when `WEX_ALIAS_TABLE_PATH` overrides the classpath default. Disk-mutable file post-startup. | 1 | 2 | 2 (Low) | Service reads alias table only at startup (no hot-reload); in production the path is a read-only CSI volume / `ConfigMap`. | Phase 7: pin the prod mount strategy. |

### 4.3 Repudiation

| ID | Threat | L | I | Sev | Controls (existing) | Gap / required action |
|---|---|---|---|---|---|---|
| TM-R-001 | A purchase create cannot be attributed to a caller. | 5 | 2 | 10 (Med) | v1 has no app-layer auth (A-007); audit logs carry server-side timestamp + correlation id but not user identity. | Phase 7: identity-origin (OQ-010) provides the missing attribution. |
| TM-R-002 | An auditor cannot confirm "no PAN was ever stored." | 2 | 4 | 8 (Med) | DB schema cannot store PAN by design (no column shaped for it); content-guard audit events `purchase_validation_failed{reason}` record every rejection; PCI evidence package (Phase 7 / Phase 8). | Phase 7. |
| TM-R-003 | An operator denies they accessed the audit log. | 2 | 3 | 6 (Low) | Access to the audit log itself is audit-logged (NFR-016b). | Phase 7: choose the audit-of-audit destination. |

### 4.4 Information disclosure

| ID | Threat | L | I | Sev | Controls (existing) | Gap / required action |
|---|---|---|---|---|---|---|
| TM-I-001 | `description` leaks via logs. | 1 | 4 | 4 (Low) | NFR-017 + AC-032 / AC-032b: `description` never logged in plaintext; HMAC-SHA-256 digest with `vN:` version prefix; mandatory env-sourced key in `prod`/`staging`. | Phase 7: rotation procedure + key storage policy. |
| TM-I-002 | `description.length` side-channel reveals card-pattern attempts. | 2 | 2 | 4 (Low) | Documented residual (Phase-4 G4-P2-4); the metric `purchase.create.validation_error.count{reason=pan_pattern\|luhn-encoded}` makes the *attempt* the metric, not the length. | Accepted residual for v1. |
| TM-I-003 | Treasury rates accidentally exposed to a client by including PII or tokens. | 1 | 3 | 3 (Low) | Treasury rates are public; nothing PII-shaped in our response. | None. |
| TM-I-004 | Secrets in stack traces / error responses. | 1 | 5 | 5 (Low) | Centralised `ProblemDetailsExceptionHandler`; no stack traces in API bodies; logback masking patterns for secret shapes; CI gitleaks/trufflehog. | Phase 7: SIEM rule for secret-shape in any log line. |
| TM-I-005 | Description-hash key in plaintext env on host disk (process env, `/proc/<pid>/environ`). | 2 | 3 | 6 (Low) | Acceptable v1; platform secrets retrieval pattern (mounted secret file / Vault Agent / CSI) in production. | Phase 7 (G4-P1-11). |
| TM-I-006 | Correlation id reveals across-tenant linkability. | 1 | 2 | 2 (Low) | v1 is single-tenant; correlation id is opaque. | Re-evaluate when multi-tenancy lands. |
| TM-I-007 | OpenAPI / Swagger UI exposed in production. | 2 | 2 | 4 (Low) | Architecture: Swagger UI disabled in prod or restricted by gateway; OpenAPI doc remains served on the management port. | Phase 7: pick. |
| TM-I-008 | Metric labels leak business intel (e.g., per-customer currency mix). | 2 | 1 | 2 (Low) | Default RED counters omit `currency` (NFR-018b); allow-list governed by config. | None. |
| TM-I-009 | Tomcat access log captures the request line including `?currency=<value>`, potentially logging client-supplied junk if validation upstream is lax. | 1 | 2 | 2 (Low) | `currency` is Bean-Validated and bounded (length ≤ 64); access-log redaction at the platform layer for tokens matching `?[a-z]+=`. Default config does not log query strings if `WEX_ACCESS_LOG_LEVEL=minimal`. | Phase 7: pin the access-log policy. |

### 4.5 Denial of service

| ID | Threat | L | I | Sev | Controls (existing) | Gap / required action |
|---|---|---|---|---|---|---|
| TM-D-001 | Flood of `POST /purchases` from a misbehaving / malicious client. | 3 | 3 | 9 (Med) | Gateway rate limiting (platform). Service has no app-layer rate limiter v1 (G4-P1-15). | Phase 7: add Resilience4j `RateLimiter` at controller layer as defense-in-depth (cheap; ~ 50 LOC + config). |
| TM-D-002 | Flood of `GET /…/conversion` with diverse `currency` causes cache-miss storm + Treasury fanout. | 3 | 4 | 12 (Med) | Single-flight gate (G4-P0-1 refined); CB on Treasury client; bulkhead 50 concurrent permits. | Phase 5: ratify bulkhead size against capacity plan. |
| TM-D-003 | Single-flight winner stalls; many losers block on the future. | 2 | 3 | 6 (Low) | Phase-4 G4-P1-6: losers wait bounded 200 ms then re-check DB. | Pinned. |
| TM-D-004 | DB connection-pool exhaustion saturates the service. | 2 | 4 | 8 (Med) | Readiness probe considers pool capacity (G4-P1-19 pinned); LB drains saturated replicas. | Phase 5: pool size + alert thresholds. |
| TM-D-005 | Treasury sustained-outage cascade: every conversion times out. | 2 | 3 | 6 (Low) | CB opens after 50 % over 20 calls; reads from local DB cache for eligible rates; `503` with `Retry-After: 300 s` for the rest. | Phase 5: CB calibration (G4-P1-17). |
| TM-D-006 | Large-payload POST (e.g., 50 char `description` but `amountUsd="1e300"` or repeated requests with huge JSON). | 2 | 2 | 4 (Low) | Jackson default limits; Bean Validation; HTTP request size cap. | Phase 13: assert request-size cap in CI. |
| TM-D-007 | Slow-loris on the Spring Tomcat connector. | 2 | 3 | 6 (Low) | Spring Boot defaults; Tomcat connection-timeout + max-connections; LB layer. | None v1. |
| TM-D-008 | CPU exhaustion via the encoded-PAN decoder pipeline (base64 / hex / URL decoders run on every `description`). Phase-4 G4-P0-5 added the pipeline. | 1 | 2 | 2 (Low) | `description` length capped at 50 chars (no input expansion); decoder set excludes any compression decoder (no `gzip`/`deflate`); each decoder operates in O(n) on bounded input. Cost: ~100 µs per inbound POST. | None; documented residual. |

### 4.6 Elevation of privilege

| ID | Threat | L | I | Sev | Controls (existing) | Gap / required action |
|---|---|---|---|---|---|---|
| TM-E-001 | App-layer authz bypass (none in v1 → no bypass surface). | n/a | n/a | n/a | A-007. | Phase 7 (when auth lands). |
| TM-E-002 | DB connection used to perform admin operations outside the service. | 1 | 5 | 5 (Low) | Least-privilege role; `DELETE` and `DROP` revoked; migrations under a separate role used by Flyway, not by the runtime. | Phase 7 codifies the role split. |
| TM-E-003 | Container escape via known CVE in the base image. | 1 | 5 | 5 (Low) | Distroless option recorded; Temurin base scanned in CI. | Phase 7: SLA on critical CVEs; Phase 8 re-attacks the Temurin choice. |
| TM-E-004 | Privileged access to secrets backend exfiltrates the HMAC key. | 1 | 4 | 4 (Low) | Least-privilege IAM on the secrets backend; rotation policy. | Phase 7. |
| TM-E-005 | Privileged access to the audit-log sink edits records. | 1 | 4 | 4 (Low) | WORM / append-only destination; access audited. | Phase 7. |

## 5. Abuse cases

| ID | Abuse case | What we expect to happen | Cross-link |
|---|---|---|---|
| AB-001 | Submit `description="4242 4242 4242 4242"` (Luhn-valid card number). | `400 PAN_PATTERN_DETECTED` with `details.reason="luhn"`; audit event; payload not logged. | AC-010b |
| AB-002 | Submit `description="%B4242424242424242^FOO/BAR^25011010000000000000000?"` (track-1 shape). | `400 PAN_PATTERN_DETECTED` with `details.reason="track1"`; audit event. | AC-010c |
| AB-003 | Submit `description="NDI0MiAyNDIyIDQyNDIgNDI0Mg=="` (base64 of Luhn-valid card). | `400 PAN_PATTERN_DETECTED` with `details.reason="luhn-encoded"`; audit event. | AC-010d |
| AB-004 | Replay `POST /purchases` 1 000× / minute from one client. | Gateway-layer rate-limit kicks in; service remains responsive. Phase 7 adds app-layer defense-in-depth. | TM-D-001 |
| AB-005 | Submit `?currency=' OR 1=1 --` (SQL-injection probe). | `400 INVALID_CURRENCY`. Parameterised queries via Spring Data JPA — no SQL passthrough. | NFR-014 |
| AB-006 | Submit `?currency=<script>alert(1)</script>`. | `400 INVALID_CURRENCY`. RFC-9457 body is JSON; UI rendering is not the service's concern (no UI). | NFR-014 |
| AB-007 | Submit `amountUsd="1e308"` (max-double-shaped numeric). | `400 VALIDATION_ERROR` with `code=FORMAT` or `code=SCALE_EXCEEDED` (rejected by `@PositiveScaleTwo` Bean Validation). | AC-007 / AC-008 |
| AB-008 | Submit `transactionDate="9999-12-31"` (far-future). | `422 FUTURE_DATE`. | AC-006 |
| AB-009 | Concurrent floods of the same `(purchase, currency)` to cause Treasury fanout. | Single-flight gate dedupes; at most one Treasury call (AC-027b/d). | TM-D-002 |
| AB-010 | Concurrent floods of different purchases mapping to the same Treasury quarter. | Single-flight gate dedupes at the quarter granularity; at most one Treasury call per quarter under contention (AC-027e). | G4-P0-1 |
| AB-011 | Replay an old `X-Correlation-Id` from a previous session to confuse log correlation. | Server prefixes a server-instance tag to the client value; the correlation graph distinguishes (Phase-4 G4-P1-13). | observability.md |
| AB-012 | Send mismatched `Content-Type: text/plain`. | `415 Unsupported Media Type`. | AC-T-2 |
| AB-013 | Mass-create purchases with id-enumeration probes (`GET /api/v1/purchases/<random>`). | `400 MALFORMED_IDENTIFIER` for non-UUID/non-ULID shapes; `404 PURCHASE_NOT_FOUND` for well-formed unknown ids. UUID v7 is not enumerable in practice (122 random bits). | AC-013 / AC-027 |
| AB-014 | Multi-encoding chain — `description` = URL-encoded base64 of a Luhn-valid PAN (or hex-of-base64, etc.). | The decoder pipeline runs single-pass per encoding (base64, hex, URL) — **not** recursively. A URL-encoded base64 PAN decodes once via URL-decode to a base64 string, then is checked by Luhn against the base64 string (no match); the base64 layer is not unwound. **Accepted residual at v1; Phase 7 PCI grill re-attacks.** | TM-T-005, AC-010d |

## 6. Trust-boundary controls — summary

| Boundary crossing | Trust direction | Controls in place |
|---|---|---|
| Client → API | Untrusted-in | Bean Validation, content guards (Luhn / track / encoded), bounded length, payload-shape checks, RFC-9457 mapping, no stack traces. |
| API → Application | Trusted | Domain types only; no DTO leakage. |
| Application → Treasury | Untrusted-out for payload, trusted-out for TLS | Bounded timeout, bounded retry, CB, bulkhead, schema validator, sanity bounds, orientation contract check, single-flight gate. |
| Application → DB | Trusted-out | Least-privilege role; parameterised queries; readiness considers pool capacity; transaction templates for the upsert step. |
| Service → Telemetry sinks | Trusted-out | JSON logs with HMAC-hashed `description`; cardinality budget; correlation-id prefix binding. |
| Service → Audit sink | Trusted-out | Append-only / signed / WORM (Phase-7 destination). |
| Platform → Service (secrets) | Trusted-in | Env-only at v1 / mounted-secret pattern in production; refuse-to-start on absence. |

## 7. Residuals accepted at v1

- No app-layer authn (A-007); offset by the trusted-gateway assumption and OQ-010 BLOCKING-for-prod.
- No app-layer rate limiter (G4-P1-15); offset by gateway rate-limiting; Phase 7 to decide if defense-in-depth is added.
- `description.length` side-channel (G4-P2-4) — measured residual; not addressed v1.
- Treasury-orientation contract check is WARN-only at v1 (G4-P1-27); Phase 5 promotes to fail-closed at threshold.
- HMAC key in plain env at v1; Phase 7 chooses the production retrieval pattern (G4-P1-11).
- **Multi-encoding chain bypass** of the encoded-PAN guard (AB-014) — decoders are single-pass, not recursive; Phase 7 PCI grill re-attacks.
- **Decoder pipeline CPU cost** (TM-D-008) — ~100 µs per inbound POST; bounded by 50-char description cap.
- **Alias-table TOCTOU on `WEX_ALIAS_TABLE_PATH` override** (TM-T-009) — mitigated by read-only mount in prod; documented residual on insecure overrides.
- **Tomcat access-log query-string capture** (TM-I-009) — bounded by Bean Validation; Phase 7 pins the access-log redaction policy.
- **Supply-chain dependency compromise** (TM-T-008) — mitigated by version pinning + checksum verification + CI scans; Phase 7 ratifies the SBOM cadence; Phase 13 enables Maven dependency verification.

**Phase-4 mitigations now in force (no longer residuals):**
- Base64 / hex / URL-encoded PAN bypass — closed by AC-010d encoded-input pre-pass (TM-T-005).
- Single-flight gate fragmentation — closed by quarter-end re-keying (G4-P0-1, TM-D-002).
- Single-flight gate holding locks across SIGTERM — closed by release-on-shutdown (G4-P1-20, TM-D-003 reduced).
- `X-Correlation-Id` log-confusion — closed by server-side prefix binding (G4-P1-13, TM-S-002).
- DB connection-URL password leak — closed by env-var split (G4-P1-14, TM-I-004 reduced).
- TLS 1.2 floor — lifted to TLS 1.3 preferred / 1.2 minimum (G4-P1-12, TM-S-003 reduced).
- Hyperinflation-currency rejection by sanity bound — closed by 10⁹ → 10³⁰ widening (G4-P1-3).

## 8. What changes when each later phase lands

- **Phase 5 (Operational Design):** SLO ratification (incl. Treasury-uptime ceiling), CB calibration, alert thresholds, warm-up job spec, rate-orientation contract-check threshold + fail-closed path.
- **Phase 6 (Reliability/Scalability Grill):** capacity-anchor verification; single-flight fairness under load; cache-invalidation policy.
- **Phase 7 (PCI Security Design):** identity origin (OQ-010); HMAC key sourcing; audit-log destination + tamper-evidence mechanism; encoded-PAN-guard implementation; TLS posture finalisation; rate-limiter decision; vulnerability-management SLA. **Phase-4-hardening explicit carry-forwards (this gate's deliverable list):**
  - **SBOM (Software Bill of Materials)** produced on every release artefact, published alongside the container image. Format: CycloneDX or SPDX; cadence: per release. Closes TM-T-008 audit dimension.
  - **SAST tool selection.** Concrete tool pick (SonarQube Cloud, Snyk Code, Semgrep, etc.) + CI integration; severity policy (HIGH blocks build, MEDIUM blocks release).
  - **Container image signing chain.** Cosign / sigstore-style signing on push; verification at admission control / pull. Documented in `secure-config-hardening.md`.
- **Phase 8 (PCI Security Grill):** adversarial re-attack of the above + the Temurin vs Distroless question + Unicode-confusable handling + multi-encoding-chain recursive bypass (AB-014).
- **Phase 11 (PCI Security Readiness Gate):** evidence collection across all the above; explicit pass/fail per control.

## 9. Linked artefacts

- [docs/planning/design-grill.md](../planning/design-grill.md) — Phase-4 adversarial review (this gate's grill record).
- [docs/architecture/adr-0001-core-architecture.md](../architecture/adr-0001-core-architecture.md) — security-posture decision (D-13).
- [docs/operations/observability.md](../operations/observability.md) — telemetry contract; correlation-id binding.
- [docs/security/pci-scope-and-cde.md](pci-scope-and-cde.md) — Phase-7 deliverable.
- [docs/security/secrets-policy.md](secrets-policy.md) — Phase-7 deliverable.
- [docs/security/authn-authz-design.md](authn-authz-design.md) — Phase-7 deliverable.
- [docs/security/dependency-risk-policy.md](dependency-risk-policy.md) — Phase-7 deliverable.
