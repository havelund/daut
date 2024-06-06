package daut41_mexec


import daut.{Monitor}

enum MEXECmd:
  case START
  case CLEANUP
  case STOP

type Time = Int

// System Under Test

trait SUTEvent
case class Stop(taskId: Int) extends SUTEvent
case class Swap(taskId: Int) extends SUTEvent

class SUT {
  def submit(event: SUTEvent): Unit = {
    println(s"submitting $event to SUT")
  }
}

// Abstract events:

sealed trait AbstractEvent
case class Command(taskId: Int, cmdNum: Int) extends AbstractEvent

// Concrete events:

trait MexecEvent
case class DispatchRequest(taskId: Int, cmdNum: Int, cmdType: MEXECmd) extends MexecEvent
case class DispatchReply(success: Boolean, valStatus: Int, taskId: Int, cmdType: MEXECmd) extends MexecEvent
case class CommandComplete(opCode: Int, cmplStatus: Boolean, cmdLen: Int, dispatchTime: Time,
                           cmdNum: Int, cmdType: MEXECmd, meta1: Int, meta2: Int) extends MexecEvent

// Monitors:

class AbstractMonitor extends Monitor[AbstractEvent] {
  val sut = SUT()

  always {
    case Command(taskId1, cmdNum1) => always {
      case Command(taskId2, cmdNum2) =>
        println(s"two commands: $taskId1 $cmdNum1 -> $taskId2 $cmdNum2")
        if (taskId1 == taskId2 && cmdNum1 == cmdNum2) {
          sut.submit(Stop(taskId1))
        }
        ensure (taskId1 != taskId2 || cmdNum1 != cmdNum2)
    }
  }
}

class ConcreteMonitor extends Monitor[MexecEvent] {
  val abstractMonitor = AbstractMonitor()

  always {
    case DispatchRequest(taskId, cmdNum, MEXECmd.START) =>
      hot {
        case DispatchRequest(`taskId`, _, _) => error
        case DispatchReply(true, valStatus, `taskId`, MEXECmd.START) =>
          hot {
            case CommandComplete(opCode, cmplStatus, cmdLen, dispatchTime, `cmdNum`, MEXECmd.START, meta1, meta2) =>
              abstractMonitor(Command(taskId, cmdNum))
              println(s"$taskId $cmdNum $valStatus $opCode $cmplStatus $cmdLen $dispatchTime $meta1 $meta2")
          }
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
      CommandComplete(42, true, 20, 2032, 4, MEXECmd.START, 100, 200),

      DispatchRequest(1, 2, MEXECmd.START),
      DispatchReply(true, 10, 1, MEXECmd.START),
      CommandComplete(42, false, 20, 2030, 2, MEXECmd.START, 100, 200)
    )

    val monitor = new ConcreteMonitor
    monitor(trace)
  }
}



