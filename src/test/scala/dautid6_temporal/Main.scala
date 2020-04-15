package dautid6_temporal

import daut._
import daut.Util.time

/**
 * Property AcquireRelease: A task acquiring a lock should eventually release it. A task can acquire at most
 * one lock at a time. Furthermore, the `CANCEL` event will cancel all lock acquisitions.
 */

trait LockEvent
case class acquire(t:Int, x:Int) extends LockEvent
case class release(t:Int, x:Int) extends LockEvent
case object CANCEL extends LockEvent

class AcquireRelease extends Monitor[LockEvent] {
  override def keyOf(event: LockEvent): Option[Int] = {
    event match {
      case acquire(t, _) => Some(t)
      case release(t, _) => Some(t)
      case CANCEL => None
    }
  }

  always {
    case acquire(t, x) =>
      hot {
        case acquire(`t`,_) => error
        case CANCEL | release(`t`,`x`) => ok
      } label(t,x)
  }
}

object Main {
  def main(args: Array[String]) {
    val INDEX = 3
    DautOptions.DEBUG = true
    val m = new AcquireRelease
    time (s"analyzing $INDEX acquisitions") {
      for (index <- 1 to INDEX) {
        m.verify(acquire(index, index))
      }
      m.verify(CANCEL)
    }
    m.end()
  }
}
