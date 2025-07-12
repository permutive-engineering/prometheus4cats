# Functional Prometheus Metrics API for Scala

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
  "com.permutive" %% "prometheus4cats" % "4.0.2",
  "com.permutive" %% "prometheus4cats-java" % "4.0.2"
)
```

```scala
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

If you want to know more about all the library's features, please head on to [its website](https://permutive-engineering.github.io/prometheus4cats/) where you will find much more information.

## Contributors for this project

| <a href="https://github.com/janstenpickle"><img alt="janstenpickle" src="https://avatars.githubusercontent.com/u/1926225?v=4&s=120" width="120px" /></a> | <a href="https://github.com/TimWSpence"><img alt="TimWSpence" src="https://avatars.githubusercontent.com/u/3360080?v=4&s=120" width="120px" /></a> | <a href="https://github.com/alejandrohdezma"><img alt="alejandrohdezma" src="https://avatars.githubusercontent.com/u/9027541?v=4&s=120" width="120px" /></a> | <a href="https://github.com/bastewart"><img alt="bastewart" src="https://avatars.githubusercontent.com/u/10614835?v=4&s=120" width="120px" /></a> | <a href="https://github.com/desbo"><img alt="desbo" src="https://avatars.githubusercontent.com/u/1064734?v=4&s=120" width="120px" /></a> |
| :--: | :--: | :--: | :--: | :--: |
| <a href="https://github.com/janstenpickle"><sub><b>janstenpickle</b></sub></a> | <a href="https://github.com/TimWSpence"><sub><b>TimWSpence</b></sub></a> | <a href="https://github.com/alejandrohdezma"><sub><b>alejandrohdezma</b></sub></a> | <a href="https://github.com/bastewart"><sub><b>bastewart</b></sub></a> | <a href="https://github.com/desbo"><sub><b>desbo</b></sub></a> |

[Prometheus]: https://prometheus.io
[Epimetheus]: https://github.com/davenverse/epimetheus
[Cats-Effect]: https://typelevel.org/cats-effect
