package daut50_readme

/*
Example illustrating the use of the `stay` directive.
 */

import daut.{DautOptions, Monitor}

/*
A task acquiring a lock should eventually release it. At most one task
can acquire a lock at a time. Locks are reentrant, meaning that a thread that
currently holds the lock to acquire it again without causing a deadlock.
A task cannot release a lock it has not acquired.
 */

trait LockEvent
case class acquire(t: Int, x: Int) extends LockEvent // thread t acquires lock x
case class release(t: Int, x: Int) extends LockEvent // thread t releases lock x

class AcquireReleaseTextBookStayAutomaton extends Monitor[LockEvent] {
  override def keyOf(event: LockEvent): Option[Int] = {
    event match {
      case acquire(_, x) => Some(x)
      case release(_, x) => Some(x)
    }
  }

  def doAcquire(): state =
    wnext {
      case acquire(t, x) => doRelease(t, x)
    }

  def doRelease(t: Int, x: Int): state =
    next {
      case acquire(`t`, `x`) => stay // the lock is reentrant
      case release(`t`, `x`) => doAcquire()
    } label(t, x)

  doAcquire()
}

object Main {
  def main(args: Array[String]): Unit = {
    DautOptions.DEBUG = true
    val m = new AcquireReleaseTextBookStayAutomaton
    m.verify(acquire(1, 10))
    m.verify(acquire(1, 10))
    m.verify(release(1, 10))
    m.end()
  }
}


