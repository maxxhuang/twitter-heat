import com.typesafe.sbt.packager.docker._
import sbt.Keys._

name := "twitter-heat"

lazy val commonSettings = Seq(
  version := "0.1.0",
  scalaVersion := "2.12.1"
)

def dockerSettings(mainClassName: String): Seq[Setting[_]] =
  Seq(
    mainClass in Compile := Some(mainClassName),
    version in Docker := "latest",
    maintainer in Docker := "maxxhuang@maxxlife.gmail",
//    dockerExposedPorts := Seq(3001, 3002),
    dockerBaseImage := "java:8",
    dockerCommands := dockerCommands.value.filterNot {
      // ExecCmd is a case class, and args is a varargs variable, so you need to bind it with @
      case Cmd("USER", args@_*) => true
      // dont filter the rest
      case cmd => false
    }
  )

lazy val root = (project in file(".")).
  aggregate(common, collector, api, sandbox)

lazy val common = (project in file("common")).
  settings(commonSettings: _*)

lazy val collector =
  Project(
    id = "collector",
    base = file("collector"),
    settings = commonSettings ++ dockerSettings("twitterheat.collector.Main")
  )
  .dependsOn(common)
  .enablePlugins(JavaAppPackaging)

lazy val api =
  Project(
    id = "api",
    base = file("api"),
    settings = commonSettings ++ dockerSettings("twitterheat.api.Main")
  )
    .dependsOn(common, collector)
    .enablePlugins(JavaAppPackaging)

lazy val sandbox =
  Project(
    id = "sandbox",
    base = file("sandbox"),
    settings = commonSettings ++ dockerSettings("akkastreamexample.ClusterTest")
  )
  .dependsOn(common, collector)
  .enablePlugins(JavaAppPackaging)


//lazy val sandbox = (project in file("sandbox")).
//  settings(commonSettings: _*).
//  dependsOn(common)
//


