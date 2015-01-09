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
        "org.json4s" %% "json4s-jackson" % "3.2.10"
      ),
      resolvers += Resolver.sonatypeRepo("public")
    )
  )
}
