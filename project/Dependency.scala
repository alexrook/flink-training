import sbt._
import sbt.librarymanagement.DependencyBuilders

object Dependency {

  lazy val loggingLibs = {
    val slf4jV = "2.0.13"
    val logbackVersion = "1.4.14"
    Seq(
      "org.slf4j" % "slf4j-api" % slf4jV,
      "ch.qos.logback" % "logback-classic" % logbackVersion
    )
  }

  object TestLibs {

    lazy val junit =
      Seq(
        "junit" % "junit" % "4.13",
        // see https://www.scala-sbt.org/1.x/docs/Testing.html#JUnit
        "com.github.sbt" % "junit-interface" % "0.13.3"
      )

    lazy val assertJ =
      Seq(
        "org.assertj" % "assertj-core" % "3.20.2"
      )

  }

  object FlinkLibs {
    val flinkV = "1.18.1"

    lazy val flinkCommunityScalaApi =
      Seq(
        ("org.flinkextended" %% "flink-scala-api" % s"${flinkV}_1.1.4")
          .excludeAll(
            ExclusionRule(organization = "org.apache.flink"),
            ExclusionRule(organization = "org.scalameta"),
            ExclusionRule(organization = "org.scalacheck"),
            ExclusionRule(organization = "com.google.code.findbugs")
          )
      )

    // не используется native scala api, поскольку оно обявлено устаревшим
    // see https://issues.apache.org/jira/browse/FLINK-13414, https://issues.apache.org/jira/browse/FLINK-29741
    lazy val flinkCoreDeps: Seq[ModuleID] =
      Seq(
        "org.apache.flink" % "flink-streaming-java" % flinkV,
        "org.apache.flink" % "flink-runtime-web" % flinkV,
        "org.apache.flink" % "flink-clients" % flinkV,
        "org.apache.flink" % "flink-statebackend-rocksdb" % flinkV
      )

    lazy val flinkTestDeps: Seq[ModuleID] =
      Seq(
        "org.apache.flink" % "flink-test-utils-junit" % flinkV % Test,
        "org.apache.flink" % "flink-test-utils" % flinkV % Test
      )

    // https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/connectors/datastream/filesystem/
    lazy val flinkConnectorFiles: Seq[ModuleID] =
      Seq(
        "org.apache.flink" % "flink-connector-files" % flinkV,
        "org.apache.flink" % "flink-csv" % flinkV
      )

  }

}
