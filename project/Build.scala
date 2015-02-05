import sbt._
import Keys._
import xerial.sbt.Pack._

object BuildSettings {
  val buildSettings = Defaults.defaultSettings ++ Seq(
    name := "dss-data-save",
    organization := "com.calabs",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.10.4"
  )
}

object MyBuild extends Build {
  import BuildSettings._

  lazy val dssDataSave: Project = Project(
    "dss-data-save",
    file("."),
    settings = buildSettings 
    ++ packAutoSettings
    ++ Seq(
      libraryDependencies ++= Seq(
        "org.scalatest" %% "scalatest" % "2.2.1",
        "com.github.scopt" %% "scopt" % "3.2.0",
        "org.json4s" %% "json4s-jackson" % "3.2.11",
        "com.tinkerpop.blueprints" % "blueprints-core" % "2.6.0",
        "com.tinkerpop.blueprints" % "blueprints-neo4j2-graph" % "2.6.0",
        "com.thinkaurelius.titan" % "titan-cassandra" % "0.5.2",
        "com.thinkaurelius.titan" % "titan-berkeleyje" % "0.5.2",
        "com.thinkaurelius.titan" % "titan-hbase" % "0.5.2",
        "com.thinkaurelius.titan" % "titan-core" % "0.5.2",
        "com.google.guava" %  "guava" % "15.0" force(), // see this issue: https://groups.google.com/forum/#!topic/aureliusgraphs/vQ90mchs62s
        "com.tinkerpop.blueprints" % "blueprints-arangodb-graph" % "1.0.10-SNAPSHOT",
        "com.calabs" %% "dss-data-import" % "0.0.1-SNAPSHOT"
      ),
      resolvers ++= Seq(
        Resolver.sonatypeRepo("public"),
        Resolver.sonatypeRepo("snapshots"),
        "DSS Artifactory (sbt-snapshots)" at "http://147.83.42.135:8081/artifactory/sbt-snapshots",
        "DSS Artifactory (maven-snapshots)" at "http://147.83.42.135:8081/artifactory/snapshots"
      )
    )
  )
}
