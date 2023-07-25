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

This implements an [OpenMetrics] counter, allowing a number to be incremented by `1` or some positive value.

See the example below on how to obtain a `Counter` from a [`MetricFactory`]:

```scala mdoc:silent
factory.counter("my_counter_total").ofLong.help("Metric description")
```

Counters also support [exemplars], to obtain a counter with exemplar support use the following method on the 
[`MetricFactory`]:

```scala mdoc:silent
factory.exemplarCounter("my_counter_total").ofLong.help("Metric description")
```

### `Gauge`

This implements an [OpenMetrics] gauge, allowing a number to be incremented and decremented by `1` or some positive
value, as well as reset to `0`.

See the example below on how to obtain a `Gauge` from a [`MetricFactory`]:

```scala mdoc:silent
factory.gauge("my_summary").ofLong.help("Metric description")
```

### `Histogram`

This implements an [OpenMetrics] histogram, allowing a number to be recorded on a distribution of pre-defined buckets.

See the example below on how to obtain a `Histogram` from a [`MetricFactory`]:

```scala mdoc:silent
val histogram = factory
  .histogram("my_histogram")
  .ofDouble
  .help("Metric description")
```

Histograms also support [exemplars], to obtain a counter with exemplar support use the following method on the
[`MetricFactory`]:

```scala mdoc:silent
val histogram = factory
  .exemplarHistogram("my_histogram")
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
### `Summary`

This implements an [OpenMetrics] summary, allowing a number to be recorded against a calculated.

See the example below on how to obtain a `Summary` from a [`MetricFactory`]:

```scala mdoc:silent
val summary = factory
  .summary("my_summary")
  .ofDouble
  .help("Metric description")
```

By default, `Summary` metrics provide the count and the sum. For example, if you measure latencies of a REST service,
the count will tell you how often the REST service was called, and the sum will tell you the total aggregated response
time. You can calculate the average response time using a Prometheus query dividing sum / count.

#### Quantiles

In addition to count and sum, you can configure a `Summary` to provide quantiles:

```scala mdoc:silent
val quantileSummary = factory
  .summary("my_summary")
  .ofDouble
  .help("Metric description")
  .quantile(0.5, 0.01)    // 0.5 quantile (median) with 0.01 allowed error
  .quantile(0.95, 0.005)  // 0.95 quantile with 0.005 allowed error
```

As an example, a 0.95 quantile of 120ms tells you that 95% of the calls were faster than 120ms, and 5% of the calls were
slower than 120ms.

Tracking exact quantiles require a large amount of memory, because all observations need to be stored in a sorted list.
Therefore, we allow an error to significantly reduce memory usage.

In the example, the allowed error of 0.005 means that you will not get the exact 0.95 quantile, but anything between the
0.945 quantile and the 0.955 quantile.

Experiments show that the Summary typically needs to keep less than 100 samples to provide that precision, even if you
have hundreds of millions of observations.

There are a few special cases:

- You can set an allowed error of 0, but then the Summary will keep all observations in memory.
- You can track the minimum value with .quantile(0.0, 0.0). This special case will not use additional memory even though
the allowed error is 0.
- You can track the maximum value with .quantile(1.0, 0.0). This special case will not use additional memory even though
the allowed error is 0.

#### Maximum Age and Age Buckets

Typically, you don't want to have a `Summary` representing the entire runtime of the application, but you want to look
at a reasonable time interval. Summary metrics implement a configurable sliding time window:

```scala mdoc:silent
import scala.concurrent.duration._

val ageSummary = factory
  .summary("my_summary")
  .ofDouble
  .help("Metric description")
  .maxAge(10.seconds)
  .ageBuckets(5)
```

The default is a time window of 10 minutes and 5 age buckets, i.e. the time window is 10 minutes wide, and we slide it
forward every 2 minutes.

### `Info`

This implements an [OpenMetrics] info type, allowing labels to be registered as a sort of gauge where the value is
always `1`.

```scala mdoc:silent
val info = factory
  .info("my_info")
  .help("Info description")
```

[Metrics DSL]: ../interface/dsl.md
[`MetricFactory`]: ../interface/metric-factory.md
[exemplars]: ../interface/exemplar.md

[OpenMetrics]: https://github.com/OpenObservability/OpenMetrics
