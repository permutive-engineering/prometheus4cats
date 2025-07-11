ThisBuild / scalaVersion           := "2.13.16"
ThisBuild / crossScalaVersions     := Seq("2.12.20", "2.13.16", "3.3.6")
ThisBuild / organization           := "com.permutive"
ThisBuild / versionPolicyIntention := Compatibility.BinaryCompatible

addCommandAlias("ci-test", "fix --check; versionPolicyCheck; mdoc; publishLocal; +test")
addCommandAlias("ci-docs", "github; mdoc; headerCreateAll; docusaurusPublishGhpages")
addCommandAlias("ci-publish", "versionCheck; github; ci-release")

lazy val documentation = project
  .dependsOn(prometheus4cats, `prometheus4cats-java`, `prometheus4cats-testing`)
  .enablePlugins(MdocPlugin)

lazy val website = project
  .settings(libraryDependencies ++= Dependencies.website)
  .dependsOn(prometheus4cats, `prometheus4cats-java`, `prometheus4cats-testing`)
  .enablePlugins(MdocPlugin, DocusaurusPlugin)
  .settings(mdocIn := baseDirectory.value / "docs")
  .settings(mdocOut := (Compile / target).value / "mdoc")
  .settings(watchTriggers += mdocIn.value.toGlob / "*.md")

lazy val prometheus4cats = module
  .settings(libraryDependencies ++= Dependencies.prometheus4cats)
  .settings(libraryDependencies ++= scalaVersion.value.on(2)("org.scala-lang" % "scala-reflect" % scalaVersion.value))
  .settings(libraryDependencies ++= scalaVersion.value.on(2)(Dependencies.shapeless))
  .settings(libraryDependencies ++= scalaVersion.value.on(2)(Dependencies.`kind-projector`))
  .settings(scalacOptions ++= scalaVersion.value.on(2)("-Wconf:cat=unused-nowarn:s"))

lazy val `prometheus4cats-testkit` = module
  .settings(libraryDependencies ++= Dependencies.`prometheus4cats-testkit`)
  .dependsOn(prometheus4cats)

lazy val `prometheus4cats-testing` = module
  .settings(libraryDependencies ++= Dependencies.`prometheus4cats-testing`)
  .dependsOn(prometheus4cats)
  .settings(scalacOptions ++= scalaVersion.value.on(2)("-Wconf:cat=unused-nowarn:s"))

lazy val `prometheus4cats-java` = module
  .settings(libraryDependencies ++= Dependencies.`prometheus4cats-java`)
  .settings(libraryDependencies ++= scalaVersion.value.on(2, 12)(Dependencies.`scala-collection-compat`))
  .dependsOn(prometheus4cats, `prometheus4cats-testkit` % "test->compile")
