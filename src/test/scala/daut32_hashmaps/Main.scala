package daut32_hashmaps

import daut._
import daut.Util.time

/**
  * Property OneThread: When a thread takes a lock no other thread (including itself)
  * can take the lock.
  *
  * Testing speed of using mutable hashmap versus immutable hashmap for indexing of
  * monitor states. Mutable hashmap seems to be around 40% faster than immutable hashmap.
  */

object Util {
  type ThreadId = Int
  type LockId = String
}
import Util._

trait LockEvent
case class acquire(t:ThreadId, x:LockId) extends LockEvent
case class release(t:ThreadId, x:LockId) extends LockEvent

class OneThreadAtATime extends Monitor[LockEvent] {
  override def keyOf(event: LockEvent): Option[LockId] = {
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
      } // label(t, x)
  }
}

object Main {
  def mkLock(i:Int): LockId = i.toString

  def main(args: Array[String]): Unit = {
    val INDEX = 10000000
    DautOptions.DEBUG = false
    val m = new OneThreadAtATime
    time (s"analyzing $INDEX acquisitions") {
      for (index <- 1 to INDEX) {
        m.verify(acquire(index, mkLock(index)))
      }
      for (index <- 1 to INDEX) {
        m.verify(release(index, mkLock(index)))
      }
    }
    m.end()
  }
}


