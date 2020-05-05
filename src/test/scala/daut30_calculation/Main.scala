package daut30_calculation

import daut.Monitor

/**
  * Illustrating calculation.
  */

trait Event

case class Login(user: String) extends Event

case class Logout(user: String) extends Event

class HowOften extends Monitor[Event] {
  var login: Int = 0
  var logout: Int = 0

  always {
    case Login(u) =>
      login += 1
      watch {
        case Logout(`u`) =>
          logout += 1
      }
  }

  def fraction: Float = logout.toFloat / login
}

object Main {
  def main(args: Array[String]): Unit = {
    val m = new HowOften
    val events = List(
      Login("john"),
      Login("mike"),
      Logout("john"),
      Login("ann"),
      Login("sofia"),
      Logout("ann")
    )

    m.verify(events)
    println(s"fraction that logged out: ${m.fraction}")
  }
}
