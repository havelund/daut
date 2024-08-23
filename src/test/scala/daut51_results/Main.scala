package daut51_results

import daut.{DautOptions, Monitor}

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
    watch {
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

object Main {
  def main(args: Array[String]): Unit = {
    DautOptions.DEBUG = false
    DautOptions.SHOW_TRANSITIONS = false
    DautOptions.REPORT_OK_TRANSITIONS = false
    DautOptions.REPORT_OK_TRANSITIONS = false
    val trace: List[Event] = List(
      DispatchRequest(1, 1),
      DispatchRequest(1, 1),
      DispatchReply(1, 1),
      CommandCompleted(1, 1),
      DispatchRequest(1, 3),
      DispatchReply(1, 3)
    )
    val monitor = new Monitors
    monitor(trace)
  }
}






