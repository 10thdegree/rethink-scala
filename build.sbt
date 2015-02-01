name := """Bravo"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.4"

resolvers ++= Seq(
  "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/")

resolvers ++= Seq("RethinkScala Repository" at "http://kclay.github.io/releases")

lazy val apiDeps = Seq("org.apache.xmlrpc" % "xmlrpc-client" % "3.1.3",
                "org.apache.xmlrpc" % "xmlrpc-server" % "3.1.3",
                "com.google.apis" % "google-api-services-dfareporting" % "v1.3-rev27-1.19.0",
                "com.google.api-client" % "google-api-client-java6" % "1.19.0",
                "com.google.api-client" % "google-api-client-extensions" % "1.6.0-beta",
                "com.google.oauth-client" % "google-oauth-client-jetty" % "1.19.0",
                "com.github.tototoshi" %% "scala-csv" % "1.0.0",
                "com.google.apis" % "google-api-services-doubleclicksearch" % "v2-rev37-1.19.0")



lazy val angularDeps = Seq(// Angular-js
  "org.webjars" % "angularjs" % "1.3.10",
  "org.webjars" % "angular-ui" % "0.4.0-3"  exclude("org.webjars", "angularjs"),
  "org.webjars" % "angular-ui-bootstrap" % "0.12.0"  exclude("org.webjars", "angularjs"),
  "org.webjars" % "angular-ui-router" % "0.2.13" exclude("org.webjars", "angularjs"),
  "org.webjars" % "smart-table" % "1.4.8"
)

lazy val testingDeps = Seq(
  "org.scalacheck" %% "scalacheck" % "1.10.1" % "test"
)

initialCommands in console := "import scalaz._;import Scalaz._;import org.joda.time._;import scala.concurrent.Future;import bravo.core.Util._; import scala.reflect.runtime.universe.reify; import scala.concurrent.duration._; import scala.concurrent.{Future,Await}; import scala.concurrent.ExecutionContext.Implicits.global"

lazy val otherDeps = Seq(
  //scalaz
  "org.scalaz" % "scalaz-core_2.11" % "7.1.0",
  "org.scalaz" %% "scalaz-scalacheck-binding" % "7.1.0",
  "org.typelevel" %% "scalaz-specs2" % "0.3.0" % "test",
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
  "org.webjars" %% "webjars-play" % "2.3.0-2",
  //"org.webjars" % "requirejs" % "2.1.11-1",
  //"org.webjars" % "react" % "0.10.0",
  "org.webjars" % "jquery" % "2.1.0-2",
  //
  //
  // Bootstrap
  "org.webjars" % "bootstrap" % "3.1.1-2",
  //
  // Play Stylus
  //"com.typesafe.sbt" % "sbt-stylus" % "1.0.0",
  //
  // D3.js
  "org.webjars" % "d3js" % "3.4.4-1",
  "org.webjars" % "c3" % "0.4.9", // exclude("org.webjars", "d3js"),
  // Google-charts (simpler than D3 but less flexible)
  //"org.webjars" % "angular-google-chart" % "0.0.8",
  //
  // Slick
  // "com.typesafe.slick" %% "slick" % "2.1.0-M2",
  "joda-time" % "joda-time" % "2.5",
  //"org.joda" % "joda-convert" % "1.5",
  "com.github.nscala-time" %% "nscala-time" % "1.6.0",
  // "com.github.tototoshi" %% "slick-joda-mapper" % "1.2.0-SNAPSHOT"
  //v
  // Rules Engine
  "org.codehaus.groovy" % "groovy-jsr223" % "2.3.6",
  //"org.jruby" % "jruby-complete" % "1.7.13"
  //
  "org.scalaz" %% "scalaz-effect" % "7.1.0",
  "org.specs2" % "specs2_2.11" % "2.4"
)

libraryDependencies ++= (apiDeps ++ otherDeps ++ angularDeps ++ testingDeps)

