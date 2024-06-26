name := "long-ride-alerts"

import Dependency._

libraryDependencies ++= loggingLibs //.map(_ % "provided")
libraryDependencies ++= FlinkLibs.flinkCoreDeps //.map(_ % "provided")
libraryDependencies ++= FlinkLibs.flinkConnectorFiles //.map(_ % "provided")
libraryDependencies ++= FlinkLibs.flinkCommunityScalaApi
//Test
libraryDependencies ++= TestLibs.junit
libraryDependencies ++= TestLibs.assertJ
libraryDependencies ++= FlinkLibs.flinkTestDeps

//disable publishing the main jar produced by `package`
Compile / packageBin / publishArtifact := false

// make run command include the provided dependencies
Compile / run := Defaults
  .runTask(
    Compile / fullClasspath,
    Compile / run / mainClass,
    Compile / run / runner
  )
  .evaluated

// stays inside the sbt console when we press "ctrl-c" while a Flink programme executes with "run" or "runMain"
Compile / run / fork := true
Global / cancelable := true

Test / fork := true
