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
      "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion
    )
  )