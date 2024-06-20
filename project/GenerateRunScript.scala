import sbt._
import sbt.Keys._
import complete.DefaultParsers._

object GenerateRunScript extends AutoPlugin {
  object autoImport {
    val generateRunScript = inputKey[Unit]("Generates a script to run the application with the specified main class")
  }
  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    generateRunScript := {
      val args = spaceDelimited("<mainClass> <scriptFile>").parsed
      val mainClass = args.lift(0).getOrElse(sys.error("Main class not specified"))
      val scriptFileName = args.lift(1).getOrElse(sys.error("Script file name not specified"))

      val log = streams.value.log

      val runtimeClasspath = (Runtime / fullClasspath).value.files
      val testClasspath = (Test / fullClasspath).value.files
      val combinedClasspath = (runtimeClasspath ++ testClasspath).absString.split(":").mkString(":\\\n")

      val scriptContent =
        s"""#!/bin/bash
           |
           |CP="$combinedClasspath"
           |java -cp "$$CP" "$mainClass"
         """.stripMargin

      val scriptFile = baseDirectory.value / scriptFileName
      IO.write(scriptFile, scriptContent)
      scriptFile.setExecutable(true)

      log.info(s"Run script generated at: ${scriptFile.getAbsolutePath}")
    }
  )

}
