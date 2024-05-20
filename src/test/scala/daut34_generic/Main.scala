package daut34_generic

import daut._

import scala.reflect.ClassTag

/**
  * Experimenting with rule notation.
  */

trait Event {
  val time: Int
}

case class A(time: Int) extends Event

case class B(time: Int) extends Event

case class C(time: Int) extends Event

class Generic[T <: Event](implicit tag: ClassTag[T]) extends Monitor[Event] {
  always {
    case A(t) => hot {
      case e: T if e.time == t =>
        println(s"great! we saw response at ${e.time}")
    }
  }
}

class Specific(e: Int => Event) extends Monitor[Event] {
  always {
    case A(t) =>
      val expected = e(t)
      hot {
        case `expected` =>
          println(s"great! we saw response at ${t}")
      }
  }
}

object Main {
  val traceAB = List(A(10), B(10))
  val traceAC = List(A(10), C(10))

  def main(args: Array[String]): Unit = {
    DautOptions.DEBUG = true
    val gB = new Generic[B]
    val gC = new Generic[C]

    val sB = new Specific(t => B(t))
    val sC = new Specific(t => C(t))

    println("===== gB traceAB : ok =====")
    gB.verify(traceAB)
    println("===== gB traceAC : error =====")
    gB.verify(traceAC)

    println("===== gC traceAB : error =====")
    gC.verify(traceAB)
    println("===== gC traceAC : ok =====")
    gC.verify(traceAC)

    println("===== sB traceAB : ok =====")
    sB.verify(traceAB)
    println("===== SB traceAC : error =====")
    sB.verify(traceAC)

    println("===== sC traceAB : error =====")
    sC.verify(traceAB)
    println("===== SC traceAC : ok =====")
    sC.verify(traceAC)
  }
}

