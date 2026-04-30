---
id: migration-from-v5
title: Migrating from v5 to v6
sidebar_label: Migrating from v5
---

# Migrating from v5 to v6

v6 swaps the underlying Prometheus Java client from the EOL
`io.prometheus:simpleclient:0.16.0` line to the new
`io.prometheus:prometheus-metrics-core:1.x` line, adds opt-in support for
[native histograms](./native-histograms.md), and drops a small amount of
v5 surface that didn't fit the new model.

The fluent metric DSL (`metricFactory.counter("name").help(...).build`,
`metricFactory.histogram("...").buckets(...)`, etc.) is **unchanged** for
the common cases. Most consumers will only need a coordinate bump.

## Maven coordinates

The `com.permutive` group:artifact pairs are unchanged — only the version
bumps:

```scala
libraryDependencies ++= Seq(
  "com.permutive" %% "prometheus4cats"      % "6.0.0",
  "com.permutive" %% "prometheus4cats-java" % "6.0.0"
)
```

If your build resolved `io.prometheus:simpleclient*` transitively through
prometheus4cats, those artifacts are gone in v6 — replaced by
`prometheus-metrics-core:1.6.1` and
`prometheus-metrics-instrumentation-jvm:1.6.1`. If you depended on
simpleclient explicitly somewhere in your build (rather than transitively),
you'll need to migrate those imports too — see
[Direct simpleclient leakage](#direct-simpleclient-leakage) below.

## Package rename

The internal Java-client adapter moved from `prometheus4cats.javasimpleclient`
to `prometheus4cats.javaclient`. Anyone who instantiates `JavaMetricRegistry`
directly needs the import update:

```diff
- import prometheus4cats.javasimpleclient.JavaMetricRegistry
+ import prometheus4cats.javaclient.JavaMetricRegistry
```

The `JavaMetricRegistry.Builder` API (`.withRegistry`, `.withCallbackTimeout`,
`.build`) is unchanged. The one signature break: `.withRegistry` now takes
`io.prometheus.metrics.model.registry.PrometheusRegistry` (upstream's new
type) instead of `io.prometheus.client.CollectorRegistry`:

```diff
- import io.prometheus.client.CollectorRegistry
+ import io.prometheus.metrics.model.registry.PrometheusRegistry

- JavaMetricRegistry.Builder[IO]().withRegistry(new CollectorRegistry()).build
+ JavaMetricRegistry.Builder[IO]().withRegistry(new PrometheusRegistry()).build
```

If you weren't calling `.withRegistry(...)` (i.e. you were letting the
builder use its default) nothing changes.

## `Summary.Value.count: Double → Long`

`Summary.Value` previously typed `count` and `sum` with the same
parametric `A`. Counts are integer in Prometheus and the new
`SummaryDataPointSnapshot` constructor takes a `long`; v6 fixes the type:

```scala
// v5
final case class Value[A](count: A, sum: A, quantiles: Map[Double, A] = Map.empty)

// v6
final case class Value[A](count: Long, sum: A, quantiles: Map[Double, A] = Map.empty)
```

Source-incompatible at every `Summary.Value(...)` construction site. The
fix at each call site is one or two characters:

```diff
- Summary.Value(count = 1.0, sum = 1.0, quantiles = Map(0.5 -> 1.0))
+ Summary.Value(count = 1L,  sum = 1.0, quantiles = Map(0.5 -> 1.0))
```

If you're computing the count from a `Double` accumulator somewhere, add
`.toLong` at the boundary. If you're destructuring a `Summary.Value`, the
`count` binding is now `Long` instead of `A` — you may need an explicit
`.toDouble` if it flows into double-typed arithmetic.

The compiler catches every break — there are no scenarios where v5 code
silently does the wrong thing on v6.

## `Info` declares labels at build time

In v5, `Info` accepted labels at observation time:

```scala
// v5
val info = factory.info("build_info").help("...").build
info.use(_.info(Map(Label.Name("version") -> "1.0", Label.Name("commit") -> "abc")))
```

In v6, `Info` declares its label names at build time (consistent with
how all other metric types work):

```scala
// v6
val info = factory.info("build_info")
  .help("...")
  .label[String]("version")
  .label[String]("commit")
  .build

info.use(_.info(("1.0", "abc")))
```

This was a v5 quirk that didn't fit the upstream 1.x model. The new shape
is more consistent with `Counter` / `Gauge` / `Histogram` / `Summary` and
catches missing-label bugs at compile time.

## Native histogram support

New in v6. Classic histograms (`factory.histogram(...)`) keep working
unchanged. To opt into native histograms, see the dedicated
[native histogram guide](./native-histograms.md).

The recommended migration target for existing classic histograms with
curated bucket boundaries is the **NHCB-friendly dual mode**:

```scala
factory
  .histogram("http_request_duration_seconds")
  .ofDouble
  .help("...")
  .buckets(0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10)
  .withNative                                    // emit BOTH classic + native
  .label[String]("method")
  .build
```

This preserves your bucket intent for any Prometheus that doesn't speak
native, emits a native exponential histogram alongside, and Prometheus
2.49+'s `convert_classic_histograms_to_nhcb` can convert the classic
form to NHCB at scrape time.

## Removed self-observability metrics

The v5 `javasimpleclient` registry exposed a set of self-metrics on the
underlying registry — `prometheus4cats_registered_metrics`,
`prometheus4cats_combined_callback_metric_total`, etc. The v6 backend
does not currently emit any of these.

If you have dashboards or alerts wired to `prometheus4cats_*` series,
those will go silent after the upgrade. The audit so far across active
consumers (Permutive's own deployments, the prometheus4cats GitHub issue
history, and the contrib test suite) found no usage that wasn't a
diagnostic test probe — the v6 backend's stricter collision detection
(eager `IllegalArgumentException` on duplicate registrations) makes the
runtime probes redundant. We don't currently plan to re-add the
self-metrics, but please open an issue if you depended on them in
production.

## Direct simpleclient leakage

If your code reaches past prometheus4cats into the simpleclient API
directly (constructing `CollectorRegistry`, calling `addMetric` on a
`MetricFamily`, etc.), v6 won't migrate that automatically — you'll need
to swap to the new `prometheus-metrics-*` API yourself.

Mapping table for the most common direct-simpleclient patterns:

| v5 / simpleclient | v6 / prometheus-metrics-core |
|---|---|
| `io.prometheus.client.CollectorRegistry` | `io.prometheus.metrics.model.registry.PrometheusRegistry` |
| `CollectorRegistry.defaultRegistry` | `PrometheusRegistry.defaultRegistry` |
| `io.prometheus.client.Counter` | `io.prometheus.metrics.core.metrics.Counter` |
| `io.prometheus.client.Gauge` | `io.prometheus.metrics.core.metrics.Gauge` |
| `io.prometheus.client.Histogram` | `io.prometheus.metrics.core.metrics.Histogram` |
| `io.prometheus.client.Summary` | `io.prometheus.metrics.core.metrics.Summary` |
| `io.prometheus.client.Info` | `io.prometheus.metrics.core.metrics.Info` |
| `io.prometheus.client.exporter.PushGateway` | `io.prometheus.metrics.exporter.pushgateway.PushGateway` |
| `io.prometheus.client.exporter.common.TextFormat` | `io.prometheus.metrics.expositionformats.OpenMetricsTextFormatWriter` (or `PrometheusTextFormatWriter` / `PrometheusProtobufWriter` depending on Accept) |
| `simpleclient_hotspot.DefaultExports.initialize()` | `JvmMetrics.builder().register(prometheusRegistry)` |
| Per-collector hotspot exports (`StandardExports`, `MemoryPoolsExports`, etc.) | `JvmMetrics.builder().register(...)` (bundles all of them) |

The 1.x API uses a builder pattern throughout —
`Counter.builder().name("foo").help("...").register(registry)` instead of
`Counter.build().name("foo").help("...").register(registry)`. The full
upstream migration guide is at
<https://prometheus.github.io/client_java/migration/simpleclient/>.

## Bytecode compatibility

v5 binaries don't link against v6 — `versionPolicyIntention` is set to
`Compatibility.None` to make this explicit. Don't try to mix v5 and v6
artifacts on the same classpath.

If you can't migrate immediately, v5.x stays on Maven Central in
perpetuity for stragglers. We strongly recommend migrating though, since
the simpleclient line is in EOL maintenance mode upstream and won't
receive new security or feature updates.

## See also

- [Native histogram conversion guide](./native-histograms.md)
- [Upstream simpleclient → 1.x migration guide](https://prometheus.github.io/client_java/migration/simpleclient/)
