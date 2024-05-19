package daut17_id_temporal

import daut._
import daut.Util.time

/**
 * Property LocksNotReentrant: If a thread takes a lock it cannot take that lock
 * again before it has been released.
 */

trait LockEvent
case class acquire(t:Int, x:Int) extends LockEvent
case class release(t:Int, x:Int) extends LockEvent

class LocksNotReentrant extends Monitor[LockEvent] {
  override def keyOf(event: LockEvent): Option[Int] = {
    event match {
      case acquire(t, l) => Some(t + l)
      case release(t, l) => Some(t + l)
    }
  }

  always {
    case acquire(t, x) =>
      watch {
        case acquire(`t`,`x`) => error
        case release(`t`,`x`) => ok
      } label(t,x)
  }
}

object Main {
  def main(args: Array[String]): Unit = {
    val INDEX = 3
    DautOptions.DEBUG = true
    val m = new LocksNotReentrant
    time (s"analyzing $INDEX acquisitions") {
      for (index <- 1 to INDEX) {
        m.verify(acquire(index, index))
      }
      for (index <- 1 to INDEX) {
        m.verify(release(index, index))
      }
    }
    m.end()
  }
}

