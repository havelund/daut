package daut21_id_temporal

import daut._
import daut.Util.time

/**
 * Property AcquireRelease: A task acquiring a lock should eventually release it. A lock
 * can only be held by one task at a time.
 */

trait LockEvent

case class acquire(t: Int, x: Int) extends LockEvent

case class release(t: Int, x: Int) extends LockEvent

case object CANCEL extends LockEvent

class SlowLockMonitor extends Monitor[LockEvent]

class FastLockMonitor extends Monitor[LockEvent] {
  override def keyOf(event: LockEvent): Option[Int] = {
    event match {
      case acquire(_, x) => Some(x)
      case release(_, x) => Some(x)
      case CANCEL => None
    }
  }
}

class BadLockMonitor extends Monitor[LockEvent] {
  override def keyOf(event: LockEvent): Option[Int] = {
    event match {
      case acquire(t, _) => Some(t)
      case release(t, _) => Some(t)
      case CANCEL => None
    }
  }
}

class CorrectLock extends FastLockMonitor {
  always {
    case acquire(t, x) =>
      hot {
        case acquire(_, `x`) => error
        case CANCEL | release(`t`, `x`) => ok
      } // label(t, x)
  }
}

object Main {
  def main(args: Array[String]): Unit = {
    val m = new CorrectLock
    DautOptions.DEBUG = true
    m.verify(acquire(1, 100))
    m.verify(acquire(2, 200))
    m.verify(acquire(1, 200)) // error
    m.verify(CANCEL)
    m.end()
  }
}

