package daut23_id_temporal

import daut._
import daut.Util.time

/**
 * Property CorrectElevator:
 * 1. An elevator has to have been requested to go to a floor before it can
 *    go to that floor and reach that floor.
 * 2. Once requested to go to a floor, the elevator can be repeatedly requested to
 *    go to that floor, go to that floor, and reach that floor, but not other floors.
 * 3. A RESET resets the system to its initial state for all elevators.
 *
 * Property 1 is essentially a past time temporal property that without indexing would
 * have to be expressed using facts (as in rule-based programming). With indexing we
 * can utilize the same technique as done in slicing based systems such as MOP.
 */

trait ElevatorEvent
case class request(elevator: Int, floor: Int) extends ElevatorEvent
case class going(elevator: Int, floor: Int) extends ElevatorEvent
case class reached(elevator: Int, floor: Int) extends ElevatorEvent
case object RESET extends ElevatorEvent

class ElevatorMonitor extends Monitor[ElevatorEvent] {
  override def keyOf(event: ElevatorEvent): Option[Int] = {
    event match {
      case request(e, f) => Some(e)
      case going(e, f) => Some(e)
      case reached(e, f) => Some(e)
      case RESET => None // goes to all states
    }
  }
}

class CorrectElevator extends ElevatorMonitor {
  def property(): state = watch {
    case request(e, f) => watch {
      case request(`e`, `f`) | going(`e`, `f`) => stay
      case reached(`e`, `f`) | RESET => property()
      case _ => error
    }.label(e, f)
    case RESET => stay
    case _ => error
  }

  property()
}

object Main {
  def main(args: Array[String]): Unit = {
    val m = new CorrectElevator
    DautOptions.DEBUG = true
    m(request(1, 100))
    m(request(2, 200))
    m(going(1, 100))
    m(going(2, 200))
    m(RESET)
    m(reached(1, 100))
    m(reached(2, 200))
    m.end()
  }
}



