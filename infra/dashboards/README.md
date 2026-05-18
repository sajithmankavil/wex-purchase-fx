# Grafana dashboard templates

This folder holds Grafana dashboard definitions (JSON) for the SLI/SLO panels declared in [`docs/operations/observability.md`](../../docs/operations/observability.md) and the SLO targets in [`docs/operations/slo-sli.md`](../../docs/operations/slo-sli.md).

## Files

| File | Source-of-truth SLO/SLI | Notes |
|---|---|---|
| [`slo-availability.json`](slo-availability.json) | SLO-A / SLO-B / SLO-C | RED panels for the 3 endpoints; fast-burn + slow-burn columns; one row per endpoint. |
| [`slo-latency.json`](slo-latency.json) | SLO-D / SLO-E / SLO-F / SLO-G | p50/p95/p99 histograms; cache-hit vs cache-miss split for `/conversion`. |
| [`treasury-dependency.json`](treasury-dependency.json) | Treasury-client metrics | CB state, retry budget consumed, single-flight gate winner/loser ratio. |

## Status

These are **templates** — they declare the panel structure, queries (Prometheus PromQL), and thresholds that match the SLO target. They are intended to be imported into a Grafana instance during Phase 12 production cutover; until then, no Grafana instance is provisioned. The templates exist now so the SLI/SLO contract is verifiable from the repo (the Phase 10 bundle's coverage-map references them).

**Concrete templates exist for the canonical SLI/SLO panels.** Additional dashboards (capacity-headroom, JVM/Tomcat resource saturation, alias-drift trend, content-guard fired-rate) are tracked as Phase 12 follow-ups; the panel queries are documented inline in [`docs/operations/observability.md`](../../docs/operations/observability.md) §6.

## Validation

Each template is **schema-valid Grafana 10.x dashboard JSON**. Validation:

```bash
python3 -c "import json; json.load(open('infra/dashboards/slo-availability.json'))"
```

The CI quality-gate workflow runs the JSON-parse check on every PR that touches `infra/dashboards/*.json`.

## Production cutover

At Phase 12:

1. Provision Grafana instance with Prometheus datasource pointing at the OTel-exporter Prometheus endpoint.
2. Import each `*.json` via the Grafana API (`POST /api/dashboards/db`) or the Grafana provisioning sidecar.
3. Verify panels render against live metric data.
4. Pin the dashboard URLs in [`docs/operations/monitoring-alerting.md`](../../docs/operations/monitoring-alerting.md) §2 "Dashboards".
