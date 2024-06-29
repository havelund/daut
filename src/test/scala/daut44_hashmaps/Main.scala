package daut44_hashmaps

import daut._

/**
  * An A should be followed by one B, then one or more Cs, and then one D.
  * and nothing else, for a particular task id t. This process is only to
  * occur once.
  *
  * This monitor illustrates the use of indexing, relevance filter, and wnext and next states,
  * to model a textbook automaton.
  */

trait Event
case class A(t: Int) extends Event
case class B(t: Int) extends Event
case class C(t: Int) extends Event
case class D(t: Int) extends Event
case class E(t: Int) extends Event

class MissionMonitor extends Monitor[Event] {
  override def keyOf(event: Event): Option[Int] = {
    event match {
      case A(t) => Some(t)
      case B(t) => Some(t)
      case C(t) => Some(t)
      case D(t) => Some(t)
      case E(t) => Some(t)
    }
  }

  override def relevant(event: Event): Boolean = {
    event match {
      case E(_) => false
      case _ => true
    }
  }
}

class ABCDMonitor extends MissionMonitor {
  wnext {
    case A(t) => next { // if an A
      case B(`t`) => next { // then one B
        case C(`t`) => next  { // then one or more Cs, the first here
          case C(`t`) => stay // the rest of the Cs here, note 'stay'
          case D(`t`) => ok // finally a D gets us out of that loop
        } label("waiting for D", t) // labels are just for debugging
      } label("waiting for first C", t)
    } label("waiting for B", t)
  }
}

object Main {
  def main(args: Array[String]): Unit = {
    val m = new ABCDMonitor
    DautOptions.DEBUG = true
    m(A(1))
    m(B(1))
    m(C(1))
    m(C(1))
    m(D(1))
    m.end()
  }
}
