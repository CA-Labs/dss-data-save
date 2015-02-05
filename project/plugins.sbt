resolvers ++= Seq(
  "Sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "Xerial" at "http://repo1.maven.org/maven2/org/xerial/sbt/"
)

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.6.0")

addSbtPlugin("org.xerial.sbt" % "sbt-pack" % "0.6.5")
