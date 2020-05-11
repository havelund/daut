package daut31_calculation

import daut.{Monitor, Translate}

/**
  * Illustrating calculation.
  */

trait Event // event base class
case class Value(t: Int, name: String, action: String, x: Any) extends Event

case class Interval(t1: Int, t2: Int)

class IntervalTranslator(name: String,
                         open: String,
                         close: String,
                         p: Any => Boolean) extends Translate[Event, Interval] {
  always {
    case value1 @ Value(t1, `name`, `open`, _) =>
      watch {
        case value2 @ Value(_, nm, act, v) if !(nm == name && act == close) && !p(v) =>
          ok
        case value3 @ Value(t2, `name`, `close`, _) =>
          push(Interval(t1, t2))
      }
  }
}

class IntervalMonitor(epsilon: Int) extends Monitor[Interval] {
  always {
    case Interval(t1, t2) => watch {
      case Interval(t3, t4) =>
        ensure(t3 - t2 <= epsilon)
    }
  }
}

object Main {
  def main(args: Array[String]): Unit = {
    val trace = List(
      Value(10, "receivedClientHeartbeat", "connectionEstablished", 1),
      Value(12, "...", "...", 99),
      Value(14, "...", "...", 00),
      Value(20, "receivedClientHeartbeat", "connectionClosed", 2),
      Value(22, "...", "...", 99),
      Value(24, "...", "...", 99),
      Value(30, "receivedClientHeartbeat", "connectionEstablished", 3),
      Value(32, "...", "...", 99),
      Value(33, "...", "...", 99),
      Value(40, "receivedClientHeartbeat", "connectionClosed", 4)
    )

    val translate = new IntervalTranslator(
      "receivedClientHeartbeat",
      "connectionEstablished",
      "connectionClosed",
      (x) => x.isInstanceOf[Int]
    )

    val monitor = new IntervalMonitor(8)
    val intervals = translate(trace).trace
    println(intervals.mkString("\n"))
    monitor(intervals)
  }
}
