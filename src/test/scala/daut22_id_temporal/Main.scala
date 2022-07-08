package daut22_id_temporal

import daut._
import daut.Util.time

trait ElevatorEvent

case class request(floor: Int) extends ElevatorEvent

case class going(floor: Int) extends ElevatorEvent

case class reached(floor: Int) extends ElevatorEvent

class UpMonitor extends Monitor[ElevatorEvent] {
  var thisFloor : Option[Int] = None
  override def keyOf(event: ElevatorEvent): Option[Int] = {
    event match {
      case request(f) if thisFloor == None =>
        thisFloor = Some(f)
        Some(f)
      case _ => thisFloor
    }
  }
}

class CorrectElevator extends UpMonitor {
  always {
    case request(floor) =>
      watch {
        case reached(`floor`) =>
          thisFloor = None
          ok
        case request(floor2) if floor2 != floor => error
        case going(floor2) if floor2 != floor => error
        case reached(floor2) if floor2 != floor => error
      } label (floor)
  }
}

object Main {
  def main(args: Array[String]): Unit = {
    val m = new CorrectElevator
    DautOptions.DEBUG = true
    m.verify(request(100))
    m.verify(going(100))
    m.verify(going(100))
    m.verify(request(200))
    m.end()
  }
}


