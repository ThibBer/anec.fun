ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.6.1"

val AkkaVersion = "2.9.3"
val AkkaHttpVersion = "10.6.3"

lazy val root = (project in file("."))
  .settings(
    name := "ScalaServer",
    idePackagePrefix := Some("be.unamur.anecdotfun"),
    resolvers += "Akka library repository".at("https://repo.akka.io/maven"),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
      "com.fazecast" % "jSerialComm" % "2.9.3"
    )
  )
