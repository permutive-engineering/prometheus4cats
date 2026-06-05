# Java Registry

The Java registry implements [`MetricRegistry`], wrapping the [Prometheus Java library]. This provides interoperability
with anything that depends on the Java library.

> ℹ️ The Java Registry does add a runtime constraint that goes beyond constraints that Prometheus itself imposes:
> You cannot have two metrics of the same name with different labels.
> [This issue](https://github.com/prometheus/client_java/issues/696) describes the problem.

See the example below on how to use the Java Registry:

```scala mdoc:silent
import cats.effect.IO
import cats.effect.Resource

import io.prometheus.metrics.model.registry.PrometheusRegistry

import prometheus4cats.MetricFactory
import prometheus4cats.javaclient.JavaMetricRegistry

// Construct a Java registry using a default PrometheusRegistry
val default: Resource[IO, JavaMetricRegistry[IO]] =
  JavaMetricRegistry.Builder[IO]().build

// Construct a Java registry using a custom PrometheusRegistry
val custom: Resource[IO, JavaMetricRegistry[IO]] =
  JavaMetricRegistry.Builder[IO]().withRegistry(new PrometheusRegistry()).build

// Use the registry to get a factory
val factory: Resource[IO, MetricFactory[IO]] =
  custom.map(MetricFactory.builder.build(_))
```

## Implementation Notes

As per the [`MetricRegistry`] interface, this implementation returns Cats-Effect `Resource`s to indicate a metric
being registered and ultimately de-registered. Requesting a metric of the same name (and labels) multiple times **will
not** result in an error, instead you will be returned the currently registered metric. The Java registry wrapper
implements reference counting to ensure that a metric will only be de-registered when there are no more references to
it or when the wrapper's surrounding `Resource` is finalized.

[metrics]: ../metrics/primitive-metric-types.md

[`MetricRegistry`]: ../interface/metric-registry.md

[Prometheus Java library]: https://github.com/prometheus/client_java/
