package daut44_hashmaps

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
      } label(t, x)
      case release(_, _) => error
    }

  start()
}

class AcquireReleaseTextBookAutomaton extends Monitor[LockEvent] {
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

  def doRelease(t: Int, x: Int) =
    next {
      case release(`t`, `x`) => doAcquire()
    } label(t, x)

  doAcquire()
}

class AcquireReleaseTextBookLogic extends Monitor[LockEvent] {
  override def keyOf(event: LockEvent): Option[Int] = {
    event match {
      case acquire(_, x) => Some(x)
      case release(_, x) => Some(x)
    }
  }

  override def relevant(event: LockEvent): Boolean = {
    event match {
      case acquire(_, _) | release(_, _) => true
      case _ => false
    }
  }

  def doAcquire(): state =
    wnext {
      case acquire(t, x) => next {
        case release(`t`, `x`) => doAcquire()
      } label(t, x)
    }

  doAcquire()
}

object Main {
  def main(args: Array[String]): Unit = {
    val m = new AcquireReleaseTextBookLogic
    DautOptions.DEBUG = true
    m(acquire(1, 10))
    m(acquire(2, 20))
    m(release(1, 10))
    m(release(2, 20))
    m.end()
  }
}

