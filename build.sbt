name := "daut"

version := "0.1"

scalaVersion := "2.13.8"

scalacOptions ++= Seq(
  "-encoding", "utf8", // Option and arguments on same line
  //"-Xfatal-warnings",  // Uncomment later
  "-deprecation",
  "-unchecked",
  "-feature",
  //"-Xsource:3" // one warning is an error on Scala 3
)

// libraryDependencies += "com.github.tototoshi" %% "scala-csv" % "1.3.6"
libraryDependencies += "de.siegmar" %"fastcsv" %"1.0.1"
