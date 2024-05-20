package daut1_temporal

import daut._

/**
 * Property AcquireRelease: A task acquiring a lock should eventually release it. At most one task
 * can acquire a lock at a time.
 */

trait LockEvent
case class acquire(t:Int, x:Int) extends LockEvent
case class release(t:Int, x:Int) extends LockEvent

class AcquireRelease extends Monitor[LockEvent] {
  always {
    case acquire(t, x) =>
      hot {
        case acquire(_,`x`) => error
        case release(`t`,`x`) => ok
      }
  }
}

object Main {
  def main(args: Array[String]): Unit = {
    DautOptions.DEBUG = true
    val m = new AcquireRelease
    m.verify(acquire(1, 10))
    m.verify(release(1, 10))
    m.end()
  }
}

