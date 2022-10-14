## Primitive Metric Types

**For more detailed information on building metrics see the [Metrics DSL] section.**

The examples in this section assume you have imported the following and have created a
[`MetricFactory`](../interface/metric-factory.md):

```scala mdoc
import cats.effect._
import prometheus4cats._

val factory: MetricFactory[IO] = MetricFactory.noop[IO]
```

### `Counter`

This implements an [OpenMetrics] [counter], allowing a number to be incremented by `1` or some positive value.

See the example below on how to obtain a `Counter` from a [`MetricsFactory`]:

```scala mdoc:silent
factory.counter("my_counter_total").ofLong.help("Metric description")
```

### `Gauge`

This implements an [OpenMetrics] [gauge], allowing a number to be incremented and decremented by `1` or some positive
value, as well as reset to `0`.

See the example below on how to obtain a `Gauge` from a [`MetricsFactory`]:

```scala mdoc:silent
factory.gauge("my_counter_total").ofLong.help("Metric description")
```

### `Histogram`

This implements an [OpenMetrics] [histogram], allowing a number to be recorded on a distribution of pre-defined buckets.

See the example below on how to obtain a `Gauge` from a [`MetricsFactory`]:

```scala mdoc:silent
val histogram = factory
  .histogram("my_counter_total")
  .ofDouble
  .help("Metric description")
```

#### User Defined Buckets

You can set buckets statically with your own values, the type of these values must match that of the `Histogram`
(either `Double` or `Long`).

```scala mdoc:silent
histogram.buckets(0.1, 0.5, 1.0, 1.5, 2.0)
```

You can also use pre-defined values for default HTTP timing buckets.

**Note that this syntax is only available on `Histogram`s that record `Double` values.**

```scala mdoc:silent
histogram.defaultHttpBuckets
```

#### Generated Buckets

It is possible to generate buckets of a certain size, which is specified at compile time with a natural number.

In Scala 2, you must import Shapeless' `Nat`, whereas in Scala 3 this will work with integers.

```scala mdoc:silent
import shapeless.Nat
```

Linear buckets may be generated using the method below:

```scala mdoc:silent
histogram.linearBuckets[Nat._10](start = 1, width = 3)
```

Exponential buckets may be generated using the method below:

**Note that this syntax is only available on `Histogram`s that record `Double` values.**

```scala mdoc:silent
histogram.exponentialBuckets[Nat._5](start = 1.0, factor = 1.5)
```


[Metrics DSL]: ../interface/dsl.md
[`MetricsFactory`]: ../interface/metrics-factory.md

[OpenMetrics]: https://github.com/OpenObservability/OpenMetrics
[counter]: https://github.com/OpenObservability/OpenMetrics/blob/main/specification/OpenMetrics.md#counter
[gauge]: https://github.com/OpenObservability/OpenMetrics/blob/main/specification/OpenMetrics.md#gauge
[histogram]: https://github.com/OpenObservability/OpenMetrics/blob/main/specification/OpenMetrics.md#histogram
