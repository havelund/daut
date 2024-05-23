package daut40_mexec

import daut.{Abstract, Translate}

enum MEXECmd:
  case START
  case CLEANUP
  case STOP

type Time = Int

// Events in log:
trait MexecEvent
case class DispatchRequest(taskId: Int, cmdNum: Int, cmdType: MEXECmd) extends MexecEvent
case class DispatchReply(success: Boolean, valStatus: Int, taskId: Int, cmdType: MEXECmd) extends MexecEvent
case class CommandComplete(opCode: Int, cmplStatus: Boolean, cmdLen: Int, dispatchTime: Time,
                           cmdNum: Int, cmdType: MEXECmd, meta1: Int, meta2: Int) extends MexecEvent

// Abstract events:
trait AbstractEvent
case class Command(taskId: Int, cmdNum: Int) extends AbstractEvent

class CommandExecution extends Translate[MexecEvent, AbstractEvent] {
  always {
    case DispatchRequest(taskId, cmdNum, MEXECmd.START) =>
      hot {
        case DispatchReply(true, valStatus, `taskId`, MEXECmd.START) =>
          hot {
            case CommandComplete(opCode, cmplStatus, cmdLen, dispatchTime, `cmdNum`, MEXECmd.START, meta1, meta2) =>
              println(s"$taskId $cmdNum $valStatus $opCode $cmplStatus $cmdLen $dispatchTime $meta1 $meta2")
              push(Command(taskId, cmdNum))
          }
      }
  }
}

class CommandExecutions extends Abstract[AbstractEvent] {
  always {
    case Command(taskId1, cmdNum1) => watch {
      case Command(taskId2, cmdNum2) =>
        println(s"two commands: $taskId1 $cmdNum1 -> $taskId2 $cmdNum2")
    }
  }
}

object Main {
  def main(args: Array[String]): Unit = {
    val trace: List[MexecEvent] = List(
      DispatchRequest(1, 2, MEXECmd.START),
      DispatchReply(true, 10, 1, MEXECmd.START),
      CommandComplete(42, false, 20, 2030, 2, MEXECmd.START, 100, 200),
      DispatchRequest(3, 4, MEXECmd.START),
      DispatchReply(true, 10, 3, MEXECmd.START),
      CommandComplete(42, true, 20, 2032, 4, MEXECmd.START, 100, 200)
    )

    val monitor1 = new CommandExecution
    val monitor2 = new CommandExecutions
    monitor1(trace)
    println(monitor1.trace)
    monitor2(monitor1.trace)
  }
}


