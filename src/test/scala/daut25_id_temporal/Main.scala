package daut25_id_temporal

import daut._

/**
 * A task acquiring a lock should eventually release it. At most one task
 * can acquire a lock at a time. A task cannot release a lock it has not acquired.
 *
 * This monitor illustrates the use of indexing to model past time properties, instead
 * of using case classes (facts) to model the past as was done in example `daut4_rules`.
 */

trait LockEvent

case class acquire(t: Int, x: Int) extends LockEvent

case class release(t: Int, x: Int) extends LockEvent

class AcquireRelease extends Monitor[LockEvent] {
  override def keyOf(event: LockEvent): Option[Int] = {
    event match {
      case acquire(_, x) => Some(x)
      case release(_, x) => Some(x)
    }
  }

  def start(): state =
    watch {
      case acquire(t, x) => hot {
        case acquire(_, `x`) => error
        case release(`t`, `x`) => start()
      }.label(t, x)
      case release(_, _) => error
    }

  start()
}

object Main {
  def main(args: Array[String]): Unit = {
    val m = new AcquireRelease
    DautOptions.DEBUG = true
    m(acquire(1, 10))
    m(acquire(2, 20))
    m(release(3, 30))
    m.end()
  }
}

