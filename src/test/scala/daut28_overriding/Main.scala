package daut28_overriding

import daut.{DautOptions, Monitor}

import scala.math._

abstract class Event {
  val t: Int
}

case class Time(t: Int) extends Event

case class Value(t: Int, name: String, x: Int) extends Event

class NoFact extends Monitor[Event] {
  always {
    case v1@Value(t, name, x) =>
      watch {
        case v2@Value(t, name, x) =>
          println(s"saw $v1 and then $v2")
      }
  }
}

class Fact extends Monitor[Event] {

  new Run()

  case class Run() extends fact {
    always {
      case v1@Value(t, name, x) =>
        watch {
          case v2@Value(t, name, x) =>
            println(s"saw $v1 and then $v2")
        }
    }
  }

}

object Main {
  def main(args: Array[String]): Unit = {
    val m = new Fact
    DautOptions.DEBUG = true

    var eventStream = List[Event](
      Time(0),
      Value(0, "x", 1),
      Value(0, "y", -1),
      Time(1),
      Value(1, "x", 0),
      Value(1, "y", 0),
      Value(8, "y", 0),
    )

    for (e <- eventStream) {
      m.verify(e)
    }
  }
}
