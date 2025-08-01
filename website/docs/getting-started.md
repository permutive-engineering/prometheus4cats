---
sidebar_position: 1
---

# Getting Started

This library is designed to provide a tasteful Scala API for exposing [Prometheus] metrics using [Cats-Effect]. It is a
mix between Permutive's internal library and [Epimetheus]. See the [design page](design.md) for more information.

## Features

- [A fluent metric builder DSL](interface/dsl.md)
- [Metric name prefix and common labels](interface/metric-factory.md)
- [Decoupled backend for registering metrics](interface/metric-registry.md)

## Quickstart Usage

This library is currently available for Scala binary versions 2.12 and 2.13 and 3.2.

**For detailed code examples see the [metrics DSL](interface/dsl.md) documentation.**

To use the latest version, include the following in your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  "com.permutive" %% "prometheus4cats" % "@VERSION@",
  "com.permutive" %% "prometheus4cats-java" % "@VERSION@"
)
```

```scala mdoc:silent
import cats.effect.IO

import prometheus4cats.MetricFactory
import prometheus4cats.javasimpleclient.JavaMetricRegistry

val counterResource =
  for {
    registry <- JavaMetricRegistry.Builder[IO]().build
    factory = MetricFactory.builder.build(registry)
    counter <- factory.counter("my_counter_total")
                 .ofLong
                 .help("My Counter")
                 .label[String]("some_label")
                 .build
  } yield counter

counterResource.use { counter => counter.inc("Some label value") }
```

[Prometheus]: https://prometheus.io
[Epimetheus]: https://github.com/davenverse/epimetheus
[Cats-Effect]: https://typelevel.org/cats-effect
