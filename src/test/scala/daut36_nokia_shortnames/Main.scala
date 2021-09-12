package daut36_nokia_shortnames

import daut._
import util.control.Breaks._

/**
  * ============================================================
  * VERSION WITH SHORT NAMES FOR PUBLICATION WITH LIMITED SPACE.
  * ============================================================
  */

/**
  * The Ins_1_2 and Del_1_2 properties from the paper:
  * Monitoring Usage-control Policies in Distributed Systems
  * David Basin, Matus Harvan, Felix Klaedtke, and Eugen Zalinescu
  * Proceedings of the International Workshop on Temporal Representation and Reasoning
  * TIME 2011, pages 88 - 95.
  */

trait Db

case object Db1 extends Db

case object Db2 extends Db

trait Ev {
  val t: Long
}

case class Ins(t: Long, u: String, db: Db, d: String) extends Ev

case class Del(t: Long, u: String, db: Db, d: String) extends Ev

class Ins_1_2 extends Monitor[Ev]  {
  val hrs_30 = 108000
  val sec_1 = 1

  case class I2D1(t: Long, d: String) extends state {
    watch {
      case e if e.t - t > sec_1 => ok
    }
  }

  always {
    case Del(t, _, Db1, d) => I2D1(t, d)
    case Ins(t, _, Db2, d) => I2D1(t, d)

    case Ins(t, _, Db1, d) if d != "[unknown]" =>
      if (exists { case I2D1(`t`, `d`) => true }) ok else
        hot {
          case e if e.t - t > hrs_30 => error
          case Ins(_, _, Db2, `d`) => ok
          case Del(_, _, Db1, `d`) => ok
        }
  }
}

class Del_1_2 extends Monitor[Ev] {
  val hrs_30 = 108000
  val sec_1 = 1

  case class D(t: Long, db: Db, d: String) extends state {
    watch {
      case event if event.t - t > sec_1 => ok
    }
  }

  case class I(t: Long, db: Db, d: String) extends state {
    watch {
      case event if event.t - t > hrs_30 => ok
    }
  }

  always {
    case Del(t, _, Db2, d) => D(t, Db2, d)
    case Ins(t, _, db, d) => I(t, db, d)

    case Del(t, _, Db1, d) if d != "[unknown]" =>
      if (exists { case D(`t`, Db2, `d`) => true }) ok else {
        val s1 = if (exists { case I(t0, Db1, `d`) => t - t0 <= hrs_30 }) ok else
          hot {
            case e if e.t - t > hrs_30 => error
            case Del(t1, _, Db2, `d`) if t1 - t <= hrs_30 => ok
            case Ins(`t`, _, Db1, `d`) => ok
          }
        val s2 = if (exists { case I(t0, Db2, `d`) => t - t0 <= hrs_30 }) hot {
          case e if e.t - t > hrs_30 => error
          case Del(t1, _, Db2, `d`) if t1 - t <= hrs_30 => ok
        } else hot {
          case e if e.t - t > hrs_30 => ok
          case Del(t1, _, Db2, `d`) if t1 - t <= hrs_30 => ok
          case Ins(t1, _, Db2, `d`) if t1 - t <= hrs_30 => hot {
            case e if e.t - t > hrs_30 => error
            case Del(t1, _, Db2, `d`) if t1 - t <= hrs_30 => ok
          }
        }
        (s1, s2)
      }
  }
}

class History(resetBound: Int, timeLimit: Long) {
  val map = collection.mutable.Map[String, Long]()
  var counter : Int = 0

  def get(d: String): Option[Long] = map.get(d)

  def put(d: String, t : Long) : Unit = {
    counter += 1
    if (counter == resetBound) {
      counter = 0
      map.filterInPlace {
        case (_,t0) => t - t0 <= timeLimit
      }
    }
    map.put(d, t)
  }

  def within(d: String, now: Long): Boolean = {
    get(d) match {
      case None => false
      case Some(t) => now - t <= timeLimit
    }
  }
}

class Del_1_2_opt extends Monitor[Ev] {
  val hrs_30 = 108000
  val sec_1 = 1
  val sec_0 = 0

  val I1 = new History(500000, hrs_30)
  val I2 = new History(500000, hrs_30)
  val D2 = new History(500000, sec_0)

  always {
    case Ins(t, _, Db1, d) => I1.put(d, t)
    case Ins(t, _, Db2, d) => I2.put(d, t)
    case Del(t, _, Db2, d) => D2.put(d,t)

    case Del(t, _, Db1, d) if d != "[unknown]" =>
      if (D2.within(d, t)) ok else {
        val s1 = if (I1.within(d, t) ) ok else
          hot {
            case e if e.t - t > hrs_30 => error
            case Del(t1, _, Db2, `d`) if t1 - t <= hrs_30 => ok
            case Ins(`t`, _, Db1, `d`) => ok
          }
        val s2 = if (I2.within(d, t)) hot {
          case e if e.t - t > hrs_30 => error
          case Del(t1, _, Db2, `d`) if t1 - t <= hrs_30 => ok
        } else hot {
          case e if e.t - t > hrs_30 => ok
          case Del(t1, _, Db2, `d`) if t1 - t <= hrs_30 => ok
          case Ins(t1, _, Db2, `d`) if t1 - t <= hrs_30 => hot {
            case e if e.t - t > hrs_30 => error
            case Del(t1, _, Db2, `d`) if t1 - t <= hrs_30 => ok
          }
        }
        (s1, s2)
      }
  }
}

class Monitors extends Monitor[Ev] {
  monitor(new Ins_1_2, new Del_1_2_opt)
}

/**
  * Testing Ins_1_2
  */

object Test_Ins_1_2 {
  def main(args: Array[String]) {
    DautOptions.DEBUG = false
    val m = new Ins_1_2

    /**
      * Correct Traces:
      */

    // correct trace, deletion in past

    val okTrace1: List[Ev] = List(
      Del(1000, "user2", Db1, "data1000"),
      Ins(1000, "user1", Db1, "data1000"),
    )

    // correct trace, insertion in past

    val okTrace2: List[Ev] = List(
      Ins(1000, "user2", Db2, "data1000"),
      Ins(1000, "user1", Db1, "data1000"),
    )

    // correct trace, deletion in future

    val okTrace3: List[Ev] = List(
      Ins(1000, "user1", Db1, "data1000"),
      Del(5000, "user2", Db1, "data1000"),
    )

    // correct trace, insertion in future

    val okTrace4: List[Ev] = List(
      Ins(1000, "user1", Db1, "data1000"),
      Ins(5000, "user2", Db2, "data1000"),
    )

    /**
      * Incorrect Traces:
      */

    // error trace, deletion too early in past and too late in future

    val errTrace1: List[Ev] = List(
      Del(999, "user2", Db1, "data1000"),
      Ins(1000, "user1", Db1, "data1000"),
      Del(200000, "user2", Db1, "data1000"),
    )

    // error trace, deletion too early in past and too late in future

    val errTrace2: List[Ev] = List(
      Ins(999, "user2", Db2, "data1000"),
      Ins(1000, "user1", Db1, "data1000"),
      Ins(200000, "user2", Db2, "data1000"),
    )

    /**
      * Verify:
      */

    m.verify(errTrace2)
  }
}

/**
  * Testing Del_1_2
  */

object Test_Del_1_2 {
  def main(args: Array[String]) {
    DautOptions.DEBUG = false
    val m = new Del_1_2_opt

    /**
      * Correct Traces:
      */

    // correct trace, db2 deletion in past

    val okTrace1: List[Ev] = List(
      Del(1000, "user2", Db2, "data1000"),
      Del(1000, "user1", Db1, "data1000"), // <-- trigger
    )

    // correct trace, db2 deletion in the future

    val okTrace2: List[Ev] = List(
      Del(1000, "user1", Db1, "data1000"), // <-- trigger
      Del(9999, "user2", Db2, "data1000")
    )

    // correct trace, db1 insertion in the past and no timely db2 insertion in the past or future

    val okTrace3: List[Ev] = List(
      Ins(100000, "user4", Db2, "data1000"),
      Ins(400000, "user2", Db1, "data1000"),
      Del(500000, "user1", Db1, "data1000"), // <-- trigger
      Ins(800000, "user3", Db2, "data1000")
    )

    // correct trace, db1 insertion in next step and no db2 insertion in past or future

    val okTrace4: List[Ev] = List(
      Ins(100000, "user4", Db2, "data1000"),
      Del(500000, "user1", Db1, "data1000"), // <-- trigger
      Ins(500000, "user2", Db1, "data1000"),
      Ins(800000, "user3", Db2, "data1000")
    )

    /**
      * Incorrect Traces:
      */

    // error trace, no db2 deletion in past

    val errTrace1: List[Ev] = List(
      Del(999, "user2", Db2, "data1000"),
      Del(1000, "user1", Db1, "data1000"), // <-- trigger
    )

    // error trace, no db2 deletion in the future

    val errTrace2: List[Ev] = List(
      Del(1000, "user1", Db1, "data1000"), // <-- trigger
      Del(500000, "user2", Db2, "data1000")
    )

    // error trace, db1 insertion in the past but db2 insertion in the past or future

    val errTrace3: List[Ev] = List(
      Ins(100000, "user4", Db2, "data1000"),
      Ins(400000, "user2", Db1, "data1000"),
      Del(500000, "user1", Db1, "data1000"), // <-- trigger
      Ins(600000, "user3", Db2, "data1000")
    )

    // error trace, db1 insertion in next step but db2 insertion in past or future

    val errTrace4: List[Ev] = List(
      Ins(100000, "user4", Db2, "data1000"),
      Del(500000, "user1", Db1, "data1000"), // <-- trigger
      Ins(500000, "user2", Db1, "data1000"),
      Ins(600000, "user3", Db2, "data1000")
    )

    /**
      * Verify:
      */

    m.verify(errTrace1)
  }
}

/**
  * Alternative CSV parsers.
  */

//class TototoshiCSVReader(fileName: String) {
//  import com.github.tototoshi.csv._
//
//  val reader = CSVReader.open(fileName).iterator
//
//  def hasNext: Boolean = reader.hasNext
//
//  def next(): List[String] = reader.next().asInstanceOf[List[String]]
//}

class FastCSVReader(fileName: String) {
  // https://github.com/osiegmar/FastCSV
  import java.io.File
  import de.siegmar.fastcsv.reader.CsvReader
  import de.siegmar.fastcsv.reader.CsvRow
  import java.nio.charset.StandardCharsets
  import scala.collection.JavaConverters._

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

/**
  * Analyzing Nokia log.
  */

class LogReader(fileName: String) {
  val reader = new FastCSVReader(fileName)
  val INSERT = "insert"
  val DELETE = "delete"
  var lineNr: Long = 0

  def getData(line: List[String]) : Map[String,String] = {
    var map : Map[String,String] = Map()
    for (element <- line.tail) {
      val src_rng = element.split("=").map(_.trim())
      map += (src_rng(0) -> src_rng(1))
    }
    map
  }

  def hasNext: Boolean = reader.hasNext

  def next: Option[Ev] = {
    var e: Option[Ev] = None
    breakable {
      while (reader.hasNext) {
        val line = reader.next()
        lineNr += 1
        val name = line(0)
        if (name == INSERT || name == DELETE) {
          val map = getData(line)
          val db = map("db")
          if (db == "db1" || db == "db2")  {
            val t = map("ts").toLong
            val u = map("u")
            val db = if (map("db") == "db1") Db1 else Db2
            val d = map("d")
            name match {
              case INSERT => e = Some(Ins(t, u, db, d))
              case DELETE => e = Some(Del(t, u, db, d))
            }
            break
          }
        }
      }
    }
    e
  }
}

object VerifyNokiaLog {
  def main(args: Array[String]): Unit = {
    val csvFile = new LogReader("/Users/khavelun/Desktop/daut-logs/ldcc/ldcc.csv")
    val monitor = new Ins_1_2
    // val monitor = new Del_1_2_opt
    // val monitor = new Monitors
    Util.time ("Analysis of ldcc.csv") {
      while (csvFile.hasNext) {
        csvFile.next match {
          case Some(event) =>
            monitor.verify(event, csvFile.lineNr)
          case None =>
            println("done - pew!")
        }
      }
      monitor.end()
      println(s"${csvFile.lineNr} lines processed")
    }
  }
}
