package daut29_abstraction

import daut.{Abstract, DautOptions, Monitor, Translate}

/**
  * Events: these represent behavior of spacecraft. A spacecraft can boot,
  * consisting of a boot start and a boot end. A double boot is
  * a boot followed by a boot.
  *
  * Property 1: A boot start should be followed by a boot end without another
  * boot start in between. Likewise, a boot end should be followed by a boot start,
  * without another boot end in between. The trace should start with a boot start.
  *
  * Property 2: in a double boot there should more than 60 seconds
  * from the start of the first boot to the end of the second boot.
  *
  * Approach: first we abstract from the boot starts and boot ends to the boots.
  * Then we abstract from the boots to the double boots.
  * Then we generate messages
  * Then we verify the messages.
  */

trait BootEvent

case class BootStart(time: Int) extends BootEvent

case class BootEnd(time: Int) extends BootEvent

case class Boot(time1: Int, time2: Int) extends BootEvent

case class DoubleBoot(time1: Int, time2: Int) extends BootEvent

/**
  * Monitor that abstracts boot starts and boot ends to boots.
  */

class BootStartEndToBoot extends Abstract[BootEvent] {
  record(true)

  watch {
    case BootEnd(_) => error("starts with BootEnd")
    case BootStart(_) => ok
  }

  always {
    case BootStart(time1) => watch {
      case BootStart(_) => error("BootEnd expected")
      case BootEnd(time2) =>
        push(Boot(time1, time2))
        watch {
          case BootEnd(_) => error("BootStart expected")
          case BootStart(_) => ok
        }
    }
  }
}

/**
  * Monitor that abstracts boots to double boots.
  */

class BootBootToDoubleBoot extends Abstract[BootEvent] {
  record(true)

  always {
    case Boot(time1, _) => watch {
      case Boot(_, time2) =>
        push(DoubleBoot(time1, time2))
    }
  }
}

/**
  * Monitor that verifies double boots and generates messages.
  */

trait Message
case class Warning(boot: Boot) extends Message
case class Error(dboot: DoubleBoot) extends Message

class GenerateMessages extends Translate[BootEvent, Message] {
  var countDoubleBoots : Int  = 0

  always {
    case b @ Boot(_,_) =>
      push(Warning(b))
    case db @ DoubleBoot(_,_) =>
      countDoubleBoots += 1
      push(Error(db))
  }

  always {
    case DoubleBoot(time1, _) =>
      watch {
        case DoubleBoot(_, time2) =>
          ensure(time2 - time1 > 60)
      }
  }
}

/**
  * Check messages
  */

class CheckMessages extends Monitor[Message] {
  always {
    case Error(db) => error(s"error: $db")
    case Warning(b) => println(s"warning: $b")
  }
}

/**
  * Creating initial trace and applying the processes one by one.
  */

object Main {
  def main(args: Array[String]): Unit = {
    DautOptions.PRINT_ERROR_BANNER = false

    val trace0 : List[BootEvent] = List(
      BootStart(10),
      BootEnd(20),

      BootStart(30),
      BootEnd(40),

      BootStart(50),
      BootEnd(60),

      BootStart(70),
      BootEnd(80)
    )

    val boots = new BootStartEndToBoot
    val doubleBoots = new BootBootToDoubleBoot
    val messages = new GenerateMessages
    val monitor = new CheckMessages

    val bootTrace = boots(trace0).trace
    val dbootTrace = doubleBoots(bootTrace).trace
    val messageTrace = messages(dbootTrace).trace
    monitor(messageTrace)

    println(s"number of double boots: ${messages.countDoubleBoots}")
    println()
    println(dbootTrace.mkString("\n"))
    println()
    println(messageTrace.mkString("\n"))
  }
}
