## Java Registry

The Java registry implements both [`MetricRegistry`] and [`CallbackRegistry`] using the [Prometheus Java library]. This
provides interoperability with anything that depends on the Java library.

> ℹ️ The Java Registry does add a runtime constraint that go beyond constraints that Prometheus itself imposes:
> You cannot have two metrics of the same name with different labels.
> [This issue](https://github.com/prometheus/client_java/issues/696) describes the problem.

See the example below on how to use the Java Registry:

```scala mdoc:silent
import cats.effect._
import io.prometheus.client.CollectorRegistry
import org.typelevel.log4cats.noop.NoOpLogger
import prometheus4cats._
import prometheus4cats.javasimpleclient.JavaMetricRegistry

implicit val logger = NoOpLogger.impl[IO] // a logger is required to construct the Java registry

// Construct a Java regisitry using the default collector registry
val default: Resource[IO, JavaMetricRegistry[IO]] = JavaMetricRegistry.default[IO]()

// Construct a Java registry using a custom collector registry
val custom: Resource[IO, JavaMetricRegistry[IO]] =
  JavaMetricRegistry.fromSimpleClientRegistry[IO](new CollectorRegistry())

// Use the registry to get a factory
val factory: Resource[IO, MetricFactory.WithCallbacks[IO]] =
  custom.map(MetricFactory.builder.build(_))
```

[`MetricRegistry`]: ../interface/metric-registry.md
[`CallbackRegistry`]: ../interface/callback-registry.md

[Prometheus Java library]: https://github.com/prometheus/client_java/
