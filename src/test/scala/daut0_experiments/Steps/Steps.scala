package daut0_experiments.Steps

import daut0_experiments.Events._

import daut._
import daut.Monitor

// ---------------------------------------------------------------------------------------------------------------------------------------------

// =====
// Step:
// =====

abstract class Step(var stepName: String, var dtStable: Int, timeout: Int) extends Monitor[Event] {

  val isTimedout = (t_now: Int, t: Int) => t_now - t >= timeout

  val isStable = (t_now: Int, t: Int) => t_now - t >= dtStable

  def Init(): Unit = {}

  def isInOneOfStates(names: String*): Boolean = {
    exists { case fact => {
      names.contains(fact.getClass.getSimpleName)
    }
    }
  }

  // Falko: this is used in main for logging
  // comment: implementation is terrible
  // justification: couldn't figure out how to get all facts
  // remark: it would be better to have the state including the parameters
  // also it should be several states now, since Active in general can be present several times - in the 02 and 03 classes below
  // Klaus: not needed anymore
  //  def state(): String = {
  //    if (isInOneOfStates("Initial")) "Initial"
  //    else if (isInOneOfStates("Active")) "Active"
  //    else if (isInOneOfStates("Done")) "Done"
  //    else if (isInOneOfStates("Failed")) "Failed"
  //    else if (isInOneOfStates("Success")) "Success"
  //    else "unknown"
  //  }

  //Klaus: try this instead, I provided a getAllStates function which can be called on a monitor.
  //It returns all states with arguments, as case objects. See example in Main file of how
  //it otherwise can be used.
  def activeStateNames: Set[String] = getAllStates.map(_.getClass.getSimpleName)

  def isSuccessful(untilState: Option[String], untilStep: Option[Step]): Boolean = {
    (untilState, untilStep) match {
      case (None, None) => true
      case (Some(state), Some(step)) =>
        state == "Initial" ||
          (state == "Active" && step.isInOneOfStates("Active", "Done", "Failed", "Success")) ||
          (state == "Done") && step.isInOneOfStates("Done", "Failed", "Success") ||
          (state == "Success") && step.isInOneOfStates("Success") ||
          (state == "Failed") && step.isInOneOfStates("Failed")
      case (_, _) =>
        assert(false).asInstanceOf[Boolean]
    }
  }
}

// ---------------------------------------------------------------------------------------------------------------------------------------------

// ========
// Observe:
// ========

// Semantics:
// All values x shall be in range and stable within n time steps and if the values
// x are in range and stable, then they shall stay in range until a dedicated event
// or step.
//

abstract class Observe(stepName: String, dtStable: Int, timeout: Int) extends Step(stepName, dtStable, timeout) {

  // Active / "All values x shall be in range and stable within n time steps"

  case class Active[S](
                        t_start: Int,
                        t: Int,
                        x: Option[S],
                        isInRange: (Option[S] => Boolean),
                        untilStep: Option[Step],
                        untilState: Option[String],
                        observedEventName: String) extends fact {

    watch {
      case Value(t_NEW, eventName, x_NEW: Option[S]) if eventName == observedEventName => {
        if (!isInRange(x) && isInRange(x_NEW)) Active(t_start, t_NEW, x_NEW, isInRange, untilStep, untilState, observedEventName)
        else Active(t_start, t, x_NEW, isInRange, untilStep, untilState, observedEventName)
      }
      case Time(t_NEW) => {
        if (isTimedout(t_NEW, t_start)) Failed(t_NEW, s"timeout at t=${t_NEW}")
        else if (
          (isInRange(x))
            && isStable(t_NEW, t)) Done[S](t_NEW, x, isInRange, untilStep, untilState, observedEventName)
        else Active(t_start, t, x, isInRange, untilStep, untilState, observedEventName) // ignore
      }
    }
  }

  // Done / "All values x shall stay in range until a dedicated event or step"

  case class Done[S](
                      t: Int, x: Option[S],
                      isInRange: (Option[S] => Boolean),
                      untilStep: Option[Step],
                      untilState: Option[String],
                      observedEventName: String) extends fact {

    watch {
      case Value(t_NEW, eventName, x_NEW: Option[S]) if (eventName == observedEventName) => {
        if (isSuccessful(untilState, untilStep))
          Success(t_NEW)
        else if (t_NEW < t) {
          Done(t, x, isInRange, untilStep, untilState, observedEventName)
        } else if (!isInRange(x_NEW)) {
          Failed(t_NEW, s"altered value at t=${t_NEW}")
        } else {
          Done(t_NEW, x_NEW, isInRange, untilStep, untilState, observedEventName)
        }
      }
    }
  }

  case class Failed(t: Int, msg: String) extends fact {

    watch { case _ => Failed(t, msg) } // stay passive
  }

  case class Success(t: Int) extends fact {

    watch { case _ => Success(t) } // stay passive
  }

}

// ---------------------------------------------------------------------------------------------------------------------------------------------

// ===========
// Observe_01:
// ===========

// Semantics: (see above Observe)

class Observe_01[T1](
                      observedEventName: String,
                      isInRange01: (Option[T1] => Boolean),
                      var untilStep01: Option[Step],
                      var untilState01: Option[String],
                      var conditionStep: Option[Step],
                      stepName: String = "_",
                      dtStable: Int = 3,
                      timeout: Int = 10) extends Observe(stepName, dtStable, timeout) {

  override def Init(): Unit = {
    Initial()
  }

  case class Initial() extends fact {

    watch {
      case Time(t_NEW) if (conditionStep == None || conditionStep.get.isInOneOfStates("Done")) =>
        Active[T1](t_NEW, t_NEW, None, isInRange01, untilStep01, untilState01, observedEventName)
    }
  }

}

// ---------------------------------------------------------------------------------------------------------------------------------------------

// ===========
// Observe_02:
// ===========

// Semantics: (see above Observe)

class Observe_02[T1, T2](
                          observedEventName01: String,
                          isInRange01: (Option[T1] => Boolean),
                          var untilStep01: Option[Step],
                          var untilState01: Option[String],
                          observedEventName02: String,
                          isInRange02: (Option[T2] => Boolean),
                          var untilStep02: Option[Step],
                          var untilState02: Option[String],
                          var conditionStep: Option[Step],
                          stepName: String = "_",
                          dtStable: Int = 3,
                          timeout: Int = 10) extends Observe(stepName, dtStable, timeout) {

  override def Init(): Unit = {
    Initial()
  }

  case class Initial() extends fact {

    watch {
      case Time(t_NEW) if (conditionStep == None || conditionStep.get.isInOneOfStates("Done")) =>
        Active[T1](t_NEW, t_NEW, None, isInRange01, untilStep01, untilState01, observedEventName01)
        Active[T2](t_NEW, t_NEW, None, isInRange02, untilStep02, untilState02, observedEventName02)
    }
  }

}

// ---------------------------------------------------------------------------------------------------------------------------------------------

// ===========
// Observe_03:
// ===========

// Semantics: (see above Observe)

class Observe_03[T1, T2, T3](
                              observedEventName01: String,
                              isInRange01: (Option[T1] => Boolean),
                              var untilStep01: Option[Step],
                              var untilState01: Option[String],
                              observedEventName02: String,
                              isInRange02: (Option[T2] => Boolean),
                              var untilStep02: Option[Step],
                              var untilState02: Option[String],
                              observedEventName03: String,
                              isInRange03: (Option[T3] => Boolean),
                              var untilStep03: Option[Step],
                              var untilState03: Option[String],
                              var conditionStep: Option[Step],
                              stepName: String = "_",
                              dtStable: Int = 3,
                              timeout: Int = 10)
  extends Observe(stepName, dtStable, timeout) {

  override def Init(): Unit = {
    Initial()
  }

  case class Initial() extends fact {

    watch {
      case Time(t_NEW) if (conditionStep == None || conditionStep.get.isInOneOfStates("Done")) =>
        Active[T1](t_NEW, t_NEW, None, isInRange01, untilStep01, untilState01, observedEventName01)
        Active[T2](t_NEW, t_NEW, None, isInRange02, untilStep02, untilState02, observedEventName02)
        Active[T3](t_NEW, t_NEW, None, isInRange03, untilStep03, untilState03, observedEventName03)
    }
  }

}
