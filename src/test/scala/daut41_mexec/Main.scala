package daut41_mexec


import daut.{Monitor}

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

sealed trait ConcreteEvent
case class DispatchRequest(taskId: Int, cmdNum: Int) extends ConcreteEvent
case class DispatchReply(taskId: Int, cmdNum: Int) extends ConcreteEvent
case class CommandComplete(taskId: Int, cmdNum: Int) extends ConcreteEvent

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
              println(s"$taskId $cmdNum")
          }
      }
  }
}

object Main {
  def main(args: Array[String]): Unit = {
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

    val monitor = new ConcreteMonitor
    monitor(trace)
  }
}



