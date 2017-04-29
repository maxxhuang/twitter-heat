name := "akka-social-stream-example-common"

libraryDependencies ++= {
  val akkaVersion = "2.5.0"
  val akkaHttpVersion = "10.0.5"

  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
    "org.twitter4j" % "twitter4j-core" % "4.0.2",
    "org.twitter4j" % "twitter4j-async" % "4.0.2",
    "org.twitter4j" % "twitter4j-stream" % "4.0.2"
  )
}