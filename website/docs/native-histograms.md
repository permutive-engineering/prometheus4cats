---
id: native-histograms
title: Converting classic histograms to native
sidebar_label: Native histograms
---

# Converting classic histograms to native

v6 adds opt-in support for [Prometheus native histograms][native-spec]
through two new entrypoints. This guide covers when to convert, what
the code change looks like, and the operational implications you need
to plan for **before** flipping a metric.

[native-spec]: https://prometheus.io/docs/concepts/metric_types/#histogram

## TL;DR

- New metrics with no curated bucket boundaries → use
  `factory.nativeHistogram(...)` for pure native exponential.
- Existing classic histograms with curated buckets you want to preserve
  → use `factory.histogram(...).buckets(...).withNative` to emit BOTH
  classic and native simultaneously (the NHCB-friendly dual mode).
- Stick with `factory.histogram(...).buckets(...)` if you just want
  classic and aren't ready for the operational changes.

PromQL queries against native histograms are different from classic
ones. **Don't flip a metric to native without updating its dashboards,
recording rules, and alerts in the same change.**

## When to convert

### Good candidates

- **High-cardinality histograms** — many bucket boundaries × many
  label combinations. Native histograms collapse the per-bucket label
  fan-out into a single exponential representation, which is where
  most of the cardinality reduction comes from.
- **New metrics** — if you're adding a histogram and don't have an
  established bucket scheme that downstream queries depend on, native
  is the better default.

### Stay classic

- **Low-cardinality histograms** — the cardinality win is small and
  the operational change has fixed cost.
- **Histograms with downstream consumers you don't control** — if
  another team's recording rules / alerts query your `_bucket` /
  `_sum` / `_count` series, they have to flip in lockstep with you.
  Use the dual-mode entrypoint instead so both representations are
  available during the migration window.

## Three entrypoints

| Entrypoint | Classic emission | Native emission | When |
|---|---|---|---|
| `.histogram(name)` | ✓ (default) | ✗ | Status quo. Stay here if you're not ready. |
| `.histogram(name).buckets(...).withNative` | ✓ (dual mode) | ✓ | NHCB-friendly. Recommended migration target for histograms with curated buckets. |
| `.nativeHistogram(name)` | ✗ | ✓ (exponential-only) | New metrics with no bucket intent worth preserving. |

The `withNative` form is the most forgiving — Prometheus 2.49+'s
`convert_classic_histograms_to_nhcb` scrape-time conversion can turn
the classic representation into NHCB while you migrate dashboards,
giving you a window where both old and new queries return data.

## Code change

### From classic to dual-mode

```diff
  factory
    .histogram("http_request_duration_seconds")
    .ofDouble
    .help("Time observed for HTTP requests")
    .buckets(0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10)
+   .withNative
    .label[String]("method")
    .build
```

That's the whole code change for the dual-mode path. Bucket boundaries
are preserved verbatim for any Prometheus that doesn't speak native;
a native exponential histogram is emitted alongside.

### From classic to pure native

```diff
  factory
-   .histogram("http_request_duration_seconds")
-   .ofDouble
-   .help("Time observed for HTTP requests")
-   .buckets(0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10)
+   .nativeHistogram("http_request_duration_seconds")
+   .help("Time observed for HTTP requests")
    .label[String]("method")
    .build
```

`.nativeHistogram(...)` skips `.ofDouble` (native histograms are always
double-typed) and `.buckets(...)` (no classic boundaries to declare).
You can pass a `NativeHistogram.Config` for non-default schema /
max-bucket-count / reset-duration tuning:

```scala
factory
  .nativeHistogram("...")
  .help("...")
  .nativeConfig(NativeHistogram.Config.default.copy(
    initialSchema = 5,             // coarser resolution; default 8
    maxNumberOfBuckets = 80         // smaller buckets cap; default 160
  ))
  .build
```

The defaults (`initialSchema = 8`, `maxNumberOfBuckets = 160`) match
upstream and are appropriate for the common case.

## PromQL impact

This is the section most consumers skip and then break things.

### Classic histograms

```promql
histogram_quantile(0.99,
  sum by (le) (rate(http_request_duration_seconds_bucket{job="api"}[5m]))
)
```

### Native histograms

```promql
histogram_quantile(0.99,
  sum (rate(http_request_duration_seconds{job="api"}[5m]))
)
```

Differences:

- **No `_bucket` suffix**. The metric name is the histogram itself.
- **No `by (le)`**. The native histogram is a single histogram-typed
  sample; `le` doesn't exist.
- **`rate()` returns a histogram-typed sample**, not a counter rate.
  Aggregation must be histogram-aware. `sum()` works (it's defined
  on histograms). Arithmetic like `× 100` does NOT — you need to
  pull out a scalar first via `histogram_count()` or
  `histogram_sum()`.

### Dual mode (classic + native)

If you used `withNative`, both sets of series are available. New
queries should be written against the native form. Old `_bucket` /
`_sum` / `_count` queries continue to work against the classic form.

### NHCB (classic-converted-to-native)

If your Prometheus has `convert_classic_histograms_to_nhcb: true` on
the scrape config, classic-only histograms get converted to NHCB at
scrape time. Queries against the converted form use the **native**
PromQL syntax (no `_bucket`, no `by (le)`), even though the source
metric was classic. This is the "have your cake and eat it too" path
for short-term migrations.

## Recording rules and alerts

Every recording rule and alert that references `<metric>_bucket`,
`<metric>_sum`, or `<metric>_count` series **must** be rewritten or
removed when the underlying metric flips to pure native. Selectors
using `{le="..."}` no longer match anything.

The dual-mode (`withNative`) path avoids this — the classic series
keep being emitted, so existing rules continue to resolve.

Common cases to audit:

- **Apdex / SLO recording rules** — usually built off `_bucket` series.
- **Alert thresholds on response time** — `histogram_quantile(0.99,
  rate(foo_bucket[5m])) > 0.5`. Rewrite as
  `histogram_quantile(0.99, rate(foo[5m])) > 0.5` for native.
- **Per-bucket panels** — Grafana panels that hardcode bucket labels
  (`{le="0.5"}`) lose their data source. Switch to histogram-aware
  panel queries.

## Dashboards

Grafana 10+ understands native histograms in panel and legend
queries — pick the histogram-aware variant when constructing
queries. Panels using classic `histogram_quantile()` with the
`_bucket` form need to be updated to the native form.

If you're on a pre-10 Grafana, the dual-mode path is the safer choice
until the upgrade — your existing classic-form panels keep working.

## Server-side requirements

Native histograms require:

- **Prometheus must scrape over protobuf** (not text format) to
  receive native histograms. Set
  `scrapeProtocols: [PrometheusProto, OpenMetricsText1.0.0,
  PrometheusText0.0.4]` on your `ServiceMonitor` (or `--enable-feature=
  native-histograms` plus matching protocol negotiation if you're
  configuring Prometheus directly).
- **Prometheus 3.x or 2.49+** to ingest native histograms. Earlier
  versions reject them.
- **Grafana 10+** to render native histograms cleanly in dashboards
  (see above).

## Recommended transition strategy

1. **Deploy code change behind a config toggle** so the metric can
   flip between classic and native (or pure-native vs dual-mode)
   without a redeploy.
2. **Roll out to staging.** Verify the `/metrics` endpoint shows the
   native histogram (look for `# TYPE foo histogram` followed by a
   binary protobuf payload, not `_bucket{le=...}` lines). Verify
   Prometheus is actually ingesting it (`up{}` parity, samples in
   the head block).
3. **Update all referencing dashboards / alerts / recording rules**
   in a single PR. Merge before flipping production.
4. **Flip production.** Monitor for one full scrape interval; confirm
   series stop being created with the `_bucket` suffix (for pure
   native) or that both representations exist (for dual mode).

For dual-mode (`withNative`) deployments, steps 3 and 4 are
independent — the new native series start showing up immediately,
existing classic queries continue working, and you can update the
queries on whatever timeline suits.

## See also

- [Migration from v5 to v6](./migration-from-v5.md)
- [Prometheus native histograms spec][native-spec]
- [`convert_classic_histograms_to_nhcb` Prometheus config](https://prometheus.io/docs/prometheus/latest/configuration/configuration/#scrape_config)
