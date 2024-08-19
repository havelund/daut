package daut38_sacsvt_2022

import daut._
import util.control.Breaks._

/**
  * ============================================================
  * SAC-SVT 2022.
  * ============================================================
  */

/**
  * Monitor.
  */

trait Event
case class Com(cmd: String, nr: String, kind: String) extends Event
case class Can(cmd: String, nr: String) extends Event
case class Dis(cmd: String, nr: String) extends Event
case class Fai(cmd: String, nr: String) extends Event
case class Suc(cmd: String, nr: String) extends Event
case class Clo(cmd: String, nr: String) extends Event

class M4 extends Monitor[Event] {
  always {
    case Com(c, n, "FSW") => Dispatch(c, n)
  }

  case class Dispatch(cmd: String, nr: String) extends state {
    hot {
      case Can(`cmd`, `nr`) => ok
      case Dis(`cmd`, `nr`) => Succeed(cmd, nr)
    }
  }

  case class Succeed(cmd: String, nr: String) extends state {
    hot {
      case Suc(`cmd`, `nr`) => Close(cmd, nr)
      case Com(`cmd`, _, "FSW") => error(s"command $cmd re-issued")
      case Fai(`cmd`, `nr`) => error(s"failure of cmd=$cmd, n=$nr")
    }
  }

  case class Close(cmd: String, nr: String) extends state {
    hot {
      case Suc(`cmd`, `nr`) => error(s"cmd=$cmd, n=$nr succeeds more than once")
      case Clo(`cmd`, `nr`) => ok
    }
  }
}

/**
  * Analyzing log.
  */

class FastCSVReader(fileName: String) {
  // https://github.com/osiegmar/FastCSV
  import java.io.File
  import de.siegmar.fastcsv.reader.CsvReader
  import de.siegmar.fastcsv.reader.CsvRow
  import java.nio.charset.StandardCharsets
  // import scala.collection.JavaConverters._
  import scala.jdk.CollectionConverters._

  val file = new File(fileName)
  val csvReader = new CsvReader
  val csvParser = csvReader.parse(file, StandardCharsets.UTF_8)
  var row: CsvRow = csvParser.nextRow()

  def hasNext: Boolean = row != null

  def next(): List[String] = {
    val line = row.getFields.asScala.toList
    row = csvParser.nextRow()
    line
  }
}

class LogReader(fileName: String) {
  val reader = new FastCSVReader(fileName)

  def converte(line: List[String]): Event = {
    line(0) match {
      case "command" => Com(line(1), line(2), line(3))
      case "cancel" => Can(line(1), line(2))
      case "dispatch" => Dis(line(1), line(2))
      case "fail" => Fai(line(1), line(2))
      case "succeed" => Suc(line(1), line(2))
      case "close" => Clo(line(1), line(2))
    }
  }

  def hasNext: Boolean = reader.hasNext

  def next: Event = {
    converte(reader.next())
  }
}

/*
log-1-12500.csv
log-50-250.csv

log-1-50000.csv
log-5-10000.csv
log-10-5000.csv
log-20-2500.csv

log-1-125000.csv
log-5-25000.csv
 */

object VerifyLog {
  def main(args: Array[String]): Unit = {
    val file = "log-5-25000.csv"
    val dir = "/Users/khavelun/Desktop/development/ideaworkspace/daut/src/test/scala/daut38_sacsvt_2022"
    val csvFile = new LogReader(s"$dir/$file")
    val monitor = new M4
    DautOptions.DEBUG = false
    Util.time ("Analysis of log") {
      while (csvFile.hasNext) {
        if (Monitor.eventNumber % 1000 == 0) println(s"---> ${Monitor.eventNumber}")
        monitor.verify(csvFile.next)
      }
      monitor.end()
    }
  }
}


