## Prometheus4Cats

This library is designed to provide a tasteful Scala API for exposing [Prometheus] metrics. It is a mix between
Permutive's internal library and [Epimetheus]. See the [design page](design/design.md) for more information.

### Features

- [A fluent metric builder DSL](interface/dsl.md)
- [Metric name prefix and common labels](interface/metrics-factory.md)
- [Decoupled backend for registering metrics](interface/metrics-registry.md)

### Usage

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
import cats.effect._
import org.typelevel.log4cats._
import org.typelevel.log4cats.noop.NoOpLogger
import prometheus4cats._
import prometheus4cats.javasimpleclient._

// Java client requires a logger
implicit val logger: Logger[IO] = NoOpLogger.impl

for {
  regsitry <- JavaMetricsRegistry.default[IO]
  factory = MetricsFactory.builder.build(regsitry)

} yield ()
```

[Prometheus]: https://prometheus.io
[Epimetheus]: https://github.com/davenverse/epimetheus
