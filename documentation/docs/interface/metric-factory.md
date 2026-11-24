# Metric Factory

The `MetricFactory` creates an entrypoint to the [Metric DSL](../interface/dsl.md) from an instance of
[`MetricRegistry`].

The following examples assume that you have the following imports in scope and that you have constructed a
[`MetricRegistry`].

```scala mdoc:silent
import cats.effect._
import prometheus4cats._

val metricRegistry: MetricRegistry[IO] = MetricRegistry.noop[IO]
```

## Constructing a No-op `MetricFactory`

For testing purposes it is possible to construct a `MetricFactory` whose metrics perform no operations.

```scala mdoc:silent
MetricFactory.noop[IO]
```

## Constructing from a `MetricRegistry`

`MetricFactory.builder` provides a fluent API that allows you to create an instance that adds an optional prefix and/or
common label set to all metrics.

```scala mdoc:silent
MetricFactory
  .builder
  .build(metricRegistry)
```

Literal prefixes are checked at compile time to ensure they conform to the [OpenMetrics] format. Alternatively you may
provide an instance of `Metric.Prefix` that has been refined at runtime.

```scala mdoc:silent
MetricFactory
  .builder
  .withPrefix("app_name")
  .build(metricRegistry)
```

Common labels are a set of labels that are checked at runtime so that the label names conform to the [OpenMetrics]
format and no more than ten are defined at any one time, which helps to reduce cardinality.

There is no compile time checking of these labels as it is assumed they will come from the runtime environment.

```scala mdoc:silent
val commonLabels: Either[String, Metric.CommonLabels] =
  Metric.CommonLabels.ofStrings("name" -> "value")

commonLabels.map { labels =>
  MetricFactory
    .builder
    .withCommonLabels(labels)
    .build(metricRegistry)
}
```

## Changing a `MetricFactory`

It is possible to obtain a new instance of a `MetricFactory` from an existing one with a different/no prefix or
common labels.

The snippet below showcases all the syntax for modifying `MetricFactory`:

```scala mdoc:silent
val newCommonLabels: Either[String, Metric.CommonLabels] =
  Metric.CommonLabels.ofStrings("name2" -> "value2")

newCommonLabels.map { labels =>
  MetricFactory
    .builder
    .build(metricRegistry)
    .dropPrefix
    .withPrefix("different_prefix")
    .dropCommonLabels
    .withCommonLabels(labels)
}
```

## Transforming the Effect Type (`mapK`)

You may transform the effect type (`F`) of a `MetricFactory` with a
[Cats natural transformation](https://typelevel.org/cats/datatypes/functionk.html) (`~>`).

```scala mdoc:silent
import cats.~>
import cats.data.EitherT

type F[A] = IO[A]
type G[A] = EitherT[IO, Throwable, A]

val fk: F ~> G = EitherT.liftK[F, Throwable]

val factoryF = MetricFactory.builder.build(metricRegistry)

val factoryG = factoryF.mapK(fk)
```

[`MetricRegistry`]: metric-registry.md
[OpenMetrics]: https://github.com/OpenObservability/OpenMetrics
