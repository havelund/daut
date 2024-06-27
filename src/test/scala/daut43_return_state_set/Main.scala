package daut43_return_state_set

package daut2_statemachine

import daut._

/**
  * Property AcquireRelease: A task acquiring a lock should eventually release it. At most one task
  * can acquire a lock at a time.
  *
  * The property is here stated naming the intermediate state `acquired`, corresponding to writing
  * a state machine. Note, however, that the state machine is parameterized with data.
  */

trait LockEvent
case class acquire(t:Int, x:Int) extends LockEvent
case class release(t:Int, x:Int) extends LockEvent

class AcquireRelease extends Monitor[LockEvent] {
  always {
    case acquire(t, x) =>
      Set(
        doRelease(t, x),
        dontAcquire(t,x)
      )
  }

  def doRelease(t: Int, x: Int) : state =
    hot {
      case release(`t`,`x`) => ok
    }

  def dontAcquire(t: Int, x: Int) : state =
    watch {
      case acquire(`t`,`x`) => error("lock acquired again by same task")
    }
}

object Main {
  def main(args: Array[String]): Unit = {
    DautOptions.DEBUG = true
    val m = new AcquireRelease
    m.verify(acquire(1, 10))
    m.verify(release(1, 10))
    m.verify(acquire(1, 10))
    m.end()
  }
}
