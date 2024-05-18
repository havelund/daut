package daut13_timing

import daut._
import daut.Util.time

/**
 * Property AcquireRelease: A task acquiring a lock should eventually release it. A task can acquire at most
 * one lock at a time.
 */

trait LockEvent
case class acquire(t:Int, x:Int) extends LockEvent
case class release(t:Int, x:Int) extends LockEvent

class AcquireRelease extends Monitor[LockEvent] {
  always {
    case acquire(t, x) =>
      hot {
        case acquire(`t`,_) => error
        case release(`t`,`x`) => ok
      }
  }
}

object Main {
  def main(args: Array[String]): Unit = {
    val INDEX = 100000
    DautOptions.DEBUG = false
    val m = new AcquireRelease
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


