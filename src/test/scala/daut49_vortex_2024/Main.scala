package daut49_vortex_2024

import daut.{Monitor}

/*
This example concerns the dispatch and completion of commands on a spacecraft.
The following requirements must be monitored.

- Each command dispatch and completion is identified by a task id, a command number, and time
- Each command that is dispatched must eventually be completed within 20 seconds, with same task id and cmd nr.
- Once a command is dispatched it cannot be dispatched again with same task id and cmd nr before it completes.
- After a command completion the monitor must send a message to a database informing about it.
- The average of execution times of commands must be printed out, and be less than or equal to 15 seconds.
 */

class DB {
  def executed(taskId: Int, cmdNum: Int, time1: Int, time2: Int): Unit = {
    println(s"command executed($taskId,$cmdNum,$time1,$time2)")
  }
}

enum Event {
  case Dispatch(taskId: Int, cmdNum: Int, time: Int)
  case Complete(taskId: Int, cmdNum: Int, time: Int)
}

import Event._

// Simple Monitor:

class CommandMonitor1 extends Monitor[Event] {
  always {
    case Dispatch(taskId, cmdNum, time1) =>
      hot {
        case Dispatch(`taskId`, `cmdNum`, time2) => error
        case Complete(`taskId`, `cmdNum`, time2) if time2 - time1 <= 20 => ok
      }
  }
}

// Complex Monitor:

def average(list: List[Int]): Double = {
  val sum = list.sum
  val count = list.size
  sum.toDouble / count
}

class CommandMonitor2(db: DB) extends Monitor[Event] {
  var execTimes: List[Int] = List()

  always {
    case Dispatch(taskId, cmdNum, time1) =>
      hot {
        case Dispatch(`taskId`, `cmdNum`, time2) => error
        case Complete(`taskId`, `cmdNum`, time2) if time2 - time1 <= 20 =>
          db.executed(taskId, cmdNum, time1, time2)
          execTimes = execTimes :+ (time2-time1)
      }
  }

  override def end(): this.type = {
    val averageExecutionTime = average(execTimes)
    check(averageExecutionTime <= 15)
    println(s"Average = $averageExecutionTime")
    super.end()
  }
}

object Main {
  def main(args: Array[String]): Unit = {
    val trace: List[Event] = List(
      Dispatch(10, 1, 1000),
      Complete(10, 1, 1010),

      Dispatch(10, 2, 2000),
      Complete(10, 2, 2020),

      Dispatch(20, 1, 3000),
      Complete(20, 1, 3030)
    )

    val sut = DB()
    val monitor = new CommandMonitor1
    monitor(trace)
  }
}





