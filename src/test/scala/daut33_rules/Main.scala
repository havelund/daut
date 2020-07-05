package daut33_rules

import daut._

/**
 * Experimenting with rule notation.
 */

trait Event
case class E01(t : Int) extends Event
case class E02(t : Int) extends Event

class Rules extends Monitor[Event] {
  var recorded_t : Int = 0

  case class E01Seen(t: Int) extends fact
  case class E02Seen(t: Int) extends fact
  case class E01andE02Seen(t: Int) extends fact

  always {
    case E01(t) => E01Seen(t)
    case E02(t) => E02Seen(t)
  }

  always {
    case _ if exists {
      case E01Seen(t) =>
        exists {
          case E02Seen(`t`) =>
            recorded_t = t
            true
        }
    } =>
      E01andE02Seen(recorded_t)
  }

}

object Main {
  def main(args: Array[String]) {
    DautOptions.DEBUG = true
    val m = new Rules
    m.verify(E01(10))
    m.verify(E02(20))
    m.verify(E01(20))
    m.verify(E01(30))
    m.end()
  }
}

/*

// find all e01_and_e02

Always case e01(t) => E01_seen(t)

case e02(t) => E02_seen(t)

Always case _ && E01_seen(t1) && E02_seen(t2) && t1 == t2 => record ( e01_and_e02(t1) )

 */