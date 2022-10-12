// https://typelevel.org/sbt-typelevel/faq.html#what-is-a-base-version-anyway
ThisBuild / tlBaseVersion := "0.0" // your current series x.y

ThisBuild / organization := "com.permutive"
ThisBuild / organizationName := "Permutive"
ThisBuild / startYear := Some(2022)
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(
  // your GitHub handle and name
  tlGitHubDev("TimWSpence", "Tim Spence")
)

// publish to s01.oss.sonatype.org (set to true to publish to oss.sonatype.org instead)
ThisBuild / tlSonatypeUseLegacyHost := false

val Scala213 = "2.13.8"

val CatsEffect = "3.3.14"

val Log4Cats = "2.5.0"

val Munit = "0.7.29"

val MunitCe3 = "1.0.7"

val ScalacheckEffect = "1.0.4"

ThisBuild / crossScalaVersions := Seq("2.12.15", "3.2.0", Scala213)
ThisBuild / scalaVersion := crossScalaVersions.value.last

lazy val root = tlCrossRootProject.aggregate(core, testkit, prometheus)

lazy val core = project
  .in(file("core"))
  .settings(
    name := "openmetrics4s",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core" % "2.8.0",
      "org.typelevel" %%% "cats-effect-kernel" % CatsEffect,
      "org.typelevel" %%% "cats-effect" % CatsEffect % Test,
      "org.typelevel" %% "cats-effect-testkit" % CatsEffect % Test,
      "org.scalameta" %%% "munit" % Munit % Test,
      "org.typelevel" %% "munit-cats-effect-3" % MunitCe3,
      "org.scalameta" %% "munit-scalacheck" % Munit % Test,
      "org.typelevel" %% "scalacheck-effect-munit" % ScalacheckEffect % Test
    ),
    libraryDependencies ++= PartialFunction
      .condOpt(CrossVersion.partialVersion(scalaVersion.value)) { case Some((2, _)) =>
        Seq(
          "org.scala-lang" % "scala-reflect" % scalaVersion.value,
          "com.chuusai" %% "shapeless" % "2.3.9"
        )
      }
      .toList
      .flatten,
    scalacOptions := {
      // Scala 3 macros won't compile with the default Typelevel settings
      if (tlIsScala3.value) Seq("-Ykind-projector") else scalacOptions.value
    }
  )

lazy val testkit = project
  .in(file("testkit"))
  .settings(
    name := "openmetrics4s-testkit",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect-testkit" % CatsEffect,
      "org.scalameta" %% "munit" % Munit,
      "org.typelevel" %% "munit-cats-effect-3" % MunitCe3,
      "org.scalameta" %% "munit-scalacheck" % Munit,
      "org.typelevel" %% "scalacheck-effect-munit" % ScalacheckEffect
    )
  )
  .dependsOn(core)

lazy val prometheus =
  project
    .in(file("prometheus"))
    .settings(
      name := "openmetrics4s-prometheus",
      libraryDependencies ++= Seq(
        "org.typelevel" %% "cats-effect-std" % CatsEffect,
        "org.typelevel" %% "log4cats-core" % Log4Cats,
        "io.prometheus" % "simpleclient" % "0.16.0",
        "org.typelevel" %% "log4cats-noop" % Log4Cats % Test
      ),
      libraryDependencies ++= PartialFunction
        .condOpt(CrossVersion.partialVersion(scalaVersion.value)) { case Some((2, 12)) =>
          "org.scala-lang.modules" %% "scala-collection-compat" % "2.8.1"
        }
        .toList
    )
    .dependsOn(core, testkit % "test->compile")

lazy val docs = project.in(file("site"))
