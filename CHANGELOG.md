# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

For releases before 6.0.0 see the
[GitHub releases page](https://github.com/permutive-engineering/prometheus4cats/releases).

## [Unreleased]

Major release rebuilt on the `prometheus-metrics-core` 1.x family — the v6 generation of the
official Prometheus Java client and successor to the `simpleclient` family that v5 wrapped. See
the [migration guide](./website/docs/migrating-from-v5.md) for the upgrade path.

### Added

- Native histogram support via `factory.nativeHistogram(name)` (exponential bucket layout, no
  pre-declared boundaries)
- Dual-mode classic + native histograms via `histogram.buckets(...).withNative`
- `Exemplar` and `ExemplarSampler` typeclasses, with `*WithExemplar` / `*WithSampledExemplar` /
  `*ProvidedExemplar` variants on counter and histogram observation methods
- Sandbox project (`modules/sandbox/`) with a runnable Prometheus + Grafana stack for local
  exploration of every metric kind
- Opt-in stale-series eviction via `JavaMetricRegistry.Builder#withStaleSeriesEviction(ttl)`:
  label sets that have not been written to within `ttl` are exposed one final time and then
  removed at scrape time, so idle series stop being exposed; a later write recreates the series
  from zero. Only stateful metrics with at least one dynamic label participate — gauges included,
  so a labelled gauge set once or only on change will disappear until its next `set` — eviction
  only happens when the registry is scraped, and the registry retains a timestamp per live label
  set

### Changed

- **BREAKING**: package `prometheus4cats.javasimpleclient` renamed to `prometheus4cats.javaclient`
- **BREAKING**: underlying Prometheus Java client dependency switched from `prometheus-simpleclient`
  to `prometheus-metrics-core` 1.x
- **BREAKING**: Scala 2.12 no longer supported; supported versions are 2.13 and 3.3
- Default scrape format negotiation now prefers protobuf when offered (required for native
  histograms to ingest)
- Exemplar retention period enforced by upstream `prometheus-metrics-core` — approximately 7 seconds
  minimum between consecutive exemplars landing in the same classic-histogram bucket

### Deprecated

- `.ofLong` / `.ofDouble` on the factory type-step. `Double` is now the default — call `.help`
  directly off `factory.counter(name)` / `.gauge(name)` / `.histogram(name)` / `.summary(name)`.
  Callers wanting `Long` semantics should use `.contramap[Long](_.toDouble)` on the resulting DSL.
  The deprecated methods still work for one release.

### Removed

- **BREAKING**: self-observability metrics (`prometheus4cats_registered_metrics`,
  `prometheus4cats_combined_callback_metric_total`, and related internal counters). Removal is
  permanent — register a custom `Collector` against your `PrometheusRegistry` if you depended on
  them.
- **BREAKING**: callback support is dropped entirely. `CallbackRegistry`,
  `MetricFactory.WithCallbacks` (as a distinct subtype), the `.callback(...)` DSL step on
  counter/gauge/histogram/summary, `factory.metricCollectionCallback(...)`, and
  `JavaMetricRegistry.Builder.withCallbackTimeout` / `withCallbackCollectionTimeout` are gone.
  `MetricFactory.WithCallbacks[F]` is kept as a type alias for `MetricFactory[F]` so existing
  references continue to type-check; the alias will be removed in a future release. For pull-mode
  metrics, register a custom `io.prometheus.metrics.model.registry.Collector` directly on the
  `PrometheusRegistry` your `JavaMetricRegistry` is built on.

[Unreleased]: https://github.com/permutive-engineering/prometheus4cats/compare/v5.0.1...HEAD
