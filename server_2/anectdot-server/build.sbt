name := "anectdotfun"

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
  "org.slf4j" % "slf4j-simple" % "1.7.36"
)
scalacOptions ++= Seq("-deprecation")
