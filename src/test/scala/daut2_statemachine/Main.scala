package daut2_statemachine

import daut._

/**
 * Property AcquireRelease: A task acquiring a lock should eventually release it. At most one task
 * can acquire a lock at a time.
 *
 * The property is here stated naming the intermediate state `acquired`, corresponding to writing
 * a state machine. Note, however, that the state machine is parameterized with data.
 */

trait Event
case class acquire(t:Int, x:Int) extends Event
case class release(t:Int, x:Int) extends Event

class AcquireRelease extends Monitor[Event] {
  always {
    case acquire(t, x) => acquired(t, x)
  }

  def acquired(t: Int, x: Int) : state =
    hot {
      case acquire(_,`x`) => error("lock acquired before released")
      case release(`t`,`x`) => ok
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