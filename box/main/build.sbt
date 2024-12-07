import com.typesafe.sbt.packager.docker.Cmd
import com.typesafe.sbt.packager.linux.LinuxPlugin.mapGenericFilesToLinux
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.*

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.4"

val AkkaVersion = "2.8.6"
val AkkaHttpVersion = "10.5.3"
val Slf4jVersion = "2.0.13"

lazy val root = project
  .in(file("."))
  .settings(
    name := "ScalaGame",
    resolvers += "Akka library repository".at("https://repo.akka.io/maven"),
    libraryDependencies += "org.scalameta" %% "munit" % "1.0.0" % Test,
    libraryDependencies += "com.fazecast" % "jSerialComm" % "2.11.0",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,
      "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
      "com.lihaoyi" %% "os-lib" % "0.10.1"
    )
  )

enablePlugins(UniversalPlugin)
enablePlugins(JavaAppPackaging, AshScriptPlugin)
enablePlugins(DockerPlugin)

dockerBaseImage := "eclipse-temurin:23-jre-alpine"
dockerUsername := Some("vsantele")
Docker / packageName := "anecdotfun-box"
dockerRepository := Some("ghcr.io")
dockerVersion := Some("lastest")
dockerCommands ++= Seq(
  Cmd("USER", "root"),
  Cmd("RUN", "apk add --no-cache ffmpeg"),
  Cmd("USER", "1001:0")
)

mapGenericFilesToLinux
