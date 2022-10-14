## Primitive Metric Types

**For more detailed information on building metrics see the [Metrics DSL](../interface/dsl.md) section.**

The examples in this section assume you have imported the following and have created a
[`MetricsFactory`](../interface/metrics-factory.md):

```scala mdoc
import cats.effect._
import prometheus4cats._

val factory: MetricsFactory[IO] = MetricsFactory.noop[IO]
```

### Counter



```scala mdoc
factory.counter("my_counter_total")
```

