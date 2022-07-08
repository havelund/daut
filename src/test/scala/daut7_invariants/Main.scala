package daut7_invariants

import daut._

/**
 * Property AcquireRelease: A task acquiring a lock should eventually release it. At most one task
 * can acquire a lock at a time. At most 4 locks should be acquired at any moment.
 */

trait LockEvent
case class acquire(t:Int, x:Int) extends LockEvent
case class release(t:Int, x:Int) extends LockEvent

class AcquireReleaseLimit extends Monitor[LockEvent] {
  var count : Int = 0

  invariant ("max four locks acquired") {count <= 4}

  always {
    case acquire(t, x) =>
      count +=1
      hot {
        case acquire(_,`x`) => error
        case release(`t`,`x`) => count -= 1; ok
      }
  }
}

object Main {
  def main(args: Array[String]): Unit = {
    DautOptions.DEBUG = true
    val m = new AcquireReleaseLimit
    m.verify(acquire(1, 10))
    m.verify(acquire(2, 20))
    m.verify(acquire(3, 30))
    m.verify(acquire(4, 40))
    m.verify(release(1, 10))
    m.verify(acquire(5, 50))
    m.verify(acquire(6, 60))
    m.verify(release(2, 20))
    m.verify(release(3, 30))
    m.verify(release(4, 40))
    m.verify(release(5, 50))
    m.verify(release(6, 60))
    m.end()
  }
}


