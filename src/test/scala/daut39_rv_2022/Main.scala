package daut39_rv_2022

import daut._

/**
  * A thread acquiring a lock must eventually release it, with no further
  * acquisitions in between, and thread can only release a lock if it has acquired
  * it, and not yet released it (for each lock we want acquisitions and releases
  * to alternate).
 */

/*
===============
Python version:
===============

class M3(Monitor):
    def key(self, event) -> Optional[object]:
        match event:
            case Acquire(_, lock) | Release(_, lock):
                return lock

    @initial
    class Idle(NextState):
        def transition(self, event):
            match event:
                case Acquire(thread, lock):
                    return M3.DoRelease(thread, lock)

    @data
    class DoRelease(HotNextState):
        thread: str
        lock: int

        def transition(self, event):
            match event:
                case Release(self.thread, self.lock):
                    return M3.Idle()
 */

trait Event
case class Acquire(thread: String, lock: Int) extends Event
case class Release(thread: String, lock: Int) extends Event

class M3 extends Monitor[Event] {
  override def keyOf(event: Event): Option[Int] = {
    event match {
      case Acquire(_, lock) => Some(lock)
      case Release(_, lock) => Some(lock)
    }
  }

  case class Idle() extends state {
    wnext {
      case Acquire(thread, lock) => DoRelease(thread, lock)
    }
  }

  case class DoRelease(thread: String, lock: Int) extends state {
    next {
      case Release(`thread`, `lock`) => Idle()
    }
  }

  initial(Idle())
}

object Main {
  def main(args: Array[String]): Unit = {
    val m = new M3
    DautOptions.DEBUG = false
    val trace =  List(
      Acquire("T1", 1),
      Acquire("T2", 2),
      Acquire("T3", 3),
      Release("T1", 1),
      Acquire("T3", 2),  // double acquisition by other thread: bad
      // missing release of lock 3 by T3: bad
    )
    m.verify(trace)
  }
}
