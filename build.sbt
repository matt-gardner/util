organization := "edu.cmu.ml.rtw"

name := "matt-util"

version := "2.1-SNAPSHOT"

scalaVersion := "2.11.2"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

javacOptions ++= Seq("-Xlint:unchecked")

crossScalaVersions := Seq("2.11.2", "2.10.3")

libraryDependencies ++= Seq(
  // Java utility libraries (collections, option parsing, such things)
  "com.google.guava" % "guava" % "17.0",
  "log4j" % "log4j" % "1.2.16",
  "commons-io" % "commons-io" % "2.4",
  "org.apache.commons" % "commons-compress" % "1.9",
  "net.sf.trove4j" % "trove4j" % "2.0.2",
  // Scala utility libraries
  "org.json4s" %% "json4s-native" % "3.2.11",
  // Testing dependencies
  "junit" % "junit" % "4.12",
  "com.novocode" % "junit-interface" % "0.11" % "test",
  "org.scalacheck" %% "scalacheck" % "1.11.4" % "test",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test"
)

testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oD")

jacoco.settings

jacoco.reportFormats in jacoco.Config := Seq(
  de.johoop.jacoco4sbt.ScalaHTMLReport(encoding = "utf-8", withBranchCoverage = true))

publishMavenStyle := true

pomIncludeRepository := { _ => false }

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

publishArtifact in Test := false

licenses := Seq("GPL-3.0" -> url("http://www.opensource.org/licenses/GPL-3.0"))

homepage := Some(url("http://github.com/matt-gardner/util"))

pomExtra := (
  <scm>
    <url>git@github.com:matt-gardner/util.git</url>
    <connection>scm:git:git@github.com:matt-gardner/util.git</connection>
  </scm>
  <developers>
    <developer>
      <id>matt-gardner</id>
      <name>Matt Gardner</name>
      <url>http://cs.cmu.edu/~mg1</url>
    </developer>
  </developers>)
