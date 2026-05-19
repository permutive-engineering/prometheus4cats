import sbt._

object Dependencies {

  lazy val prometheus4cats = Seq(
    "org.typelevel" %% "cats-core"           % "2.13.0",
    "org.typelevel" %% "cats-effect-kernel"  % "3.6.3",
    "org.typelevel" %% "cats-effect"         % "3.6.3"  % Test,
    "org.typelevel" %% "cats-effect-testkit" % "3.6.3"  % Test,
    "org.typelevel" %% "cats-laws"           % "2.13.0" % Test,
    "org.scalameta" %% "munit"               % "1.2.1"  % Test,
    "org.typelevel" %% "munit-cats-effect"   % "2.1.0"  % Test,
    "org.typelevel" %% "discipline-munit"    % "2.0.0"  % Test,
    "org.scalameta" %% "munit-scalacheck"    % "1.2.0"  % Test,
    "org.typelevel" %% "scalacheck-effect"   % "1.0.4"  % Test
  )

  lazy val `kind-projector` = compilerPlugin(("org.typelevel" % "kind-projector" % "0.13.4").cross(CrossVersion.full))

  lazy val shapeless = "com.chuusai" %% "shapeless" % "2.3.13"

  lazy val `prometheus4cats-testkit` = Seq(
    "org.typelevel" %% "cats-effect-testkit" % "3.6.3",
    "org.scalameta" %% "munit"               % "1.2.1",
    "org.typelevel" %% "munit-cats-effect"   % "2.1.0",
    "org.scalameta" %% "munit-scalacheck"    % "1.2.0",
    "org.typelevel" %% "scalacheck-effect"   % "1.0.4"
  )

  lazy val `prometheus4cats-testing` = Seq(
    "org.typelevel" %% "cats-effect-testkit" % "3.6.3",
    "org.scalameta" %% "munit"               % "1.2.1",
    "org.typelevel" %% "munit-cats-effect"   % "2.1.0",
    "org.scalameta" %% "munit-scalacheck"    % "1.2.0",
    "org.typelevel" %% "scalacheck-effect"   % "1.0.4"
  )

  lazy val `prometheus4cats-java` = Seq(
    "org.typelevel" %% "alleycats-core"  % "2.13.0",
    "org.typelevel" %% "cats-effect-std" % "3.6.3",
    // simpleclient (0.x) — backs the legacy `prometheus4cats.javasimpleclient` adapter. Will be removed
    // once the new `prometheus4cats.javaclient` adapter on prometheus-metrics-core 1.x is complete.
    "io.prometheus" % "simpleclient"         % "0.16.0",
    "io.prometheus" % "simpleclient_hotspot" % "0.16.0",
    // prometheus-metrics-core (1.x) — backs the new `prometheus4cats.javaclient` adapter. Adds native
    // histogram support. Coexists with simpleclient during migration: different package namespaces
    // (`io.prometheus.metrics.*` vs `io.prometheus.client.*`) so there's no classpath conflict.
    "io.prometheus" % "prometheus-metrics-core"                % "1.6.1",
    "io.prometheus" % "prometheus-metrics-instrumentation-jvm" % "1.6.1"
  )

  lazy val website = Seq(
    "org.typelevel" %% "cats-effect" % "3.6.3"
  )

}
