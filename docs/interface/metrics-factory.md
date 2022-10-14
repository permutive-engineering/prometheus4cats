## MetricsFactory

The `MetricsFactory` creates an entrypoint to the [Metrics DSL](../interface/dsl.md) from an instance of
[`MetricsRegistry`].

The following examples assume that you have the following imports in scope and that you have constructed a
[`MetricsRegistry`].

```scala mdoc:silent
import cats.effect._
import prometheus4cats._

val registry: MetricsRegistry[IO] = null
```

### Constructing a No-op `MetricsFactory`

For testing purposes it is possible to construct a `MetricsFactory` that create metrics who perform no operations.

To obtain a no-op instance use the snippet below:

```scala mdoc:silent
MetricsFactory.noop[IO]
```

### Constructing from a `MetricsRegitry`

`MetricsRegitry` provides a builder with a fluent API that allows you to create an instance that adds an optional
prefix and/or common label set to all metrics.

```scala mdoc:silent
MetricsFactory
  .builder
  .build(registry)
```

Literal prefixes are checked at compile time to ensure they conform to the [OpenMetrics] format. Alternatively you may
provide an instance of `Metric.Prefix` that has been refined at runtime.

```scala mdoc:silent
MetricsFactory
  .builder
  .withPrefix("app_name")
  .build(registry)
```

Common labels are a set of labels that are checked at runtime so that the label names conform to the [OpenMetrics]
format and no more than ten are defined at any one time, which helps to reduce cardinality.

There is no compile time checking of these labels as it is assumed they will come from the runtime environment.

```scala mdoc:silent
val commonLabels: Either[String, Metric.CommonLabels] =
  Metric.CommonLabels.ofStrings("name" -> "value")

commonLabels.map { labels =>
  MetricsFactory
    .builder
    .withCommonLabels(labels)
    .build(registry)
}
```

### Changing a `MetricsFactory`

It is possible to obtain a new instance of a `MetricsFactory` from an existing one with a different/no prefix or
common labels.

The snippet below showcases all of the syntax for modifying `MetricsFactory`:

```scala mdoc:silent
val newCommonLabels: Either[String, Metric.CommonLabels] =
  Metric.CommonLabels.ofStrings("name2" -> "value2")

newCommonLabels.map { labels =>
  MetricsFactory
    .builder
    .build(registry)
    .dropPrefix
    .withPrefix("different_prefix")
    .dropCommonLabels
    .withCommonLabels(labels)
}
```

[`MetricsRegistry`]: metrics-registry.md
[OpenMetrics]: https://github.com/OpenObservability/OpenMetrics
