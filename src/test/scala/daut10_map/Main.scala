package daut10_map

import daut._

trait LockEvent
case class acquire(thread: Int, lock: Int) extends LockEvent
case class release(thread: Int, lock: Int) extends LockEvent

/**
 * At most one task can acquire a lock at a time. A task cannot release a lock it has not acquired.
 *
 * This monitor illustrates the exists and map functions, which look for stored facts matching a pattern,
 * and the ensure function, which checks a condition (an assert). This function here in this
 * example tests for the presence of a Locked fact.
 */

// We provide two alternative formulations of this property, one using the
// exists function and one using the map function.

// Using the exists function:

class TestMonitor1 extends Monitor[LockEvent] {
  case class Locked(t: Int, x: Int) extends state {
    watch {
      case release(`t`, `x`) => ok
    }
  }

  always {
    case acquire(t, x) =>
      if (exists {case Locked(_, `x`) => true}) error else Locked(t, x)
    case release(t, x) => ensure(Locked(t,x))
  }
}

// Using the map function:

class TestMonitor2 extends Monitor[LockEvent] {
  case class Locked(t: Int, x: Int) extends state {
    watch {
      case release(`t`, `x`) => ok
    }
  }

  always {
    case acquire(t, x) => {
      map {
        case Locked(_,`x`) => error
      } orelse {
        Locked(t, x)
      }
    }
    case release(t, x) => ensure(Locked(t, x))
  }
}

/**
 * When a task t is acquiring a lock that some other task holds,
 * and t therefore cannot get it, then t is not allowed to hold
 * any other locks (to prevent deadlocks).
 *
 * This monitor illustrates nested find calls, simulating a rule-based
 * style of writing monitors.
 */

class TestMonitor3 extends Monitor[LockEvent] {
  case class Locked(t: Int, x: Int) extends state {
    watch {
      case release(`t`, `x`) => ok
    }
  }

  always {
    case acquire(t, x) => {
      map {
        case Locked(_,`x`) =>
          map {
            case Locked(`t`,y) if x != y => error
          } orelse {
            println("Can't lock but is not holding any other lock, so it's ok")
          }
      } orelse {
        Locked(t, x)
      }
    }
  }
}

/**
 * This monitor illustrates how monitors can be composed in a hierarchy.
 */

class TestMonitor extends Monitor[LockEvent] {
  monitor(
    new TestMonitor1,
    new TestMonitor2,
    new TestMonitor3
  )
}

object Main {
  def main(args: Array[String]): Unit = {
    DautOptions.DEBUG = true
    val m = new TestMonitor
    // m.stopOnError()
    m.verify(acquire(2,  1))
    m.verify(acquire(2,  5))
    m.verify(acquire(1, 10))
    m.verify(acquire(2, 10))
    println(s"Number of errors: ${m.getErrorCount}")
  }
}
