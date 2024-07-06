package daut48_log_json

/*
Illustrates how events that trigger monitor transitions can be recorded to permanent
memory as JSON objects in a jsonl file. The example is based on a concrete monitor
that sends events to an abstract monitor.
 */

import org.json4s._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.write

import daut.{Monitor}

// Abstract events:

sealed trait AbstractEvent
case class Command(taskId: Int, cmdNum: Int) extends AbstractEvent

// Concrete events:

sealed trait ConcreteEvent
case class DispatchRequest(taskId: Int, cmdNum: Int) extends ConcreteEvent
case class DispatchReply(taskId: Int, cmdNum: Int) extends ConcreteEvent
case class CommandComplete(taskId: Int, cmdNum: Int) extends ConcreteEvent

// Monitors:

class AbstractMonitor extends Monitor[AbstractEvent] {
  always {
    case Command(taskId1, cmdNum1) => always {
      case Command(taskId2, cmdNum2) =>
        ensure (taskId1 != taskId2 || cmdNum1 != cmdNum2)
    }
  }
}

class ConcreteMonitor extends Monitor[ConcreteEvent] {
  val abstractMonitor = monitorAbstraction(AbstractMonitor())

  always {
    case DispatchRequest(taskId, cmdNum) =>
      hot {
        case DispatchRequest(`taskId`, `cmdNum`) => error
        case DispatchReply(`taskId`, `cmdNum`) =>
          hot {
            case CommandComplete(`taskId`, `cmdNum`) =>
              abstractMonitor(Command(taskId, cmdNum))
              println(s"submitted ${Command(taskId, cmdNum)} to abstract monitor")
          }
      }
  }
}

object Main {
  def main(args: Array[String]): Unit = {
    implicit val formats: Formats = Serialization.formats(NoTypeHints)

    def encoder(obj: Any): Option[String] = {
      val map = obj match {
        case Command(taskId, cmdNum) =>
          Map("kind" -> "Command", "name" -> taskId, "cmdNum" -> cmdNum)
        case DispatchRequest(taskId, cmdNum) =>
          Map("kind" -> "DispatchRequest", "name" -> taskId, "cmdNum" -> cmdNum)
        case DispatchReply(taskId, cmdNum) =>
          Map("kind" -> "DispatchReply", "name" -> taskId, "cmdNum" -> cmdNum)
        case CommandComplete(taskId, cmdNum) =>
          Map("kind" -> "CommandComplete", "name" -> taskId, "cmdNum" -> cmdNum)
        case _ => return None
      }
      Some(write(map))
    }

    val trace: List[ConcreteEvent] = List(
      DispatchRequest(1, 1),
      DispatchReply(1, 1),
      CommandComplete(1, 1),

      DispatchRequest(1, 2),
      DispatchReply(1, 2),
      CommandComplete(1, 2),

      DispatchRequest(1, 2),
      DispatchReply(1, 2),
      CommandComplete(1, 2)
    )

    Monitor.SHOW_TRANSITIONS = true
    Monitor.logTransitionsAsJson("output.jsonl", encoder)

    val monitor = new ConcreteMonitor
    monitor(trace)
  }
}




