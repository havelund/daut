package daut45_network

/**
  * This example illustrates how monitors can be set up to call each other
  * in a circular manner. For this to work we have to play some tricks
  * with call by name parameters (e.g. `m2: => M2`) and `lazy val` definition.
  */

import daut.{DautOptions, Monitor}

// Events:

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

// Monitors:

class M1(m2: => M2) extends Monitor[I1 | E21] {
  always {
    case I1.A(x) =>
      println("O1: M1 receives I1")
      m2(E12.A(x)) // this one call invokes all the evaluation
      hot {
        // note that the E21.A message coming back is not caught here
        // since the hot state has not been spawned yet.
        case E21.A(x) =>
          println("O1: M1 receives E21")
          println("O1: round trip done")
      }
    case E21.A(x) =>
      // the E21.A message coming back is caught here
      println("O1: M1 receives E21 at outermost level")
  }
}

class M2(m1: => M1, m3: => M3) extends Monitor[I2 | E12 | E32] {
  always {
    case E12.A(x) =>
      m3(E23.A(x))
      println("O2: M2 receives E12")
    case E32.A(x) =>
      m1(E21.A(x))
      println("O2: M2 receives E21")
  }
}

class M3(m2: => M2) extends Monitor[I3 | E23] {
  always {
    case E23.A(x) =>
      m2(E32.A(x))
      println("O3: M3 receives E23")
  }
}

object Main {
  def main(args: Array[String]): Unit = {
    lazy val m1: M1 = M1(m2)
    lazy val m2: M2 = M2(m1, m3)
    lazy val m3: M3 = M3(m2)
    m1(I1.A(42))
    m1.end()
    m2.end()
    m3.end()
  }
}



