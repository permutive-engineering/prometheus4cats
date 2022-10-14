## Metrics DSL

The metrics DSL provides a fluent API for constructing [primitive] and [derived] metrics from a [`MetricFactory`].

The examples in this section assume you have imported the following and have created a [`MetricFactory`]:

```scala mdoc
import cats.effect._
import prometheus4cats._

val factory: MetricFactory[IO] = MetricFactory.noop[IO]
```

### Refined Types

Value classes exist for metric and label names that are refined at compile time from string literals. It is also
possible to refine at runtime, where the result is returned in an `Either`.

The value classes used by the DSL are as follows:

- `Counter.Name`
- `Gauge.Name`
- `Histogram.Name`
- `Label.Name`
- `Metric.Help`

When used in the DSL with string literals the value classes are implicitly resolved, so there is no need to wrap every
value.

### Choosing a Primitive Metric

```scala mdoc:silent
factory.counter("counter_total")
factory.gauge("gauge")
factory.histogram("histogram")
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

```scala mdoc:silent
val simpleCounter = factory
  .counter("counter_total")
  .ofDouble
  .help("Describe what this metric does")

simpleCounter.build
simpleCounter.resource
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

tupleLabelledCounter.build.flatMap(_.inc(2.0, ("label_value", MyClass("label_value"))))
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

classLabelledCounter.build.flatMap(_.inc(2.0, MyMultiClass("label_value", 42)))
```

#### Unchecked Sequence of Labels

```scala mdoc:silent
val unsafeLabelledCounter = factory
  .counter("counter_total")
  .ofDouble
  .help("Describe what this metric does")
  .unsafeLabels(Label.Name("label1"), Label.Name("label2"))

val labels = Map(Label.Name("label1") -> "label1_value", Label.Name("label2") -> "label1_value")
unsafeLabelledCounter.build.flatMap(_.inc(3.0, labels))
```

### Contramapping a Metric Type

#### Simple Metric

```scala mdoc:silent
val intCounter: IO[Counter[IO, Int]] = factory
  .counter("counter_total")
  .ofLong
  .help("Describe what this metric does")
  .contramap[Int](_.toLong)
  .build
```

```scala mdoc:silent
val shortCounter: IO[Counter[IO, Short]] = intCounter.map(_.contramap[Short](_.toInt))
```

#### Labelled Metric

```scala mdoc:silent
val intLabelledCounter: IO[Counter.Labelled[IO, Int, (String, Int)]] = factory
  .counter("counter_total")
  .ofLong
  .help("Describe what this metric does")
  .label[String]("string_label")
  .label[Int]("int_label")
  .contramap[Int](_.toLong)
  .build
```

```scala mdoc:silent
val shortLabelledCounter: IO[Counter.Labelled[IO, Short, (String, Int)]] =
  intLabelledCounter.map(_.contramap[Short](_.toInt))
```

### Contramapping Metric Labels

This can work as a nice alternative to
[providing a compile-time checked sequence of labels](#compile-time-checked-sequence-of-labels)

```scala mdoc:silent
case class LabelsClass(string: String, int: Int)

val updatedLabelsCounter: IO[Counter.Labelled[IO, Long, LabelsClass]] = factory
  .counter("counter_total")
  .ofLong
  .help("Describe what this metric does")
  .label[String]("string_label")
  .label[Int]("int_label")
  .contramapLabels[LabelsClass](c => (c.string, c.int))
  .build
```

[primitive]: ../metrics/primitive-metric-types.md
[derived]: ../metrics/derived-metric-types.md
[`MetricFactory`]: metric-factory.md
