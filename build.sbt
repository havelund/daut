organization := "org.havelund"

name := "daut"

version := "0.2"

scalaVersion := "3.4.2"

// libraryDependencies += "com.github.tototoshi" %% "scala-csv" % "1.3.6"
libraryDependencies += "de.siegmar" %"fastcsv" %"1.0.1"

libraryDependencies += "org.json4s" %% "json4s-native" % "4.0.6"
libraryDependencies += "org.json4s" %% "json4s-ext" % "4.0.6"

// for generating JSON:

libraryDependencies += "com.lihaoyi" %% "upickle" % "3.3.1"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core" % "0.14.3",
  "io.circe" %% "circe-generic" % "0.14.3",
  "io.circe" %% "circe-parser" % "0.14.3"
)

// ---

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % "2.6.19",
  "ch.qos.logback" % "logback-classic" % "1.2.10"
)

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-effect" % "3.3.12",
  "org.typelevel" %% "cats-effect-std" % "3.3.12"
)

scalacOptions += "-explain"
scalacOptions += "-explain-cyclic"

scalacOptions ++= Seq(
  "-deprecation",            // Emit warning and location for usages of deprecated APIs
  "-feature",                // Emit warning and location for usages of features that should be imported explicitly
  "-unchecked",              // Enable additional warnings where generated code depends on assumptions
  // "-Wshadow:_",                // Enable recommended additional warnings
  "-Xfatal-warnings"         // Fail the compilation if there are any warnings
)

enablePlugins(GenerateRunScript)

