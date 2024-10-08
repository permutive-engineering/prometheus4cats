import sbt._

object Dependencies {

  lazy val prometheus4cats = Seq(
    "org.typelevel" %% "cats-core"           % "2.12.0",
    "org.typelevel" %% "cats-effect-kernel"  % "3.5.4",
    "org.typelevel" %% "cats-effect"         % "3.5.4"  % Test,
    "org.typelevel" %% "cats-effect-testkit" % "3.5.4"  % Test,
    "org.typelevel" %% "cats-laws"           % "2.12.0" % Test,
    "org.scalameta" %% "munit"               % "1.0.1"  % Test,
    "org.typelevel" %% "munit-cats-effect"   % "2.0.0"  % Test,
    "org.typelevel" %% "discipline-munit"    % "2.0.0"  % Test,
    "org.scalameta" %% "munit-scalacheck"    % "1.0.0"  % Test,
    "org.typelevel" %% "scalacheck-effect"   % "1.0.4"  % Test
  )

  lazy val `kind-projector` = compilerPlugin(("org.typelevel" % "kind-projector" % "0.13.3").cross(CrossVersion.full))

  lazy val shapeless = "com.chuusai" %% "shapeless" % "2.3.12"

  lazy val `prometheus4cats-testkit` = Seq(
    "org.typelevel" %% "cats-effect-testkit" % "3.5.4",
    "org.scalameta" %% "munit"               % "1.0.1",
    "org.typelevel" %% "munit-cats-effect"   % "2.0.0",
    "org.scalameta" %% "munit-scalacheck"    % "1.0.0",
    "org.typelevel" %% "scalacheck-effect"   % "1.0.4"
  )

  lazy val `prometheus4cats-testing` = Seq(
    "org.typelevel" %% "cats-effect-testkit" % "3.5.4",
    "org.scalameta" %% "munit"               % "1.0.1",
    "org.typelevel" %% "munit-cats-effect"   % "2.0.0",
    "org.scalameta" %% "munit-scalacheck"    % "1.0.0",
    "org.typelevel" %% "scalacheck-effect"   % "1.0.4"
  )

  lazy val `prometheus4cats-java` = Seq(
    "org.typelevel" %% "alleycats-core"  % "2.12.0",
    "org.typelevel" %% "cats-effect-std" % "3.5.4",
    "org.typelevel" %% "log4cats-core"   % "2.7.0",
    "io.prometheus"  % "simpleclient"    % "0.16.0",
    "org.typelevel" %% "log4cats-noop"   % "2.7.0" % Test
  )

  lazy val website = Seq(
    "org.typelevel" %% "cats-effect"   % "3.5.4",
    "org.typelevel" %% "log4cats-noop" % "2.7.0"
  )

  lazy val `scala-collection-compat` = "org.scala-lang.modules" %% "scala-collection-compat" % "2.12.0"

}
