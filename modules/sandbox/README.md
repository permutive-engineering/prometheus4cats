# sandbox

A runnable local demo of every metric kind prometheus4cats exposes, wired up to a real
Prometheus + Grafana stack via docker-compose. Use it to:

- See what each `MetricFactory` DSL surface looks like end-to-end (push, callback,
  exemplars, native histograms, dual-mode, `.asTimer`, `.asOutcomeRecorder`)
- Eyeball metric shapes in a real Grafana dashboard before adopting them in production
- Iterate quickly when designing new metric shapes — add a metric, restart, see it on the
  dashboard

This module is `publish / skip := true` — it never ships to Maven Central. It depends on
`prometheus4cats` and `prometheus4cats-java`.

## Quickstart

Two commands from the repo root:

```bash
# 1. Spin up Prometheus + Grafana
docker compose -f modules/sandbox/src/main/resources/docker-compose.yaml up -d

# 2. Run the sandbox app (exposes /metrics on :9400)
sbt sandbox/run
```

Then open:

- http://localhost:9090         — Prometheus UI (query / target status)
- http://localhost:3123/d/p4c-sandbox  — pre-provisioned Grafana dashboard
- http://localhost:9400/metrics — raw scrape output

Ctrl-C the `sbt` process to stop emitting. `docker compose ... down` to stop the stack.

## What each dashboard panel demonstrates

The dashboard at `http://localhost:3123/d/p4c-sandbox` is auto-provisioned via the JSON
under `src/main/resources/grafana/dashboards/`. Panels (top to bottom):

| Panel | Metric | DSL surface | What to look at |
|---|---|---|---|
| Info | `sandbox_build_info` | `factory.info(...).label[T]…` | Labels-only metric, value always 1 |
| Counter | `sandbox_counter_total` | `counter.incWithExemplar` | One exemplar per increment |
| Sampled counter | `sandbox_sampled_counter_total` | `counter.incWithSampledExemplar` | ~1-in-10 exemplars via `ExemplarSampler` |
| Classic histogram | `sandbox_classic_histogram_seconds` | `.buckets(...).observeWithExemplar` | Quantiles via `histogram_quantile`, fixed bucket resolution |
| Native histogram | `sandbox_native_histogram_seconds` | `.nativeHistogram(...).observeWithExemplar` | Same data shape, dynamic exponential buckets |
| Dual A/B | `sandbox_dual_histogram_seconds` | `.buckets(...).withNative` | Classic and native side-by-side from one declaration |
| Heatmaps | classic + native | (above) | Distribution over time; exemplar diamonds overlay |
| Gauge | `sandbox_gauge_value` | `gauge.set` | Sine wave 0..100 |
| Summary | `sandbox_summary_seconds` | `factory.summary(...).quantile(...)` | Library-computed quantiles, no `histogram_quantile()` |
| Timer | `sandbox_operation_duration_seconds` | `factory.histogram(...).asTimer` | Histogram-backed Timer with `.time(io)` composition |
| OutcomeRecorder | `sandbox_operation_total` | `factory.counter(...).asOutcomeRecorder` | succeeded/errored/canceled outcome counters |
| Uptime | `sandbox_process_uptime_seconds` | `gauge(...).callback(io)` | Pull-mode callback example |

Each metric is prefixed with `sandbox_` via
`MetricFactory.builder.withPrefix(Metric.Prefix("sandbox"))` — change once in
`Sandbox.scala` to relabel everything.

## Adding a new metric

Edit `src/main/scala/prometheus4cats/sandbox/Sandbox.scala`:

1. Add the metric to the `for { … } yield` block, e.g.
   ```scala
   queueDepth <- factory.gauge("queue_depth").ofLong.help("…").build
   ```
2. Use it in the per-second loop body, e.g. `queueDepth.set(currentDepth)`.
3. Restart `sbt sandbox/run`.
4. (Optional) Add a dashboard panel by appending to
   `src/main/resources/grafana/dashboards/sandbox.json` and
   `docker compose … restart grafana`.

## Hot-reload tip

`sbt ~sandbox/run` runs in watch mode and re-runs the app whenever a Scala file changes.
Pair with `docker compose … restart grafana` after editing the dashboard JSON for an
end-to-end inner loop of ~2 seconds.

## Why no Tempo / Jaeger?

Exemplar `trace_id` labels are emitted and visible on hover, but there's no trace store
to click through to. Adding Tempo + an otel4s integration is on the roadmap (see
`prometheus4cats`'s exemplar roadmap memory). For now, the diamond tooltip shows the
trace_id and that's the end of the breadcrumb trail in this demo.

## File layout

```
modules/sandbox/
├── README.md                                      (this file)
├── src/main/
│   ├── scala/prometheus4cats/sandbox/
│   │   └── Sandbox.scala                          the runnable app
│   └── resources/
│       ├── docker-compose.yaml                    Prometheus + Grafana stack
│       ├── prometheus.yml                         scrape config
│       └── grafana/
│           ├── provisioning/
│           │   ├── datasources/prometheus.yml     auto-register Prometheus as a datasource
│           │   └── dashboards/sandbox.yml         dashboard provider
│           └── dashboards/sandbox.json            the actual dashboard
```
