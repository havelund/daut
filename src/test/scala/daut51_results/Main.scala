package daut51_results

import daut.{DautOptions, Monitor}
import scala.sys

/*
Requirement:

Commands and identified by a task id and a command number.
Commands are dispatched for execution on a processor, replied to (acknowledgement of dispatch
received), and completed.

   1. Dispatch
      1.1 Monotonic
          Command numbers must increase by 1 for each dispatch.
      1.2 DispatchReplyComplete:
          When a command is dispatched by a task, it must be replied to
          without a dispatch in between, and this must be followed
          by a completion.
   2. Completion
      A command number can not complete more than once.
 */

sealed trait Event

case class DispatchRequest(taskId: Int, cmdNum: Int) extends Event

case class DispatchReply(taskId: Int, cmdNum: Int) extends Event

case class CommandCompleted(taskId: Int, cmdNum: Int) extends Event

class CommandMonitor extends Monitor[Event] {
  override def instanceOf(event: Event): Option[Any] = {
    event match {
      case DispatchRequest(_, cmdNum) => Some(cmdNum)
      case DispatchReply(_, cmdNum) => Some(cmdNum)
      case CommandCompleted(_, cmdNum) => Some(cmdNum)
    }
  }
}

class MonotonicMonitor extends CommandMonitor {
  always {
    case DispatchRequest(_, cmdNum1) =>
      watch(s"$cmdNum1 seen, next should be ${cmdNum1 + 1}") {
        case DispatchRequest(_, cmdNum2) =>
          ensure(cmdNum2 == cmdNum1 + 1)
      }
  }
}

class DispatchReplyCompleteMonitor extends CommandMonitor {
  always {
    case DispatchRequest(taskId, cmdNum) =>
      hot(s"reply to $cmdNum") {
        case DispatchRequest(`taskId`, `cmdNum`) => error
        case DispatchReply(`taskId`, `cmdNum`) =>
          reportError(s"We saw a reply for $taskId/$cmdNum")
          hot(s"complete $cmdNum") {
            case CommandCompleted(`taskId`, `cmdNum`) => ok
          }
      }
  }
}

class DispatchMonitor extends CommandMonitor {
  monitor(MonotonicMonitor(), DispatchReplyCompleteMonitor())
}

class CompletionMonitor extends CommandMonitor {
  always {
    case CommandCompleted(_, cmdNum) => NoMoreCompletions(cmdNum)
  }

  case class NoMoreCompletions(cmdNum: Int) extends state {
    watch(ID(cmdNum)) {
      case CommandCompleted(_, `cmdNum`) => error
    }
  }
}

class Monitors extends CommandMonitor {
  monitor(
    DispatchMonitor(),
    CompletionMonitor()
  )
}

def reportOnMonitors(monitor: Monitor[?]): Unit = {
  def reportOnMonitor(monitor: Monitor[?]): Unit = {
    def headline(s: String): Unit = {
      println("---" + s)
    }

    val line = "REPORT FOR: " + monitor.getMonitorName
    val sep = "=" * line.length
    println()
    println(sep)
    println(line)
    println(sep)
    // get all states:
    headline("All states")
    for (s <- monitor.getAllStates) {
      println("  state:")
      println(s"    name      = ${s.getName}")
      println(s"    instid    = ${s.getInstanceId}")
      println(s"    instidstr = ${s.getInstanceIdString}")
      println(s"    trace     = ${s.getTrace}")
    }
    // whether transition triggered in monitor:
    headline(s"This monitor triggered: ${monitor.transitionTriggered.toString}")
    // Get error counts:
    headline("Error counts")
    println(s"  for this monitor        : ${monitor.getErrorCountForThisMonitor}")
    println(s"  for all monitors        : ${monitor.getErrorCount}")
    println(s"  latest for this monitor : ${monitor.getLatestErrorCountForThisMonitor}")
    println(s"  latest for all monitors : ${monitor.getLatestErrorCount}")
    // Get reports:
    headline("Reports for this monitor")
    for (r <- monitor.getReportsForThisMonitor) {
      println("  " + r.toString)
    }
    headline("Reports including for submonitors")
    for (r <- monitor.getReports) {
      println("  " + r.toString)
    }
    // Get latest reports:
    headline("Latest reports for this monitor")
    for (r <- monitor.getLatestReportsForThisMonitor) {
      println("  " + r.toString)
    }
    headline("Latest reports")
    for (r <- monitor.getLatestReports) {
      println("  " + r.toString)
    }
    // get all sub monitors, direct as well as recursively:
    headline("Direct sub monitors")
    for (m <- monitor.getDirectSubMonitors) {
      println("  " + m.getMonitorName)
    }
    headline("Direct abstract monitors")
    for (m <- monitor.getDirectAbstractMonitors) {
      println("  " + m.getMonitorName)
    }
  }

  reportOnMonitor(monitor)
  for (m <- monitor.getAllSubAbsMonitors) {
    reportOnMonitor(m)
  }
}

object Main {
  def main(args: Array[String]): Unit = {
    DautOptions.DEBUG = false
    DautOptions.SHOW_TRANSITIONS = false
    DautOptions.REPORT_OK_TRANSITIONS = false
    DautOptions.REPORT_OK_TRANSITIONS = false
    val monitor = new Monitors
    monitor(DispatchRequest(1, 1))
    monitor(DispatchRequest(1, 1))
    monitor(DispatchReply(1, 1))
    monitor(CommandCompleted(1, 1))
    monitor(DispatchRequest(1, 3))
    monitor(DispatchReply(1, 3))
    monitor.end()
  }
}






