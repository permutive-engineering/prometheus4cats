## Derived Metric Types
**For more detailed information on building metrics see the [Metrics DSL](../interface/dsl.md) section.**

Derived metric types are types of metrics that add extra functionality to an underlying [primitive] metric.

These metrics exist for the following reasons:

- Separation of concerns between recording a value and performing some additional higher level operation or side effect
- Making the addition of future [`MetricRegistry`] instances easier as higher level functionality isn't implemented by
  the [primitive] metrics
- Provides a pattern for adding functionality that will not break binary compatibility

The examples in this section assume you have imported the following and have created a
[`MetricFactory`](../interface/metric-factory.md):

```scala mdoc
import cats.effect._
import prometheus4cats._

val factory: MetricFactory[IO] = MetricFactory.noop[IO]
```

### `Timer`

A `Timer` can be derived from either a [`Gauge`], [`Histogram`] or [`Summary`] that record `Double` values. It uses
[`Clock`] from [Cats-Effect] to time a given operation.

The underlying metric type should be carefully considered; a [`Histogram`] or [`Summary`] can be used to measure many
operations at differing runtime costs, where a [`Gauge`] will only record the last value so is best for singular
operations.

#### Obtaining from a `Histogram`

```scala mdoc:silent
val simpleTimerHistogram: Resource[IO, Timer.Aux[IO, Histogram]] = factory
  .histogram("time")
  .ofDouble
  .help("Records the how long an opertation took")
  .buckets(1.0, 2.0)
  .asTimer
  .build
```

```scala mdoc:silent
val labelledTimerHistogram: Resource[IO, Timer.Aux[IO, String, Histogram]] = factory
  .histogram("time")
  .ofDouble
  .help("Records the how long an opertation took")
  .buckets(1.0, 2.0)
  .label[String]("some_label")
  .asTimer
  .build
```

#### Obtaining from a `Summary`

```scala mdoc:silent
val simpleTimerSummary: Resource[IO, Timer.Aux[IO, Summary]] = factory
  .summary("time")
  .ofDouble
  .help("Records the how long an opertation took")
  .asTimer
  .build
```

```scala mdoc:silent
val labelledTimerSummary: Resource[IO, Timer.Aux[IO, String, Summary]] = factory
  .summary("time")
  .ofDouble
  .help("Records the how long an opertation took")
  .label[String]("some_label")
  .asTimer
  .build
```

#### Obtaining from a `Gauge`

```scala mdoc:silent
val simpleTimerGauge: Resource[IO, Timer.Aux[IO, Gauge]] = factory
  .gauge("time")
  .ofDouble
  .help("Records the how long an opertation took")
  .asTimer
  .build
```

```scala mdoc:silent
val labelledTimerGauge: Resource[IO, Timer.Aux[IO, String, Gauge]] = factory
  .gauge("time")
  .ofDouble
  .help("Records the how long an opertation took")
  .label[String]("some_label")
  .asTimer
  .build
```

### `CurrentTimeRecorder`

A `CurrentTimeRecorder` can be derived from any [`Gauge`]. It uses [`Clock`] from [Cats-Effect] to get the current
system time.

#### Obtaining from a `Gauge`

```scala mdoc:silent
val simpleCurrentTimeRecorderGauge: Resource[IO, CurrentTimeRecorder[IO]] = factory
  .gauge("current_time")
  .ofDouble
  .help("Records the how long an opertation took")
  .asCurrentTimeRecorder
  .build
```

```scala mdoc:silent
val labelledCurrentTimeRecorderGauge:
  Resource[IO, CurrentTimeRecorder.Labelled[IO, String]] =
    factory
      .gauge("current_time")
      .ofDouble
      .help("Records the how long an opertation took")
      .label[String]("some_label")
      .asCurrentTimeRecorder
      .build
```


### `OutcomeRecorder`

A `Timer` can be derived from either a [`Counter`] or [`Gauge`]. It uses [`Outcome`] from [Cats-Effect] to record the status
of a given operation via a `outcome_status` label, the value of which will either be `succeeded`, `canceled` or
`errored`.

The underlying metric type should be carefully considered based on the behaviour covered below: a [`Counter`] can be used
to measure repeated operations, where a [`Gauge`] will only record the last value so is best for singular operations.

When using a with a [`Counter`], each [`Outcome`] will increment its corresponding label value, but when using a [`Gauge`]
the last invocation will set the corresponding label value to `1`, with each of the other label values set to `0`.

To help disambiguate the difference in behaviour the `OutcomeRecorder` type will be tagged with the underlying
[primitive] metric type. **This is just a type alias and is not required when passing around instances of
`OutcomeRecorder`**.

#### Obtaining from a `Counter`

```scala mdoc:silent
val simpleOutcomeCounter: Resource[IO, OutcomeRecorder.Aux[IO, Long, Counter]] = factory
  .counter("outcome_total")
  .ofLong
  .help("Records the outcome of some operation")
  .asOutcomeRecorder
  .build
```

```scala mdoc:silent
val labelledOutcomeCounter:
  Resource[IO, OutcomeRecorder.Labelled.Aux[IO, Long, String, Counter]] = factory
    .counter("outcome_total")
    .ofLong
    .help("Records the outcome of some operation")
    .label[String]("some_label")
    .asOutcomeRecorder
    .build
```

#### Obtaining from a `Gauge`

```scala mdoc:silent
val simpleOutcomeGauge: Resource[IO, OutcomeRecorder.Aux[IO, Long, Gauge]] = factory
  .gauge("outcome")
  .ofLong
  .help("Records the outcome of some operation")
  .asOutcomeRecorder
  .build
```

```scala mdoc:silent
val labelledOutcomeGauge:
  Resource[IO, OutcomeRecorder.Labelled.Aux[IO, Long, String, Gauge]] = factory
    .gauge("outcome")
    .ofLong
    .help("Records the outcome of some operation")
    .label[String]("some_label")
    .asOutcomeRecorder
    .build
```

[`MetricRegistry`]: ../interface/metric-registry.md
[primitive]: primitive-metric-types.md
[`Counter`]: primitive-metric-types.md#counter
[`Gauge`]: primitive-metric-types.md#gauge
[`Histogram`]: primitive-metric-types.md#histogram

[Cats-Effect]: https://typelevel.org/cats-effect/
[`Outcome`]: https://typelevel.org/cats-effect/api/3.x/cats/effect/kernel/Outcome.html
[`Clock`]: https://typelevel.org/cats-effect/api/3.x/cats/effect/kernel/Clock.html
