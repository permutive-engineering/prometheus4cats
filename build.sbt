import laika.ast.LengthUnit._
import laika.ast.{Path => LPath, _}
import laika.helium.Helium
import laika.helium.config.Favicon
import laika.helium.config.HeliumIcon
import laika.helium.config.IconLink
import laika.helium.config.ImageLink
import laika.theme.config.Color
import laika.config._
import laika.rewrite.link._
import org.typelevel.sbt.site.TypelevelProject

// https://typelevel.org/sbt-typelevel/faq.html#what-is-a-base-version-anyway
ThisBuild / tlBaseVersion := "2.0" // your current series x.y

ThisBuild / organization := "com.permutive"
ThisBuild / organizationName := "Permutive"
ThisBuild / startYear := Some(2022)
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(
  tlGitHubDev("janstenpickle", "Chris Jansen"),
  tlGitHubDev("TimWSpence", "Tim Spence")
)

// publish to s01.oss.sonatype.org (set to true to publish to oss.sonatype.org instead)
ThisBuild / tlSonatypeUseLegacyHost := false

val Scala213 = "2.13.11"

val Cats = "2.9.0"

val CatsEffect = "3.5.1"

val Log4Cats = "2.6.0"

val Munit = "0.7.29"

val MunitCe3 = "1.0.7"

val ScalacheckEffect = "1.0.4"

ThisBuild / crossScalaVersions := Seq("2.12.18", "3.3.0", Scala213)
ThisBuild / scalaVersion := crossScalaVersions.value.last

ThisBuild / tlSitePublishBranch := Some("main")

ThisBuild / tlSonatypeUseLegacyHost := true

lazy val root = tlCrossRootProject.aggregate(core, testkit, testing, java, unidocs)

lazy val core = project
  .in(file("core"))
  .settings(
    name := "prometheus4cats",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core" % Cats,
      "org.typelevel" %%% "cats-effect-kernel" % CatsEffect,
      "org.typelevel" %%% "cats-effect" % CatsEffect % Test,
      "org.typelevel" %% "cats-effect-testkit" % CatsEffect % Test,
      "org.typelevel" %%% "cats-laws" % Cats % Test,
      "org.scalameta" %%% "munit" % Munit % Test,
      "org.typelevel" %% "munit-cats-effect-3" % MunitCe3 % Test,
      "org.typelevel" %%% "discipline-munit" % "1.0.9" % Test,
      "org.scalameta" %% "munit-scalacheck" % Munit % Test,
      "org.typelevel" %% "scalacheck-effect-munit" % ScalacheckEffect % Test
    ),
    libraryDependencies ++= PartialFunction
      .condOpt(CrossVersion.partialVersion(scalaVersion.value)) { case Some((2, _)) =>
        Seq(
          "org.scala-lang" % "scala-reflect" % scalaVersion.value,
          "com.chuusai" %% "shapeless" % "2.3.10"
        )
      }
      .toList
      .flatten,
    scalacOptions += "-Wconf:cat=unused-nowarn:s",
    scalacOptions := {
      // Scala 3 macros won't compile with the default Typelevel settings
      if (tlIsScala3.value) Seq("-Ykind-projector") else scalacOptions.value
    }
  )

lazy val testkit = project
  .in(file("testkit"))
  .settings(
    name := "prometheus4cats-testkit",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect-testkit" % CatsEffect,
      "org.scalameta" %% "munit" % Munit,
      "org.typelevel" %% "munit-cats-effect-3" % MunitCe3,
      "org.scalameta" %% "munit-scalacheck" % Munit,
      "org.typelevel" %% "scalacheck-effect-munit" % ScalacheckEffect
    )
  )
  .dependsOn(core)

lazy val testing = project
  .in(file("testing"))
  .settings(
    name := "prometheus4cats-testing",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect-testkit" % CatsEffect,
      "org.scalameta" %% "munit" % Munit,
      "org.typelevel" %% "munit-cats-effect-3" % MunitCe3,
      "org.scalameta" %% "munit-scalacheck" % Munit,
      "org.typelevel" %% "scalacheck-effect-munit" % ScalacheckEffect
    )
  )
  .dependsOn(core)

lazy val java =
  project
    .in(file("java"))
    .settings(
      name := "prometheus4cats-java",
      libraryDependencies ++= Seq(
        "org.typelevel" %%% "alleycats-core" % Cats,
        "org.typelevel" %% "cats-effect-std" % CatsEffect,
        "org.typelevel" %% "log4cats-core" % Log4Cats,
        "io.prometheus" % "simpleclient" % "0.16.0",
        "org.typelevel" %% "log4cats-noop" % Log4Cats % Test
      ),
      libraryDependencies ++= PartialFunction
        .condOpt(CrossVersion.partialVersion(scalaVersion.value)) { case Some((2, 12)) =>
          "org.scala-lang.modules" %% "scala-collection-compat" % "2.11.0"
        }
        .toList
    )
    .dependsOn(core, testkit % "test->compile")

lazy val docs = project
  .in(file("site"))
  .settings(
    tlSiteHeliumConfig := Helium.defaults.site
      .metadata(
        title = Some("Prometheus4Cats"),
        language = Some("en")
      )
      .site
      .darkMode
      .disabled
      .site
      .themeColors(
        primary = Color.hex("000000"),
        primaryMedium = Color.hex("ffcee3"),
        primaryLight = Color.hex("ffcee3"),
        secondary = Color.hex("8ed1fc"),
        text = Color.hex("000000"),
        background = Color.rgba(0, 0, 0, 0),
        bgGradient = (Color.hex("ffffff"), Color.hex("ffffff"))
      )
      .site
      .layout(
        contentWidth = px(860),
        navigationWidth = px(275),
        topBarHeight = px(50),
        defaultBlockSpacing = px(10),
        defaultLineHeight = 1.5,
        anchorPlacement = laika.helium.config.AnchorPlacement.Right
      )
      .site
      .favIcons(
        Favicon.internal(LPath.Root / "img" / "icon-150x150.png", "32x32"),
        Favicon.internal(LPath.Root / "img" / "icon-300x300.png", "192x192")
      )
      .site
      .topNavigationBar(
        homeLink = ImageLink.external(
          "https://permutive.com",
          Image.internal(LPath.Root / "img" / "symbol.svg")
        ),
        navLinks = tlSiteApiUrl.value.toList.map { url =>
          IconLink.external(
            url.toString,
            HeliumIcon.api,
            options = Styles("svg-link")
          )
        } ++ List(
          IconLink.external(
            scmInfo.value.fold("https://github.com/permutive-engineering")(_.browseUrl.toString),
            HeliumIcon.github,
            options = Styles("svg-link")
          )
        )
      ),
    laikaConfig := LaikaConfig.defaults
      .withConfigValue(
        LinkConfig(
          targets = Seq(
            TargetDefinition("Prometheus Java Client", ExternalTarget("https://github.com/prometheus/client_java/")),
            TargetDefinition("Prometheus", ExternalTarget("https://prometheus.io"))
          ),
          sourceLinks =
            Seq(SourceLinks(baseUri = "https://github.com/permutive-engineering/prometheus4cats", suffix = "scala"))
        )
      ),
    tlSiteApiPackage := Some("prometheus4cats"),
    tlSiteRelatedProjects ++= Seq(
      TypelevelProject.CatsEffect,
      ("epimetheus", new URL("https://github.com/davenverse/epimetheus"))
    ),
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-effect" % CatsEffect,
      "org.typelevel" %% "log4cats-noop" % Log4Cats
    ),
    scalacOptions := Seq()
  )
  .dependsOn(core, java, testing)
  .enablePlugins(TypelevelSitePlugin)

lazy val unidocs = project
  .in(file("unidocs"))
  .enablePlugins(TypelevelUnidocPlugin) // also enables the ScalaUnidocPlugin
  .settings(
    name := "prometheus4cats-docs",
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(
      core,
      testkit,
      java
    )
  )
