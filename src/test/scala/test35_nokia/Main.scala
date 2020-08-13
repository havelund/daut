package test35_nokia

import daut._
import daut.Util.time

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

case object Db3 extends Database

trait Event {
  val time: Int
}

case class Insert(time: Int, user: String, db: Database, data: String) extends Event

case class Delete(time: Int, user: String, db: Database, data: String) extends Event

class NokiaMonitor extends Monitor[Event] {
  override def keyOf(event: Event): Option[String] = {
    event match {
      case Insert(_, _, _, data) => Some(data)
      case Delete(_, _, _, data) => Some(data)
    }
  }
}

class Ins_1_2 extends NokiaMonitor {

  case class InsDb2_or_DelDb1(time: Int, data: String) extends fact {
    watch {
      case event if event.time - time > 1 => ok
    }
  }

  always {
    case Insert(time, _, Db2, data) => InsDb2_or_DelDb1(time, data)
    case Delete(time, _, Db1, data) => InsDb2_or_DelDb1(time, data)
    case Insert(time, _, Db1, data) if data != "[unknown]" =>
      if (exists { case InsDb2_or_DelDb1(`time`, `data`) => true }) ok else
        hot {
          case event if event.time - time > 108000 => error
          case Insert(_, _, Db2, `data`) => ok
          case Delete(_, _, Db1, `data`) => ok
        }
  }
}

class Del_1_2 extends NokiaMonitor {

  case class Del(time: Int, db: Database, data: String) extends fact {
    watch {
      case event if event.time - time > 1 => ok
    }
  }

  case class Ins(time: Int, db: Database, data: String) extends fact {
    watch {
      case event if event.time - time > 108000 => ok
    }
  }

  always {
    case Delete(time, _, Db2, data) => Del(time, Db2, data)
    case Insert(time, _, db, data) => Ins(time, db, data)

    case Delete(time, _, Db1, data) if data != "[unknown]" =>
      if (exists { case Del(`time`, Db2, `data`) => true }) ok else {
        val s1 = if (exists { case Ins(time0, Db1, `data`) => time - time0 <= 108000 }) ok else
          hot {
            case event if event.time - time > 108000 => error
            case Delete(time1, _, Db2, `data`) if time1 - time <= 108000 => ok
            case Insert(`time`, _, Db1, `data`) => ok
          }
        val s2 = if (exists { case Ins(time0, Db2, `data`) => time - time0 <= 108000 }) hot {
          case event if event.time - time > 108000 => error
          case Delete(time1, _, Db2, `data`) if time1 - time <= 108000 => ok
        } else hot {
          case event if event.time - time > 108000 => ok
          case Delete(time1, _, Db2, `data`) if time1 - time <= 108000 => ok
          case Insert(time1, _, Db2, `data`) if time1 - time <= 108000 => hot {
            case event if event.time - time > 108000 => error
            case Delete(time1, _, Db2, `data`) if time1 - time <= 108000 => ok
          }
        }
        (s1, s2)
      }
  }
}

/**
  * Testing Ins_1_2
  */

object Main1 {
  def main(args: Array[String]) {
    DautOptions.DEBUG = true
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
    m.end()
  }
}

/**
  * Testing Del_1_2
  */

object Main2 {
  def main(args: Array[String]) {
    DautOptions.DEBUG = true
    val m = new Del_1_2

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

    val errTrace5: List[Event] = List(
      Insert(100000, "user4", Db2, "data1000"),
      Delete(500000, "user1", Db1, "data1000"), // <-- trigger
      Insert(500000, "user2", Db1, "data1000"),
      Insert(600000, "user3", Db2, "data1000")
    )

    /**
      * Verify:
      */

    m.verify(errTrace5)
    m.end()
  }
}