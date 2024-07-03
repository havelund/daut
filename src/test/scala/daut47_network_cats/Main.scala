package daut47_network_cats

/**
  * This example illustrates how monitors can be defined as cats fibers,
  * which communicate.
  */

import cats.effect.{IO, IOApp, Ref}
import cats.effect.std.Queue
import cats.syntax.all._
import scala.collection.immutable.Queue as ScalaQueue

// Event Types

sealed trait LockEvent
case class I1(x: Int) extends LockEvent
case class I2(x: Int) extends LockEvent
case class I3(x: Int) extends LockEvent
case class E12(x: Int) extends LockEvent
case class E23(x: Int) extends LockEvent
case class E32(x: Int) extends LockEvent
case class E21(x: Int) extends LockEvent

// Monitors

class M1(m2Queue: Queue[IO, LockEvent]) {
  def process(event: LockEvent): IO[Unit] = event match {
    case I1(x) =>
      IO(println(s"O1: M1 receives I1($x)")) *>
        m2Queue.offer(E12(x))
    case E21(x) =>
      IO(println(s"O1: M1 receives E21($x) at outermost level"))
    case _ => IO.unit
  }
}

class M2(m1Queue: Queue[IO, LockEvent], m3Queue: Queue[IO, LockEvent]) {
  def process(event: LockEvent): IO[Unit] = event match {
    case E12(x) =>
      IO(println(s"O2: M2 receives E12($x)")) *>
        m3Queue.offer(E23(x))
    case E32(x) =>
      IO(println(s"O2: M2 receives E32($x)")) *>
        m1Queue.offer(E21(x))
    case _ => IO.unit
  }
}

class M3(m2Queue: Queue[IO, LockEvent]) {
  def process(event: LockEvent): IO[Unit] = event match {
    case E23(x) =>
      IO(println(s"O3: M3 receives E23($x)")) *>
        m2Queue.offer(E32(x))
    case _ => IO.unit
  }
}

// Main Application

object Main extends IOApp.Simple {

  def processEvents(queue: Queue[IO, LockEvent], monitor: LockEvent => IO[Unit]): IO[Unit] = {
    queue.take.flatMap { event =>
      IO(println(s"Processing event: $event")) *> monitor(event)
    }.foreverM
  }

  def run: IO[Unit] = {
    for {
      // Create queues for communication between monitors
      m1Queue <- Queue.unbounded[IO, LockEvent]
      m2Queue <- Queue.unbounded[IO, LockEvent]
      m3Queue <- Queue.unbounded[IO, LockEvent]

      // Instantiate monitors with references to queues
      m1Monitor = new M1(m2Queue)
      m2Monitor = new M2(m1Queue, m3Queue)
      m3Monitor = new M3(m2Queue)

      // Start processing events in each monitor
      fiber1 <- processEvents(m1Queue, m1Monitor.process).start
      fiber2 <- processEvents(m2Queue, m2Monitor.process).start
      fiber3 <- processEvents(m3Queue, m3Monitor.process).start

      // Send an initial message to start the communication
      _ <- m1Queue.offer(I1(42))

      // Keep the application running to allow fibers to continue processing
      _ <- IO.never

    } yield ()
  }
}


