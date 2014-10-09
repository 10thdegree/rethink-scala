name := """Bravo"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.1"

resolvers ++= Seq(
  "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/")

resolvers ++= Seq("RethinkScala Repository" at "http://kclay.github.io/releases")

lazy val apiDeps = Seq("org.apache.xmlrpc" % "xmlrpc-client" % "3.1.3",
                "org.apache.xmlrpc" % "xmlrpc-server" % "3.1.3")

lazy val angularDeps = Seq(// Angular-js
  "org.webjars" % "angularjs" % "1.2.16",
  "org.webjars" % "angular-ui" % "0.4.0-2"  exclude("org.webjars", "angularjs"),
  "org.webjars" % "angular-ui-bootstrap" % "0.10.0-1"  exclude("org.webjars", "angularjs"),
  "org.webjars" % "angular-ui-router" % "0.2.10" exclude("org.webjars", "angularjs")
)

lazy val testingDeps = Seq(
  "org.scalacheck" %% "scalacheck" % "1.10.1" % "test"
)

lazy val otherDeps = Seq(
  //scalaz
  "org.scalaz" % "scalaz-core_2.11" % "7.1.0",
  // Rethink-DB Driver
  "com.rethinkscala" % "core_2.11" % "0.4.4-SNAPSHOT",
  // Secure Social
  "ws.securesocial" %% "securesocial" % "master-SNAPSHOT",
  "org.slf4j" % "slf4j-api" % "1.7.7",
  // DI
  "com.google.inject" % "guice" % "4.0-beta4",
  "javax.inject" % "javax.inject" % "1",
  "net.codingwell" % "scala-guice_2.11" % "4.0.0-beta4",
  // See: http://www.webjars.org
  "org.webjars" %% "webjars-play" % "2.3.0",
  //"org.webjars" % "requirejs" % "2.1.11-1",
  //"org.webjars" % "react" % "0.10.0",
  "org.webjars" % "jquery" % "2.1.0-2",
  //
  //
  // Bootstrap
  "org.webjars" % "bootstrap" % "3.1.1",
  //
  // Play Stylus
  //"com.typesafe.sbt" % "sbt-stylus" % "1.0.0",
  //
  // D3.js
  //"org.webjars" % "d3js" % "3.4.4-1",
  // Google-charts (simpler than D3 but less flexible)
  //"org.webjars" % "angular-google-chart" % "0.0.8",
  //
  // Slick
  // "com.typesafe.slick" %% "slick" % "2.1.0-M2",
  // "joda-time" % "joda-time" % "2.3",
  // "org.joda" % "joda-convert" % "1.5",
  // "com.github.tototoshi" %% "slick-joda-mapper" % "1.2.0-SNAPSHOT"
  //
  // Rules Engine
  "org.codehaus.groovy" % "groovy-jsr223" % "2.3.6"
  //"org.jruby" % "jruby-complete" % "1.7.13"
)

libraryDependencies := (apiDeps ++ otherDeps ++ angularDeps ++ testingDeps)

