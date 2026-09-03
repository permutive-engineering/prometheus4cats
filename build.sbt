ThisBuild / scalaVersion           := "2.13.18"
ThisBuild / crossScalaVersions     := Seq("2.13.18", "3.3.8")
ThisBuild / organization           := "com.permutive"
ThisBuild / versionPolicyIntention := Compatibility.BinaryAndSourceCompatible

addCommandAlias("ci-test", "fix --check; versionPolicyCheck; mdoc; publishLocal; +test")
addCommandAlias("ci-docs", "github; mdoc; headerCreateAll; docusaurusPublishGhpages")
addCommandAlias("ci-publish", "versionCheck; github; ci-release")

lazy val documentation = project
  .settings(libraryDependencies ++= Dependencies.documentation)
  .dependsOn(prometheus4cats, `prometheus4cats-java`)
  .enablePlugins(MdocPlugin)

lazy val website = project
  .settings(libraryDependencies ++= Dependencies.website)
  .dependsOn(prometheus4cats, `prometheus4cats-java`)
  .enablePlugins(MdocPlugin, DocusaurusPlugin)
  .settings(mdocIn := baseDirectory.value / "docs")
  .settings(mdocOut := (Compile / target).value / "mdoc")
  .settings(watchTriggers += mdocIn.value.toGlob / "*.md")
  // `@SUPPORTED_SCALA@` in markdown gets replaced with e.g. "2.13 and 3.3" — sourced from
  // `crossScalaVersions` so the docs always reflect the actual cross-build matrix. Updating
  // crossScalaVersions in this file is the single point of change.
  .settings(mdocVariables += "SUPPORTED_SCALA" -> {
    val binaries = (ThisBuild / crossScalaVersions).value
      // NOTE(take(2)): "2.13.18" → "2.13"
      .map(_.split('.').take(2).mkString("."))
      .distinct
    binaries match {
      case Seq(one) => one
      case versions => versions.init.mkString(", ") + " and " + versions.last
    }
  })

lazy val prometheus4cats = module
  .settings(libraryDependencies ++= Dependencies.prometheus4cats)
  .settings(libraryDependencies ++= scalaVersion.value.on(2)("org.scala-lang" % "scala-reflect" % scalaVersion.value))
  .settings(libraryDependencies ++= scalaVersion.value.on(2)(Dependencies.shapeless))
  .settings(libraryDependencies ++= scalaVersion.value.on(2)(Dependencies.`kind-projector`))
  .settings(scalacOptions ++= scalaVersion.value.on(2)("-Wconf:cat=unused-nowarn:s"))

lazy val `prometheus4cats-testkit` = module
  .settings(libraryDependencies ++= Dependencies.`prometheus4cats-testkit`)
  .dependsOn(prometheus4cats)

lazy val `prometheus4cats-java` = module
  .settings(libraryDependencies ++= Dependencies.`prometheus4cats-java`)
  .dependsOn(prometheus4cats, `prometheus4cats-testkit` % "test->compile")

/** Local-only sandbox app for poking at metric shapes against a real Prometheus + Grafana stack. Not published. The
  * docker-compose + Prometheus/Grafana config live under `modules/sandbox/src/main/resources/`. Run with `sbt
  * sandbox/run`.
  */
lazy val sandbox = module
  .settings(libraryDependencies ++= Dependencies.sandbox)
  .settings(publish / skip := true)
  // Regression guard for the v6 DSL: factory methods (`counter`, `gauge`, `histogram`, `summary`)
  // must compile without the `higherKinds` language feature enabled at the consumer. Removing
  // this flag here exercises the same scalac setup downstream services have (tpolecat defaults
  // don't include `-language:higherKinds`). If a future change re-introduces a higher-kinded
  // implicit on the factory return types, the sandbox compile will catch it.
  .settings(Compile / scalacOptions -= "-language:higherKinds")
  .dependsOn(prometheus4cats, `prometheus4cats-java`)
