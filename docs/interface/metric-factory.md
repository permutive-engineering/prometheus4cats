## Metric Factory

The `MetricFactory` creates an entrypoint to the [Metric DSL](../interface/dsl.md) from an instance of
[`MetricRegistry`], with the option of also creating from a [`CallbackRegistry`].

The following examples assume that you have the following imports in scope and that you have constructed a
[`MetricRegistry`] and [`CallbackRegistry`].

```scala mdoc:silent
import cats.effect._
import prometheus4cats._

val metricRegistry: MetricRegistry[IO] = MetricRegistry.noop[IO]
val callbackRegistry: CallbackRegistry[IO] = CallbackRegistry.noop[IO]
val registry: MetricRegistry[IO] with CallbackRegistry[IO] = null
```

### `MetricFactory` or `MetricFactory.WithCallbacks`

There are two variants of Metric Factory: `MetricFactory` and `MetricFactory.WithCallbacks`, the latter extending and
providing the same functionality as the former but with the ability to pass [callbacks](dsl.md#metric-callbacks) to the
DSL.

Whether you are able to obtain a `MetricFactory.WithCallbacks` depends on two things:

- Whether you have an implementation [`CallbackRegistry`] available
- [How you may have transformed the effect type of `MetricsFactory.WithCallbacks`](#transforming-the-effect-type)

### Constructing a No-op `MetricFactory`

For testing purposes it is possible to construct a `MetricFactory` that create metrics who perform no operations.

To obtain a no-op instance use the snippet below:

```scala mdoc:silent
MetricFactory.noop[IO]
```

A no-op `MetricsFactory.WithCallabacks` can be obtained via either of the methods shown below:

```scala mdoc:silent
MetricFactory.WithCallbacks.noop[IO]
MetricFactory.builder.noop[IO]
```

### Constructing from a `MetricRegitry`

`MetricRegitry` provides a builder with a fluent API that allows you to create an instance that adds an optional
prefix and/or common label set to all metrics.

```scala mdoc
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

### Constructing from a `CallbackRegistry`

To construct with a `CallbackRegistry` you also need a `MetricRegistry`, these may be separate or the same class.

The same builder is used as above, but the type of metrics factory built will be a `MetricsFactory.WithCallbacks` if
you provide a `CallbackRegistry`:

```scala mdoc
MetricFactory
  .builder
  .build(metricRegistry, callbackRegistry)
```

Alternatively:

```scala mdoc
MetricFactory
  .builder
  .build(registry)
```

As above, you may add `CommonLabels` and a `Prefix` as you see fit.

### Changing a `MetricFactory`

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

### Transforming the Effect Type

You may transform the effect type (`F`) of a `MetricsFactory` with a
[Cats natural transformation](https://typelevel.org/cats/datatypes/functionk.html) (`~>`).

`MetricsFactory` provides a `mapK` method to do this, while `MetricsFactory.WithCallbacks` provides an `imapK` method,
`mapK` is also available on `MetricsFactory.WithCallbacks` as it extends `MetricsFactory`, but you will only ever get a
`MetricsFactory` when calling `mapK`, whereas `imapK` will yield `MetricsFactory.WithCallbacks`. This is due to the
nature of a callback needing to be run in the original effect to retrieve the metric value.

```scala mdoc
import cats.~>
import cats.data.EitherT
import cats.syntax.all._

type F[A] = IO[A]
type G[A] = EitherT[IO, Throwable, A]

val fk: F ~> G = EitherT.liftK[F, Throwable]

val gk: G ~> F = new (G ~> F) {
  def apply[A](fa: G[A]): F[A] = fa.rethrowT
}

val factoryWithCallbacksF = MetricFactory.builder.build(metricRegistry, callbackRegistry)

val factoryG = factoryWithCallbacksF.mapK(fk)

val factoryWithCallbacksG = factoryWithCallbacksF.imapK(fk, gk)
```

It is also possible to construct a `MetricsFactory.WithCallbacks` from a `MetricsFactory` and [`CallbackRegistry`]:

```scala
val callbackRegistryG: CallbackRegistry[G] = CallbackRegistry.noop[G]

MetricFactory.builder.build(factoryG, callbackRegistryG)
```

[`MetricRegistry`]: metric-registry.md
[`CallbackRegistry`]: callback-registry.md
[OpenMetrics]: https://github.com/OpenObservability/OpenMetrics
