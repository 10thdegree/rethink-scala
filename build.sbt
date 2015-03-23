import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt.Project.projectToRef
import sbt.Keys._
import sbt._


/**** 

common settings for all projects

****/
lazy val clients = Seq(reportClient, loginClient, navClient, userManageClient)

lazy val coredeps = Seq(
  //scalaz
  "org.scalaz" % "scalaz-core_2.11" % "7.1.1",
  "org.scalaz" %% "scalaz-scalacheck-binding" % "7.1.0",
  "org.typelevel" %% "scalaz-specs2" % "0.3.0" % "test",
  "joda-time" % "joda-time" % "2.5",
  "org.scalaz" %% "scalaz-effect" % "7.1.0",
  "org.specs2" % "specs2_2.11" % "2.4",
  "org.scalacheck" %% "scalacheck" % "1.10.1" % "test",
  "org.joda" % "joda-convert" % "1.5",
  "com.vmunier" %% "play-scalajs-scripts" % "0.1.0",
  "com.github.benhutchison" %% "prickle" % "1.1.4"
)

lazy val commonSettings = Seq(
  version := "1.0-SNAPSHOT",
  scalaVersion := "2.11.4",
  scalacOptions ++= Seq("-unchecked", "-deprecation", "feature"),
  resolvers ++= Seq(
    "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
    "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
    "karchedon-repo" at "http://maven.karchedon.de/"),
  libraryDependencies ++= coredeps,
  initialCommands in console := "import scalaz._;import Scalaz._;import org.joda.time._;import scala.concurrent.Future; import scala.reflect.runtime.universe.reify; import scala.concurrent.duration._; import scala.concurrent.{Future,Await}; import scala.concurrent.ExecutionContext.Implicits.global"
)

lazy val util = project.settings(commonSettings: _*)

val sharedSrcDir = "shared"

lazy val shared = (crossProject.crossType(CrossType.Pure) in file(sharedSrcDir)).
  settings(scalaVersion := "2.11.4").
  jsConfigure(_ enablePlugins ScalaJSPlay)

lazy val sharedJVM = shared.jvm
lazy val sharedJS = shared.js

lazy val bravo = (project in file("."))
  .settings(commonSettings: _*)
  .settings(
    scalaJSProjects := clients,
    pipelineStages := Seq(scalaJSProd),
    unmanagedSourceDirectories in Compile += baseDirectory.value / sharedSrcDir / "src" / "main" / "scala",
    unmanagedSourceDirectories in Test += baseDirectory.value / sharedSrcDir / "src" / "test" / "scala"
  )
  .enablePlugins(PlayScala)
  .aggregate(clients.map(projectToRef): _*)
  .dependsOn(util, api, sharedJVM)

lazy val commonClientSettings = Seq(
  scalaVersion := "2.11.4",
  persistLauncher := true,
  persistLauncher in Test := false,
  unmanagedSourceDirectories in Compile := Seq((scalaSource in Compile).value),
  resolvers ++= Seq(
    "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
    "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
  ),
  libraryDependencies ++= Seq(
    "biz.enef" %%% "scalajs-angulate" % "0.2-SNAPSHOT",
    "com.github.benhutchison" %%% "prickle" % "1.1.4"
  )
)

def clientProject(project: Project, name: String): Project = {
  project
    .settings(commonClientSettings: _*)
    .settings(
      artifactPath in (Compile, fullOptJS) := file(s"public/core/js/target/$name-opt.js"),
      artifactPath in (Compile, fastOptJS) := file(s"public/core/js/target/$name-fastopt.js"),
      artifactPath in (Compile, packageScalaJSLauncher) := file(s"public/core/js/target/$name-launcher.js"),
      unmanagedSourceDirectories in Compile ++= Seq(
				baseDirectory.value / ".." / ".." / sharedSrcDir / "src" / "main" / "scala",
				baseDirectory.value / ".." / "core" / "src" / "main" / "scala"
			),
      unmanagedSourceDirectories in Test ++= Seq(
				baseDirectory.value / ".." / ".." / sharedSrcDir / "src" / "test" / "scala",
				baseDirectory.value / ".." / "core" / "src" / "test" / "scala"
			)
    ).enablePlugins(ScalaJSPlugin, ScalaJSPlay)
}

lazy val reportClient = clientProject(project in file("client/report"),"report").dependsOn(sharedJS)

lazy val loginClient = clientProject(project in file("client/login"),"login")

lazy val navClient = clientProject(project in file("client/nav"),"nav").dependsOn(sharedJS)

lazy val userManageClient = clientProject(project in file("client/userManage"),"userManage")

onLoad in Global := (Command.process("project bravo", _: State)) compose (onLoad in Global).value

lazy val api = (project in file("api")).settings(commonSettings: _*).settings(libraryDependencies ++= apiDeps).dependsOn(util) 

resolvers ++= Seq("RethinkScala Repository" at "http://kclay.github.io/releases")

lazy val apiDeps = Seq("org.apache.xmlrpc" % "xmlrpc-client" % "3.1.3",
                "org.apache.xmlrpc" % "xmlrpc-server" % "3.1.3",
                "com.google.apis" % "google-api-services-dfareporting" % "v2.0-rev4-1.19.1",
                "com.google.api-client" % "google-api-client-java6" % "1.19.0",
                "com.google.api-client" % "google-api-client-extensions" % "1.6.0-beta",
                "com.google.oauth-client" % "google-oauth-client-jetty" % "1.19.0",
                "com.github.tototoshi" %% "scala-csv" % "1.0.0",
                "com.google.apis" % "google-api-services-doubleclicksearch" % "v2-rev37-1.19.0")

lazy val angularDeps = Seq(// Angular-js
  "org.webjars" % "angularjs" % "1.3.10",
  "org.webjars" % "angular-ui" % "0.4.0-3"  exclude("org.webjars", "angularjs"),
  "org.webjars" % "angular-ui-bootstrap" % "0.12.1"  exclude("org.webjars", "angularjs"),
  "org.webjars" % "angular-ui-router" % "0.2.13" exclude("org.webjars", "angularjs"),
	"org.webjars" % "angular-chosen" % "1.0.6" exclude("org.webjars", "angularjs"),
  "org.webjars" % "smart-table" % "1.4.8"
)

lazy val otherDeps = Seq(
  // Secure Social using jar in lib
  // Rethink-DB Driver using jar in lib
  "org.slf4j" % "slf4j-api" % "1.7.7",
  "org.julienrf" %% "play-json-variants" % "1.1.0",
  // DI
  "com.google.inject" % "guice" % "4.0-beta4",
  "javax.inject" % "javax.inject" % "1",
  "net.codingwell" % "scala-guice_2.11" % "4.0.0-beta4",
  // See: http://www.webjars.org
  "org.webjars" %% "webjars-play" % "2.3.0-2",
  "org.webjars" % "jquery" % "2.1.0-2",
  //
  //
  // Bootstrap
  "org.webjars" % "bootstrap" % "3.3.2",
  "org.webjars" % "bootstrap-datepicker" % "1.3.1",
  "org.webjars" % "momentjs" % "2.9.0",
  //
  // Play Stylus
  //"com.typesafe.sbt" % "sbt-stylus" % "1.0.0",
  //
  // D3.js
  "org.webjars" % "d3js" % "3.4.4-1",
  "org.webjars" % "c3" % "0.4.9", // exclude("org.webjars", "d3js"),
  // Slick
  // "com.typesafe.slick" %% "slick" % "2.1.0-M2",
  //"org.joda" % "joda-convert" % "1.5",
  "com.github.nscala-time" %% "nscala-time" % "1.6.0",
  // "com.github.tototoshi" %% "slick-joda-mapper" % "1.2.0-SNAPSHOT"
  //v
  // Rules Engine
  "org.codehaus.groovy" % "groovy-jsr223" % "2.3.6"
  //"org.jruby" % "jruby-complete" % "1.7.13"
  //
  )

lazy val securesocialDeps = Seq(
  cache,
  ws,
  filters,
  "com.typesafe.play.plugins" %% "play-plugins-util" % "2.3.0",
  "com.typesafe.play.plugins" %% "play-plugins-mailer" % "2.3.0",
  "org.mindrot" % "jbcrypt" % "0.3m",
  "org.specs2" %% "specs2" % "2.3.12" % "test",
  "org.mockito" % "mockito-all" % "1.9.5" % "test"
)

val jacksonVersion = "2.4.1"
val jacksonScalaVersion = "2.4.1"
def jackson = Seq(
  "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
  "com.fasterxml.jackson.datatype" % "jackson-datatype-joda" % jacksonVersion,
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonScalaVersion
)

lazy val rethinklDeps = Seq(
  "org.scalatest" %% "scalatest" % "2.1.3" % "test",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  "org.slf4j" % "slf4j-log4j12" % "1.7.6",
  "io.netty" % "netty" % "3.9.3.Final",
  "com.google.protobuf" % "protobuf-java" % "2.5.0",
  "joda-time" % "joda-time" % "2.3",
  "org.joda" % "joda-convert" % "1.5",
  "org.scala-lang" % "scala-reflect" % scalaVersion.toString,
  "org.scala-lang.modules" %% "scala-xml" % "1.0.1"  % "test"
)

libraryDependencies ++= (apiDeps ++ otherDeps ++ angularDeps ++ securesocialDeps ++ rethinklDeps ++ jackson)



