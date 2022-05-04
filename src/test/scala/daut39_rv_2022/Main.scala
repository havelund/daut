package daut39_rv_2022

import daut._

/**
 * A thread acquiring a lock must eventually release it, and while acquired
 * the lock cannot be acquired by another thread before being released again.
 */

/*
Pyton Monitor:

class M1(Monitor):
    def transition(self, event):
        match event:
            case Acquire(thread, lock):
                return M1.DoRelease(thread, lock)

    @data
    class DoRelease(HotState):
        thread: str
        lock: int

        def transition(self, event):
            match event:
                case Acquire(thread, self.lock)
                  if thread != self.thread:
                    return error()
                case Release(self.thread, self.lock):
                    return ok
 */

trait Event
case class Acquire(thread: String, lock: Int) extends Event
case class Release(thread: String, lock: Int) extends Event

class M1 extends Monitor[Event] {
  always {
    case Acquire(thread, lock) => DoRelease(thread, lock)
  }

  case class DoRelease(thread: String, lock: Int) extends state {
    hot {
      case Acquire(thr, `lock`)  if thr != thread => error
      case Release(`thread`, `lock`) => ok
    }
  }
}

object Main {
  def main(args: Array[String]) {
    val m = new M1
    val trace =  List(
      Acquire("T1", 1),
      Acquire("T1", 1),  // double acquisition by same thread: ok
      Release("T1", 1),
      Acquire("T2", 2),
      Acquire("T3", 2),  // double acquisition by other thread: bad
      // missing release of lock 2 by T3: bad
    )
    m.verify(trace)
  }
}
