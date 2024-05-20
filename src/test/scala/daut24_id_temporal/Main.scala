package daut24_id_temporal

import daut._
import daut.Util.time

/**
 * Property AcquireRelease: A task acquiring a lock should eventually release it.
 * A task can only be acquired by one task at a time, but a task can acquire the
 * same lock numerous times before releasing it.
 */

trait LockEvent

case class acquire(t: Int, x: Int) extends LockEvent

case class release(t: Int, x: Int) extends LockEvent

class TestMonitor extends Monitor[LockEvent] {
  override def keyOf(event: LockEvent): Option[Int] = {
    event match {
      case acquire(_, x) => Some(x)
      case release(_, x) => Some(x)
    }
  }

  def start(): state = {
    watch {
      case acquire(t,x) => watch {
        case acquire(`t`, `x`) => stay
        case acquire(_, `x`) => error
        case release(`t`, `x`) => ok
      } label(t,x)
    }
  }

  start()
}

class AcquireRelease extends Monitor[LockEvent] {
  override def keyOf(event: LockEvent): Option[Int] = {
    event match {
      case acquire(_, x) => Some(x)
      case release(_, x) => Some(x)
    }
  }

  always {
    case acquire(t, x) =>
      hot {
        case acquire(`t`, `x`) => stay
        case acquire(_, `x`) => error
        case release(`t`, `x`) => ok
      } label(t, x)
  }
}

object Main {
  def main(args: Array[String]): Unit = {
    DautOptions.DEBUG = true
    val m = new TestMonitor
    m.verify(acquire(1, 100))
    m.verify(acquire(1, 100))
    m.verify(acquire(2, 200))
    m.verify(acquire(2, 200))
    m.verify(release(1, 100))
    m.verify(acquire(1, 200))
    m.end()
  }
}





