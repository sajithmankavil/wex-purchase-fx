# PCI Security Design Session — Phase 7

> **Status:** COMPLETED — 2026-05-17
> **Verdict:** **READY FOR PHASE 8 — PCI ADVERSARIAL SECURITY GRILL.**
>
> The 12 PCI DSS 4.0.1 requirements are addressed for the service's **out-of-CDE** posture. Four Phase-4/5/6-deferred items are closed in this gate. Concrete tool picks for SAST / image signing / SBOM / audit-log destination are *recommended* with platform-dependent flexibility — the Phase-12 hand-off picks the platform-specific binding.

---

## 1. Scope

PCI DSS 4.0.1 hygiene posture for `wex-purchase-fx`. The service is **out-of-CDE**: no PAN / SAD / CHD by API contract; the only attack surface for accidental CHD ingestion (the `description` field) is governed by a four-layer defense stack (Luhn + track-data + encoded-PAN + payload-redacted audit). All controls in [pci-dss-control-matrix.md](../security/pci-dss-control-matrix.md) are designed; Phase 13 realises them in code; Phase 11 collects evidence.

Out of session scope:
- Implementation work (Phase 13).
- Operational design (Phase 5/6 — done).
- Production deployment cutover (Phase 12).
- QSA-led assessment (Phase 12 / external).

## 2. Inputs

| Input | Status |
|---|---|
| Phase-1..6 artefacts (requirements, architecture, operational design, grills) | Closed; reconciled. |
| `security-profile.yml` — PCI Tier-1 mode | In force from Day-1 ratification (D-1). |
| Phase-2 grill PCI items (G-P0-5, G-P1-4, G-P1-6) | Closed in Phase 4 + Phase 7. |
| Phase-4 grill PCI items (G4-P0-5, G4-P1-1, G4-P1-11, G4-P1-15, G4-P1-16, G4-P1-26) | Closed in Phase 7 (this session). |
| Phase-6 grill items (G6-P1-5 audit-event rate limiter) | Addressed in this session via §3 (rate-limiter decision). |

## 3. Phase-7 design decisions

The four deferred-to-Phase-7 items now have ratified working positions.

### 3.1 D-15: App-layer rate-limiter (closes G4-P1-15)

**Decision: YES — add a Resilience4j RateLimiter at the controller layer; default disabled.**

| Aspect | Value |
|---|---|
| Mechanism | Resilience4j `@RateLimiter` annotation on `PurchaseController` + `ConversionController`; ~50 LOC + config |
| Default | **Disabled** (`WEX_RATE_LIMIT_RPM` unset or 0) — gateway is the primary rate-limit control |
| Production override | Set `WEX_RATE_LIMIT_RPM=600` (10/sec per replica) as defense-in-depth on mis-deploys where the gateway is bypassed |
| Per-client granularity | Per-client-IP via `X-Forwarded-For` (or platform-equivalent) when gateway-passed; otherwise per-process |
| Burst tolerance | 2× the limit for 5 s, then 429 |
| Response when limit hit | `429 Too Many Requests` with RFC 9457 envelope; `Retry-After` header |

**Rationale:** the gateway rate-limits primary. A mis-deployed service without a gateway is a credible operational mistake (already documented as TM-S-001 residual). An always-disabled-by-default in-app limiter is ~50 LOC of insurance.

### 3.2 D-16: HMAC log-hash key sourcing in production (closes G4-P1-11)

**Decision: mounted secret file via CSI driver (preferred) or Vault Agent sidecar (alternative); plain env var only in `local`/`test`.**

Detail in [encryption-key-management.md §3.2](../security/encryption-key-management.md#32-key-storage--production-retrieval-pattern-closes-g4-p1-11).

Implementation contract for Phase 13: read from `WEX_LOG_HASH_KEY_PATH` first (default `/var/run/secrets/wex/log-hash-key`); fall back to `WEX_LOG_HASH_KEY` env. Refuse-to-start in `prod`/`staging` if neither.

### 3.3 D-17: Audit-log destination (closes G4-P1-16)

**Decision: append-only / WORM destination required in production; platform-specific pick at Phase 12.**

Acceptable options (any satisfies PCI Req 10.5.2):
- AWS S3 + Object Lock (compliance mode) → Glacier Deep Archive after 90 days
- GCP Cloud Logging + `_Required` immutable log buckets
- Azure Storage + Immutable Blob (legal hold + time-based retention)
- Self-hosted MinIO / immudb with object-lock for on-prem

Detail in [logging-monitoring-pci.md §3](../security/logging-monitoring-pci.md#3-audit-log-destination--phase-7-ratified). Required properties: append-only, signed/hash-chained, ≥ 1-year retention, 3 months online, access-of-audit logged separately.

### 3.4 D-18: Vulnerability-management cadence + SLA (closes G4-P1-26)

**Decision:**

| CVSS severity | SLA (CVE-disclosure → patched production deploy) |
|---|---|
| CRITICAL (≥ 9.0) | 24 h to staging; 48 h to production |
| HIGH (7.0–8.9) | 7 days |
| MEDIUM (4.0–6.9) | 30 days |
| LOW (< 4.0) | Next quarterly batch |

Detail in [vulnerability-management-pci.md §3](../security/vulnerability-management-pci.md#3-remediation-sla). Exception process documented; re-scoring against service context allowed with SecArch sign-off.

### 3.5 D-19: Encoded-PAN guard implementation direction (closes G4-P0-5)

Phase-4 pinned the direction; Phase 7 confirms the implementation contract:

```
ContentGuard.check(description):
  candidates = [ description ]
  for decoder in [ base64-standard, base64-urlsafe, hex, urlEncoded ]:
      try:
          decoded = decoder.tryDecode(description)
          if decoded is not None and decoded != description:
              candidates.append(decoded)
      except DecoderException: pass
  for candidate in candidates:
      if matchesLuhn(candidate):  reject "luhn" (or "luhn-encoded" if candidate != description)
      if matchesTrack1(candidate): reject "track1"
      if matchesTrack2(candidate): reject "track2"
```

Implementation in Phase 13. AC-010d in [acceptance-criteria.md](../requirements/acceptance-criteria.md). The decoder pipeline is **single-pass** (no recursive decoding) — multi-encoding chains (AB-014) are an accepted residual that Phase-8 PCI grill re-attacks.

### 3.6 D-20: PMD / Checkstyle / Spotless ruleset for logging hygiene (closes G4-P1-1)

**Decision:** ruleset specified at the policy level here; concrete `pmd-ruleset.xml` / `checkstyle.xml` lands in Phase 13.

Rules:
1. **No log statement may pass `description` directly as an argument.** Pattern: `log\..*\(.*description.*\)` → fail.
2. **Logging hygiene applies to: SLF4J / Logback / java.util.logging.** All three.
3. **Exception messages may include `description` only if HMAC-hashed first.** Pattern: `throw new .*Exception\(.*description.*\)` → warn; require explicit `DescriptionHasher.hash(description)` call.

Integration-level enforcement remains `LoggingPiiGuardTest` (AC-032).

## 4. Architecture options considered (Phase-7 perspective)

The architecture is set (ADR-0001 + Phase-4/6 refinements). Phase 7 considered five PCI-specific options:

| Option | Description | Pros | Cons | Decision |
|---|---|---|---|---|
| **Defense stack as detection-and-alert** (chosen) | Content guards reject + emit audit events + monitor for spikes; primary control is contract-level | Aligns to PCI-conscious threat model; provides QSA-defensible evidence | Audit-log destination becomes load-bearing | **Accepted (D-13 / Phase 4; re-ratified Phase 7).** |
| Defense stack as fail-open with monitoring | Reject, but rely on monitoring to catch | Lower false-positive cost | Accepts CHD ingestion as a possibility | Rejected. Out-of-CDE claim breaks. |
| Bring service into-CDE | Accept CHD; apply Req 3/4 controls | Maximum flexibility for future features | Massive control + audit overhead; not needed for source rule | Rejected. |
| Tokenisation via payment provider | Use a hosted-fields provider | SAQ-A; scope reduction | No CHD in scope; nothing to tokenise | Rejected. Documented as forward option in [tokenization-and-pan-handling.md](../security/tokenization-and-pan-handling.md). |
| Encrypt `description` at rest | Treat `description` as quasi-CHD | Defense in depth | The contract says no CHD; encryption is no substitute for the boundary guards | Rejected. Boundary guards + HMAC-only logging already address the residual. |

## 5. Selected security posture (one-paragraph summary)

`wex-purchase-fx` is **out of PCI CDE**. The primary control is the API contract's prohibition on payment data ([pci-scope-and-cde.md](../security/pci-scope-and-cde.md)); the schema cannot store PAN-shaped data; the boundary content-guard stack (Luhn + track + encoded-PAN, with payload-redacted audit) detects and rejects accidental CHD ingestion. The `description` field is logged only as length + HMAC-SHA-256 digest with a `vN:` version prefix; the HMAC key is loaded from a CSI-mounted secret file (preferred) or Vault sidecar in production. TLS 1.3 preferred / 1.2 minimum end-to-end. Audit logs flow to an append-only WORM destination with ≥ 1 year retention; the audit-of-audit destination is separately controlled. Identity is established at the upstream gateway in production (OQ-010 BLOCKING-for-prod). Vulnerability management runs CRITICAL-in-24-h-to-staging / HIGH-in-7-days through OWASP Dependency-Check + Snyk + Trivy. Twelve PCI DSS Req 1–12 controls are designed; evidence collection cadences are recorded in [evidence-register.md](../security/evidence-register.md); the Phase-8 PCI grill adversarially attacks every claim.

## 6. Key design decisions (Phase 7 only)

| Decision | Rationale | ADR/doc | Risk addressed |
|---|---|---|---|
| **D-15 App-layer rate-limiter YES (default disabled; env-enabled)** | Defense in depth; gateway is primary; ~50 LOC insurance for mis-deploys | [pci-dss-control-matrix.md](../security/pci-dss-control-matrix.md) Req 6 / [logging-monitoring-pci.md](../security/logging-monitoring-pci.md) (audit-event rate limiter applies the same Resilience4j primitive) | TM-D-001, G4-P1-15, G6-P1-5 |
| **D-16 HMAC key from CSI-mounted file** | Lower leak surface vs env var | [encryption-key-management.md §3.2](../security/encryption-key-management.md#32-key-storage--production-retrieval-pattern-closes-g4-p1-11) | G4-P1-11, R-023 |
| **D-17 Audit-log destination = WORM/append-only/signed (platform-dependent)** | PCI Req 10.5.2 | [logging-monitoring-pci.md §3](../security/logging-monitoring-pci.md) | G4-P1-16 |
| **D-18 Vulnerability-management SLA: 24h C / 7d H / 30d M / quarterly L** | PCI Req 6.3.3 industry-standard | [vulnerability-management-pci.md §3](../security/vulnerability-management-pci.md#3-remediation-sla) | G4-P1-26 |
| **D-19 Encoded-PAN decoder pipeline contract** | Closes the base64/hex/URL-encoded PAN bypass at the boundary | [pci-security-design-session §3.5](#35-d-19-encoded-pan-guard-implementation-direction-closes-g4-p0-5); AC-010d | G4-P0-5, R-031, TM-T-005 |
| **D-20 PMD/Checkstyle ruleset (policy)** | Closes G4-P1-1 at policy level; Phase 13 implements | [secure-sdlc-pci.md §2](../security/secure-sdlc-pci.md#2-secure-coding-requirements-phase-7-ratified) | G4-P1-1 |

## 7. Security design (cross-reference)

The full security posture is documented across the 13 Phase-7 documents in `docs/security/`. This session is the meta document.

- **Authentication:** None at app layer v1 (A-007); gateway in production (OQ-010 BLOCKING-for-prod).
- **Authorisation:** None at app layer v1; gateway in production.
- **Secrets management:** Env / CSI-mounted file; refuse-to-start invariants; rotation procedure.
- **Data protection:** TLS 1.3 / 1.2; HMAC for description; no CHD by design.
- **Audit logging:** WORM destination, 1-year retention, time-sync, access-of-audit logged.
- **Abuse controls:** Gateway WAF + rate-limit + app-layer rate-limiter (defense in depth).

## 8. Operational implications (cross-reference)

- Adds: 1 new env var (`WEX_LOG_HASH_KEY_PATH`); 1 new env var (`WEX_RATE_LIMIT_RPM`); new audit-log destination configuration in `prod`/`staging`.
- Modifies: alert routing for A-021 / A-025 to flow to SecArch on PCI-relevant rejections.
- Adds: 4 new CI gates per release (SAST, SCA, container scan, image signing); 2 quarterly + 1 annual external (ASV, internal vuln scan, pen-test).

## 9. Failure modes added in Phase 7

| Mode | Detection | Mitigation | Test |
|---|---|---|---|
| HMAC key file unreadable (CSI mount fails) | Refuse-to-start; A-026 fires | Same as missing env: container restart loop; SecArch investigates secrets store | Phase 13 startup test |
| Rate-limiter triggers (gateway bypass or attack) | 429 rate counter | Documented behaviour; clients retry with backoff | Phase 13 |
| Audit-destination outage | Audit sink lag metric (platform) | Buffered emission; alert | Phase 13 + provider drill |

## 10. Open design questions (carried into Phase 8)

| ID | Question | Status |
|---|---|---|
| OQ-010 | Identity origin for production | Carried to Phase 12; documented direction at [access-control-pci.md §5](../security/access-control-pci.md#5-application-layer-access-the-oq-010-closure-direction) |
| OQ-011 | PAN-pattern guard policy (reject/mask/warn) | **Closed by Phase-4 + Phase-7:** reject; no allow-list v1; encoded-PAN pre-pass; defense-in-depth framing. |
| OQ-019 | HMAC key sourcing | **Closed Phase 7 (D-16)** |
| OQ-022 | Audit-log retention/integrity | **Closed Phase 7 (D-17)** |
| (new) | Unicode confusable normalisation for content guards | Carried to Phase 8 |
| (new) | Multi-encoding chain (URL-encoded base64) — accepted residual at v1 | Carried to Phase 8 (re-attack) |

## 11. Exit-criteria checklist

| Criterion | Status |
|---|---|
| 12 PCI DSS requirements addressed in `pci-dss-control-matrix.md` | ✅ |
| Scope-and-CDE document records the out-of-CDE claim + segmentation evidence | ✅ |
| Cardholder-data-flow shows no CHD; cardholder-data-classification inventories all data | ✅ |
| Tokenisation / PAN-handling explicit non-position recorded | ✅ |
| Encryption + key management spec (closes G4-P1-11) | ✅ |
| Network segmentation documented (out-of-CDE; default-deny posture) | ✅ |
| Access control + role matrix (OQ-010 direction noted) | ✅ |
| Logging + monitoring (closes G4-P1-16 audit destination) | ✅ |
| Secure SDLC (closes G4-P1-1 PMD ruleset policy; Phase-13 implementation note) | ✅ |
| Vulnerability management (closes G4-P1-26 cadence + SLA) | ✅ |
| Change control | ✅ |
| TPSP responsibility matrix (Treasury + cloud + GitHub + pen-test + ASV + QSA) | ✅ |
| Evidence register (EVD-001..012) | ✅ |
| Phase-7 design decisions D-15..D-20 ratified | ✅ |
| Source-requirements.md untouched | ✅ |
| `.human-approvals/` untouched | ✅ |
| No implementation files created | ✅ |
| Verdict recorded | ✅ |

## 12. Verdict

**READY FOR PHASE 8 — PCI ADVERSARIAL SECURITY GRILL.**

Phase 8 attacks the design from the QSA / red-team / privacy-engineer / auditor perspective. Specific things the grill should push on:

1. The out-of-CDE claim — every attack vector for accidental CHD ingestion.
2. The encoded-PAN pre-pass — multi-encoding chains, Unicode confusables, length-cap edge cases.
3. The HMAC-only logging — what happens with short / low-entropy descriptions; key-version-prefix correlation.
4. The audit-log destination — what if the WORM destination itself is compromised.
5. The identity-at-gateway approach — what if the gateway is mis-configured.
6. The TPSP responsibility model — what does Treasury actually attest to, given they don't publish an AOC.

Phase 8 begins on explicit "proceed to Phase 8" approval per the D-2 pause cadence.
