name := "common"

import Dependency._

libraryDependencies ++= loggingLibs.map(_ % "provided")
libraryDependencies ++= FlinkLibs.flinkCoreDeps.map(_ % "provided")
libraryDependencies ++= FlinkLibs.flinkConnectorFiles.map(_ % "provided")
libraryDependencies ++= FlinkLibs.flinkCommunityScalaApi

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

// Assembly options for publish and publishLocal sbt tasks
enablePlugins(AssemblyPlugin)

assembly / assemblyMergeStrategy := {
  case "logback-test.xml"                                  => MergeStrategy.discard
  case PathList(ps @ _*) if ps.last == "module-info.class" => MergeStrategy.discard // для зависимостей с Java 9+
  case PathList(ps @ _*) if ps.last endsWith ".conf"       => MergeStrategy.concat
  case PathList(ps @ _*) if ps.last endsWith ".properties" => MergeStrategy.concat
  case x                                                   => MergeStrategy.defaultMergeStrategy(x)
}

assembly / mainClass := Some("com.altuera.genesys.events.stats.app.Main")
