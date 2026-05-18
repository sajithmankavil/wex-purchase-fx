# Infrastructure and integration artefacts

Consumer integration assets for the WEX Purchase FX API, ordered by depth of use.

## At a glance

| Artefact | Path | Purpose |
|---|---|---|
| **OpenAPI 3.1 contract** (committed baseline) | [`openapi/baseline.yaml`](openapi/baseline.yaml) | Canonical, regression-checked source of truth. CI diffs the live spec against this file on every PR. |
| **OpenAPI live spec** (running service) | `http://localhost:8080/v3/api-docs.yaml` | The spec as the running service describes itself (springdoc-generated). |
| **Swagger UI** (running service) | `http://localhost:8080/swagger-ui.html` | Interactive browser UI for the endpoints. |
| **Postman collection** | [`postman/wex-purchase-fx.postman_collection.json`](postman/wex-purchase-fx.postman_collection.json) | Importable into Postman, Insomnia, and the VS Code REST Client. Includes happy-path and error-case requests with pre-baked test assertions. |
| **Bruno collection** | [`bruno/wex-purchase-fx/`](bruno/wex-purchase-fx/) | Plain-text `.bru` request files version-controlled alongside the API. No proprietary cloud-sync dependency. |
| **Grafana dashboard templates** | [`dashboards/`](dashboards/) | Three pre-built dashboards: SLO availability, SLO latency, Treasury dependency. Intended for use when wiring the service into an observability stack. |
| **SDK generation** (any language) | §3 below | Procedure for generating a typed client in TypeScript, Java, Python, Go, Rust, or other supported languages, directly from the OpenAPI spec. |

## 1. Postman

1. In Postman, choose `Import` and select `infra/postman/wex-purchase-fx.postman_collection.json`.
2. Start the service locally with `mvn spring-boot:run -Dspring-boot.run.profiles=local`, or set the `{{baseUrl}}` collection variable to a different base URL.
3. Execute the requests in sequence: `1. Create a purchase` returns an `id` captured by the test script into `{{purchaseId}}`; `2. Retrieve` and `3. Convert` consume that variable.

The collection includes three error-case requests that exercise the boundary controls referenced in the brief:

- Description with a PAN-shaped substring → `400 PAN_PATTERN_DETECTED` (boundary `ContentGuard`).
- Non-UUID-v7 identifier → `400 MALFORMED_IDENTIFIER` with HMAC-hashed input and redacted `instance` URI.
- Unknown currency or no rate within the 6-month window → `422 CONVERSION_RATE_NOT_AVAILABLE`.

Headless execution via the Newman CLI:

```bash
newman run infra/postman/wex-purchase-fx.postman_collection.json --env-var baseUrl=http://localhost:8080
```

## 2. Bruno

Bruno is a git-native alternative to Postman. Request definitions are stored as plain-text `*.bru` files version-controlled in the repository; no cloud-sync dependency is required.

1. Install Bruno from <https://www.usebruno.com/> or via `brew install bruno`.
2. In Bruno, choose `Open Collection` and select `infra/bruno/wex-purchase-fx/`.
3. Select the `local` environment in the top-right selector and execute the requests.

Headless execution for CI or scripts:

```bash
npm install -g @usebruno/cli
bru run infra/bruno/wex-purchase-fx --env local
```

Collection structure:

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

## 3. SDK generation via `openapi-generator`

[`openapi/baseline.yaml`](openapi/baseline.yaml) is descriptive across all endpoints, error codes, field types, and examples. [openapi-generator](https://openapi-generator.tech/) produces typed clients in approximately fifty languages from this spec.

```bash
# One-time install (Java 11+ required for the CLI jar).
npm install -g @openapitools/openapi-generator-cli

# TypeScript fetch client (no axios dependency).
openapi-generator-cli generate \
  -i infra/openapi/baseline.yaml \
  -g typescript-fetch \
  -o build/clients/typescript \
  --additional-properties=npmName=@wex/purchase-fx-client,supportsES6=true

# Java client (OkHttp-based; JDK 11+ consumer).
openapi-generator-cli generate \
  -i infra/openapi/baseline.yaml \
  -g java \
  -o build/clients/java \
  --additional-properties=library=okhttp-gson,artifactId=wex-purchase-fx-client

# Python client (httpx-based).
openapi-generator-cli generate \
  -i infra/openapi/baseline.yaml \
  -g python \
  -o build/clients/python \
  --additional-properties=packageName=wex_purchase_fx_client

# Go client (net/http).
openapi-generator-cli generate \
  -i infra/openapi/baseline.yaml \
  -g go \
  -o build/clients/go \
  --additional-properties=packageName=wexpurchasefx

# Rust client (reqwest).
openapi-generator-cli generate \
  -i infra/openapi/baseline.yaml \
  -g rust \
  -o build/clients/rust \
  --additional-properties=packageName=wex-purchase-fx-client
```

The OpenAPI spec types `amountUsd`, `exchangeRate`, and `convertedAmount` as `string`-formatted `BigDecimal` values. Generated clients therefore deserialize these fields into the target language's exact-decimal type (`java.math.BigDecimal`, Python `decimal.Decimal`, or `string` with downstream parsing for Go and Rust), preserving the scale guarantees defined by AC-014 rather than coercing through IEEE-754 floating-point.

## 4. Live API discovery

When the service is running:

| URL | Content |
|---|---|
| `http://localhost:8080/swagger-ui.html` | Interactive Swagger UI for endpoint execution from a browser. |
| `http://localhost:8080/v3/api-docs` | OpenAPI 3.1 JSON. |
| `http://localhost:8080/v3/api-docs.yaml` | OpenAPI 3.1 YAML, diffed by CI against `infra/openapi/baseline.yaml`. |
| `http://localhost:8080/actuator/health` | Aggregated health (database connection-pool headroom and gateway-required indicators). Returns `UP` or `DOWN`. |
| `http://localhost:8080/actuator/info` | Build information and git SHA. |

## 5. CI integration gates

The committed `infra/openapi/baseline.yaml` is the **regression contract**. On every PR, [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) performs the following:

1. Generates the live OpenAPI specification from a transient `mvn spring-boot:start` instance.
2. Diffs the live spec against the baseline using `oasdiff`. When `oasdiff` is not present on the runner, [`scripts/quality/oas_path_diff.py`](../scripts/quality/oas_path_diff.py) provides a structural fallback.
3. Runs Spectral lint against the baseline using [`.spectral.yaml`](../.spectral.yaml) rules, including a custom `wex-no-pii-field-names` rule that rejects schemas declaring `pan`, `cvv`, `track1|2`, `password`, `secret`, `ssn`, or `cardnumber` as field names.

Intentional API changes require an ADR (`docs/architecture/adr-XXXX-*.md`) and a refreshed `baseline.yaml` commit; CI fails closed otherwise.

## 6. Infrastructure-as-code (not in scope)

Production deployment infrastructure (Terraform, Helm) is out of scope for the case study. The brief specifies a service deployable without separately installed databases or servlet containers, and Phase 12 (production approval) is a demonstrative-ceremony gate rather than a live deployment. A real engagement would extend this directory as follows:

```text
infra/
  terraform/                            # cloud resources (VPC, RDS, IAM, etc.)
    modules/
    envs/{dev,staging,prod}/
  helm/                                 # Kubernetes manifests
  docker/                               # Dockerfile + buildx config
```

The deploy workflows at [`.github/workflows/deploy-{dev,staging,prod}.yml`](../.github/workflows/) are marker-gated skeletons. They wire the Phase 12 multi-party signature procedure documented in [`docs/security/change-control-pci.md §1`](../docs/security/change-control-pci.md).

## 7. Scope exclusions

The following items are not included in the integration surface. Rationale is provided for each.

- **API keys and developer portal** — the brief does not require authentication. PCI DSS Req 8 (Identity) is explicitly tracked as `BLOCKING-for-prod` (gateway-bound, see [`docs/security/pci-dss-control-mapping.md`](../docs/security/pci-dss-control-mapping.md)). No client-credentialing surface is published.
- **Webhooks and asynchronous events** — outside the brief; the service is request/response only.
- **Rate-limit response headers** (`X-RateLimit-Remaining`) — `WexRateLimiterFilter` returns `429` with `Retry-After` but does not surface remaining budget. Exposing budget headers is a separate consumer-API design decision and is not included.
- **HATEOAS link relations** — the response shape is flat per the brief's requirement to "include the identifier, the description, the transaction date, the original US dollar purchase amount, the exchange rate used, and the converted amount". No hypermedia surface is added.
