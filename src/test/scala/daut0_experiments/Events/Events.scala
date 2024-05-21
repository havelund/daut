package daut0_experiments.Events

abstract class Event {
  val t: Int
}

case class Time(t: Int) extends Event
case class Value[T](t: Int, name: String, x: Option[T]) extends Event
