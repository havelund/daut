package dautid2_temporal

import daut._
import daut.Util.time

/**
 * Property OneThread: When a thread takes a lock no other thread (including itself)
 * can take the lock.
 */

trait LockEvent
case class acquire(t:Int, x:Int) extends LockEvent
case class release(t:Int, x:Int) extends LockEvent

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
        case acquire(_,`x`) => error
        case release(`t`,`x`) => ok
      } label(t,x)
  }
}

object Main {
  def main(args: Array[String]) {
    val INDEX = 3
    DautOptions.DEBUG = true
    val m = new OneThread
    time (s"analyzing $INDEX acquisitions") {
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


