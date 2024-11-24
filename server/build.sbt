name := "anecdotfun"

version := "0.0.1"

scalaVersion := "3.5.2"

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

lazy val AkkaVersion = "2.9.3"
lazy val AkkaHttpVersion = "10.6.3"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
  "org.slf4j" % "slf4j-simple" % "2.0.13",
  "com.azure" % "azure-ai-openai" % "1.0.0-beta.12",
  "com.azure" % "azure-identity" % "1.14.1"
)
scalacOptions ++= Seq("-deprecation")

inThisBuild(
  List(
    scalaVersion := "3.5.2",
    scalafixOnCompile := true,
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    scalacOptions += "-Wunused:imports"
  )
)

ThisBuild / scalafixDependencies += "io.github.dedis" %% "scapegoat-scalafix" % "1.1.3"
