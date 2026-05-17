# Deployment Architecture

> Operational view: how the service is packaged, configured, run, scaled, observed, and rolled back. Two deployment modes are defined — **case-study local** and **production reference** — with shared invariants.
> **Status:** Phase 3 (Architecture & Design Session), 2026-05-17.
> **Phase-4 grill refinements (2026-05-17):** DB connection env split into discrete vars; TLS floor lifted; new vars for retry-after default. See [design-grill.md](../planning/design-grill.md) §6.

---

## 1. Environments

| Environment | Purpose | Data | Deployment trigger | Approval |
|---|---|---|---|---|
| `local` | Developer / case-study reviewer execution. Default profile. | H2 file mode (synthetic). | Manual `./mvnw spring-boot:run` or `java -jar`. | None. |
| `test` | CI test execution. | H2 in-memory per JVM. | CI on every push. | None; CI rules apply. |
| `dev` (production reference) | Integration sandbox for the production deployment. | PostgreSQL, non-production data. | Merge to `main`. | Automated. |
| `staging` (production reference) | Pre-prod validation. | PostgreSQL, production-like non-prod data. | Manual promote. | Required. |
| `prod` (production reference) | Customer/internal traffic. | PostgreSQL, production data. | Manual promote. | Required (gate + change-control). |

The case study only requires `local` and `test`. `dev` / `staging` / `prod` are the **production reference** the architecture must stay credible against.

---

## 2. Packaging

### 2.1 Case-study artefact: single executable jar

```
target/wex-purchase-fx-<version>.jar
```

Built by `./mvnw -DskipTests=false clean verify`. Contains:

- Spring Boot 3.x application with embedded Tomcat.
- Embedded H2 driver and dialect support.
- Flyway with engine-agnostic migrations under `db/migration/`.
- Resilience4j, springdoc-openapi, Micrometer, OpenTelemetry SDK.
- `currency-aliases.json` on the classpath (read at startup; readiness fails if missing/parse-error).

Run:

```
java -jar target/wex-purchase-fx-<version>.jar
```

The application listens on `0.0.0.0:8080` by default (configurable via `SERVER_PORT`). No external dependencies need installing; the H2 file lands at `${WEX_DATA_DIR}/wex.mv.db` (default `${user.home}/.wex-purchase-fx/data`).

### 2.2 Production artefact: container image

```
ghcr.io/<org>/wex-purchase-fx:<version>
```

Base: `eclipse-temurin:21-jre-jammy`. Distroless (`gcr.io/distroless/java21-debian12`) was considered (ADR-0001 Options) but Temurin is chosen for v1 to keep `jcmd` / `jstack` available for in-place diagnostics.

Image properties:

- Non-root user `wex` (UID 10001), no shell.
- Workdir `/app`. The application jar at `/app/wex-purchase-fx.jar`.
- HEALTHCHECK invokes `curl -fsS http://127.0.0.1:8080/actuator/health/liveness || exit 1` every 30 s.
- `EXPOSE 8080`.
- ENV defaults to `prod` profile.

Build chain: `./mvnw spring-boot:build-image` (Cloud Native Buildpacks) → image is reproducible across architectures (amd64 + arm64).

Image scan in CI: Trivy or Grype for OS-level CVEs; Snyk or `dependency-check` for Java deps (NFR-011..014). Fails the build on HIGH/CRITICAL findings without an approved exception.

---

## 3. Case-study local deployment

```
    Developer machine (stock JDK 21)
    ┌─────────────────────────────────────────────────────┐
    │                                                     │
    │   ./mvnw spring-boot:run                            │
    │   (or java -jar target/wex-purchase-fx-*.jar)       │
    │                                                     │
    │   ┌─────────────────────────────────────────────┐   │
    │   │  wex-purchase-fx (one process)              │   │
    │   │  • Embedded Tomcat on 8080                  │   │
    │   │  • H2 file mode at WEX_DATA_DIR             │   │
    │   │  • Outbound HTTPS to api.fiscaldata.        │   │
    │   │    treasury.gov                             │   │
    │   └─────────────────────────────────────────────┘   │
    │              │                       │              │
    │              ▼                       ▼              │
    │   ${WEX_DATA_DIR}/wex.mv.db   stdout (JSON)        │
    │   (default: ~/.wex-purchase-fx/data)               │
    │                                                     │
    └─────────────────────────────────────────────────────┘
                       │  HTTPS
                       ▼
              Internet (Treasury Fiscal Data API)
```

Properties:

- **One command to start.** No external DB, web server, or servlet container required (NFR-028).
- **Durable across restart.** AC-010 asserts the H2 file persists rows; the smoke test confirms.
- **`WEX_DATA_DIR` defaults off cloud-sync drives.** Startup WARNs if the resolved path is under `OneDrive`, `Dropbox`, or `iCloud Drive` (D-14; closes G-P1-1 / R-022).
- **Telemetry to stdout.** JSON logs; metrics on `/actuator/prometheus`. No external sinks in `local`.
- **Treasury reachable.** Anonymous fetch over public TLS; no API key.

---

## 4. Production-reference deployment

```
              ┌────────────────────────────────────────────────────────────────────────────┐
              │                          Customer / internal traffic                       │
              └──────────────────────────────┬─────────────────────────────────────────────┘
                                             │ HTTPS (TLS 1.2+)
                                             ▼
                            ┌────────────────────────────────────┐
                            │   Ingress / API gateway            │
                            │   • TLS termination, mTLS optional │
                            │   • Identity establishment         │
                            │     (OQ-010: OIDC/JWT/SPIFFE)      │
                            │   • Rate limiting, WAF             │
                            └──────────────────┬─────────────────┘
                                               │
                                               ▼
                            ┌────────────────────────────────────┐
                            │   Internal LB (L7)                 │
                            │   • Round-robin across replicas    │
                            │   • Reads readiness probe          │
                            └──────────────────┬─────────────────┘
                                               │
       ┌───────────────────────────────────────┼───────────────────────────────────────┐
       │                                       │                                       │
       ▼                                       ▼                                       ▼
┌──────────────────┐                  ┌──────────────────┐                  ┌──────────────────┐
│  wex-purchase-fx │                  │  wex-purchase-fx │                  │  wex-purchase-fx │
│  replica #1      │                  │  replica #2      │   …              │  replica #N      │
│  (Temurin JRE 21,│                  │                  │                  │                  │
│   non-root)      │                  │                  │                  │                  │
└────────┬─────────┘                  └────────┬─────────┘                  └────────┬─────────┘
         │                                     │                                     │
         │                                     │                                     │
         │  ┌──────────────────────────────────┼─────────────────────────────────────┘
         │  │                                  │
         ▼  ▼                                  ▼
   ┌──────────────────┐               ┌──────────────────┐               ┌────────────────────────┐
   │   PostgreSQL     │               │   Egress proxy   │ ───HTTPS───▶  │  Treasury Fiscal Data  │
   │   (managed; PITR │               │   (mTLS to       │               │  API                   │
   │    + snapshots)  │               │   gateway out)   │               └────────────────────────┘
   └──────────────────┘               └──────────────────┘
         │
         │
         ▼
   ┌──────────────────┐               ┌──────────────────┐               ┌──────────────────┐
   │  Secrets         │               │  Metrics scrape  │               │  Log sink        │
   │  (WEX_LOG_HASH_  │               │  (Prometheus →   │               │  (JSON → Loki /  │
   │   KEY, DB creds) │               │   Grafana)       │               │   ELK)           │
   └──────────────────┘               └──────────────────┘               └──────────────────┘
                                               │
                                               ▼
                                     ┌──────────────────┐
                                     │  OTel collector  │
                                     │  → Tempo/Jaeger  │
                                     └──────────────────┘
```

### 4.1 Replica sizing (capacity reference; ratified in Phase 5)

- v1 target: **2 replicas minimum** in `prod` for HA (one can fail without dropping traffic). 4 replicas in `staging` would be wasteful; 1 replica is sufficient.
- Per-replica capacity: **100 req/s mixed read/write** at p99 NFR-001/002/003 targets (proposed; NFR-008).
- Memory: 512 MiB heap + ~200 MiB overhead (RestClient pools, Caffeine, OTel) → request 1 GiB, limit 1.5 GiB.
- CPU: 0.5 vCPU request, 1.0 vCPU limit. Spring Boot startup is CPU-bound for the first ~5–10 s.

### 4.2 Rolling deploy

- Strategy: **rolling with surge.** maxSurge=1, maxUnavailable=0. New replica must pass readiness before old is removed. Total deploy time scales linearly with replica count.
- Alternative considered: blue-green (cleaner cutover, double cost during rollout). Rejected for v1; reconsider at scale.
- Canary alternative: 5 % traffic to a new image for ≥ 30 min before promote. Reserved for the first prod release after material domain change.

### 4.3 Graceful shutdown

- Spring Boot graceful shutdown enabled (`server.shutdown=graceful`, `spring.lifecycle.timeout-per-shutdown-phase=60s` per Phase-6 G6-P1-4; raised from 30 s).
- On SIGTERM: readiness flips DOWN immediately → LB stops routing new requests; in-flight HTTP drains within 60 s; Treasury bulkhead closes first to halt new outbound calls; single-flight gate releases all locks (Phase-4 G4-P1-20).
- For sustained peak load, canary deploy is preferred over rolling — the 60 s drain may still abandon some in-flight work if Tomcat is saturated.
- Idempotency-Key (when implemented; OQ-009 BLOCKING-for-prod) makes retries safe.

---

## 5. Configuration (12-factor)

All configuration via env vars. The application has **no** required CLI flags.

| Env var | Profile | Required | Default | Purpose |
|---|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | all | no | `local` | Profile selection. |
| `SERVER_PORT` | all | no | `8080` | HTTP listen port. |
| `WEX_DATA_DIR` | `local`, `test` | no | `${user.home}/.wex-purchase-fx/data` | H2 file directory. Startup WARNs on cloud-sync prefix. |
| `WEX_DB_HOST` | `dev`/`staging`/`prod` | yes | — | DB host (Phase-4 G4-P1-14; replaces the single `WEX_DB_URL` to keep credentials out of any URL that might be echoed in debug dumps). |
| `WEX_DB_PORT` | `dev`/`staging`/`prod` | no | `5432` | DB port. |
| `WEX_DB_NAME` | `dev`/`staging`/`prod` | yes | — | Database name. |
| `WEX_DB_USERNAME` | `dev`/`staging`/`prod` | yes | — | DB user. Least-privilege role. |
| `WEX_DB_PASSWORD` | `dev`/`staging`/`prod` | yes (from secrets) | — | DB password. Loaded from platform secrets store; never echoed, never logged. |
| `WEX_RETRY_AFTER_SECONDS` | all | no | `300` (Phase-4 G4-P1-18) | Default `Retry-After` value on `503 UPSTREAM_UNAVAILABLE`. |
| `WEX_LOG_HASH_KEY` | `prod`, `staging` | **yes (refuse-to-start)** | — | HMAC-SHA-256 key for `description.hash`. `vN:` version-prefix on output. |
| `WEX_LOG_HASH_KEY_VERSION` | all | no | `v0` (local/test); `v1` (prod/staging) | Key version tag carried in the digest output. |
| `WEX_TREASURY_BASE_URL` | all | no | `https://api.fiscaldata.treasury.gov` | Treasury endpoint base. |
| `WEX_TREASURY_TIMEOUT_MS` | all | no | `2000` | Per-request timeout (connect+read). |
| `WEX_TREASURY_RETRY_MAX` | all | no | `3` | Max retry attempts. |
| `WEX_TREASURY_CB_THRESHOLD` | all | no | `0.5` | Circuit breaker failure threshold (50 %). |
| `WEX_TREASURY_CB_WAIT_MS` | all | no | `30000` | Circuit-open wait. |
| `WEX_TREASURY_USER_AGENT` | all | no | `wex-purchase-fx/<version> (contact:<email>)` | Treasury best-practice identifier. |
| `WEX_CACHE_TTL_HOURS` | all | no | `24` (default), `168` (recommended prod) | Hot-cache TTL. |
| `WEX_CORS_ALLOWED_ORIGINS` | `dev`/`staging`/`prod` | no | unset (no CORS) | Comma-separated origin allow-list; wildcards unsupported. |
| `WEX_ALIAS_TABLE_PATH` | all | no | classpath:/currency-aliases.json | Override for the alias table location. |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `dev`/`staging`/`prod` | no | unset | OpenTelemetry collector endpoint. |
| `OTEL_SERVICE_NAME` | all | no | `wex-purchase-fx` | OTel service name attribute. |
| `MANAGEMENT_PORT` | all | no | `8081` | Actuator port (production deployments separate the management port from the request port). |

Notes:
- **Refuse-to-start.** Missing required env in `prod` or `staging` causes the application context to fail to start. Container restart loops; alert fires.
- **Secrets via env, never via CLI flags.** NFR-013/026.
- **No secrets in source.** Repository has `.env.example` only.

---

## 6. Secrets

| Secret | Source | Rotation |
|---|---|---|
| `WEX_LOG_HASH_KEY` | Platform secrets provider (Vault / cloud KMS). | Quarterly or on suspected compromise. Rotation produces a new `vN:` tag in digest output; pre-rotation digests remain correlatable by tag. |
| `WEX_DB_PASSWORD` | Platform secrets provider. | Per platform policy. |
| TLS certificates (ingress) | Platform / ACME. | 90-day cert rotation (Let's Encrypt-style) or platform default. |
| Container-image signing keys | Platform. | Per platform policy. |
| Treasury client | No secret needed; anonymous. | n/a |

Audit:
- Secret reads at startup are logged with the key version (never the value).
- Repository scans (`gitleaks`, `trufflehog`) fail the build on secret-shape matches.

---

## 7. Observability sinks

| Signal | Path | Sink |
|---|---|---|
| Logs (JSON) | stdout | Log aggregator (Loki / ELK / CloudWatch). Retention ≥ 1 yr for audit-event-flagged lines (NFR-016b); ≥ 30 d for everything else. |
| Metrics (Prometheus) | `/actuator/prometheus` on management port | Prometheus scrape every 30 s; Grafana dashboards. |
| Traces (OTLP) | OTLP exporter → collector | Tempo / Jaeger backend. W3C `traceparent` propagated outbound to Treasury. |
| Audit events | flagged via log `event` field (`purchase_validation_failed`, `currency_alias_drift_detected`, …) | Append-only / WORM destination per NFR-016b. |
| Build info | `/actuator/info` | Inspected by oncall and CI. |
| Health | `/actuator/health{,/liveness,/readiness}` | Platform health-check polling. |

Dashboards (named in `docs/operations/monitoring-alerting.md`):
- **Service health.** RED per endpoint, JVM heap, GC pauses, thread-pool saturation.
- **Dependency health.** Treasury client RED, circuit-breaker state, sanity-check rejection rate.
- **Business.** Conversions per minute per currency (top-N), `CONVERSION_RATE_NOT_AVAILABLE` rate, alias-drift events.
- **Rollout health.** Per-version metrics during a rolling deploy.

---

## 8. Health checks

| Probe | Path | What it checks | UP requires |
|---|---|---|---|
| Liveness | `/actuator/health/liveness` | JVM healthy. | `LivenessState=CORRECT`. |
| Readiness | `/actuator/health/readiness` | Service ready to serve. | DB reachable (`SELECT 1`) AND alias table loaded AND hot cache initialised. |
| Aggregated | `/actuator/health` | Combined view. | All sub-indicators UP. |

**Treasury degradation alone does NOT flip readiness DOWN** (FR-001/FR-002 remain serviceable; AC-022 guarantees FR-003 from local cache where eligible rates exist).

---

## 9. Rollback

| Class | Trigger | Action | Validation |
|---|---|---|---|
| Code (app version) | SLO burn-rate breach during rollout; readiness flap; error-rate spike. | `kubectl rollout undo` or platform-native rollback to previous image tag. ≤ 5 min to complete. | Smoke: `POST /purchases`, then `GET /…/conversion`; confirm AC-014/015 happy paths. |
| Migration (schema) | Migration failed in pre-prod *or* prod migration introduces an unexpected lock. | Flyway migrations are forward-only by policy. Backout requires a new forward migration (`V<N+1>__revert_<reason>.sql`). Destructive backouts (drop column) require approved RFC. | Smoke: read-and-write the affected columns. |
| Data | Catastrophic data corruption (e.g., bulk wrong rate persisted via Treasury bug). | Postgres PITR to the latest pre-corruption point. RTO ≤ 30 min (NFR-007). | Smoke + targeted query for the affected `(currency, record_date)` range. |
| Config | New env var introduces a bug (e.g., timeout too low). | Roll back the config in the platform; restart the affected pods. ≤ 2 min. | Smoke + the metric that the config drives. |

Rollback signals (alerts that should trigger one of the above):
- 5xx rate > 1 % over 5 min during a rolling deploy.
- p99 latency > 2× baseline for ≥ 5 min.
- Readiness probe failing on > 1 replica simultaneously.
- `treasury.contract.orientation_drift.count > 0` (G-P0-1 follow-up; signals a Treasury convention flip).

---

## 10. Disaster recovery (production reference)

- **Recovery Point Objective (RPO):** ≤ 5 min for stored purchases. Achieved by Postgres synchronous replication to a secondary in the same region.
- **Recovery Time Objective (RTO):** ≤ 30 min for a single-region restart. Achieved by automated replica replacement.
- **Multi-region:** out-of-scope v1; OQ-012 IMPORTANT for Phase 5 if/when business requires.
- **Backup integrity:** quarterly restore-to-staging drill; failure to validate a restore is a P1 incident.

The case-study local deployment has no DR posture beyond `cp wex.mv.db wex.mv.db.bak`.

---

## 11. Capacity assumptions (informative; ratified in Phase 5)

| Dimension | v1 anchor |
|---|---|
| Steady-state throughput per replica | 100 req/s mixed |
| Peak burst per replica | 250 req/s for ≤ 60 s |
| Cache hit ratio steady-state | ≥ 95 % |
| Treasury upstream RPS | ≤ 1/s steady-state per cluster (single-flight + DB caching) |
| Data growth `purchase_transactions` | up to 10⁷ rows over the design horizon |
| Data growth `exchange_rates` | ~1 600 rows initial; +200/quarter empirically |
| JVM heap | 512 MiB request, 1 GiB limit |
| DB connection pool | 10 connections per replica |

---

## 12. Forward-looking gaps (called out for Phase 4 grill)

- The production-reference diagram assumes an L7 LB inside the cluster. Phase 4 should challenge whether a service-mesh sidecar (Istio / Linkerd) is the better choice for mTLS and identity.
- The egress proxy is shown abstractly. Phase 7 (PCI design) must name it concretely and document the TLS-inspection / SNI policy.
- The container image is Temurin JRE; Distroless is recorded as an Option in ADR-0001 but Phase 4 should re-attack the `jcmd`-availability trade-off.
- Multi-region DR is deferred to OQ-012; Phase 5 must explicitly accept or escalate the deferral.
