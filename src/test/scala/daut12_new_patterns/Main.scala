package daut12_new_patterns

import daut._
import scala.reflect.Selectable.reflectiveSelectable

/**
 * ReceiveWhenOpen: when the radio is open, all sent messages should be
 * received.
 *
 * CloseOpened: once opened, the radio should eventually be closed.
 */

class Response[E](e1: E, e2: E) extends Monitor[E] {
  always {
    case `e1` => hot {
      case `e2` => ok
    }
  }
}

class NewMonitor[E] extends Monitor[E] {
  def between(e1: E, e2: E)(tr: Transitions): state = {
    always {
      case `e1` =>
        unless {
          case `e2` => ok
        }.watch (tr)
    }
  }
}

trait RadioEvent
case object Open extends RadioEvent
case object Close extends RadioEvent
case class Send(msg: String) extends RadioEvent
case class Receive(msg: String) extends RadioEvent

class ReceiveWhenOpen extends NewMonitor[RadioEvent] {
  between(Open,Close) {
    case Send(m) => hot {
      case Receive(`m`) => true
    }
  }
}

class AllMonitors extends Monitor[RadioEvent] {
  monitor(
    new Response(Open,Close),
    new ReceiveWhenOpen
  )
}

object Main {
  def main(args: Array[String]): Unit = {
    val m = new AllMonitors
    m.verify(Send("ignore this message"))
    m.verify(Open)
    m.verify(Send("hello"))
    m.verify(Send("world"))
    m.verify(Send("I just saw a UFO!")) // violating since not received
    m.verify(Receive("hello"))
    m.verify(Close)
    m.verify(Receive("world"))
    m.verify(Send("and ignore this one too"))
    m.end()
    println(s"Number of errors: ${m.getErrorCount}")
  }
}
