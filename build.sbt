import sbtassembly.AssemblyPlugin

ThisBuild / useCoursier := false // https://www.scala-sbt.org/1.x/docs/sbt-1.3-Release-Notes.html#Library+management+with+Coursier

ThisBuild / version := "0.0.1"

ThisBuild / scalaVersion := "2.13.12"

lazy val compileSettings =
  scalacOptions ++= Seq(
    "-unchecked",
    "-deprecation",
    "-feature",
    "-language:implicitConversions",
    "-language:existentials",
    "-language:higherKinds",
    "-Ywarn-unused",
    "-Ywarn-dead-code",
    "-Ymacro-annotations"
  )

lazy val commonProject = (project in file("common"))
  .settings(compileSettings)

lazy val hourlyTips = (project in file("hourly-tips"))
  .settings(compileSettings)
  .dependsOn(commonProject % "compile->compile;test->test")

lazy val longRideAlerts = (project in file("long-ride-alerts"))
  .settings(compileSettings)
  .dependsOn(commonProject % "compile->compile;test->test")

lazy val rideCleansing = (project in file("ride-cleansing"))
  .settings(compileSettings)
  .dependsOn(commonProject % "compile->compile;test->test")

lazy val ridesAndFares = (project in file("rides-and-fares"))
  .settings(compileSettings)
  .dependsOn(commonProject % "compile->compile;test->test")

lazy val root = (project in file("."))
  .aggregate(
    commonProject,
    hourlyTips,
    longRideAlerts,
    rideCleansing,
    ridesAndFares
  )
