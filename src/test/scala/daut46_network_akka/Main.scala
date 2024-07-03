package daut46_network_akka

/**
  * This example illustrates how monitors can be defined in actors,
  * which communicate.
  */

import daut.{Monitor}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorSystem

// Reference type needed for circular dependencies

class ActorReference[T] {
  var actorRef: ActorRef[T] = _
}

// Monitor Event Types

enum I1 {
  case A(x: Int)
  case B(x: Int)
}

enum I2 {
  case A(x: Int)
  case B(x: Int)
}

enum I3 {
  case A(x: Int)
  case B(x: Int)
}

enum E12 {
  case A(x: Int)
  case B(x: Int)
}

enum E23 {
  case A(x: Int)
  case B(x: Int)
}

enum E32 {
  case A(x: Int)
  case B(x: Int)
}

enum E21 {
  case A(x: Int)
  case B(x: Int)
}

// Actor messages

type MonitorType1 = I1 | E21
type MonitorType2 = I2 | E12 | E32
type MonitorType3 = I3 | E23

// Needed because Akka actors do not cope well with union types

sealed trait ActorType
case class ActorType1(event: MonitorType1) extends ActorType
case class ActorType2(event: MonitorType2) extends ActorType
case class ActorType3(event: MonitorType3) extends ActorType

// Monitors

class M1(m2: ActorReference[ActorType2]) extends Monitor[MonitorType1] {
  always {
    case I1.A(x) =>
      println("O1: M1 receives I1")
      m2.actorRef ! ActorType2(E12.A(x))
      hot {
        case E21.A(x) =>
          println("O1: M1 receives E21")
          println("O1: round trip done")
      }
    case E21.A(x) =>
      println("O1: M1 receives E21 at outermost level")
  }
}

class M2(m1: ActorReference[ActorType1], m3: ActorReference[ActorType3]) extends Monitor[MonitorType2] {
  always {
    case E12.A(x) =>
      println("O2: M2 receives E12")
      m3.actorRef ! ActorType3(E23.A(x))
    case E32.A(x) =>
      println("O2: M2 receives E32")
      m1.actorRef ! ActorType1(E21.A(x))
  }
}

class M3(m2: ActorReference[ActorType2]) extends Monitor[MonitorType3] {
  always {
    case E23.A(x) =>
      println("O3: M3 receives E23")
      m2.actorRef ! ActorType2(E32.A(x))
  }
}

// Actor classes

object A1 {
  def apply(m2: ActorReference[ActorType2]): Behavior[ActorType1] = Behaviors.setup { context =>
    val monitor = new M1(m2)

    Behaviors.receiveMessage {
      case ActorType1(event) =>
        monitor(event)
        Behaviors.same
    }
  }
}

object A2 {
  def apply(m1: ActorReference[ActorType1], m3: ActorReference[ActorType3]): Behavior[ActorType2] = Behaviors.setup { context =>
    val monitor = new M2(m1, m3)

    Behaviors.receiveMessage {
      case ActorType2(event) =>
        monitor(event)
        Behaviors.same
    }
  }
}

object A3 {
  def apply(m2: ActorReference[ActorType2]): Behavior[ActorType3] = Behaviors.setup { context =>
    val monitor = new M3(m2)

    Behaviors.receiveMessage {
      case ActorType3(event) =>
        monitor(event)
        Behaviors.same
    }
  }
}

object Main {
  def main(args: Array[String]): Unit = {
    val system: ActorSystem[ActorType] = ActorSystem(Behaviors.setup[ActorType] { context =>

      // Declare indirect references
      val m1Reference = new ActorReference[ActorType1]
      val m2Reference = new ActorReference[ActorType2]
      val m3Reference = new ActorReference[ActorType3]

      // Instantiate actors with these references
      val m1: ActorRef[ActorType1] = context.spawn(A1(m2Reference), "A1")
      val m2: ActorRef[ActorType2] = context.spawn(A2(m1Reference, m3Reference), "A2")
      val m3: ActorRef[ActorType3] = context.spawn(A3(m2Reference), "A3")

      // Define the value of the references
      m1Reference.actorRef = m1
      m2Reference.actorRef = m2
      m3Reference.actorRef = m3

      // Send a message to the top level monitor.
      // This will cause a round trip of messages going down and back up.
      m1 ! ActorType1(I1.A(42))

      Behaviors.empty
    }, "PingPongSystem")
  }
}
