package daut8_realtime

import daut._

/**
 * Property ReleaseWithin: A task acquiring a lock should eventually release
 * it within a given time period.
 */

trait LockEvent
case class acquire(t:Int, x:Int, ts:Int) extends LockEvent
case class release(t:Int, x:Int, ts:Int) extends LockEvent

class ReleaseWithin(limit: Int) extends Monitor[LockEvent] {
  always {
    case acquire(t, x, ts1) =>
      hot {
        case release(`t`,`x`, ts2) =>
          ts2 - ts1 <= limit
          // ensure(ts2 - ts1 <= limit)
          // check(ts2 - ts1 <= limit)
      }
  }
}

object Main {
  def main(args: Array[String]) {
    val m = new ReleaseWithin(500)
    m.verify(acquire(1, 10, 100))
    m.verify(release(1, 10, 800)) // violates property
    m.end()
  }
}


