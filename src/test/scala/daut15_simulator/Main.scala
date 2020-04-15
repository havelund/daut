package daut15_simulator

import daut.Monitor

// ----------------------------------------------------------------------
//
// Events
//
abstract class Event {
  val time: Int
}

// step02 events
case class SetDapMode (time: Int, mode: String) extends Event
case class SetDapActive (time: Int, isDapActiveAsBool: Boolean) extends Event
case class SetDapSetPointAsMeter (time: Int, valueAsMeter: Int) extends Event

// step03 events
case class SetCrtFloodWith (time: Int, volumeAsLiters: Int) extends Event
case class SetI18Open (time: Int, valueAsPercentage: Int) extends Event

// step04 events
case class StatusDepth (time: Int, depthAsMeter: Float) extends Event
//case class SetDapMode (time: Int, mode: String) extends Event // see step 02
case class StatusOperationMode (time: Int, mode: String) extends Event

//
// Facts
//

// Klaus: I turned the class into an object as an experiment.
// I also changed the Boolean flags to a set of monitor ids (integers).

object Global {
  private var done : Set[Int] = Set()

  def done(m: Int): Unit = {
    done += m
  }

  def isDone(m: Int): Boolean = {
    done.contains(m)
  }
}

// Klaus: now not paramererized with global.

class Step02_Facts extends Monitor[Event] {

  var dapMode: String = "undefined"
  var isDapActive: Boolean = false
  var dapSetPointAsMeter: Int = -1

  def IsCompletedStep02() = {
    if (
      dapMode != "normal"
        || isDapActive != true
        || dapSetPointAsMeter != 100) {
      println("Step 02 unfinished")
      false
    }
    else {
      println("Step 02 finished")
      Global.done(2) // Klaus: calling method on global object.
      true
    }
  }
}

class Step03_Facts extends Monitor[Event] {

  var crtFloodedVolumeAsLiters: Int = -1
  var i18OpenAsPercentage: Int = -1

  def IsCompletedStep03() = {
    if (
      crtFloodedVolumeAsLiters != 2000
        || i18OpenAsPercentage != 1) {
      println("Step 03 unfinished")
      false
    }
    else {
      println("Step 03 finished")
      Global.done(3)
      true
    }
  }
}

class Step04_Facts extends Monitor[Event] {

  var reachedDepth100Meter: Boolean = false
  var dapMode: String = "undefined"
  var operationMode: String = "undefined"

  def IsCompletedStep04() = {
    if (
      reachedDepth100Meter != true
        || dapMode != "deactivated"
        || operationMode != "manual") {
      println("Step 04 unfinished")
      false
    }
    else {
      println("Step 04 finished")
      Global.done(4)
      true
    }
  }
}

class Step02_Req  extends Step02_Facts {

  Step2(-1)

  def Step2(t0: Int): state = {
    watch {
      case SetDapMode(t1, value) => {
        dapMode = value
        IsCompletedStep02()
        AfterStep2(t1) // Klaus: going directly to not miss events
      }
      case SetDapActive(t1, value) => {
        isDapActive = value
        IsCompletedStep02()
        AfterStep2(t1)
      }
      case SetDapSetPointAsMeter(t1, value) => {
        dapSetPointAsMeter = value
        IsCompletedStep02()
        AfterStep2(t1)
      }
    }
  }

  def AfterStep2(t0: Int): state = { // SETTING values shall stay in range
    watch {
      case _ if Global.isDone(3) => ok // Until(gf.isFinishedStep03)
      case SetDapMode(t1, value) => {
        dapMode = value
        check(IsCompletedStep02(), "values of step 02 changed after step 02 completed")
        AfterStep2(t1) // Klaus: going directly.
      }
      case SetDapActive(t1, value) => {
        isDapActive = value
        check(IsCompletedStep02(), "values of step 02 changed after step 02 completed")
        AfterStep2(t1)
      }
      case SetDapSetPointAsMeter(t1, value) => {
        dapSetPointAsMeter = value
        check(IsCompletedStep02(), "values of step 02 changed after step 02 completed")
        AfterStep2(t1)
      }
    }
  }
}

class Step03_Req extends Step03_Facts {

  Step3(-1)

  def Step3(t0: Int): state = {
    watch {
      // WaitUntil(isFinishedStep02)
      case _ if !Global.isDone(2) => Step3(t0)
      case SetCrtFloodWith(t1, value) => {
        crtFloodedVolumeAsLiters = value
        IsCompletedStep03()
        AfterStep3(t1) // Klaus: going directly.
      }
      case SetI18Open(t1, value) => {
        i18OpenAsPercentage = value
        IsCompletedStep03()
        AfterStep3(t1)
      }
    }
  }

  def AfterStep3(t0: Int): state = { // SETTING values shall stay in range
    watch {
      case _ => ok // TBD
    }
  }
}

class Step04_Req extends Step04_Facts {

  Step4(-1)

  def Step4(t0: Int): state = {
    watch {

      // WaitUntil(isFinishedStep03)
      case _ if !Global.isDone(3) => Step4(t0)

      case StatusDepth(t1, value) => {
        if (value >= 100) reachedDepth100Meter = true
        IsCompletedStep04()
        AfterStep4(t1) // Klaus: going directly
      }
      case SetDapMode(t1, value) => {
        if (!reachedDepth100Meter ) {
          error ("altered DAP mode before 100 m depth reached")
        }

        dapMode = value
        IsCompletedStep04()
        AfterStep4(t1)
      }
      case StatusOperationMode(t1, value) => {
        operationMode = value
        IsCompletedStep04()
        AfterStep4(t1)
      }
    }
  }

  def AfterStep4(t0: Int): state = { // SETTING values shall stay in range
    watch {
      case _ => ok // TBD
    }
  }
}

// Main Monitor

// Klaus: creating one monitpr containing the other monitors.

class AllMonitors extends Monitor[Event] {
  monitor(
    new Step02_Req,
    new Step03_Req,
    new Step04_Req
  )
}

// --------------------------------------------------------------------------------
object Main {

  def main(args: Array[String]) {

    val m = new AllMonitors

    val eventStream = List( // example log
      //
      // Step 02 events
      //
      SetDapMode (1000, "off"), 	// allowed / no error
      SetDapMode (1000, "normal"),
      SetDapActive (2000, false), // allowed / no error
      SetDapActive (2000, true),
      SetDapSetPointAsMeter (3000, 100), // step finished / no error
      //SetDapSetPointAsMeter (3000, 200), // varying step 01 settings - not allowed here
      //
      // Step 03 events
      //
      SetCrtFloodWith (4000, 2000),
      SetCrtFloodWith (4000, 3000),
      //SetDapSetPointAsMeter (3000, 200), // varying step 01 settings - not allowed here
      SetI18Open (5000, 1),
      SetI18Open (5000, 2),
      SetI18Open (5000, 1),
      SetCrtFloodWith (5000, 2000),
      //
      // Step 04 events
      //
      StatusDepth (6000, 70),
      StatusDepth (7000, 80),
      StatusDepth (8000, 90),
      StatusDepth (9000, 100),
      SetDapMode (10*1000, "deactivated"),
      StatusOperationMode (10*1000, "manual"),
      StatusDepth (11*1000, 110),
    )

    for (event <- eventStream) {
      m.verify(event)
    }
  }
}
