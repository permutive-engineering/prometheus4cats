ThisBuild / scalaVersion           := "2.13.14"
ThisBuild / crossScalaVersions     := Seq("2.12.19", "2.13.14", "3.3.3")
ThisBuild / organization           := "com.permutive"
ThisBuild / versionPolicyIntention := Compatibility.BinaryCompatible

addCommandAlias("ci-test", "fix --check; versionPolicyCheck; mdoc; publishLocal; +test")
addCommandAlias("ci-docs", "github; mdoc; headerCreateAll")
addCommandAlias("ci-publish", "versionCheck; github; ci-release")

lazy val documentation = project
  .enablePlugins(MdocPlugin)

lazy val prometheus4cats = module
  .settings(libraryDependencies ++= Dependencies.prometheus4cats)
  .settings(libraryDependencies ++= scalaVersion.value.on(2)("org.scala-lang" % "scala-reflect" % scalaVersion.value))
  .settings(libraryDependencies ++= scalaVersion.value.on(2)(Dependencies.shapeless))
  .settings(libraryDependencies ++= scalaVersion.value.on(2)(Dependencies.`kind-projector`))
  .settings(scalacOptions += "-Wconf:cat=unused-nowarn:s")
  .settings(scalacOptions ++= scalaVersion.value.on(3)("-Ykind-projector"))

lazy val `prometheus4cats-testkit` = module
  .settings(libraryDependencies ++= Dependencies.`prometheus4cats-testkit`)
  .dependsOn(prometheus4cats)

lazy val `prometheus4cats-testing` = module
  .settings(libraryDependencies ++= Dependencies.`prometheus4cats-testing`)
  .dependsOn(prometheus4cats)

lazy val `prometheus4cats-java` = module
  .settings(libraryDependencies ++= Dependencies.`prometheus4cats-java`)
  .settings(libraryDependencies ++= scalaVersion.value.on(2, 12)(Dependencies.`scala-collection-compat`))
  .dependsOn(prometheus4cats, `prometheus4cats-testkit` % "test->compile")
