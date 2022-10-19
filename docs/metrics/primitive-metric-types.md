## Primitive Metric Types

**For more detailed information on building metrics see the [Metrics DSL](../interface/dsl.md) section.**

The examples in this section assume you have imported the following and have created a
[`MetricFactory`](../interface/metric-factory.md):

```scala mdoc
import cats.effect._
import prometheus4cats._

val factory: MetricFactory[IO] = MetricFactory.noop[IO]
```

### `Counter`


```scala mdoc
factory.counter("my_counter_total").ofLong.help("Metric description")
```

### `Gauge`

```scala mdoc
factory.gauge("my_counter_total").ofLong.help("Metric description")
```

### `Histogram`


```scala mdoc
factory.histogram("my_counter_total").ofDouble.help("Metric description").buckets(0.1, 0.5, 1.0, 1.5, 2.0)
```
