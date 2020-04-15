package daut11_during

import daut._
import daut.Monitor

trait Event
case class enter(thread: Int) extends Event
case class exit(thread:Int) extends Event
case class abort(thread:Int) extends Event

/**
 * A task acquiring a lock should eventually release it.
 *
 * This monitor illustrates the during predicate and invariants.
 */

class TestMonitor extends Monitor[Event] {
  val critical1 = during(enter(1))(exit(1), abort(1))
  val critical2 = during(enter(2))(exit(2), abort(1))

  invariant {
    !(critical1 && critical2)
  }

  override def verifyBeforeEvent(event: Event): Unit = {
    println(s"BEGIN PROCESS $event")
  }

  override def verifyAfterEvent(event: Event): Unit = {
    println(s"END PROCESS $event")
  }
}

object Main {
  def main(args: Array[String]) {
    DautOptions.DEBUG = true
    val m = new TestMonitor
    m.verify(enter(1))
    m.verify(enter(2))
    m.verify(exit(1))
    m.verify(abort(2))
    m.end()
  }
}


