---
sidebar_position: 2
---

# Migrating from v5

v6 is built on the [Prometheus Java client] 1.x family (`prometheus-metrics-core`), the successor to
the `simpleclient` family that v5 wrapped. The upgrade is a clean break — no compatibility shims for
the package rename, no fallback path for the dependency swap. In return v6 brings native histograms,
dual-mode (NHCB-friendly) histograms, exemplar storage, and a protobuf-first scrape negotiation by
default.

This page is for v5 users with an existing codebase. If you're new to the library, start with
[Getting Started](getting-started.md) instead.

## Required code changes

### Package rename

The Java backend moved from `prometheus4cats.javasimpleclient` to `prometheus4cats.javaclient`. The
class name (`JavaMetricRegistry`) is unchanged; only the package differs. Every import has to
change.

### Underlying Prometheus Java client

The transitive dependency changed from `prometheus-simpleclient` (v5) to `prometheus-metrics-core
1.x` (v6). If your code interacted with the underlying client directly — constructing
`CollectorRegistry`, registering custom `Collector` instances, calling exposition writers — those
types and APIs have changed in the upstream library. The upstream
[1.x migration notes](https://prometheus.github.io/client_java/migration/simpleclient/) cover that
side of the change.

### Scala version

v5 supported Scala 2.12, 2.13, 3.2. v6 supports @SUPPORTED_SCALA@ only. Scala 2.12 is dropped; 3.x
is on the LTS line.

## New capabilities

### Native histograms

A new metric kind. Bucket boundaries are computed dynamically (exponential schema) rather than
declared up-front, giving finer quantile resolution and lower storage cost on most workloads.

```scala mdoc:silent
import cats.effect.IO
import prometheus4cats.MetricFactory

def withNativeHistogram(factory: MetricFactory[IO]) =
  factory
    .nativeHistogram("request_latency_seconds")
    .ofDouble
    .help("Request latency, native bucket distribution")
    .build
```

Requires Prometheus 2.40+ at the scrape side and a Prometheus server configured with
`--enable-feature=native-histograms`. See [Java Registry](implementations/java.md) for the wire
format requirements, and the runnable example under `modules/sandbox/` for end-to-end behaviour.

### Dual-mode histograms (`.withNative`)

A classic histogram with declared bucket boundaries that *also* emits a native bucket
representation. Useful when you want to keep an existing dashboard (querying classic
`*_bucket{le=...}` series) while also surfacing the native form to consumers that prefer it.

```scala mdoc:silent
import cats.data.NonEmptySeq

def withDualHistogram(factory: MetricFactory[IO]) =
  factory
    .histogram("request_latency_seconds")
    .ofDouble
    .help("Request latency, both classic and native representations")
    .buckets(NonEmptySeq.of(0.005, 0.01, 0.05, 0.1, 0.5, 1.0, 5.0))
    .withNative
    .build
```

> ℹ️ Prometheus 3.x discards the classic side at ingest by default when protobuf is the negotiated
> scrape protocol. To keep both representations stored, set `always_scrape_classic_histograms: true`
> in the scrape config.

### Exemplar storage

Exemplars on counters and histograms now round-trip through the v6 protocol path. Use
`.incWithExemplar` / `.observeWithExemplar` (or the `*WithSampledExemplar` variants) to attach a
trace id at observation time.

The `Exemplar` typeclass is described at [Exemplar](interface/exemplar.md). The
`modules/sandbox/src/main/scala/prometheus4cats/sandbox/Sandbox.scala` file shows the implicit-instance
pattern in practice.

## Behavioural changes (same API, different behaviour)

### Exemplar retention period

**This change is upstream**, not in prometheus4cats. The `prometheus-metrics-core` 1.x library now
enforces a minimum interval (~7 seconds) between consecutive exemplars landing in the *same*
classic-histogram bucket — the reservoir / sampling policy lives in the upstream Java client, not in
this wrapper. Observations that would have overwritten an older exemplar in v5 (where the
`simpleclient`-era policy was different) may keep the original in v6.

Rarely visible — exemplars are designed to be sparse. Mainly affects tests that observe many values
rapidly and assert on which trace id ended up attached.

### Scrape format negotiation

Prometheus 2.40+ scrapers negotiate protobuf when the `scrape_protocols` config includes
`PrometheusProto`. The v6 wire format carries native histograms over protobuf; the classic text
format does not. If your scrape config doesn't list `PrometheusProto`, native histograms silently
fail to ingest — the metric is emitted by your app but Prometheus drops the native part.

### Self-observability metrics removed

v5 exposed `prometheus4cats_registered_metrics`, `prometheus4cats_combined_callback_metric_total`,
and a few related counters for inspecting registry state and callback outcomes. **v6 does not emit
these.** The decision is permanent — they were low-value and hard to keep accurate across v6's
dispatcher-based callback machinery. Dashboards and alerts that depended on them need to be
rewritten, or the metrics need to be re-implemented in user code via a custom `Collector` registered
against the same `PrometheusRegistry` your `JavaMetricRegistry` is built on.

## Known limitations in v6

These are gaps acknowledged in the v6 codebase. Calling them out so you can plan around them rather
than discover them at the wrong moment.

### Callback-emitted metrics don't carry exemplars

`*WithExemplar` and `*WithSampledExemplar` are wired only for the active (push) side. The
`.callback(...)` API path constructs snapshots with `Exemplars.EMPTY` regardless of any `Exemplar`
instance in scope. If you need exemplars on a pull-mode metric, switch that metric to the active
path and call `.incWithExemplar` / `.observeWithExemplar` directly.

### Native histogram callbacks are unimplemented

`factory.nativeHistogram(...).callback(...)` is declared in the trait but the v6 backend throws
`NotImplementedError` at call time. The data-shape design for "a callback that emits a native
bucket distribution" is open — the obvious shapes (count + sum only; full exponential bucket map;
something in between) each have ergonomic trade-offs. Until that decision lands, restrict native
histograms to the active path.

## Migration recipe

A concrete sequence for an existing v5 codebase. This section consolidates the actions implied by
each individual change above into one ordered list.

1. **Bump library versions** in `build.sbt`:
   ```scala
   libraryDependencies ++= Seq(
     "com.permutive" %% "prometheus4cats"      % "@VERSION@",
     "com.permutive" %% "prometheus4cats-java" % "@VERSION@"
   )
   ```
2. **Rewrite imports** for the package rename:
   ```bash
   git grep -l javasimpleclient | xargs sed -i '' 's/javasimpleclient/javaclient/g'
   ```
   (drop the empty `''` on Linux / GNU sed).
3. **Drop Scala 2.12** from any `crossScalaVersions` list. Add 3.3 if you weren't already on it.
4. **Recompile and fix call sites** for direct uses of the underlying Java client (`CollectorRegistry`,
   custom `Collector` implementations, exposition writers). Most consumers never touch these.
5. **Update Prometheus scrape config** to include `PrometheusProto` first if you want native
   histograms to ingest:
   ```yaml
   scrape_protocols:
     - PrometheusProto
     - PrometheusText0.0.4
   ```
6. **Drop dashboards / alerts** that depended on the v5 self-observability metrics, or implement a
   custom `Collector` to re-surface them.
7. **Scrape, verify** every existing metric still appears with the same name and label set.
8. **(Optional) Adopt native or dual-mode histograms** for new metric declarations — see the
   examples above.

## Where to find working examples

The repo's `modules/sandbox/` project is a runnable end-to-end demo of every v6 surface (counter,
gauge, classic + native + dual histogram, summary, info, exemplars, sampled exemplars, timer,
outcome recorder, gauge callback) wired up against a local Prometheus + Grafana stack via
docker-compose. See `modules/sandbox/README.md` for the quickstart.

[Prometheus Java client]: https://github.com/prometheus/client_java
