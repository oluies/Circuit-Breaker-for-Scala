ThisBuild / scalaVersion := "3.3.4"
ThisBuild / organization := "org.circuitbreaker"
ThisBuild / version      := "0.1.0"

lazy val root = (project in file("."))
  .settings(
    name := "circuit-breaker-core",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "1.0.2" % Test
    ),
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Wunused:all"
    ),
    Test / parallelExecution := false
  )
