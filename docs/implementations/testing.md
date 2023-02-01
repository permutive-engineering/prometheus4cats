# Testing Registry

There exists a testing `MetricRegistry` implementation which allows you to check the value
history of any created metrics. For `Histogram` and `Summary` this is a `Chain` of all
the values that have been `observe`d for that metric. For `Counter` and `Gauge` this is
the history of the current value of the metric over time.

```scala mdoc:silent
import cats.syntax.all._
import cats.data.Chain
import cats.effect._
import prometheus4cats._
import prometheus4cats.testing._

TestingMetricRegistry[IO].flatMap { reg =>
  val factory = MetricFactory.builder.build(reg)
  factory
    .counter("counter_total")
    .ofDouble
    .help("Describe what this metric does")
    .build
    .use { counter =>
      counter.inc >> counter.inc(2.0)
        reg.counterHistory(
          Counter.Name("counter_total"),
          Metric.CommonLabels.empty
        ).flatMap { hist =>
          IO(hist === Some(Chain(0.0, 1.0, 3.0)))
        }
    }
}
```


