package daut14_firstorder_patterns

import daut._

abstract class E {
  val time: Int
}

case class E1(time: Int) extends E

case class E2(time: Int) extends E

class Response1[E](p1: PartialFunction[E, Boolean], p2: PartialFunction[E, Boolean]) extends Monitor[E] {
  always {
    case e1 if p1.isDefinedAt(e1) && p1(e1) => hot {
      case e2 if p2.isDefinedAt(e2) && p2(e2) => ok
    }
  }
}

class Response2(p1: Int => Boolean, p2: (Int, Int) => Boolean) extends Monitor[E] {
  always {
    case E1(t1) if p1(t1) => hot {
      case E2(t2) if p2(t1, t2) => ok
    }
  }
}

class Response3[E, R1, R2](p1: PartialFunction[E, R1], p2: PartialFunction[E, R2], f: (R1, R2) => Boolean) extends Monitor[E] {
  always {
    case e1 if p1.isDefinedAt(e1) => hot {
      case e2 if p2.isDefinedAt(e2) && f(p1(e1), p2(e2)) => ok
    }
  }
}

object Main {
  def main(args: Array[String]): Unit = {
    val m1 = new Response1[E](
      { case E1(t) => t > 100 },
      { case E2(t) => t < 200 }
    )
    m1.verify(E1(150))
    m1.verify(E2(140))
    m1.end() // no error reported

    val m2 = new Response2(t1 => t1 > 100, (t1, t2) => t2 > t1 && t2 < 200)
    m2.verify(E1(150))
    m2.verify(E2(140))
    m2.end() // error reported but Response2 is too specific to E1 and E2

    val m3 = new Response3[E, Int, Int](
      { case E1(t1) if t1 > 100 => t1 },
      { case E2(t2) if t2 < 200 => t2 },
      (t1, t2) => t2 > t1
    )
    m3.verify(E1(150))
    m3.verify(E2(140))
    m3.end() // error reported and Response3 is generic
  }
}
