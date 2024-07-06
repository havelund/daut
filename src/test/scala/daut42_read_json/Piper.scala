package daut42_read_json

import java.io.File
import sys.process._

object Piper:

  def main(args: Array[String]): Unit =
    //    if (args.length != 1) {
    //      println("Usage: Piper <file-path>")
    //      sys.exit(1)
    //    }

    val filePath = "src/test/scala/daut42_json/file1.jsonl" // args(0)
    val classpath = System.getProperty("java.class.path")
    val command = Seq("java", "-cp", classpath, "daut42_json.Main")

    // Read the file and pipe its content to the Main program
    val file = new File(filePath)
    if (file.exists && file.isFile)
      val process = Process(command)
      val io = new ProcessIO(
        stdin => {
          // Write the content of the file to the stdin of the Main program
          val source = scala.io.Source.fromFile(file)
          try {
            source.getLines().foreach { line =>
              stdin.write(line.getBytes)
              stdin.write('\n')
            }
          } finally {
            source.close()
            stdin.close()
          }
        },
        stdout => scala.io.Source.fromInputStream(stdout).getLines().foreach(println),
        stderr => scala.io.Source.fromInputStream(stderr).getLines().foreach(Console.err.println)
      )

      val p = process.run(io)
      p.exitValue() // Wait for the process to exit

    else
      println(s"File not found: $filePath")
      sys.exit(1)

