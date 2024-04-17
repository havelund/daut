package daut4_rules

import daut._

/**
 * A task acquiring a lock should eventually release it. At most one task
 * can acquire a lock at a time. A task cannot release a lock it has not acquired.

 * This monitor illustrates always states, hot states, and the use of a
 * fact (Locked) to record history. This is effectively in part a past time property.
 */

trait LockEvent
case class acquire(t: Int, x: Int) extends LockEvent
case class release(t: Int, x: Int) extends LockEvent

class AcquireRelease extends Monitor[LockEvent] {
  case class Locked(t: Int, x: Int) extends state {
    hot {
      case acquire(_, `x`) => error
      case release(`t`, `x`) => ok
    }
  }

  always {
    case acquire(t, x)                  => Locked(t, x)
    case release(t, x) if !Locked(t, x) => error
  }
}

object Main {
  def main(args: Array[String]): Unit = {
    val m = new AcquireRelease
    m.verify(acquire(1, 10))
    m.verify(acquire(2, 10))
  }
}

