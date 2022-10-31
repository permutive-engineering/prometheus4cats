## Metrics DSL

The metrics DSL provides a fluent API for constructing [primitive] and [derived] metrics from a [`MetricFactory`].

The examples in this section assume you have imported the following and have created a [`MetricFactory`]:

```scala mdoc
import cats.effect._
import prometheus4cats._

val factory: MetricFactory.WithCallbacks[IO] = MetricFactory.builder.noop[IO]
```

### Refined Types

Value classes exist for metric and label names that are refined at compile time from string literals. It is also
possible to refine at runtime, where the result is returned in an `Either`.

The value classes used by the DSL are as follows:

- `Counter.Name`
- `Gauge.Name`
- `Histogram.Name`
- `Summary.Name`
- `Info.Name`
- `Label.Name`
- `Metric.Help`

When used in the DSL with string literals the value classes are implicitly resolved, so there is no need to wrap every
value.

### Choosing a Primitive Metric

```scala mdoc:silent
factory.counter("counter_total")
factory.gauge("gauge")
factory.histogram("histogram")
factory.summary("summary")
factory.info("info_info")
```

### Specifying the Underlying Number Format

```scala mdoc:silent
factory.counter("counter_total").ofDouble
factory.counter("counter_total").ofLong
```

### Defining the Help String

```scala mdoc:silent
factory.counter("counter_total").ofDouble.help("Describe what this metric does")
```

### Building a Simple Metric
Once you have specified all the parameters with which you want to create your metric you can call the `build` method.
This will return a `cats.effect.Resource` of your desired metric, which will de-register the metric from the underlying
[`MetricRegistry`] or [`CallbackRegistry`] upon finalization.

```scala mdoc:silent
val simpleCounter = factory
  .counter("counter_total")
  .ofDouble
  .help("Describe what this metric does")

simpleCounter.build
```

While not recommended, it is possible to build the metric without a `cats.effect.Resource`, which will not de-register
from the underlying [`MetricRegistry`]:

```scala mdoc:silent
simpleCounter.unsafeBuild
```

### Adding Labels

#### Adding Individual Labels

```scala mdoc:silent
case class MyClass(value: String)

val tupleLabelledCounter = factory
  .counter("counter_total")
  .ofDouble
  .help("Describe what this metric does")
  .label[String]("this_uses_show")
  .label[MyClass]("this_doesnt_use_show", _.value)

tupleLabelledCounter.build.evalMap(_.inc(2.0, ("label_value", MyClass("label_value"))))
```

#### Compile-Time Checked Sequence of Labels

```scala mdoc:silent
case class MyMultiClass(value1: String, value2: Int)

val classLabelledCounter = factory
  .counter("counter_total")
  .ofDouble
  .help("Describe what this metric does")
  .labels(Sized(Label.Name("label1"), Label.Name("label2")))((x: MyMultiClass) =>
    Sized(x.value1, x.value2.toString)
  )

classLabelledCounter.build.evalMap(_.inc(2.0, MyMultiClass("label_value", 42)))
```

#### Unchecked Sequence of Labels

```scala mdoc:silent
val unsafeLabelledCounter = factory
  .counter("counter_total")
  .ofDouble
  .help("Describe what this metric does")
  .unsafeLabels(Label.Name("label1"), Label.Name("label2"))

val labels = Map(Label.Name("label1") -> "label1_value", Label.Name("label2") -> "label1_value")
unsafeLabelledCounter.build.evalMap(_.inc(3.0, labels))
```

### Contramapping a Metric Type

#### Simple Metric

```scala mdoc:silent
val intCounter: Resource[IO, Counter[IO, Int]] = factory
  .counter("counter_total")
  .ofLong
  .help("Describe what this metric does")
  .contramap[Int](_.toLong)
  .build
```

```scala mdoc:silent
val shortCounter: Resource[IO, Counter[IO, Short]] = intCounter.map(_.contramap[Short](_.toInt))
```

#### Labelled Metric

```scala mdoc:silent
val intLabelledCounter: Resource[IO, Counter.Labelled[IO, Int, (String, Int)]] = factory
  .counter("counter_total")
  .ofLong
  .help("Describe what this metric does")
  .label[String]("string_label")
  .label[Int]("int_label")
  .contramap[Int](_.toLong)
  .build
```

```scala mdoc:silent
val shortLabelledCounter: Resource[IO, Counter.Labelled[IO, Short, (String, Int)]] =
  intLabelledCounter.map(_.contramap[Short](_.toInt))
```

### Contramapping Metric Labels

This can work as a nice alternative to
[providing a compile-time checked sequence of labels](#compile-time-checked-sequence-of-labels)

```scala mdoc:silent
case class LabelsClass(string: String, int: Int)

val updatedLabelsCounter: Resource[IO, Counter.Labelled[IO, Long, LabelsClass]] = factory
  .counter("counter_total")
  .ofLong
  .help("Describe what this metric does")
  .label[String]("string_label")
  .label[Int]("int_label")
  .contramapLabels[LabelsClass](c => (c.string, c.int))
  .build
```

### Metric Callbacks

The callback DSL is only available with the `MetricFactory.WithCallbacks` implementation of [`MetricFactory`].

Callbacks are useful when you have some runtime source of a metric value, like a JMX MBean, which will be loaded when
the current values for each metric is inspected for export to Prometheus.

Callbacks are both extremely powerful and dangerous, so should be used with care. Callbacks are assumed to be
side-effecting in that each execution of the callback may yield a different underlying value, this also means that
the operation could take a long time to complete if there is I/O involved (this is strongly discouraged). Therefore,
implementations of [`CallbackRegistry`] may include a timeout.

Some general guidance on callbacks:

- **Do not perform any complex calculations as part of the callback, such as an I/O operation**
- **Make callback calculations CPU bound, such as accessing a concurrent value**

All [primitive] metric types, with exception to `Info` can be implemented as callbacks, like so for `Counter` and
`Gauge`:

```scala mdoc:silent
factory
  .counter("counter_total")
  .ofDouble
  .help("Describe what this metric does")
  .callback(IO(1.0))

factory
  .gauge("gauge")
  .ofDouble
  .help("Describe what this metric does")
  .callback(IO(1.0))
```

`Histogram` and `Summary` metrics are slightly different as they need a special value to contain the calculated
components of each metric type:

```scala mdoc:silent
import cats.data.NonEmptySeq

factory
  .histogram("histogram")
  .ofDouble
  .help("Describe what this metric does")
  .buckets(0.1, 0.5)
  .callback(
    IO(Histogram.Value(sum = 2.0, bucketValues = NonEmptySeq.of(0.0, 1.0, 1.0)))
  )
```

**Note that with a histogram value there must always be one more bucket value than defined when creating the metric,
this is to provide a value for `+Inf`.**


```scala mdoc:silent
factory
  .summary("gauge")
  .ofDouble
  .help("Describe what this metric does")
  .callback(
    IO(Summary.Value(count = 1.0, sum = 1.0, quantiles = Map(0.5 -> 1.0)))
  )
```

**Note that is you specify quantiles, max age or age buckets for the summary, you cannot register a callback. This is
because these parameters are used when configuring a summary metric type which would be returned you, whereas the
summary implementation may be configured differently.**

[primitive]: ../metrics/primitive-metric-types.md
[derived]: ../metrics/derived-metric-types.md
[`MetricFactory`]: metric-factory.md
[`MetricRegistry`]: metric-registry.md
[`CallbackRegistry`]: callback-registry.md
