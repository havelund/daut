package daut27_init

import daut.{DautOptions, Monitor}

case class Event(t: Int)

class MyMonitor() extends Monitor[Event] {

  def activeStates: Set[String] = getAllStates.map(x => s"${x}")

  watch {
    case Event(1) => Saw(1)
  }

  always {
    case Event(2) => Saw(2)
  }

  def Init() {
    MyFact(3)
    MyFact(4)
  }

  case class MyFact(t: Int) extends fact {
    watch {
      case Event(`t`) => Saw(t)
    }
  }

  case class Saw(t: Int) extends fact
}
object Main extends App {
  DautOptions.DEBUG = true

  var eventStream = List[Event](
    Event (1),
    Event (2),
    Event (3),
    Event (4)
  )

  var req = new MyMonitor()
  req.Init()
  req.getAllStates foreach println // just the initial state

  var requirements = List[MyMonitor]( req )

  for (event <- eventStream) {
    req.verify(event)
  }
}