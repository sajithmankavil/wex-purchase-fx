# Infrastructure + Integration artefacts

Everything a consumer needs to integrate with the WEX Purchase FX API, ordered from quickest-to-use to most-thorough.

## At a glance

| Artefact | Path | Use when |
|---|---|---|
| **OpenAPI 3.1 contract** (committed baseline) | [`openapi/baseline.yaml`](openapi/baseline.yaml) | You want the canonical, regression-checked source of truth. CI diffs this against the live spec on every PR. |
| **OpenAPI live spec** (running service) | `http://localhost:8080/v3/api-docs.yaml` | You want the spec as the running service describes itself (springdoc-generated). |
| **Swagger UI** (running service) | `http://localhost:8080/swagger-ui.html` | You want an interactive browser UI to poke the endpoints by hand. |
| **Postman collection** (universal) | [`postman/wex-purchase-fx.postman_collection.json`](postman/wex-purchase-fx.postman_collection.json) | One-click import into Postman / Insomnia / VS Code REST Client. Includes happy-path + error-case requests with pre-baked test assertions. |
| **Bruno collection** (git-friendly modern) | [`bruno/wex-purchase-fx/`](bruno/wex-purchase-fx/) | You prefer plain-text request files that diff cleanly in git (no proprietary JSON blob, no cloud sync). |
| **Grafana dashboard templates** | [`dashboards/`](dashboards/) | You're wiring the service into your observability stack. Three pre-built dashboards: SLO availability, SLO latency, Treasury dependency. |
| **SDK generation** (any language) | This doc, §3 below | You want a typed client in TypeScript / Java / Python / Go / Rust / etc. — generated directly from the OpenAPI spec. |

## 1. Postman

1. Open Postman → `Import` → drop in `infra/postman/wex-purchase-fx.postman_collection.json`.
2. Boot the service locally: `mvn spring-boot:run -Dspring-boot.run.profiles=local` (or change the `{{baseUrl}}` collection variable to point elsewhere).
3. Run the requests in order: `1. Create a purchase` → the test script captures the returned `id` into `{{purchaseId}}` → `2. Retrieve` and `3. Convert` reuse it.

The collection also includes three explicit **error cases** that demonstrate the boundary controls described in the brief:
- description with PAN-shaped substring → `400 PAN_PATTERN_DETECTED` (boundary `ContentGuard`)
- non-UUID-v7 id → `400 MALFORMED_IDENTIFIER` with HMAC-hashed input + redacted `instance` URI
- unknown currency / no rate in window → `422 CONVERSION_RATE_NOT_AVAILABLE`

Postman Newman CLI works too: `newman run infra/postman/wex-purchase-fx.postman_collection.json --env-var baseUrl=http://localhost:8080`.

## 2. Bruno

Bruno is a modern, fully open-source, **git-native** alternative to Postman. Request files are plain text (`*.bru` files) that diff well, review well, and need no cloud sync.

1. Install Bruno from <https://www.usebruno.com/> (or `brew install bruno`).
2. Open Bruno → `Open Collection` → point at `infra/bruno/wex-purchase-fx/`.
3. Select the **`local`** environment (top-right) and run the requests.

Headless run (in CI or scripts):

```bash
npm install -g @usebruno/cli
bru run infra/bruno/wex-purchase-fx --env local
```

The collection structure:

```text
infra/bruno/wex-purchase-fx/
├── bruno.json                          # collection metadata
├── environments/
│   └── local.bru                       # baseUrl + purchaseId vars
├── 01-create-purchase.bru              # happy path, sequenced
├── 02-retrieve-purchase.bru            # happy path, sequenced
├── 03-convert-purchase.bru             # happy path, sequenced
└── errors/
    ├── 01-pan-shape-rejected.bru       # 400 PAN_PATTERN_DETECTED
    ├── 02-malformed-id-hashed.bru      # 400 MALFORMED_IDENTIFIER (redacted)
    └── 03-no-rate-in-window.bru        # 422 CONVERSION_RATE_NOT_AVAILABLE
```

## 3. SDK generation (any language) via `openapi-generator`

The [`openapi/baseline.yaml`](openapi/baseline.yaml) spec is fully descriptive — every endpoint, every error code, every field's type + example. Run [openapi-generator](https://openapi-generator.tech/) against it to produce a typed client in any of ~50 supported languages.

```bash
# One-time install (Java 11+ required for the CLI jar).
npm install -g @openapitools/openapi-generator-cli

# Generate a TypeScript fetch client (clean, lightweight; no axios dep).
openapi-generator-cli generate \
  -i infra/openapi/baseline.yaml \
  -g typescript-fetch \
  -o build/clients/typescript \
  --additional-properties=npmName=@wex/purchase-fx-client,supportsES6=true

# Or a Java client (OkHttp-based; works in any JDK 11+ consumer).
openapi-generator-cli generate \
  -i infra/openapi/baseline.yaml \
  -g java \
  -o build/clients/java \
  --additional-properties=library=okhttp-gson,artifactId=wex-purchase-fx-client

# Or Python (httpx-based).
openapi-generator-cli generate \
  -i infra/openapi/baseline.yaml \
  -g python \
  -o build/clients/python \
  --additional-properties=packageName=wex_purchase_fx_client

# Or Go (net/http).
openapi-generator-cli generate \
  -i infra/openapi/baseline.yaml \
  -g go \
  -o build/clients/go \
  --additional-properties=packageName=wexpurchasefx

# Or Rust (reqwest).
openapi-generator-cli generate \
  -i infra/openapi/baseline.yaml \
  -g rust \
  -o build/clients/rust \
  --additional-properties=packageName=wex-purchase-fx-client
```

The generated clients respect the spec's `string`-typed `BigDecimal` fields (`amountUsd`, `exchangeRate`, `convertedAmount`) so consumers get exact-scale arithmetic in their own language (`java.math.BigDecimal` / `decimal.Decimal` / `string` with downstream parsing) — no silent IEEE-754 float drift.

## 4. Live API discovery

When the service runs:

| URL | Content |
|---|---|
| `http://localhost:8080/swagger-ui.html` | Interactive Swagger UI — try requests from the browser. |
| `http://localhost:8080/v3/api-docs` | OpenAPI 3.1 JSON. |
| `http://localhost:8080/v3/api-docs.yaml` | OpenAPI 3.1 YAML — what CI diffs against `infra/openapi/baseline.yaml`. |
| `http://localhost:8080/actuator/health` | Aggregated health (db-pool headroom + gateway-required indicators). Returns `UP` / `DOWN`. |
| `http://localhost:8080/actuator/info` | Build info + git SHA. |

## 5. CI integration gates

The committed `infra/openapi/baseline.yaml` is the **regression contract**. On every PR, [`.github/workflows/ci.yml`](../.github/workflows/ci.yml):

1. Generates the live OAS from a transient `mvn spring-boot:start` instance.
2. Diffs it against the baseline via `oasdiff` (or [`scripts/quality/oas_path_diff.py`](../scripts/quality/oas_path_diff.py) as a structural fallback when `oasdiff` isn't on the runner).
3. Runs Spectral lint against the baseline using [`.spectral.yaml`](../.spectral.yaml) rules — including a custom `wex-no-pii-field-names` rule that rejects schemas declaring `pan` / `cvv` / `track1|2` / `password` / `secret` / `ssn` / `cardnumber` as field names.

Intentional API changes need an ADR (`docs/architecture/adr-XXXX-*.md`) and a fresh `baseline.yaml` commit; otherwise CI fails closed.

## 6. Future infrastructure-as-code

Production deploy infrastructure (Terraform / Helm / etc.) is **out of scope for this case study** — the brief asks for a service deployable without separately installed databases or servlet containers, and Phase 12 (production approval) is the demonstrative-ceremony gate. A real engagement would add:

```text
infra/
  terraform/                            # cloud resources (VPC, RDS, IAM, etc.)
    modules/
    envs/{dev,staging,prod}/
  helm/                                 # Kubernetes manifests
  docker/                               # Dockerfile + buildx config
```

The deploy workflows at [`.github/workflows/deploy-{dev,staging,prod}.yml`](../.github/workflows/) are marker-gated skeletons today; they wire the Phase 12 multi-party signature procedure documented in [`docs/security/change-control-pci.md §1`](../docs/security/change-control-pci.md).

## 7. What's NOT here (and why)

- **API keys / dev portal** — the brief doesn't require authentication; Req 8 (PCI DSS Identity) is explicitly tracked as `BLOCKING-for-prod` (gateway-bound, see [`docs/security/pci-dss-control-mapping.md`](../docs/security/pci-dss-control-mapping.md)). No client credentialing surface to publish today.
- **Webhooks / async events** — not in the brief; the service is request/response only.
- **Rate-limit headers** (`X-RateLimit-Remaining`) — `WexRateLimiterFilter` issues 429 with `Retry-After` but doesn't surface budget hints (that would be a separate consumer-API design exercise).
- **HATEOAS link relations** — the response shape is flat per the brief's "include the identifier, the description, the transaction date, the original US dollar purchase amount, the exchange rate used, and the converted amount" requirement; no hypermedia surface added.
