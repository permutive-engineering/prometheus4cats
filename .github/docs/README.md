# @DESCRIPTION@

This library is designed to provide a tasteful Scala API for exposing [Prometheus] metrics using [Cats-Effect]. It is a
mix between Permutive's internal library and [Epimetheus]. See the [design page](https://permutive-engineering.github.io/prometheus4cats/docs/design) for more information.

## Features

- [A fluent metric builder DSL](https://permutive-engineering.github.io/prometheus4cats/docs/interface/dsl)
- [Metric name prefix and common labels](https://permutive-engineering.github.io/prometheus4cats/docs/interface/metric-factory)
- [Decoupled backend for registering metrics](https://permutive-engineering.github.io/prometheus4cats/docs/interface/metric-registry)

## Quickstart Usage

This library is currently available for Scala binary versions 2.12 and 2.13 and 3.2.

**For detailed code examples see the [metrics DSL](https://permutive-engineering.github.io/prometheus4cats/docs/interface/dsl) documentation.**

To use the latest version, include the following in your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  "com.permutive" %% "prometheus4cats" % "@VERSION@",
  "com.permutive" %% "prometheus4cats-java" % "@VERSION@"
)
```

```scala mdoc:silent
import cats.effect.IO

import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger

import prometheus4cats.MetricFactory
import prometheus4cats.javasimpleclient.JavaMetricRegistry

// Java client requires a logger
implicit val logger: Logger[IO] = NoOpLogger.impl

val counterResource =
  for {
    registry <- JavaMetricRegistry.Builder().build[IO]
    factory = MetricFactory.builder.build(registry)
    counter <- factory.counter("my_counter_total")
                 .ofLong
                 .help("My Counter")
                 .label[String]("some_label")
                 .build
  } yield counter

counterResource.use { counter => counter.inc("Some label value") }
```

If you want to know more about all the library's features, please head on to [its website](https://permutive-engineering.github.io/prometheus4cats/) where you will find much more information.

## Contributors for this project

@CONTRIBUTORS_TABLE@

[Prometheus]: https://prometheus.io
[Epimetheus]: https://github.com/davenverse/epimetheus
[Cats-Effect]: https://typelevel.org/cats-effect
