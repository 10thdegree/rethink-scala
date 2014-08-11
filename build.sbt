name := """Bravo"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.1"

resolvers ++= Seq("Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/")

libraryDependencies ++= Seq(
  // Rethink-DB Driver
  "com.rethinkscala" %% "core" % "0.4.4-SNAPSHOT",
  // Secure Social
  "ws.securesocial" %% "securesocial" % "master-SNAPSHOT",
  // DI
  "com.google.inject" % "guice" % "4.0-beta4",
  "javax.inject" % "javax.inject" % "1",
  "net.codingwell" % "scala-guice_2.11" % "4.0.0-beta4",
  // See: http://www.webjars.org
  "org.webjars" %% "webjars-play" % "2.3.0",
  //"org.webjars" % "requirejs" % "2.1.11-1",
  "org.webjars" % "jquery" % "2.1.0-2",
  //
  // Angular-js
  "org.webjars" % "angularjs" % "1.2.16",
  "org.webjars" % "angular-ui" % "0.4.0-2"  exclude("org.webjars", "angularjs"),
  "org.webjars" % "angular-ui-bootstrap" % "0.10.0-1"  exclude("org.webjars", "angularjs"),
  "org.webjars" % "angular-ui-router" % "0.2.10" exclude("org.webjars", "angularjs"),
  //
  // Bootstrap
  "org.webjars" % "bootstrap" % "3.1.1",
  // Play Stylus
  //"com.typesafe.sbt" % "sbt-stylus" % "1.0.0",
  jdbc,
  anorm,
  cache,
  ws
)

