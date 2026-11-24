# Metrics DSL

The metrics DSL provides a fluent API for constructing [primitive] and [derived] metrics from a [`MetricFactory`]. It
is designed to provide _some_ compile time safety when recording metrics in terms of matching label values to label
names. It **does not** check that a metric is unique - this is only checked at runtime, the uniqueness of a
metric (name & labels combination) depends on the [`MetricFactory`] implementation.

The examples in this section assume you have imported the following and have created a [`MetricFactory`]:

```scala mdoc:silent
import cats.effect._
import prometheus4cats._

val factory: MetricFactory[IO] = MetricFactory.builder.noop[IO]
```

## Expected Behaviour

Every metric created using this DSL returns a Cats-Effect `Resource` which indicates the lifecycle of that metric. When
the `Resource` is allocated the metric is registered and when it is finalized it is de-registered.

**It should be possible** to request/register the same metric multiple times without error, where you will be returned
the currently registered instance rather than a new instance. This does depend on the implementation of
[`MetricRegistry`] however, the provided [Java wrapper implementation](../implementations/java.md#implementation-notes)
implements this via reference counting and implementers of [`MetricRegistry`] are advised to do the same in order to
preserve this expected behaviour at runtime.

## Refined Types

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

## Choosing a Primitive Metric

```scala mdoc:silent
factory.counter("counter_total")
factory.gauge("gauge")
factory.histogram("histogram")
factory.nativeHistogram("native_histogram")
factory.summary("summary")
factory.info("info_info")
```

All metric values are `Double` by default — the underlying Prometheus wire format stores everything as `double`, so a
separate `Long` path was duplication. `Long`-valued sources should `.contramap[Long](_.toDouble)` on the resulting DSL.

## Defining the Help String

```scala mdoc:silent
factory.counter("counter_total").help("Describe what this metric does")
```

## Building a Simple Metric
Once you have specified all the parameters with which you want to create your metric you can call the `build` method.
This will return a `cats.effect.Resource` of your desired metric, which will de-register the metric from the underlying
[`MetricRegistry`] upon finalization.

```scala mdoc:silent
val simpleCounter = factory
  .counter("counter_total")
  .help("Describe what this metric does")

simpleCounter.build
```

While not recommended, it is possible to build the metric without a `cats.effect.Resource`, which will not de-register
from the underlying [`MetricRegistry`]:

```scala mdoc:silent
simpleCounter.unsafeBuild
```

## Adding Labels

### Adding Individual Labels

```scala mdoc:silent
case class MyClass(value: String)

val tupleLabelledCounter = factory
  .counter("counter_total")
  .help("Describe what this metric does")
  .label[String]("this_uses_show")
  .label[MyClass]("this_doesnt_use_show", _.value)

tupleLabelledCounter.build.evalMap(_.inc(2.0, ("label_value", MyClass("label_value"))))
```

### Compile-Time Checked Sequence of Labels

```scala mdoc:silent
case class MyMultiClass(value1: String, value2: Int)

val classLabelledCounter = factory
  .counter("counter_total")
  .help("Describe what this metric does")
  .labels[MyMultiClass](
    Label.Name("label1") -> (_.value1),
    Label.Name("label2") -> (_.value2.toString)
  )

classLabelledCounter.build.evalMap(_.inc(2.0, MyMultiClass("label_value", 42)))
```

### Unchecked Sequence of Labels

```scala mdoc:silent
val unsafeLabelledCounter = factory
  .counter("counter_total")
  .help("Describe what this metric does")
  .unsafeLabels(Label.Name("label1"), Label.Name("label2"))

val labels = Map(
  Label.Name("label1") -> "label1_value",
  Label.Name("label2") -> "label1_value"
)

unsafeLabelledCounter.build.evalMap(_.inc(3.0, labels))
```

## Contramapping a Metric Type

### Simple Metric

```scala mdoc:silent
val intCounter: Resource[IO, Counter[IO, Int, Unit]] = factory
  .counter("counter_total")
  .help("Describe what this metric does")
  .contramap[Int](_.toDouble)
  .build
```

```scala mdoc:silent
val shortCounter: Resource[IO, Counter[IO, Short, Unit]] =
  intCounter.map(_.contramap[Short](_.toInt))
```

### Labelled Metric

```scala mdoc:silent
val intLabelledCounter: Resource[IO, Counter[IO, Int, (String, Int)]] = factory
  .counter("counter_total")
  .help("Describe what this metric does")
  .label[String]("string_label")
  .label[Int]("int_label")
  .contramap[Int](_.toDouble)
  .build
```

```scala mdoc:silent
val shortLabelledCounter: Resource[IO, Counter[IO, Short, (String, Int)]] =
  intLabelledCounter.map(_.contramap[Short](_.toInt))
```

## Contramapping Metric Labels

This can work as a nice alternative to
[providing a compile-time checked sequence of labels](#compile-time-checked-sequence-of-labels)

```scala mdoc:silent
case class LabelsClass(string: String, int: Int)

val updatedLabelsCounter: Resource[IO, Counter[IO, Double, LabelsClass]] = factory
  .counter("counter_total")
  .help("Describe what this metric does")
  .label[String]("string_label")
  .label[Int]("int_label")
  .contramapLabels[LabelsClass](c => (c.string, c.int))
  .build
```

[primitive]: ../metrics/primitive-metric-types.md
[derived]: ../metrics/derived-metric-types.md
[`MetricFactory`]: metric-factory.md
[`MetricRegistry`]: metric-registry.md
