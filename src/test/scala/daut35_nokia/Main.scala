package daut35_nokia

import daut._
import util.control.Breaks._

/**
  * The Ins_1_2 and Del_1_2 properties from the paper:
  * Monitoring Usage-control Policies in Distributed Systems
  * David Basin, Matus Harvan, Felix Klaedtke, and Eugen Zalinescu
  * Proceedings of the International Workshop on Temporal Representation and Reasoning
  * TIME 2011, pages 88 - 95.
  */

trait Database

case object Db1 extends Database

case object Db2 extends Database

trait Event {
  val time: Long
}

case class Insert(time: Long, user: String, db: Database, data: String) extends Event

case class Delete(time: Long, user: String, db: Database, data: String) extends Event

class Ins_1_2 extends Monitor[Event]  {

  val hours_30 = 108000
  val seconds_1 = 1

  case class InsDb2_or_DelDb1(time: Long, data: String) extends fact {
    watch {
      case event if event.time - time > seconds_1 => ok
    }
  }

  always {
    case Delete(time, _, Db1, data) => InsDb2_or_DelDb1(time, data)
    case Insert(time, _, Db2, data) => InsDb2_or_DelDb1(time, data)

    case Insert(time, _, Db1, data) if data != "[unknown]" =>
      if (exists { case InsDb2_or_DelDb1(`time`, `data`) => true }) ok else
        hot {
          case event if event.time - time > hours_30 => error
          case Insert(_, _, Db2, `data`) => ok
          case Delete(_, _, Db1, `data`) => ok
        }
  }
}

class Del_1_2 extends Monitor[Event] {

  val hours_30 = 108000
  val seconds_1 = 1

  case class Del(time: Long, db: Database, data: String) extends fact {
    watch {
      case event if event.time - time > seconds_1 => ok
    }
  }

  case class Ins(time: Long, db: Database, data: String) extends fact {
    watch {
      case event if event.time - time > hours_30 => ok
    }
  }

  always {
    case Delete(time, _, Db2, data) => Del(time, Db2, data)
    case Insert(time, _, db, data) => Ins(time, db, data)

    case Delete(time, _, Db1, data) if data != "[unknown]" =>
      if (exists { case Del(`time`, Db2, `data`) => true }) ok else {
        val s1 = if (exists { case Ins(time0, Db1, `data`) => time - time0 <= hours_30 }) ok else
          hot {
            case event if event.time - time > hours_30 => error
            case Delete(time1, _, Db2, `data`) if time1 - time <= hours_30 => ok
            case Insert(`time`, _, Db1, `data`) => ok
          }
        val s2 = if (exists { case Ins(time0, Db2, `data`) => time - time0 <= hours_30 }) hot {
          case event if event.time - time > hours_30 => error
          case Delete(time1, _, Db2, `data`) if time1 - time <= hours_30 => ok
        } else hot {
          case event if event.time - time > hours_30 => ok
          case Delete(time1, _, Db2, `data`) if time1 - time <= hours_30 => ok
          case Insert(time1, _, Db2, `data`) if time1 - time <= hours_30 => hot {
            case event if event.time - time > hours_30 => error
            case Delete(time1, _, Db2, `data`) if time1 - time <= hours_30 => ok
          }
        }
        (s1, s2)
      }
  }
}

class History(resetBound: Int, timeLimit: Long, db: String) {
  val map = collection.mutable.Map[String, Long]()
  var counter : Int = 0

  def get(data: String): Option[Long] = map.get(data)

  def put(data: String, time : Long) : Unit = {
    counter += 1
    if (counter == resetBound) {
      // println(s"----- $db")
      counter = 0
      // println(map.size)
      map.filterInPlace {
        case (_,time0) => time - time0 <= timeLimit
      }
      // println(map.size)
    }
    map.put(data, time)
  }

  def withinTimeLimit(data: String, now: Long): Boolean = {
    get(data) match {
      case None => false
      case Some(time) => now - time <= timeLimit
    }
  }
}

class Del_1_2_coded extends Monitor[Event] {
  val hours_30 = 108000
  val seconds_1 = 1
  val seconds_0 = 0

  val insertedDb1 = new History(500000, hours_30, "insertedDb1")
  val insertedDb2 = new History(500000, hours_30, "insertedDb2")
  val deletedDb2 = new History(500000, seconds_0, "deletedDb2")

  always {
    case Insert(time, _, Db1, data) => insertedDb1.put(data, time)
    case Insert(time, _, Db2, data) => insertedDb2.put(data, time)
    case Delete(time, _, Db2, data) => deletedDb2.put(data,time)

    case Delete(time, _, Db1, data) if data != "[unknown]" =>
      if (deletedDb2.withinTimeLimit(data, time)) ok else {
        val s1 = if (insertedDb1.withinTimeLimit(data, time) ) ok else
          hot {
            case event if event.time - time > hours_30 => error
            case Delete(time1, _, Db2, `data`) if time1 - time <= hours_30 => ok
            case Insert(`time`, _, Db1, `data`) => ok
          }
        val s2 = if (insertedDb2.withinTimeLimit(data, time)) hot {
          case event if event.time - time > hours_30 => error
          case Delete(time1, _, Db2, `data`) if time1 - time <= hours_30 => ok
        } else hot {
          case event if event.time - time > hours_30 => ok
          case Delete(time1, _, Db2, `data`) if time1 - time <= hours_30 => ok
          case Insert(time1, _, Db2, `data`) if time1 - time <= hours_30 => hot {
            case event if event.time - time > hours_30 => error
            case Delete(time1, _, Db2, `data`) if time1 - time <= hours_30 => ok
          }
        }
        (s1, s2)
      }
  }
}

class BothMonitors extends Monitor[Event] {
  monitor(new Ins_1_2, new Del_1_2_coded)
}

/**
  * Testing Ins_1_2
  */

object Test_Ins_1_2 {
  def main(args: Array[String]): Unit = {
    DautOptions.DEBUG = false
    val m = new Ins_1_2

    /**
      * Correct Traces:
      */

    // correct trace, deletion in past

    val okTrace1: List[Event] = List(
      Delete(1000, "user2", Db1, "data1000"),
      Insert(1000, "user1", Db1, "data1000"),
    )

    // correct trace, insertion in past

    val okTrace2: List[Event] = List(
      Insert(1000, "user2", Db2, "data1000"),
      Insert(1000, "user1", Db1, "data1000"),
    )

    // correct trace, deletion in future

    val okTrace3: List[Event] = List(
      Insert(1000, "user1", Db1, "data1000"),
      Delete(5000, "user2", Db1, "data1000"),
    )

    // correct trace, insertion in future

    val okTrace4: List[Event] = List(
      Insert(1000, "user1", Db1, "data1000"),
      Insert(5000, "user2", Db2, "data1000"),
    )

    /**
      * Incorrect Traces:
      */

    // error trace, deletion too early in past and too late in future

    val errTrace1: List[Event] = List(
      Delete(999, "user2", Db1, "data1000"),
      Insert(1000, "user1", Db1, "data1000"),
      Delete(200000, "user2", Db1, "data1000"),
    )

    // error trace, deletion too early in past and too late in future

    val errTrace2: List[Event] = List(
      Insert(999, "user2", Db2, "data1000"),
      Insert(1000, "user1", Db1, "data1000"),
      Insert(200000, "user2", Db2, "data1000"),
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
  def main(args: Array[String]): Unit = {
    DautOptions.DEBUG = false
    val m = new Del_1_2_coded

    /**
      * Correct Traces:
      */

    // correct trace, db2 deletion in past

    val okTrace1: List[Event] = List(
      Delete(1000, "user2", Db2, "data1000"),
      Delete(1000, "user1", Db1, "data1000"), // <-- trigger
    )

    // correct trace, db2 deletion in the future

    val okTrace2: List[Event] = List(
      Delete(1000, "user1", Db1, "data1000"), // <-- trigger
      Delete(9999, "user2", Db2, "data1000")
    )

    // correct trace, db1 insertion in the past and no timely db2 insertion in the past or future

    val okTrace3: List[Event] = List(
      Insert(100000, "user4", Db2, "data1000"),
      Insert(400000, "user2", Db1, "data1000"),
      Delete(500000, "user1", Db1, "data1000"), // <-- trigger
      Insert(800000, "user3", Db2, "data1000")
    )

    // correct trace, db1 insertion in next step and no db2 insertion in past or future

    val okTrace4: List[Event] = List(
      Insert(100000, "user4", Db2, "data1000"),
      Delete(500000, "user1", Db1, "data1000"), // <-- trigger
      Insert(500000, "user2", Db1, "data1000"),
      Insert(800000, "user3", Db2, "data1000")
    )

    /**
      * Incorrect Traces:
      */

    // error trace, no db2 deletion in past

    val errTrace1: List[Event] = List(
      Delete(999, "user2", Db2, "data1000"),
      Delete(1000, "user1", Db1, "data1000"), // <-- trigger
    )

    // error trace, no db2 deletion in the future

    val errTrace2: List[Event] = List(
      Delete(1000, "user1", Db1, "data1000"), // <-- trigger
      Delete(500000, "user2", Db2, "data1000")
    )

    // error trace, db1 insertion in the past but db2 insertion in the past or future

    val errTrace3: List[Event] = List(
      Insert(100000, "user4", Db2, "data1000"),
      Insert(400000, "user2", Db1, "data1000"),
      Delete(500000, "user1", Db1, "data1000"), // <-- trigger
      Insert(600000, "user3", Db2, "data1000")
    )

    // error trace, db1 insertion in next step but db2 insertion in past or future

    val errTrace4: List[Event] = List(
      Insert(100000, "user4", Db2, "data1000"),
      Delete(500000, "user1", Db1, "data1000"), // <-- trigger
      Insert(500000, "user2", Db1, "data1000"),
      Insert(600000, "user3", Db2, "data1000")
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

/**
  * Analyzing Nokia log.
  */

class LogReader(fileName: String) {
  val reader = new FastCSVReader(fileName)

  val INSERT = "insert"
  val DELETE = "delete"
  var PRINT_EACH = 1000000

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

  def next: Option[Event] = {
    var event: Option[Event] = None
    breakable {
      while (reader.hasNext) {
        val line = reader.next().asInstanceOf[List[String]]
        lineNr += 1
        if ((lineNr % PRINT_EACH) == 0) println(lineNr / PRINT_EACH)
        val name = line(0)
        if (name == INSERT || name == DELETE) {
          val dataMap = getData(line)
          val db = dataMap("db")
          if (db == "db1" || db == "db2")  {
            val time = dataMap("ts").toLong
            val user = dataMap("u")
            val database = if (dataMap("db") == "db1") Db1 else Db2
            val data = dataMap("d")
            name match {
              case INSERT =>
                event = Some(Insert(time, user, database, data))
              case DELETE =>
                event = Some(Delete(time, user, database, data))
            }
            break
          }
        }
      }
    }
    event
  }
}

object VerifyNokiaLog {
  def main(args: Array[String]): Unit = {
    val csvFile = new LogReader("/Users/khavelun/Desktop/daut-logs/ldcc/ldcc.csv")
    // val monitor = new Ins_1_2
    val monitor = new Del_1_2_coded
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
