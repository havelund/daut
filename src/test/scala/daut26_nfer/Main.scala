package daut26_nfer

import daut._
import scala.reflect.Selectable.reflectiveSelectable

/**
  * Abstraction example from
  * http://www.havelund.com/Publications/nfer-fmsd-2017.pdf
  * page 4.
  */

/**
  * Events.
  */

trait Event {
  val time: Int
}

case class BootStart(time: Int, count: Int) extends Event

case class BootEnd(time: Int) extends Event

case class TurnAntenna(time: Int) extends Event

case class StartRadio(time: Int) extends Event

case class StopRadio(time: Int) extends Event

case class DownLink(time: Int, size: Int) extends Event

/**
  * There should be no downlink during booting.
  *
  * Using no facts.
  */

class DownLinkOk1 extends Monitor[Event] {
  always {
    case BootStart(_, _) => watch {
      case DownLink(_, _) => error
      case BootEnd(_) => ok
    }
  }
}

/**
  * There should be no downlink during booting within 80 seconds
  * from the boot's start.
  *
  * Using no facts.
  */

class DownLinkOk2 extends Monitor[Event] {
  always {
    case BootStart(tb, _) => watch {
      case DownLink(td, _) if td - tb < 80 => error
      case BootEnd(_) => ok
    }
  }
}

/**
  * There should be no downlink during booting.
  *
  * Using a propositional fact.
  */

class DownLinkOk3 extends Monitor[Event] {

  case class Booting() extends state {
    watch {
      case BootEnd(_) => ok
    }
  }

  always {
    case BootStart(t, c) => Booting()
    case DownLink(_, _) if Booting() => error
  }
}

/**
  * There should be no downlink during booting.
  *
  * Using a propositional fact, which itself looks for the downlink error.
  */

class DownLinkOk4 extends Monitor[Event] {

  case class Booting() extends state {
    watch {
      case DownLink(_, _) => error
      case BootEnd(_) => ok
    }
  }

  always {
    case BootStart(t, c) => Booting()
  }
}

/**
  * There should be no downlink during booting within 80 seconds
  * from the boot's start.
  *
  * Using a parameterized fact.
  */

class DownLinkOk5 extends Monitor[Event] {

  case class Booting(t: Int) extends state {
    watch {
      case BootEnd(_) => ok
    }
  }

  always {
    case BootStart(t, c) => Booting(t)
    case DownLink(td, _) if exists { case Booting(tb) => td - tb < 80 } => error
  }
}

/**
  * There should be no downlink during a double boot (two consecutive boots with no more than
  * 5 minutes from start of the first boot to the end of the second boot). a Risk interval is
  * a double boot interval during which a downlink occurs, hence a Risk interval indicates
  * a violation of this property.
  *
  * Not really using facts as much as one could.
  */

class DownLinkOk6 extends Monitor[Event] {

  case class DOWNLINK(time: Int) extends state

  always {
    case DownLink(t, _) => DOWNLINK(t)
    case BootStart(start1, _) => watch {
      case BootEnd(_) => watch {
        case BootStart(_, count2) => watch {
          case BootEnd(end2) if end2 - start1 <= 300 && exists {
            case DOWNLINK(time) if start1 <= time && time <= end2 => true
          } =>
            error
        }
      }
    }
  }
}

/**
  * There should be no downlink during a double boot (two consecutive boots with no more than
  * 5 minutes from start of the first boot to the end of the second boot). a Risk interval is
  * a double boot interval during which a downlink occurs, hemce a Risk interval indicates
  * a violation of this property.
  *
  * Heavy use of facts.
  *
  * The spec in the paper is:
  *
  *   BOOT :− BOOT_S before BOOT_E map { count → BOOT_S.count }
  *
  *   DBOOT :− b1:BOOT before b2:BOOT where b2.end − b1.begin ≤ 300 map { count → b1.count }
  *
  *   RISK :− DOWNLINK during DBOOT map { count → DBOOT.count }
  */

class DownLinkOk7 extends Monitor[Event] {

  case class DOWNLINK(time: Int) extends state {
    watch {
      case _ =>
        map {
          case DBOOT(start, end, count) if start <= time && time <= end => RISK(start, end , count)
        }.orelse {
          stay
        }
    }
   }

  case class BOOT(start: Int, end: Int, count: Int) extends state {
    watch {
      case BootStart(start2, _) => watch {
        case BootEnd(end2) if end2 - start <= 300 => DBOOT(start, end2, count)
      }
    }
  }

  case class DBOOT(start: Int, end: Int, count: Int) extends state

  case class RISK(start: Int, end: Int, count: Int) extends state {
    reportError("downlink during double boot!")
  }

  always {
    case DownLink(time, _) => DOWNLINK(time)
    case BootStart(start, count) => watch {
      case BootEnd(end) => BOOT(start, end, count)
    }
  }

}

object Main {
  def main(args: Array[String]): Unit = {
    DautOptions.DEBUG = true
    val m = new DownLinkOk7

    m.verify(DownLink(10, 430))
    m.verify(BootStart(42, 3))
    m.verify(TurnAntenna(80))
    m.verify(StartRadio(90))
    m.verify(DownLink(100, 420))
    m.verify(BootEnd(160))
    m.verify(StopRadio(205))
    m.verify(BootStart(255, 4))
    m.verify(StartRadio(286))
    m.verify(BootEnd(312))
    m.verify(TurnAntenna(412))

    m.end()
  }
}
