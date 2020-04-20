package daut19_id_temporal

import daut._
import daut.Util.time

/**
 * Property AcquireRelease: A task acquiring a lock should eventually release it. A task can acquire at most
 * one lock at a time.
 *
 * Property OneThread: When a thread takes a lock no other thread (including itself)
 * can take the lock.
 *
 * Property LocksNotReentrant: If a thread takes a lock it cannot take that lock
 * again before it has been released.
 */

trait LockEvent

case class acquire(t: Int, x: Int) extends LockEvent

case class release(t: Int, x: Int) extends LockEvent

class AcquireRelease extends Monitor[LockEvent] {
  override def keyOf(event: LockEvent): Option[Int] = {
    event match {
      case acquire(t, _) => Some(t)
      case release(t, _) => Some(t)
    }
  }

  always {
    case acquire(t, x) =>
      hot {
        case acquire(`t`, _) => error
        case release(`t`, `x`) => ok
      } label(t, x)
  }
}

class OneThread extends Monitor[LockEvent] {
  override def keyOf(event: LockEvent): Option[Int] = {
    event match {
      case acquire(_, l) => Some(l)
      case release(_, l) => Some(l)
    }
  }

  always {
    case acquire(t, x) =>
      watch {
        case acquire(_, `x`) => error
        case release(`t`, `x`) => ok
      } label(t, x)
  }
}

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
        case acquire(`t`, `x`) => error
        case release(`t`, `x`) => ok
      } label(t, x)
  }
}

class AllMonitors extends Monitor[LockEvent] {
  monitor(
    new AcquireRelease,
    new OneThread,
    new LocksNotReentrant
  )
}

object Main {
  def main(args: Array[String]) {
    val INDEX = 3
    DautOptions.DEBUG = true
    val m = new AllMonitors
    time(s"analyzing $INDEX acquisitions") {
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


