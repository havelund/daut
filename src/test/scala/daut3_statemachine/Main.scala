package daut3_statemachine

import daut._

trait TaskEvent
case class start(task: Int) extends TaskEvent
case class stop(task: Int) extends TaskEvent

/**
 * Tasks should be executed (started and stopped) in increasing order
 * according to task numbers, staring from task 0, with no other events in between, hence:
 * start(0),stop(0),start(1),stop(1),start(2),stop(2),... A started task should eventually
 * be stopped.
 *
 * This monitor illustrates next-states (as in Finite State Machines) and
 * state machines.
 */

class StartStop extends Monitor[TaskEvent] {
  def start(task: Int) : state =
    wnext {
      case start(`task`) => stop(task)
    }

  def stop(task: Int) : state =
    next {
      case stop(`task`) => start(task + 1)
    }

  start(0)
  // initial(start(0))
}

object Main {
  def main(args: Array[String]): Unit = {
    DautOptions.DEBUG = true
    val m = new StartStop
    m.verify(start(0))
    m.verify(stop(0))
    m.verify(start(1))
    m.verify(stop(1))
    m.verify(start(3))
    m.verify(stop(3))
    m.end()
  }
}


