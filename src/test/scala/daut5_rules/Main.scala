package daut5_rules

import daut._
import daut.Monitor

/**
 * Property AcquireRelease: A task acquiring a lock should eventually release it. At most one task
 * can acquire a lock at a time.
 *
 * property ReleaseAcquired: A task cannot release a lock it has not acquired.
 * */

trait Event
case class acquire(t:Int, x:Int) extends Event
case class release(t:Int, x:Int) extends Event

class AcquireRelease extends Monitor[Event] {
  always {
    case acquire(t, x) =>
      hot {
        case acquire(_,`x`) => error
        case release(`t`,`x`) => ok
      }
  }
}

class ReleaseAcquired extends Monitor[Event] {
  case class Locked(t:Int, x:Int) extends state {
    watch {
      case release(`t`,`x`) => ok
    }
  }

  always {
    case acquire(t,x) => Locked(t,x)
    case release(t,x) if !Locked(t,x) => error
  }
}

class Monitors extends Monitor[Event] {
  monitor(new AcquireRelease, new ReleaseAcquired)
}

object Main {
  def main(args: Array[String]) {
    DautOptions.DEBUG = true
    val m = new Monitors
    m.verify(acquire(1, 10))
    m.verify(release(1, 10))
    m.end()
  }
}
