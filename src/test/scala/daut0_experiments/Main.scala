
package daut0_experiments

// Note 01 (original log):
// The original log file is a table. Each row is a complete log of all relevant data, log entries are at 1 Hz.
//
// Note 02 (event log):
// The original log is translated into an event log which is the input for the RV.
// For every column in the original log, for every change in that column there is an event entry at corresponding time in the event log.
// For every row in the original log there is at least a time event in the event log,
// i.e. there is at least one event per second (time event) in the event log.

import daut0_experiments.Events._ // Event and sub classes
import daut0_experiments.EventStream._ // EventStream
import daut0_experiments.Requirements._ // Step_02, 03, ...

import Steps._

// --------------------------------------------------------------------------------
//
// Main
//
object Main {

  def main(args: Array[String]): Unit = {

    println(s"---------- starting verification session")

    //
    // Event stream
    //
    val eventStream = EventStream.instance
    //
    // Requirements
    //
    var requirements = Requirements.instance
    var hasBeenDone: List[Boolean] = List.fill(requirements.length)(false) // helper for event index update
    //
    // Verification loop
    //
    var i: Int = 0 // eventIndex
    var ready: Boolean = false
    while (!ready) {
      //
      // Next event
      //
      var event = eventStream(i)
      println()
      println(s"---------- event ${i}: ${event}")
      //
      // Verify requirements
      //
      requirements.map(x => x.verify(event)) // <---------------------VERIFICATION - all reqs. are checked against events
      requirements.map(x => log(x))
      //
      // Update event index
      //
      val (i_new, ready_new, hasBeenDone_new) = updateEventIndex(i, eventStream, hasBeenDone, requirements)
      i = i_new
      ready = ready_new
      hasBeenDone = hasBeenDone_new
    }
    //
    // Report
    //
    println()
    println("---------- report ")
    requirements.map(x => {
      if (
        x.activeStateNames.contains("Initial")
          || x.activeStateNames.contains("Failed")) {
        log(x.stepName, s"FAILED - ${x.activeStateNames}", 15)
      }
      else {
        log(x.stepName, "PASSED", 15)
      }
    }
    )
  }

  // --------------------------------------------------------------------------------
  //
  // Updating event index
  //
  def updateEventIndex(eventIndex: Int, eventStream: List[Event], hasBeenDone: List[Boolean], requirements: List[Step]): (Int, Boolean, List[Boolean]) = {

    var newEventIndex: Int = eventIndex
    var newHasBeenDone: List[Boolean] = hasBeenDone

    var didSomeStepSwitchedToDone = false
    for (j <- 0.until(requirements.length) if !didSomeStepSwitchedToDone) {
      var s = requirements(j)
      if (s.activeStateNames.contains("Done") && !newHasBeenDone(j)) { // Klaus: changed from state to activeStateNames, also other places
        newEventIndex = revertEventIndexByNTimeUnits(newEventIndex, eventStream, s.dtStable)
        newHasBeenDone = newHasBeenDone.updated(j, true)
        didSomeStepSwitchedToDone = true
      }
    }

    if (!didSomeStepSwitchedToDone) newEventIndex += 1
    //
    // Check event index
    //
    var isAnalysisFinished = newEventIndex == eventStream.length
    var isEventIndexCorrupted = newEventIndex > eventStream.length || newEventIndex < 0
    if (isAnalysisFinished) println(s"FINISHED - verification finished")
    if (isEventIndexCorrupted) println(s"ERROR - verification failed: corrupted event index ${newEventIndex}")

    var ready = isAnalysisFinished || isEventIndexCorrupted

    (newEventIndex, ready, newHasBeenDone)
  }

  // --------------------------------------------------------------------------------
  //
  // Reverting event index
  //
  def revertEventIndexByNTimeUnits(eventIndex: Int, eventStream: List[Event], nTimeUnits: Int): Int = {

    println()
    println(s"-------------------- reverting event stream by ${nTimeUnits} time units --------------------") // note: n time units may include >n events

    var finished: Boolean = false
    var timeEventCounter: Int = 0
    var revertedEventIndex = eventIndex
    while (!finished) {

      eventStream(revertedEventIndex) match {
        case Time(_) => timeEventCounter += 1
        case _ => {}
      }

      if (timeEventCounter == nTimeUnits + 1) finished = true // not counting time event of current time unit
      else revertedEventIndex -= 1
    }

    revertedEventIndex
  }

  // --------------------------------------------------------------------------------
  //
  // Logging step
  //
  def log(s: Step): Unit = {
    // Klaus: changed from state to activeStateNames
    log(s"${s.stepName} :", s"${s.activeStateNames.mkString(",")}", 15)
  }

  // --------------------------------------------------------------------------------
  //
  // Logging strings
  //
  def log(w1: String, w2: String, indent2: Int): Unit = {

    println(w1 + " " * (indent2 - w1.length) + w2)
  }
}

