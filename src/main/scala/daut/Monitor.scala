package daut

import daut.Monitor.transitionTriggeredInSomeMonitor

import scala.collection.mutable.Map as MutMap
import scala.collection.immutable.ListSet
import scala.language.{implicitConversions, reflectiveCalls}
import java.io.{BufferedWriter, FileWriter, PrintWriter}
import scala.sys.exit
import scala.reflect.Selectable.reflectiveSelectable
import io.circe.syntax._
import io.circe.{Encoder, Json}
import io.circe.parser._
import io.circe.generic.semiauto._
import scala.collection.immutable.ListMap
import scala.compiletime.uninitialized

/**
  * Daut options to be set by the user.
  */

object DautOptions {
  /**
    * When true debugging information is printed as monitors execute. Can be set by user.
    * The default value is false.
    */

  var DEBUG: Boolean = false

  /**
    * When false only events that trigger transitions in a monitor
    * or or its sub monitors are shown in `DEBUG=true` mode.
    */

  var DEBUG_ALL_EVENTS: Boolean = false

  /**
    * When true traces will be printed as part of states in memory
    * in debugging mode (when DEBUG == true).
    */

  var DEBUG_TRACES: Boolean = true

  /**
    * Option, which when set to true will cause events to be printed that trigger transitions in any monitor.
    * Default value is false.
    */

  var SHOW_TRANSITIONS: Boolean = false

  /**
    * The name of the json file into which the result of a monitoring is written.
    */

  var RESULT_FILE: String = "daut-results.json"

  /**
    * When true will cause a big ERROR banner to be printed
    * on standard out upon detection of a specification violation, making it easier
    * to quickly see that an error occurred amongst other large output.
    * The default value is false.
    */

  var PRINT_ERROR_BANNER: Boolean = false

  /**
    * When true all messages will be printed again when `end()` is called.
    * The default value is true.
    */

  var PRINT_ERRORS_AT_END: Boolean = true

  /**
    * When true, reaching `ok` states will be recorded, indicating
    * monitors that succeed.
    */

  var REPORT_OK_TRANSITIONS: Boolean = false
}

/**
  * Utilities.
  */

object Util {
  /**
    * Prints a message on standard out if the `DEBUG` flag is set.
    * Used for debugging purposes.
    *
    * @param msg the message to be printed.
    */

  def debug(msg: => String): Unit = {
    if (DautOptions.DEBUG) println(s"$msg")
  }

  /**
    * Method for timing the execution of a block of code.
    *
    * @param text  this text is printed as part of the timing information.
    * @param block the code to be executed.
    * @tparam R the result type of the block to be executed.
    * @return the result of execution the block.
    */

  def time[R](text: String)(block: => R): R = {
    val t1 = System.currentTimeMillis()
    val result = block
    val t2 = System.currentTimeMillis()
    val ms = (t2 - t1).toFloat
    val sec = ms / 1000
    println(s"\n--- Elapsed $text time: " + sec + "s" + "\n")
    result
  }

  def assertWellFormed(cond: Boolean, msg: String): Unit = {
    if (!cond) {
      println(s"*** Monitor is not wellformed: $msg")
      exit(0)
    }
  }

  /**
    * Stops execution of Daut prematurely.
    */

  def stopExecution(msg: String = ""): Unit = {
    println(s"*** Daut stops prematurely $msg")
    exit(0)
  }
}

import daut.Util._

/**
  * If the `STOP_ON_ERROR` flag is set to true, a `MonitorError` exception is thrown
  * if a monitor's specification is violated by the observed event stream.
  */

case class MonitorError() extends RuntimeException

/**
  * Represents the errors caught by a monitor for printing at the end of an
  * analysis when `end()` is called on the top monitor. It contains results
  * of sub monitors, as well as abstract monitors referred to.
  *
  * @param monitorName                   : the name of the monitor.
  * @param errorCount                    : the number of errors detected, not including sub monitor errors.
  * @param errorCountSum                 : the number of errors detected, including sub monitor errors.
  * @param errorStatusOfSubMonitors      : the list of error status for sub monitors.
  * @param errorStatusOfAbstractMonitors : the list of error statys for abstract monitors.
  */

case class ErrorStatus(
                        monitorName: String,
                        errorCount: Int,
                        errorCountSum: Int,
                        errorStatusOfSubMonitors: List[ErrorStatus],
                        errorStatusOfAbstractMonitors: List[ErrorStatus]) {
  override def toString: String = {
    toStringIndented(0)
  }

  /**
    * toString method taking indentation into account.
    *
    * @param indent : the number of indentations to make.
    * @return the string representation of the object.
    */

  def toStringIndented(indent: Int): String = {
    var result = ""
    val tab = "  "
    val space = tab * indent
    result += s"$space$monitorName : $errorCount"
    if (errorStatusOfSubMonitors.nonEmpty) {
      result += s", sum = $errorCountSum"
    }
    for (errorStatus <- errorStatusOfSubMonitors) {
      result += "\n"
      result += errorStatus.toStringIndented(indent + 2)
    }
    for (errorStatus <- errorStatusOfAbstractMonitors) {
      result += "\n"
      result += (tab * (indent + 2)) + "abstract\n"
      result += errorStatus.toStringIndented(indent + 2)
    }
    result
  }
}

/**
  * Reports of various errors and successes are case classes extending this class.
  */

sealed trait Report {
  val monitor: String
}

/**
  * Represents a transition. Recorded when `DautOptions.SHOW_TRANSITIONS` is true.
  *
  * @param monitor  the monitor it occurred in.
  * @param state    the state it occurred in.
  * @param eventNr  the number of the event in the input stream.
  * @param event    the event causing the transition.
  * @param instance instance as pointed out by user.
  */

case class TransitionReport(
                             monitor: String,
                             state: String,
                             eventNr: Long,
                             event: String,
                             instance: String
                           ) extends Report {
  override def toString: String = {
    var message = s"Daut $monitor transition: $state -[Evt $eventNr: $event]->\n"
    message
  }
}

/**
  * Represents a transition error, which a transition leading to an `error` state.
  *
  * @param monitor  the monitor it occurred in.
  * @param state    the state it occurred in.
  * @param eventNr  the number of the event in the input stream.
  * @param event    the event causing the error.
  * @param instance instance as pointed out by user.
  * @param trace    the error trace.
  * @param msg      an optional user message.
  */

case class TransitionErrorReport(
                                  monitor: String,
                                  state: String,
                                  eventNr: Long,
                                  event: String,
                                  instance: String,
                                  trace: List[TraceEvent],
                                  msg: Option[String]
                                ) extends Report {
  override def toString: String = {
    val headline = s"*** DAUT TRANSITION ERROR in state $monitor.$state"
    val separator = "-" * headline.length
    var message = ""
    message += s"$separator\n"
    message += s"$headline\n"
    message += s"$separator\n"
    msg match {
      case None =>
      case Some(txt) =>
        message += s"$txt\n"
    }
    message += s"Event number $eventNr: $event\n"
    message += s"${formatTrace(trace)}\n"
    message += s"$separator\n"
    message
  }
}

/**
  * Represents a transition ok, which a transition leading to an `ok` state.
  *
  * @param monitor  the monitor it occurred in.
  * @param state    the state it occurred in.
  * @param eventNr  the number of the event in the input stream.
  * @param event    the event causing leading to ok.
  * @param instance instance as pointed out by user.
  * @param trace    the trace.
  * @param msg      an optional user message.
  */

case class TransitionOkReport(
                               monitor: String,
                               state: String,
                               eventNr: Long,
                               event: String,
                               instance: String,
                               trace: List[TraceEvent],
                               msg: Option[String]
                             ) extends Report {
  override def toString: String = {
    val headline = s"!!! DAUT TRANSITION OK in state $monitor.$state"
    val separator = "+" * headline.length
    var message = ""
    message += s"$separator\n"
    message += s"$headline\n"
    message += s"$separator\n"
    msg match {
      case None =>
      case Some(txt) =>
        message += s"$txt\n"
    }
    message += s"Event number $eventNr: $event\n"
    message += s"${formatTrace(trace)}\n"
    message += s"$separator\n"
    message
  }
}

/**
  * Represents an error occurring due to a call of the `end()` method in a `hot`
  * or `next` state.
  *
  * @param monitor  the monitor it occurred in.
  * @param state    the state it occurred in.
  * @param instance instance as pointed out by user.
  * @param trace    the error trace.
  */

case class OmissionErrorReport(
                                monitor: String,
                                state: String,
                                instance: String,
                                trace: List[TraceEvent]
                              ) extends Report {
  override def toString: String = {
    val headline = s"*** DAUT OMISSION ERROR in state $monitor.$state"
    val separator = "-" * headline.length
    var message = ""
    message += s"$separator\n"
    message += s"$headline\n"
    message += s"$separator\n"
    message += s"${formatTrace(trace)}\n"
    message += s"$separator\n"
    message
  }
}

/**
  * Represents an error reported by a user explicitly.
  *
  * @param monitor  the monitor it occurred in.
  * @param state    the state it occurred in.
  * @param eventNr  the number of the event in the input stream.
  * @param event    the event causing the error.
  * @param instance instance as pointed out by user.
  * @param trace    the error trace.
  * @param msg      a user message.
  */

case class UserErrorReport(
                            monitor: String,
                            state: String,
                            eventNr: Long,
                            event: String,
                            instance: String,
                            trace: List[TraceEvent],
                            msg: String
                          ) extends Report {
  override def toString: String = {
    val headline = s"*** DAUT USER ERROR REPORT in state $monitor.$state"
    val separator = "-" * headline.length
    var message = ""
    message += s"$separator\n"
    message += s"$headline\n"
    message += s"$separator\n"
    message += s"$msg\n"
    message += s"Event number $eventNr: $event\n"
    message += s"${formatTrace(trace)}\n"
    message += s"$separator\n"
    message
  }
}

/**
  * Represents a user information report. This is not an error.
  *
  * @param monitor  the monitor it was reported in.
  * @param state    the state it was reported in.
  * @param eventNr  the number of the event in the input stream.
  * @param event    the event causing the report.
  * @param instance instance as pointed out by user.
  * @param trace    the trace.
  * @param msg      a user message.
  */

case class UserReport(
                       monitor: String,
                       state: String,
                       eventNr: Long,
                       event: String,
                       instance: String,
                       trace: List[TraceEvent],
                       msg: String
                     ) extends Report {
  override def toString: String = {
    val headline = s"!!! DAUT USER REPORT in state $monitor.$state"
    val separator = "." * headline.length
    var message = ""
    message += s"$separator\n"
    message += s"$headline\n"
    message += s"$separator\n"
    message += s"$msg\n"
    message += s"Event number $eventNr: $event\n"
    message += s"${formatTrace(trace)}\n"
    message += s"$separator\n"
    message
  }
}

/**
  * Used to record an event that causes a monitor state to trigger a transition.
  * Each state contains a list of trace events that lead to that state.
  * Is printed out when an error, or ok if `DautOptions.RECORD_OK == true`, is detected,
  * or in debug mode: `DautOptions.DEBUG == true`.
  *
  * @param state   the source state of the transition.
  * @param eventNr number of event.
  * @param event   the event.
  */

case class TraceEvent(state: String, eventNr: Long, event: String) {
  override def toString: String = s"State: $state, EventNr: $eventNr, Event: $event"
}

/**
  * Formats a trace associated with a state.
  *
  * @param trace  the trace to print.
  * @param indent number of spaces to indent (depends on context).
  * @return the formatted trace.
  */

def formatTrace(trace: List[TraceEvent], indent: Int = 0): String = {
  val indentation = " " * indent
  var message = indentation + "Trace:"
  for (event <- trace) {
    message += "\n" + indentation + s"  $event"
  }
  message
}

/**
  * Any monitor must sub-class this class. It provides all the DSL constructs for writing
  * a monitor.
  *
  * @tparam E the type of events submitted to the monitor.
  */

class Monitor[E] {
  thisMonitor =>

  /**
    * This class is used to identify the instance id of a state. It is used by a user
    * to point out an instance identifier explicitly e.g. in state generating expressions
    * such as `hot(ID(x), ...) { ... }`.
    *
    * @param id
    */

  case class ID(id: Any)

  /**
    * Stores the current dynamic context, including the currently processed event,
    * the current state that this event is being submitted to, and the current
    * trace including the last event. This is used by
    * the methods `report` and `reportError` which the user can call.
    */

  class Context {
    var currentEvent: E = uninitialized
    var currentState: state = uninitialized
    var currentTrace: List[TraceEvent] = uninitialized
  }

  /**
    * This class represents all the active states in a monitor (excluding those of its sub-monitors).
    */

  private class States {
    /**
      * A monitor is at any point in time in zero, one, or more states, conceptually represented as a set of states. All
      * states have to lead to success, hence there is an implicitly understood conjunction between
      * the states in the set. The implementation, however, consists of a main set of states,
      * and a map from indexes to sets of states, to facilitate
      * optimization. A key  is an optional index, where `None` represents the initial default set of states.
      */

    private var mainStates: Set[state] = Set()
    private val indexedStates: MutMap[Any, Set[state]] = MutMap()

    /**
      * Returns all states contained in the main set and in the indexed sets.
      *
      * @return all states.
      */

    def getAllStates: Set[state] = mainStates.union(indexedStates.values.flatten.toSet)

    /**
      * Returns the main non-indexed set of states.
      *
      * @return the main non-indexed set of states.
      */

    def getMainStates: Set[state] = mainStates

    /**
      * Returns the set of indexes of indexed state sets.
      *
      * @return the set of indexes of indexed state sets.
      */

    def getIndexes: Set[Any] = indexedStates.keySet.toSet

    /**
      * Get the set indexed by `index`.
      *
      * @param index the index used to look up the state set. It is assumed that there is an entry for
      *              this index.
      * @return the state set denoted by the index.
      */

    def getIndexedSet(index: Any): Set[state] = indexedStates(index)

    /**
      * Updates the main set of states to contain the state `s`.
      *
      * @param s the state to add to the main set of states.
      */

    def initial(s: state): Unit = {
      mainStates += s
    }

    /**
      * Applies an event to the states of the monitor. If the key of the event is `None`
      * it is applied to the main set of states as well as to each indexed set. Otherwise
      * the event is applied to the indexed set. If a such does not exist, the main set is used
      * as a starting point, and stored in that index when evaluated.
      *
      * @param event the event to evaluate.
      */

    def applyEvent(event: E): Unit = {
      var transitionTriggered: Boolean = false
      val key = keyOf(event)
      key match {
        case None =>
          applyEventToStateSet(event)(mainStates) match {
            case None =>
            case Some(newStates) =>
              mainStates = newStates
              transitionTriggered = true
          }
          for ((index, ss) <- indexedStates) {
            applyEventToStateSet(event)(ss) match {
              case None =>
              case Some(newStates) =>
                indexedStates += (index -> newStates)
                transitionTriggered = true
            }
          }
        case Some(index) =>
          val ss = indexedStates.getOrElse(index, mainStates)
          applyEventToStateSet(event)(ss) match {
            case None =>
            case Some(newStates) =>
              indexedStates += (index -> newStates)
              transitionTriggered = true
          }
      }
      if (transitionTriggered) {
        transitionTriggeredDueToLatestEvent = true
        Monitor.transitionTriggeredInSomeMonitor = true
      }
    }

    /**
      * Applies an event to a set of states and returns the updated set.
      * This method performs the main verification task.
      *
      * @param event  the event.
      * @param states the set of states to apply the event to.
      * @return `Some` of set new states or `None` if no transitions triggered.
      */

    private def applyEventToStateSet(event: E)(states: Set[state]): Option[Set[state]] = {
      var transitionTriggered: Boolean = false
      var statesToRemove: Set[state] = emptyStateSet
      var statesToAdd: Set[state] = emptyStateSet
      var newStates = states
      for (sourceState <- newStates) {
        sourceState(event) match {
          case None =>
          case Some(targetStates) =>
            if ((thisMonitor.SHOW_TRANSITIONS || DautOptions.SHOW_TRANSITIONS) && !(sourceState.isSilenced || thisMonitor.isSilenced)) {
              reportTransition(sourceState, event)
            }
            transitionTriggered = true
            statesToRemove += sourceState
            for (targetState <- targetStates) {
              targetState match {
                case `error` | `ok` =>
                case `stay` => statesToAdd += sourceState
                case _ => statesToAdd += targetState
              }
            }
        }
      }
      if (transitionTriggered) {
        newStates --= statesToRemove
        newStates ++= statesToAdd
        Some(newStates)
      } else
        None
    }
  }

  /**
    * True for the topmost monitor in a hierarchy of monitors (created by calls of the
    * `monitor` method). Used for controlling printing during debugging.
    */

  private var monitorAtTop: Boolean = true

  /**
    * Method for turning an event into a user defined format for being printed
    * when transition triggering events are printed (`SHOW_TRANSITIONS` is true).
    * To be overridden by the user if the default `toString` format is not desired.
    * It can for example be used to filter out unimportant arguments, or highlighting
    * instance identifier.
    *
    * @param event the event to be converted.
    * @return `None` if that event should be rendered as the default `event.toString()`.
    *         `Some(s)` if it should be rendered as `s`.
    */

  protected def renderEventAs(event: E): Option[String] =
    None

  /**
    * Computes the key for an event. Keys are used for optimizing monitoring.
    * The method has a default definition returning `None` for all events. It
    * can be overridden by the user. Care should be taken since it interferes
    * with how properties are monitored.
    *
    * @param event the event.
    * @return the key computed for the event.
    */

  protected def keyOf(event: E): Option[Any] = None

  /**
    * Computes the instance id for an event. Ids are used for making clear
    * what specific id a state is tracking. This is used for better understanding
    * output from Daut. It is not used for any operational semantics.
    * The method has a default definition returning `None` for all events. It
    * can be overridden by the user.
    *
    * @param event the event.
    * @return the instance id computed for the event.
    */

  protected def instanceOf(event: E): Option[Any] = None

  /**
    * Determines the relevance of an event for the monitor.
    * The method has a default definition returning true for all events. It
    * can be overridden by the user. It can for example be used together with
    * indexing to write textbook automata.
    *
    * @param event the event.
    * @return true iff the event is relevant for the monitor.
    */

  protected def relevant(event: E): Boolean = true

  /**
    * The name of the monitor, derived from its class name.
    */

  val monitorName = this.getClass.getSimpleName

  /**
    * A monitor can contain sub-monitors in a hierarchical manner. Any event submitted to the monitor will
    * also be submitted to the sub-monitors. This allows for better organization of many monitors. The effect,
    * however, is the same as defining all monitors flatly at the same level.
    */

  private var monitors: List[Monitor[E]] = List()

  /**
    * A monitor can define other monitors that it sends events to. Such a monitor is stored
    * in the following variable with the single purpose to allow the `end()` method of
    * this monitor to call the `end()` method on the other monitors.
    */

  private var abstractMonitors: List[Monitor[?]] = List()

  /**
    * The active states of the monitor, excluding those of its sub-monitors.
    */

  private val states = new States()

  /**
    * This variable holds invariants that have been defined by the user with one of the
    * `invariant` methods. Invariants are Boolean valued functions that are
    * evaluated after each submitted event has been processed by the monitor. An invariant
    * can e.g. check the values of variables declared local to the monitor. The violation of an
    * invariant is reported as an error.
    */

  private var invariants: List[(String, Unit => Boolean)] = Nil

  /**
    * The current context.
    */

  private var context: Context = Context()

  /**
    * A monitor's body consists of a sequence of state declarations. The very first state
    * will become the initial state. This variable is used to keep track of when this
    * first state has been added as initial state, whereupon it is set to false, such that
    * subsequent states are not added as initial states.
    */

  private var initializing: Boolean = true

  /**
    * Set to true when the `end()` method has been called. Used to avoid multiple calls of
    * `end()` on monitors stored in the `abstractMonitors` variable.
    */

  private var endCalled: Boolean = false

  /**
    * Number of violation of the specification encountered.
    */

  private var errorCount: Int = 0

  /**
    * The number of errors that were detected due to the latest
    * event processed with `verify(event)`.
    * It is set to 0 at the beginning of the `verify` method.
    */

  private var errorCountDueToLatestEvent: Int = 0

  /**
    * True after a call of `verify(event)` iff. a transition triggered in a state.
    * It is set to false at the beginning of the `verify` method.
    */

  private var transitionTriggeredDueToLatestEvent: Boolean = false

  /**
    * Returns true iff. a transition triggered in a state of this
    * monitor due to the latest event.
    *
    * @return true iff. a transition triggered in a state of this
    *         monitor due to the latest event.
    */

  def transitionTriggered: Boolean =
    transitionTriggeredDueToLatestEvent

  /**
    * Various error and ok reports generated during monitoring.
    */

  private var reports: List[Report] = List()

  /**
    * Stores reports generated during proceessing of the most recent event submitted
    * to this monitor.
    */

  private var reportsFromLastEvent: List[Report] = List()

  /**
    * Adds a report to the list of reports from the most recent event as
    * well as to the accumulating list of all reports generated so far,
    * for this monitor.
    *
    * @param report the report to add.
    */

  def addReport(report: Report): Unit = {
    reportsFromLastEvent = reportsFromLastEvent :+ report
    reports = reports :+ report
    Monitor.writeOnlineFile(encodeReportWithType(report))
  }

  /**
    * Reports a message. This is not considered an error by Daut, meaning that it
    * does not increase the error count. This is supposed to be called by the user.
    *
    * @param message the message to be reported.
    */

  def report(message: String): Unit = {
    val report = UserReport(
      monitorName,
      context.currentState.getName,
      Monitor.eventNumber,
      context.currentEvent.toString,
      context.currentState.getInstanceIdString,
      context.currentTrace,
      message)
    addReport(report)
  }

  /**
    * Option, which when set to true will cause monitoring to stop the first time
    * a specification violation is encountered. Otherwise monitoring will continue.
    * Default value is false.
    */

  var STOP_ON_ERROR: Boolean = false

  /**
    * Option, which when set to true will cause events to be printed that trigger transitions in this monitor.
    * Default value is false.
    */

  var SHOW_TRANSITIONS: Boolean = false

  /**
    * When called with `flag` being true, events that trigger transitions in this monitor,
    * and all sub monitors, will be automatically printed.
    *
    * @param flag when true events will be printed. If this method is not called with `flag`
    *             being true, then no events will be printed automatically.
    * @return the current monitor, allowing for method chaining.
    */

  def showTransitions(flag: Boolean = true): Monitor[E] = {
    SHOW_TRANSITIONS = flag
    for (monitor <- monitors) {
      monitor.showTransitions(flag)
    }
    this
  }

  /**
    * Option, which when set to true will cause events to be printed that trigger ok transitions in this monitor.
    * Default value is false.
    */

  var RECORD_OK_TRANSITIONS: Boolean = false

  /**
    * When called with `flag` being true, events that trigger ok transitions in this monitor,
    * and all sub monitors, will be automatically printed.
    *
    * @param flag when true events will be printed. If this method is not called with `flag`
    *             being true, then no events will be printed automatically.
    * @return the current monitor, allowing for method chaining.
    */

  def recordOkTransitions(flag: Boolean = true): Monitor[E] = {
    RECORD_OK_TRANSITIONS = flag
    for (monitor <- monitors) {
      monitor.recordOkTransitions(flag)
    }
    this
  }

  /**
    * When this variable is true, logging of transitions in this monitor are silenced (not shown).
    * Specifically, even if `DautOptions.SHOW_TRANSITIONS` is true, transitions in this monitor are
    * not shown. Similarly, even if `DautOptions.REPORT_OK_TRANSITIONS` is true, ok transitions
    * in this monitor are not shown.
    */

  private var silenced: Boolean = false

  /**
    * Calling this method will cause logging of transitions in this monitor to be silenced (not shown).
    * Specifically, even if `DautOptions.SHOW_TRANSITIONS` is true, transitions in this monitor are
    * not shown. Similarly, even if `DautOptions.REPORT_OK_TRANSITIONS` is true, ok transitions
    * in this monitor are not shown.
    *
    * @return the same state.
    */

  def silence(): this.type = {
    silenced = true
    this
  }

  /**
    * Returns true if the monitor has been silenced.
    *
    * @return true if the monitor has been silenced.
    */

  private[daut] def isSilenced: Boolean =
    silenced

  /**
    * Launches the monitors provided as var-argument as sub-monitors of this monitor.
    * Being a sub-monitor has no special semantics, it is just a way of grouping
    * monitors in a hierarchical manner for organization purposes.
    *
    * @param monitors the monitors to become sub-monitors of this monitor.
    */

  def monitor(monitors: Monitor[E]*): Unit = {
    for (monitor <- monitors) {
      monitor.monitorAtTop = false
    }
    this.monitors ++= monitors
  }

  /**
    * Register a monitor as being communicated to from the current monitor. Usually such a monitor
    * functions as an abstraction (an abstract monitor), which can verify higher level events produced by the
    * current monitor, as well as by other monitors. That is, an abstract monitor can be registered as
    * such on several monitors. It is the responsibility of the current monitor to send events to it.
    * Whenever `end()` is called on the current monitor it is also called on its abstract monitors, as it is
    * called on submonitors (while avoiding duplicate calls of `end()`). The reason for calling this
    * method in fact is to ensure that `end()` is called automatically on the abstract monitor.
    *
    * @param monitor the abstract monitor
    * @tparam E the event type of the abstract monitor, which can be different from the event type of
    *           the current monitor, and it usually is.
    * @return the abstract monitor, so that the current monitor can store a reference to it in a variable.
    */

  def monitorAbstraction[E](monitor: Monitor[E]): Monitor[E] = {
    abstractMonitors = abstractMonitors :+ monitor
    monitor.monitorAtTop = false
    monitor
  }

  /**
    * A call of this method will cause the monitor and all its sub-monitors to stop on the first error encountered.
    *
    * @return the monitor itself so that the method can be called dot-appended to a constructor call.
    */

  def stopOnError(): Monitor[E] = {
    STOP_ON_ERROR = true
    for (monitor <- monitors) {
      monitor.stopOnError()
    }
    this
  }

  /**
    * The type of the partial function from events to sets of states representing
    * the transitions out of a state. Note that a state transition can result in more
    * than one state: all resulting states will subsequently be explored in parallel,
    * and all must be satisfied by the subsequent sequence of events.
    */

  protected type Transitions = PartialFunction[E, Set[state]]

  /**
    * Partial function representing the empty transition function, not defined
    * for any events. Used to initialize the transition function of a state.
    *
    * @return the empty transition function not defined for any events.
    */

  private def noTransitions: Transitions = {
    case _ if false => null
  }

  /**
    * Constant representing the empty set of states.
    */

  private val emptyStateSet: Set[state] = Set()

  /**
    * Invariant method which takes an invariant Boolean valued expression (call by name)
    * as argument and adds the corresponding lambda abstraction (argument of type `Unit`)
    * to the list of invariants to check after each submission of an event.
    *
    * @param inv the invariant expression to be checked after each submitted event.
    */

  protected def invariant(inv: => Boolean): Unit = {
    invariants ::= ("", (_: Unit) => inv)
    check(inv, "")
  }

  /**
    * Invariant method which takes an invariant Boolean valued expression (call by name)
    * as argument and adds the corresponding lambda abstraction (argument of type `Unit`)
    * to the list of invariants to check after each submission of an event. The first argument
    * is a message that will be printed in case the invariant is violated.
    *
    * @param e   message to be printed in case of an invariant violation.
    * @param inv the invariant expression to be checked after each submitted event.
    */

  protected def invariant(e: String)(inv: => Boolean): Unit = {
    invariants ::= (e, (_: Unit) => inv)
    check(inv, e)
  }

  /**
    * Returns the name of the monitor.
    *
    * @return the name of the monitor.
    */

  def getMonitorName: String =
    monitorName

  /**
    * Returns the direct sub monitors of the monitor.
    *
    * @return the direct sub monitors of the monitor.
    */

  def getDirectSubMonitors: List[Monitor[E]] =
    monitors

  /**
    * Returns the direct abstract monitors references by the monitor.
    *
    * @return the direct abstract monitors references by the monitor.
    */

  def getDirectAbstractMonitors: List[Monitor[?]] =
    abstractMonitors

  /**
    * Returns all sub monitors of a monitor, as well as references abstract monitors.
    *
    * @return all sub monitors of a monitor, as well as references abstract monitors.
    */

  def getAllSubAbsMonitors: List[Monitor[?]] = {
    var result: ListSet[Monitor[?]] = ListSet(monitors *) ++ ListSet(abstractMonitors *)
    result = result ++ monitors.flatMap(_.getAllSubAbsMonitors) ++ abstractMonitors.flatMap(_.getAllSubAbsMonitors)
    result.toList
  }

  /**
    * Returns all states (facts) contained in the monitor.
    *
    * @return all states.
    */

  def getAllStates: Set[state] = states.getAllStates

  /**
    * A state of the monitor.
    */

  protected trait state {
    thisState =>

    /**
      * String used to print state when not a case class. Used for anonymous states.
      */

    protected var label: String = "anonymous"

    /**
      * Returns the string representation of a state with the associated label. If it is an anonymous state, it
      * just returns the label. If it is a user defined case class it returns the string representation of
      * the case class and then the label.
      *
      * @return string representation of state.
      */

    def getName: String = {
      if (this.isInstanceOf[anonymous]) {
        label
      } else {
        s"${this.toString}.$label"
      }
    }

    /**
      * The transitions out of this state, represented as an (initially empty) partial
      * function from events to sets of states.
      */

    private[daut] var transitions: Transitions = noTransitions

    /**
      * True iff. the transition function of the state has been initialized.
      * Used to determine wich versions of always, watch, etc, to call:
      * those of the monitor or those of the state.
      */

    private var transitionsInitialized: Boolean = false

    /**
      * This variable is true for initial states.
      */

    private[daut] var isInitial: Boolean = false

    /**
      * This variable is true for final (acceptance) states: that is states where it is
      * acceptable to end up when the `end()` method is called. This corresponds to
      * acceptance states in standard automaton theory.
      */

    private[daut] var isFinal: Boolean = true

    /**
      * When this variable is true, logging of transitions out of this state are silenced (not shown).
      * Specifically, even if `DautOptions.SHOW_TRANSITIONS` is true, transitions out of this state are
      * not shown. Similarly, even if `DautOptions.REPORT_OK_TRANSITIONS` is true, ok transitions
      * in this state are not shown.
      */

    private var silenced: Boolean = false

    /**
      * Calling this method will cause logging of transitions out of this state to be silenced (not shown).
      * Specifically, even if `DautOptions.SHOW_TRANSITIONS` is true, transitions out of this state are
      * not shown. Similarly, even if `DautOptions.REPORT_OK_TRANSITIONS` is true, ok transitions
      * in this state are not shown.
      *
      * @return the same state.
      */

    infix def silence(): this.type = {
      silenced = true
      this
    }

    /**
      * Returns true if the state has been silenced.
      *
      * @return true if the state has been silenced.
      */

    private[daut] def isSilenced: Boolean =
      silenced

    /**
      * The optional instance of a state. This is an id pointed out by the user
      * for betting understanding output from Daut.
      */

    private var instanceId: Option[Any] = None

    /**
      * Assigns a value to the `instanceId` variable.
      *
      * @param id
      */

    def setInstanceId(id: Any): Unit = {
      instanceId = Some(id)
    }

    /**
      * Returns the instance id of the state.
      *
      * @return the instance id of the state.
      */

    def getInstanceId: Option[Any] =
      instanceId

    /**
      * Returns a string representation of the instance id of the state.
      *
      * @return string representation of the instance id of the state.
      */

    def getInstanceIdString: String = {
      instanceId match {
        case None => "N/A"
        case Some(id) => id.toString
      }
    }

    /**
      * Each state is associated with a trace of events, each of which caused
      * a transition in some state to trigger, transitively leading to this state.
      */

    var trace: List[TraceEvent] = List()

    /**
      * Updates the transition function to exactly the transition function provided.
      * This corresponds to a state where the monitor is just waiting (watching) until an event
      * is submitted that makes a transition fire. The state is final.
      * If the state has already been initialized with a transition function it calls the
      * corresponding function in the monitor, which returns a new state.
      *
      * @param ts the transition function.
      * @return the state itself, allowing for further chained method calls.
      */

    /**
      * Returns the trace of events that lead to this state.
      *
      * @return the trace of events that lead to this state.
      */

    def getTrace: List[TraceEvent] =
      trace

    def watch(ts: Transitions): state = {
      if (transitionsInitialized) return thisMonitor.watch(ts)
      transitionsInitialized = true
      label = "watch"
      transitions = ts
      this
    }

    /**
      * Labelled version of the `watch` method.
      *
      * @param values the label values.
      * @param ts     the transition function.
      * @return the state itself, allowing for further chained method calls.
      */

    def watch(values: Any*)(ts: Transitions): state = {
      if (transitionsInitialized) return thisMonitor.watch(values *)(ts)
      transitionsInitialized = true
      label = "watch" + values.map(_.toString).mkString("(", ",", ")")
      transitions = ts
      if (values.nonEmpty) {
        values.head match {
          case ID(x) =>
            setInstanceId(x)
          case _ =>
        }
      }
      this
    }

    /**
      * Updates the transition function to the transition function provided,
      * modified to always include the state in the resulting state set of any transition.
      * This corresponds to a state where the monitor is always waiting  until an event
      * is submitted that makes a transition fire, and where the state has a true
      * self loop, no matter what transition fires. The state is final.
      * If the state has already been initialized with a transition function it calls the
      * corresponding function in the monitor, which returns a new state.
      *
      * @param ts the transition function.
      * @return the state itself, allowing for further chained method calls.
      */

    def always(ts: Transitions): state = {
      if (transitionsInitialized) return thisMonitor.always(ts)
      transitionsInitialized = true
      label = "always"
      transitions = ts andThen (_ + this)
      this
    }

    /**
      * Labelled version of the `always` method.
      *
      * @param values the label values.
      * @param ts     the transition function.
      * @return the state itself, allowing for further chained method calls.
      */

    def always(values: Any*)(ts: Transitions): state = {
      if (transitionsInitialized) return thisMonitor.always(values *)(ts)
      transitionsInitialized = true
      label = "always" + values.map(_.toString).mkString("(", ",", ")")
      transitions = ts
      if (values.nonEmpty) {
        values.head match {
          case ID(x) =>
            setInstanceId(x)
          case _ =>
        }
      }
      this
    }

    /**
      * Updates the transition function to the transition function provided.
      * This corresponds to a state where the monitor is just waiting (watching) until an event
      * is submitted that makes a transition fire. The state is non-final, meaning
      * that it is an error to be in this state on a call of the `end()` method.
      * If the state has already been initialized with a transition function it calls the
      * corresponding function in the monitor, which returns a new state.
      *
      * @param ts the transition function.
      * @return the state itself, allowing for further chained method calls.
      */

    def hot(ts: Transitions): state = {
      if (transitionsInitialized) return thisMonitor.hot(ts)
      transitionsInitialized = true
      label = "hot"
      transitions = ts
      isFinal = false
      this
    }

    /**
      * Labelled version of the `hot` method.
      *
      * @param values the label values.
      * @param ts     the transition function.
      * @return the state itself, allowing for further chained method calls.
      */

    def hot(values: Any*)(ts: Transitions): state = {
      if (transitionsInitialized) return thisMonitor.hot(values *)(ts)
      transitionsInitialized = true
      label = "hot" + values.map(_.toString).mkString("(", ",", ")")
      transitions = ts
      isFinal = false
      if (values.nonEmpty) {
        values.head match {
          case ID(x) =>
            setInstanceId(x)
          case _ =>
        }
      }
      this
    }

    /**
      * Updates the transition function to the transition function provided,
      * modified to yield an error if it does not fire on the next submitted event.
      * The transition is weak in the sense that a next event does not have to occur (in contrast to strong next).
      * The state is therefore final.
      * If the state has already been initialized with a transition function it calls the
      * corresponding function in the monitor, which returns a new state.
      *
      * @param ts the transition function.
      * @return the state itself, allowing for further chained method calls.
      */

    def wnext(ts: Transitions): state = {
      if (transitionsInitialized) return thisMonitor.wnext(ts)
      transitionsInitialized = true
      label = "wnext"
      transitions = ts orElse { case _ => error }
      this
    }

    /**
      * Labelled version of the `wnext` method.
      *
      * @param values the label values.
      * @param ts     the transition function.
      * @return the state itself, allowing for further chained method calls.
      */

    def wnext(values: Any*)(ts: Transitions): state = {
      if (transitionsInitialized) return thisMonitor.wnext(values *)(ts)
      transitionsInitialized = true
      label = "wnext" + values.map(_.toString).mkString("(", ",", ")")
      transitions = ts
      if (values.nonEmpty) {
        values.head match {
          case ID(x) =>
            setInstanceId(x)
          case _ =>
        }
      }
      this
    }

    /**
      * Updates the transition function to the transition function provided,
      * modified to yield an error if it does not fire on the next submitted event.
      * The transition is strong in the sense that a next event has to occur.
      * The state is therefore non-final.
      * If the state has already been initialized with a transition function it calls the
      * corresponding function in the monitor, which returns a new state.
      *
      * @param ts the transition function.
      * @return the state itself, allowing for further chained method calls.
      */

    def next(ts: Transitions): state = {
      if (transitionsInitialized) return thisMonitor.next(ts)
      transitionsInitialized = true
      label = "next"
      transitions = ts orElse { case _ => error }
      isFinal = false
      this
    }

    /**
      * Labelled version of the `next` method.
      *
      * @param values the label values.
      * @param ts     the transition function.
      * @return the state itself, allowing for further chained method calls.
      */

    def next(values: Any*)(ts: Transitions): state = {
      if (transitionsInitialized) return thisMonitor.next(values *)(ts)
      transitionsInitialized = true
      label = "next" + values.map(_.toString).mkString("(", ",", ")")
      transitions = ts
      isFinal = false
      if (values.nonEmpty) {
        values.head match {
          case ID(x) =>
            setInstanceId(x)
          case _ =>
        }
      }
      this
    }

    /**
      * An expression of the form `unless {ts1} watch {ts2`} watches `ts2` repeatedly
      * unless `ts1` fires. That is, the expression updates the transition function as
      * the combination of the two transition functions provided. The resulting transition function
      * first tries `ts1`, and if it can fire that is chosen. Otherwise `t2` is tried,
      * and if it can fire it is made to fire, and the unless-state is re-added to the resulting state set.
      * The transition function `ts1` does not need to ever fire, which makes the state final.
      * If the state has already been initialized with a transition function it calls the
      * corresponding function in the monitor, which returns a new state.
      *
      * @param ts1 the transition function.
      * @return the state itself, allowing for further chained method calls.
      */

    def unless(ts1: Transitions): Object {def watch(ts2: Transitions): state} = new {
      def watch(ts2: Transitions): state = {
        if (transitionsInitialized) return thisMonitor.unless(ts1).watch(ts2)
        transitionsInitialized = true
        label = "until"
        transitions = ts1 orElse (ts2 andThen (_ + thisState))
        thisState
      }
    }

    /**
      * An expression of the form `until {ts1} watch {ts2`} watches `ts2` repeatedly
      * until `ts1` fires. That is, the expression updates the transition function as
      * the combination of the two transition functions provided. The resulting transition function
      * first tries `ts1`, and if it can fire that is chosen. Otherwise `t2` is tried,
      * and if it can fire it is made to fire, and the unless-state is re-added to the resulting state set.
      * The transition function `ts1` will need to eventually ever fire before `end()` is
      * called, which makes the state non-final.
      * If the state has already been initialized with a transition function it calls the
      * corresponding function in the monitor, which returns a new state.
      *
      * @param ts1 the transition function.
      * @return the state itself, allowing for further chained method calls.
      */

    def until(ts1: Transitions): Object {def watch(ts2: Transitions): state} = new {
      def watch(ts2: Transitions): state = {
        if (transitionsInitialized) return thisMonitor.until(ts1).watch(ts2)
        transitionsInitialized = true
        label = "until"
        transitions = ts1 orElse (ts2 andThen (_ + thisState))
        isFinal = false
        thisState
      }
    }

    /**
      * Applies the state to an event. If the transition function associated with the state
      * can fire, the resulting state set `ss` is returned as `Some(ss)`.
      * If the transition function cannot fire `None` is returned.
      *
      * @param event the event the state is applied to.
      * @return the optional set of states resulting from taking a transition.
      */

    def apply(event: E): Option[Set[state]] =
      if (transitions.isDefinedAt(event)) {
        val newTrace = trace :+ TraceEvent(this.getName, Monitor.eventNumber, event.toString)
        context.currentState = this
        context.currentTrace = newTrace
        val newStates = transitions(event)
        for (ns <- newStates) {
          ns match {
            case `stay` =>
            case `error` => reportTransitionError(this, event, newTrace, error.message)
            case `ok` =>
              if ((thisMonitor.RECORD_OK_TRANSITIONS || DautOptions.REPORT_OK_TRANSITIONS) && !(thisState.isSilenced || thisMonitor.isSilenced)) {
                reportTransitionOk(this, event, newTrace, ok.message)
              }
            case ns =>
              if (!ns.isInitial) {
                ns.trace = newTrace
                if (ns.instanceId.isEmpty) {
                  instanceOf(event) match {
                    case None => ns.instanceId = instanceId
                    case Some(id) => ns.instanceId = Some(id)
                  }
                }
              }
          }
        }
        Some(newStates)
      } else None

    if (initializing) {
      initial(this)
    }
  }

  /**
    * A subtrait of the `state` trait. Named states introduced by the user as case classes are called
    * facts and must extend this trait, as in:
    *
    * {{{
    *   case class MyData(x:Int) extends fact
    * }}}
    *
    * The `toString` method works as for case classes.
    */

  protected trait fact extends state

  /**
    * An anonymous state can be labelled with data with a call of the `label`
    * method. The label becomes part of the result of calling the `toString`
    * method.
    */

  protected trait anonymous extends state {

    /**
      * Given an anonymous state, this method adds the arguments provided as a text
      * string in parentheses to the name of the anonymous state. This is returned by
      * the `toString` method. It is used for debugging specifications and their
      * execution.
      *
      * E.g. given the state:
      *
      * {{{
      *  always {
      *     case acquire(t, x) =>
      *       hot {
      *         case acquire(`t`,_) => error
      *         case release(`t`,`x`) => ok
      *       } label(t,x)
      *   }
      * }}}
      *
      * The outer always state prints as: `always` whereas the inner hot state
      * prints as `hot(1,3)` for data `t=1` and `x=3`.
      *
      * @param values the values to be included in the label.
      * @return the state itself, but updated with the label.
      */

    infix def label(values: Any*): state = {
      label += values.map(_.toString).mkString("(", ",", ")")
      this
    }

    /**
      * The standard `toString` method overridden.
      *
      * @return text representation of state.
      */

    override def toString: String = label
  }

  /**
    * Special state indicating that we stay in the current state. This is normally
    * achieved by none of the transitions being able to fire. However, there can be
    * situations where we want to provide a transition explicitly, to indicate that
    * we stay in the current state.
    */

  protected case object stay extends state

  /**
    * Special state indicating successful termination. It contains an optional
    * message which is set if the `ok(String)` method is called with a string parameter.
    */

  protected case object ok extends state {
    var message: Option[String] = None
  }

  /**
    * Prints a message and returns an `ok` state indicating a specification success.
    *
    * @param msg message to be printed on standard out.
    * @return the `ok` state.
    */

  protected def ok(msg: String): state = {
    println("\n+++ ok state reached: " + msg + "\n")
    ok.message = Some(msg)
    ok
  }

  /**
    * Special state indicating a specification violation. It contains an optional
    * message which is set if the `error(String)` method is called with a string parameter.
    */

  protected case object error extends state {
    var message: Option[String] = None
  }

  /**
    * Prints a message and returns an `error` state indicating a specification violation.
    *
    * @param msg message to be printed on standard out.
    * @return the `error` state.
    */

  protected def error(msg: String): state = {
    println("\n*** error state reached: " + msg + "\n")
    error.message = Some(msg)
    error
  }

  /**
    * The state is usually assigned to a local `val`-variable that can be queried
    * e.g. in invariants, either simply using the during state as a Boolean (the state is lifted to a Boolean
    * with an implicit function) or by using the ==> method. Consider the following
    * example illustrating a monitor that checks that at most one of two threads 1 and 2
    * are in a critical section at any time, using an invariant. A thread `x` can enter a critical
    * section with the `enter(x)` call, and leave with either an `exit(x)` call or an
    * `abort(x)` call.
    *
    * {{{
    * class CriticalSectionMonitor extends Monitor[Event] {
    *   val critical1 = during(enter(1))(exit(1), abort(1))
    *   val critical2 = during(enter(2))(exit(2), abort(1))
    *
    *   invariant {
    *     !(critical1 && critical2)
    *   }
    * }
    * }}}
    *
    * The invariant can also be written as follows, using the ==> method:
    *
    * {{{
    *   invariant {
    *     critical1 ==> !critical2
    *   }
    * }}}
    *
    * @param es1 any of these events starts an interval.
    * @param es2 any of these events ends an interval.
    */

  protected case class during(es1: E*)(es2: E*) extends state {
    /**
      * The set of events starting an interval.
      */

    private val begin = es1.toSet

    /**
      * The set of events ending an interval.
      */

    private val end = es2.toSet

    /**
      * This variable is true when we are within an interval.
      */

    private[daut] var on: Boolean = false

    /**
      * This method allows us, given a during-state `dur`, to write a Boolean
      * expression of the form `dur ==> condition`, meaning: if `dur` is in the interval
      * then the `condition` must hold.
      *
      * @param b the condition that must hold if the during-state is within the interval.
      * @return true if the during state is not within an interval, or if the condition `b` holds.
      */

    def ==>(b: Boolean): Boolean = {
      !on || b
    }

    /**
      * A call of this method on a during-state causes the state to initially be within
      * an interval, as if one of the events in `es1` had occurred. As an example, one can
      * write:
      *
      * {{{
      *   val dur = during(BEGIN)(END) startsTrue
      * }}}
      *
      * @return the during-state itself.
      */

    def startsTrue: during = {
      on = true
      this
    }

    this.always {
      case e =>
        if (begin.contains(e)) {
          on = true
        }
        else if (end.contains(e)) {
          on = false
        }
    }
    initial(this)
  }

  /**
    * This function lifts a during-state to a Boolean value, true iff. the during-state is
    * within the interval.
    *
    * @param iv the during-state to be lifted.
    * @return true iff. the during-state `iv` is within the interval.
    */

  protected implicit def liftInterval(iv: during): Boolean = iv.on

  /**
    * Returns a watch-state, where the transition function is exactly the transition function provided.
    * This corresponds to a state where the monitor is just waiting (watching) until an event
    * is submitted that makes a transition fire. The state is final.
    *
    * @param ts the transition function.
    * @return an anonymous watch-state.
    */

  protected def watch(ts: Transitions): anonymous = new anonymous {
    this.watch(ts)
  }

  /**
    * Labelled version of the `watch` method. This can be used instead to calling the `label` method
    * at the end of an anonymous `watch` state.
    *
    * @param values the label values.
    * @param ts     the transition function.
    * @return an anonymous watch-state with a label.
    */

  protected def watch(values: Any*)(ts: Transitions): anonymous = new anonymous {
    this.watch(ts)
    if (values.nonEmpty) {
      values.head match {
        case ID(x) =>
          setInstanceId(x)
        case _ =>
      }
    }
  }.label(values *).asInstanceOf[anonymous]

  /**
    * Returns an always-state, where the transition function is the transition function provided,
    * modified to always include the state in the resulting state set of any transition.
    * This corresponds to a state where the monitor is always waiting  until an event
    * is submitted that makes a transition fire, and where the state has a true
    * self loop, no matter what transition fires. The state is final.
    *
    * @param ts the transition function.
    * @return an anonymous always-state.
    */

  protected def always(ts: Transitions): anonymous = new anonymous {
    this.always(ts)
  }

  /**
    * Labelled version of the `always` method. This can be used instead to calling the `label` method
    * at the end of an anonymous `always` state.
    *
    * @param values the label values.
    * @param ts     the transition function.
    * @return an anonymous always-state with a label.
    */

  protected def always(values: Any*)(ts: Transitions): anonymous = new anonymous {
    this.always(ts)
    if (values.nonEmpty) {
      values.head match {
        case ID(x) =>
          setInstanceId(x)
        case _ =>
      }
    }
  }.label(values *).asInstanceOf[anonymous]

  /**
    * Returns a hot-state, where the transition function is the transition function provided.
    * This corresponds to a state where the monitor is just waiting (watching) until an event
    * is submitted that makes a transition fire. The state is non-final, meaning
    * that it is an error to be in this state on a call of the `end()` method.
    *
    * @param ts the transition function.
    * @return an anonymous hot-state.
    */

  protected def hot(ts: Transitions): anonymous = new anonymous {
    this.hot(ts)
  }

  /**
    * Labelled version of the `hot` method. This can be used instead to calling the `label` method
    * at the end of an anonymous `hot` state.
    *
    * @param values the label values.
    * @param ts     the transition function.
    * @return an anonymous hot-state with a label.
    */

  protected def hot(values: Any*)(ts: Transitions): anonymous = new anonymous {
    this.hot(ts)
    if (values.nonEmpty) {
      values.head match {
        case ID(x) =>
          setInstanceId(x)
        case _ =>
      }
    }
  }.label(values *).asInstanceOf[anonymous]

  /**
    * Returns a wnext-state (weak next), where the transition function is the transition function provided,
    * modified to yield an error if it does not fire on the next submitted event.
    * The transition is weak in the sense that a next event does not have to occur (in contrast to strong next).
    * The state is therefore final.
    *
    * @param ts the transition function.
    * @return an anonymous wnext-state.
    */


  protected def wnext(ts: Transitions): anonymous = new anonymous {
    this.wnext(ts)
  }

  /**
    * Labelled version of the `wnext` method. This can be used instead to calling the `label` method
    * at the end of an anonymous `wnext` state.
    *
    * @param values the label values.
    * @param ts     the transition function.
    * @return an anonymous wnext-state with a label.
    */

  protected def wnext(values: Any*)(ts: Transitions): anonymous = new anonymous {
    this.wnext(ts)
    if (values.nonEmpty) {
      values.head match {
        case ID(x) =>
          setInstanceId(x)
        case _ =>
      }
    }
  }.label(values *).asInstanceOf[anonymous]

  /**
    * Returns a next-state (strong next), where the transition function is the transition function provided,
    * modified to yield an error if it does not fire on the next submitted event.
    * The transition is strong in the sense that a next event has to occur.
    * The state is therefore non-final.
    *
    * @param ts the transition function.
    * @return an anonymous next-state.
    */

  protected def next(ts: Transitions): anonymous = new anonymous {
    this.next(ts)
  }

  /**
    * Labelled version of the `next` method. This can be used instead to calling the `label` method
    * at the end of an anonymous `next` state.
    *
    * @param values the label values.
    * @param ts     the transition function.
    * @return an anonymous next-state with a label.
    */

  protected def next(values: Any*)(ts: Transitions): anonymous = new anonymous {
    this.next(ts)
    if (values.nonEmpty) {
      values.head match {
        case ID(x) =>
          setInstanceId(x)
        case _ =>
      }
    }
  }.label(values *).asInstanceOf[anonymous]

  /**
    * An expression of the form `unless {ts1} watch {ts2`} watches `ts2` repeatedly
    * unless `ts1` fires. That is, the expression returns an unless-state, where the transition function is
    * the combination of the two transition functions provided. The resulting transition function
    * first tries `ts1`, and if it can fire that is chosen. Otherwise `t2` is tried,
    * and if it can fire it is made to fire, and the unless-state is re-added to the resulting state set.
    * The transition function `ts1` does not need to ever fire, which makes the state final.
    *
    * @param ts1 the transition function.
    * @return an anonymous unless-state.
    */


  protected def unless(ts1: Transitions): Object {def watch(ts2: Transitions): state} = new {
    def watch(ts2: Transitions): anonymous = new anonymous {
      this.unless(ts1).watch(ts2)
    }
  }

  /**
    * An expression of the form `until {ts1} watch {ts2`} watches `ts2` repeatedly
    * until `ts1` fires. That is, the expression returns an until-state, where the transition function is
    * the combination of the two transition functions provided. The resulting transition function
    * first tries `ts1`, and if it can fire that is chosen. Otherwise `t2` is tried,
    * and if it can fire it is made to fire, and the unless-state is re-added to the resulting state set.
    * The transition function `ts1` will need to eventually ever fire before `end()` is
    * called, which makes the state non-final.
    *
    * @param ts1 the transition function.
    * @return an anonymous until-state.
    */

  protected def until(ts1: Transitions): Object {def watch(ts2: Transitions): state} = new {
    def watch(ts2: Transitions): anonymous = new anonymous {
      this.until(ts1).watch(ts2)
    }
  }

  /**
    * Checks whether there exists an active state which satisfies the partial function
    * predicate provided as argument. That is: where the partial function is defined on
    * the state, and returns true. The method is used for rule-based programming.
    *
    * @param pred the partial function predicate tested on active states.
    * @return true iff there exists an active state `s` such that `pred.isDefinedAt(s)`
    *         and `pred(s) == true`.
    */

  def exists(pred: PartialFunction[state, Boolean]): Boolean = {
    val alwaysFalse: PartialFunction[state, Boolean] = {
      case _ => false
    }
    states.getAllStates exists (pred orElse alwaysFalse)
  }

  /**
    * The `map` method returns a set of states computed as follows.
    * If the provided argument partial function `pf` is defined for any active states,
    * the resulting set is the union of all the state sets obtained by
    * applying the function to the active states for which it is defined.
    * Otherwise the returned set is the set `otherwise` provided as
    * argument to the `orelse` method.
    *
    * As an example, consider the following monitor, which checks that
    * at most one task can acquire a lock at a time, and that
    * a task cannot release a lock it has not acquired.
    * This monitor illustrates the `map` function, which looks for stored
    * facts matching a pattern, and the ensure function, which checks a
    * condition (an assert). This function here in this example tests for
    * the presence of a Locked fact which is created when a lock is taken.
    *
    * {{{
    * trait LockEvent
    * case class acquire(thread: Int, lock: Int) extends LockEvent
    * case class release(thread: Int, lock: Int) extends LockEvent
    *
    * class OneAtATime extends Monitor[LockEvent] {
    *   case class Locked(thread: Int, lock: Int) extends state {
    *     watch {
    *       case release(thread, lock) => ok
    *     }
    *   }
    *
    *   always {
    *     case acquire(t, l) => {
    *       map {
    *         case Locked(_,`l`) => error("allocated more than once")
    *       } orelse {
    *         Locked(t,l)
    *       }
    *     }
    *     case release(t, l) => ensure(Locked(t,l))
    *   }
    * }
    * }}}
    *
    * A more sophisticated example involving nested `map` calls is
    * the following that checks that when a task `t` is acquiring a
    * lock that some other task holds, and `t` therefore cannot get it,
    * then `t` is not allowed to hold any other locks (to prevent deadlocks).
    *
    * {{{
    * class AvoidDeadlocks extends Monitor[LockEvent] {
    *   case class Locked(thread: Int, lock: Int) extends state {
    *     watch {
    *       case release(`thread`, `lock`) => ok
    *     }
    *   }
    *
    *   always {
    *     case acquire(t, l) => {
    *       map {
    *         case Locked(_,`l`) =>
    *           map {
    *             case Locked(`t`,x) if l != x => error
    *           } orelse {
    *             println("Can't lock but is not holding any other lock, so it's ok")
    *           }
    *       } orelse {
    *         Locked(t,l)
    *       }
    *     }
    *   }
    * }
    * }}}
    *
    * @param pf partial function.
    * @return set of states produced from applying the partial function `fp` to active states.
    */

  protected def map(pf: PartialFunction[state, Set[state]]): Object {def orelse(otherwise: => Set[state]): Set[state]} = new {
    def orelse(otherwise: => Set[state]): Set[state] = {
      val matchingStates = states.getAllStates filter pf.isDefinedAt
      if (matchingStates.nonEmpty) {
        (for (matchingState <- matchingStates) yield pf(matchingState)).flatten
      } else
        otherwise
    }
  }

  /**
    * Returns the state `ok` if the Boolean expression `b` is true, otherwise
    * it returns the `error` state. The method can for example be used as the
    * result of a transition.
    *
    * @param b Boolean condition.
    * @return one of the states `ok` or `error`, depending on the value of `b`.
    */

  protected def ensure(b: Boolean): state = {
    if (b) ok else error
  }

  /**
    * Checks whether the condition `b` is true, and if not, reports an error
    * on standard out. The text message `e` becomes part of the error
    * message.
    *
    * @param b the Boolean condition to be checked.
    */

  protected def check(b: Boolean, e: String): Unit = {
    if (!b) reportError(e)
  }

  /**
    * Adds the argument state `s` to the set of initial states of the monitor.
    *
    * @param s state to be added as initial state.
    */

  protected def initial(s: state): Unit = {
    s.isInitial = true
    states.initial(s)
  }

  /**
    * Implicit function lifting a state to a Boolean, which is true iff. the state
    * is amongst the current states. Hence, if `s` is a state then one can e.g.
    * write an expression (denoting a state) of the form:
    * {{{
    *   if (s) error else ok
    * }}}
    *
    * @param s the state to be lifted.
    * @return true iff. the state `s` is amongst the current states.
    */

  protected implicit def convState2Boolean(s: state): Boolean =
    states.getAllStates contains s

  /**
    * Implicit function lifting the `Unit` value `()` to the set:
    * `Set(ok)`. This allows to write code with side-effects (and return value
    * of type `Unit`) as a result of a transition.
    *
    * @param u the Unit value to be lifted.
    * @return the state set `Set(ok)`.
    */

  protected implicit def convUnit2StateSet(u: Unit): Set[state] =
    Set(ok)

  /**
    * Implicit function lifting a Boolean value `b` to the state set `Set(ok)`
    * if `b` is true, and to `Set(error)` if `b` is false.
    *
    * @param b the Boolean to be lifted.
    * @return if  `b` then `Set(ok)` else `Set(error)`.
    */

  protected implicit def convBoolean2StateSet(b: Boolean): Set[state] =
    Set(if (b) ok else error)

  /**
    * Implicit function converting a state to the a singleton state containing that state,
    * Recall that the result of a transition is a set of states. This function allows
    * to write a single state as result of a transition. That is e.g. `ok` instead
    * of `Set(ok)`.
    *
    * @param state the state to be lifted.
    * @return the singleton set `Set(state`.
    */

  protected implicit def convState2StateSet(state: state): Set[state] =
    Set(state)

  /**
    * Implicit function lifting a 2-tuple of states to the set of those states.
    * This allows the more succinct notation `(state1,state2)` instead of
    * `Set(state1,state2)`.
    *
    * @param states the 2-tuple of states to be lifted.
    * @return the set containing the two states.
    */

  protected implicit def conTuple2StateSet(states: (state, state)): Set[state] =
    Set(states._1, states._2)

  /**
    * Implicit function lifting a 3-tuple of states to the set of those states.
    * This allows the more succinct notation `(state1,state2,state3)` instead of
    * `Set(state1,state2,state3)`.
    *
    * @param states the 3-tuple of states to be lifted.
    * @return the set containing the three states.
    */

  protected implicit def conTriple2StateSet(states: (state, state, state)): Set[state] =
    Set(states._1, states._2, states._3)

  /**
    * Implicit function lifting a list of states to a set of states. This allows to
    * write the result of a transition e.g. as a for-yield construct, as in the following
    * result of a transition, which is a list of hot-states.
    *
    * {{{
    *   always {
    *     case StopAll(someList) => for (x <- someList) yield hot {case Stop(x) => ok}
    *   }
    * }}}
    *
    * @param states the list of states to be lifted.
    * @return the set containing those states.
    */

  protected implicit def convList2StateSet(states: List[state]): Set[state] =
    states.toSet

  /**
    * Implicit function lifting a state to an anonymous object defining the `&`-operator,
    * which defines conjunction of states. Hence one can write `state1 & state2`, which then
    * results in the set `Set(state1,state2)`.
    *
    * @param s1 the state to be lifted.
    * @return the anonymous object defining the method `&(s2: state): Set[state]`.
    */

  protected implicit def convState2AndState(s1: state): Object {def &(s2: state): Set[state]} = new {
    def &(s2: state): Set[state] = Set(s1, s2)
  }

  /**
    * Implicit function lifting a set of states to an anonymous object defining the `&`-operator,
    * which defines conjunction of states. Hence one can write `state1 & state2 & state3`, which then
    * results in the set `Set(state1,state2,state3)`. This works by first lifting
    * `state1 & state2` to the set `Set(state1,state2)`, and then apply `& state3` to
    * obtain `Set(state1,state2,state3)`.
    *
    * @param set the set of states to be lifted.
    * @return the anonymous object defining the method `&(s2: state): Set[state]`.
    */

  protected implicit def conStateSet2AndStateSet(set: Set[state]): Object {def &(s: state): Set[state]} = new {
    def &(s: state): Set[state] = set + s
  }

  /**
    * Implicit function lifting a Boolean to an anonymous object defining the implication
    * operator. This allows to write `b1 ==> b2` for two Boolean expressions
    * `b1` and `b2`. It has the same meaning as `!b1 || b2`.
    *
    * @param p the Boolean to be lifted.
    * @return the anonymous object defining the method `==>(q: Boolean)`.
    */

  protected implicit def liftBoolean(p: Boolean): Object {def ==>(q: Boolean): Boolean} = new {
    def ==>(q: Boolean): Boolean = !p || q
  }

  /**
    * Verifies a full trace of events. For each event `e` it calls `verify(e)`. It calls `end()`
    * at the end of the trace.
    *
    * @param events list (trace) of events to verify.
    * @return this monitor (allowing method chaining).
    */

  def verify(events: List[E]): this.type = {
    for (event <- events) {
      verify(event)
    }
    end()
  }

  /**
    * Submits an event to the monitor for verification against the specification.
    * The event is "submitted" to each relevant current state set (taking indexing into account),
    * each such application resulting in a new set of states.
    * The method evaluates the invariants as part of the verification.
    *
    * The method uses indexing to optimize the monitoring: each event is mapped to
    * a key, which is used to fast-access the set of states relevant for the event.
    *
    * @param event   the submitted event.
    * @param eventNr number of event being verified. If 0 use internal counter.
    *                Passing this as argument is used when the event stream is filtered
    *                before events get passed to this function, and we want the
    *                original positions used in error messages.
    * @return this monitor (allowing method chaining).
    */

  def verify(event: E, eventNr: Long = 0): this.type = {
    context.currentEvent = event
    errorCountDueToLatestEvent = 0
    transitionTriggeredDueToLatestEvent = false
    reportsFromLastEvent = List()
    if (monitorAtTop) {
      Monitor.transitionTriggeredInSomeMonitor = false
      if (eventNr > 0) {
        Monitor.eventNumber = eventNr
      } else {
        Monitor.eventNumber += 1
      }
    }
    if (initializing) initializing = false
    verifyBeforeEvent(event)
    if (relevant(event)) {
      states.applyEvent(event)
      invariants foreach { case (e, inv) => check(inv(()), e) }
    }
    for (monitor <- monitors) {
      monitor.verify(event)
    }
    if (monitorAtTop && DautOptions.DEBUG && (DautOptions.DEBUG_ALL_EVENTS || Monitor.transitionTriggeredInSomeMonitor)) {
      println("\n===[" + event + "]===\n")
      printStates()
    }
    verifyAfterEvent(event)
    this
  }

  /**
    * Ends the monitoring, reporting on all remaining current non-final states.
    * These represent obligations that have not been fulfilled.
    *
    * @return this monitor (allowing method chaining).
    */

  def end(): this.type = {
    if (!endCalled) {
      endCalled = true
      // debug(s"Ending Daut trace evaluation for $monitorName")
      val theEndStates = states.getAllStates
      val hotStates = theEndStates filter (!_.isFinal)
      if (hotStates.nonEmpty) {
        val headline = s"*** Daut omission errors for $monitorName"
        val separator = "=" * headline.length
        println()
        println(separator)
        println(headline)
        println(separator)
        for (hotState <- hotStates) {
          reportOmissionError(hotState, hotState.trace)
        }
      }
      for (monitor <- monitors) {
        monitor.end()
      }
      for (monitor <- abstractMonitors) {
        monitor.end()
      }
      println()
      // println(s"Monitor $monitorName detected $errorCount errors!")
    }
    if (monitorAtTop) {
      printSummary()
      writeReportToJson()
    }
    this
  }

  /**
    * Returns the error status for the monitor.
    *
    * @return the error status for the monitor.
    */

  def getErrorStatus: ErrorStatus = {
    val errorStatusOfSubMonitors: List[ErrorStatus] = monitors.map(_.getErrorStatus)
    val errorStatusOfAbstractMonitors: List[ErrorStatus] = abstractMonitors.map(_.getErrorStatus)
    ErrorStatus(monitorName, errorCount, getErrorCount, errorStatusOfSubMonitors, errorStatusOfAbstractMonitors)
  }

  /**
    * Returns the error count for the monitor and all its sub monitors and abstract
    * monitors as a flat map, mapping monitor names to error counts.
    *
    * @return the error count for the monitor and its sub monitors and abstract monitors.
    */

  def getErrorStatusMap: Map[String, Int] = {
    var result: Map[String, Int] = Map(monitorName -> getErrorCount)
    for (monitor <- monitors ++ abstractMonitors) {
      result = result ++ monitor.getErrorStatusMap
    }
    result
  }

  /**
    * prints the error summary for the monitor after `end()` has been called.
    * The error status includes all results for the monitor as well as for
    * sub monitors, and abstract monitors they refer to.
    */

  private def printSummary(): Unit = {
    if (DautOptions.PRINT_ERRORS_AT_END) {
      println()
      println("========================")
      println("Summary of Daut Reports:")
      println("========================")
      println()
      for (report <- getReports) {
        println(report)
      }
    }
    println()
    println("==============================")
    println("Daut Error Status per Monitor:")
    println("==============================")
    println(getErrorStatus)
    println("-------------------------")
  }

  // Define the encoders explicitly for each case class

  /**
    * JSON encoders for each case class we want to write in JSON format
    */

  object Encoders {
    implicit val traceEventEncoder: Encoder[TraceEvent] = deriveEncoder[TraceEvent]
    implicit val transitionErrorReportEncoder: Encoder[TransitionErrorReport] = deriveEncoder[TransitionErrorReport]
    implicit val transitionOkReportEncoder: Encoder[TransitionOkReport] = deriveEncoder[TransitionOkReport]
    implicit val omissionErrorReportEncoder: Encoder[OmissionErrorReport] = deriveEncoder[OmissionErrorReport]
    implicit val userErrorReportEncoder: Encoder[UserErrorReport] = deriveEncoder[UserErrorReport]
    implicit val userReportEncoder: Encoder[UserReport] = deriveEncoder[UserReport]
    implicit val transitionReportEncoder: Encoder[TransitionReport] = deriveEncoder[TransitionReport]
  }

  /**
    * Helper function to encode reports with a "type" field and handle "msg" as JSON if applicable.
    *
    * @param report the report to turn into JSON
    * @return the report in JSON format.
    */

  def encodeReportWithType(report: Report): Json = {
    val typeName = report.getClass.getSimpleName.replace("$", "") // Get case class name

    // Match to handle the "msg" field and "type" for specific report types

    report match {
      case r: TransitionErrorReport =>
        val msgJson = r.msg.flatMap { msg =>
          if (msg.startsWith("json")) parse(msg.stripPrefix("json").trim).toOption else Some(Json.fromString(msg))
        }.getOrElse(Json.Null)
        r.asJson(Encoders.transitionErrorReportEncoder).deepMerge(Json.obj("type" -> Json.fromString(typeName), "msg" -> msgJson))

      case r: TransitionOkReport =>
        val msgJson = r.msg.flatMap { msg =>
          if (msg.startsWith("json")) parse(msg.stripPrefix("json").trim).toOption else Some(Json.fromString(msg))
        }.getOrElse(Json.Null)
        r.asJson(Encoders.transitionOkReportEncoder).deepMerge(Json.obj("type" -> Json.fromString(typeName), "msg" -> msgJson))

      case r: UserErrorReport =>
        r.asJson(Encoders.userErrorReportEncoder).deepMerge(Json.obj("type" -> Json.fromString(typeName)))

      case r: UserReport =>
        r.asJson(Encoders.userReportEncoder).deepMerge(Json.obj("type" -> Json.fromString(typeName)))

      case r: OmissionErrorReport =>
        r.asJson(Encoders.omissionErrorReportEncoder).deepMerge(Json.obj("type" -> Json.fromString(typeName)))

      case r: TransitionReport =>
        r.asJson(Encoders.transitionReportEncoder).deepMerge(Json.obj("type" -> Json.fromString(typeName)))
    }
  }

  /**
    * Function to write reports in JSON format to a file.
    */

  private def writeReportToJson(): Unit = {
    val filePath = DautOptions.RESULT_FILE
    val jsonMap = generateJsonMap
    val jsonObject = Json.obj(
      jsonMap.map {
        case (monitor, instanceMap) =>
          monitor -> Json.obj(
            instanceMap.map {
              case (instance, reports) =>
                instance -> reports.map(encodeReportWithType).asJson
            }.toSeq*
          )
      }.toSeq*
    )

    val writer = new PrintWriter(new BufferedWriter(new FileWriter(filePath, false)))
    writer.write(jsonObject.spaces2)
    writer.close()
  }

  /**
    * Generates a map from monitor names to a map from instances to a list of reports.
    * This is used by the `writeReportToJson` to print out the result of monitoring
    * as a nested JSON object. The map is sorted according to monitor names and
    * instances respectively.
    *
    * @return the map from monitor names and instances (nested) to reports.
    */

  private def generateJsonMap: ListMap[String, ListMap[String, List[Report]]] = {
    var result: ListMap[String, ListMap[String, List[Report]]] = ListMap()

    def update(outerKey: String, innerKey: String, newValue: Report): Unit = {
      val innerMap = result.getOrElse(outerKey, ListMap())
      val updatedList = innerMap.getOrElse(innerKey, List()) :+ newValue
      val updatedInnerMap = innerMap + (innerKey -> updatedList)
      result = result + (outerKey -> updatedInnerMap)
    }

    for (report <- getReports) {
      report match {
        case TransitionReport(monitor, state, eventNr, event, instance) =>
          update(monitor, instance, report)
        case TransitionErrorReport(monitor, state, eventNr, event, instance, trace, msg) =>
          update(monitor, instance, report)
        case TransitionOkReport(monitor, state, eventNr, event, instance, trace, msg) =>
          update(monitor, instance, report)
        case OmissionErrorReport(monitor, state, instance, trace) =>
          update(monitor, instance, report)
        case UserErrorReport(monitor, state, eventNr, event, instance, trace, msg) =>
          update(monitor, instance, report)
        case UserReport(monitor, state, eventNr, event, instance, trace, msg) =>
          update(monitor, instance, report)
      }
    }

    for (monitorName <- getErrorStatusMap.keySet) {
      if (!result.contains(monitorName)) {
        result = result + (monitorName -> ListMap())
      }
    }

    val sortedResult: ListMap[String, ListMap[String, List[Report]]] = ListMap(
      result.toSeq.sortBy(_._1).map {
        case (outerKey, innerMap) =>
          val sortedInnerMap = ListMap(innerMap.toSeq.sortBy(_._1) *)
          (outerKey, sortedInnerMap)
      } *
    )

    sortedResult
  }

  /**
    * Allows applying a monitor `M` to an event `e`, as follows: `M(e)`.
    * This has the same meaning as the longer `M.verify(e)`.
    *
    * @param event the submitted event to be verified.
    * @return this monitor (allowing method chaining).
    */

  def apply(event: E): this.type = {
    verify(event)
  }

  /**
    * Allows applying a monitor `M` to an event trace `t`, as follows: `M(t)`.
    * This has the same meaning as the longer `M.verify(t)`.
    *
    * @param events list (trace) of events to verify.
    * @return this monitor (allowing method chaining).
    */

  def apply(events: List[E]): this.type = {
    verify(events)
  }

  /**
    * This method is called <b>before</b> every call of `verify(event: E)`.
    * It can be overridden by user. Its body is by default empty.
    *
    * @param event the event being verified.
    */

  protected def verifyBeforeEvent(event: E): Unit = {}

  /**
    * This method is called <b>after</b> every call of `verify(event: E)`.
    * It can be overridden by user. Its body is by default empty.
    *
    * @param event the event being verified.
    */

  protected def verifyAfterEvent(event: E): Unit = {}

  /**
    * This method is called when the monitor encounters an error, be it a safety
    * error or a liveness error.
    * It can be overridden by user. Its body is by default empty.
    */
  protected def callBack(): Unit = {}

  /**
    * Returns the number of errors detected by this monitor only, excluding sub-monitors.
    *
    * @return the number of errors detected by this monitor.
    */

  def getErrorCountForThisMonitor: Int =
    errorCount

  /**
    * Returns the number of errors detected by the monitor, including its sub-monitors.
    *
    * @return the number of errors of the monitor and its sub-monitors.
    */

  def getErrorCount: Int = {
    var count = errorCount
    for (m <- monitors) count += m.getErrorCount
    count
  }

  /**
    * Returns the number of errors detected due to the latest event.
    *
    * @return the number of errors detected due to the latest event.
    */

  def getLatestErrorCountForThisMonitor: Int =
    errorCountDueToLatestEvent

  /**
    * Returns the number of errors detected by the monitor, including its sub-monitors,
    * due to the latest event.
    *
    * @return the latest number of errors of the monitor and its sub-monitors.
    */

  def getLatestErrorCount: Int = {
    var count = errorCountDueToLatestEvent
    for (m <- monitors) count += m.getLatestErrorCount
    count
  }

  /**
    * Prints the current states of the monitor, and its sub-monitors.
    */

  def printStates(): Unit = {
    println(s"--- $monitorName:")
    println("[memory] ")
    for (s <- states.getMainStates) {
      println(s"  ${s.getName}")
      if (DautOptions.DEBUG_TRACES && s.trace.nonEmpty) {
        println(formatTrace(s.trace, 4))
      }
    }
    println
    for (index <- states.getIndexes) {
      println(s"[index=$index]")
      for (s <- states.getIndexedSet(index)) {
        println(s"  $s")
        if (DautOptions.DEBUG_TRACES && s.trace.nonEmpty) {
          println(formatTrace(s.trace, 4))
        }
      }
    }
    println()
    for (m <- monitors) m.printStates()
  }

  /**
    * Returns the reports generated for this monitor.
    *
    * @return the reports generated for this monitor.
    */

  def getReportsForThisMonitor: List[Report] = {
    reports
  }

  /**
    * Returns the reports generated for this monitor and all its sub monitors, including abstract monitors.
    *
    * @return the reports generated for this monitor and all its sub monitors, including abstract monitors.
    */

  def getReports: List[Report] =
    reports ++ getAllSubAbsMonitors.flatMap(_.getReportsForThisMonitor)

  /**
    * Returns the reports generated by processing the most recent event for this monitor.
    *
    * @return the reports generated by processing the most recent event for this monitor.
    */

  def getLatestReportsForThisMonitor: List[Report] = {
    reportsFromLastEvent
  }

  /**
    * Returns the reports generated by processing the most recent event for this monitor
    * and all its sub monitors and referenced abstract monitors.
    *
    * @return the reports generated by processing the most recent event for this monitor
    *         and all its sub monitors and referenced abstract monitors.
    */

  def getLatestReports: List[Report] =
    reportsFromLastEvent ++ getAllSubAbsMonitors.flatMap(_.getLatestReportsForThisMonitor)

  /**
    * Returns the string value of the instanceId from the state if defined, otherwise the instanceId from the event.
    *
    * @param st    the state.
    * @param event the event.
    * @return the the string value of the instanceId.
    */

  def getInstanceIdStringFromStateOrEvent(st: state, event: E): String = {
    if (!st.getInstanceId.isEmpty) {
      st.getInstanceIdString
    } else {
      instanceOf(event) match {
        case None => "N/A"
        case Some(id) => id.toString
      }
    }
  }

  /**
    * Reports a transition error.
    *
    * @param st    the state in which the error occurred.
    * @param event the triggering event causing the error.
    * @param trace the trace leading to the error. Includes only events that
    *              triggered transitions leading to this state.
    * @param msg   a user provided optional message.
    */

  protected def reportTransitionError(st: state, event: E, trace: List[TraceEvent], msg: Option[String]): Unit = {
    val instanceIdString = getInstanceIdStringFromStateOrEvent(st, event)
    val report = TransitionErrorReport(monitorName, st.getName, Monitor.eventNumber, event.toString, instanceIdString, trace, msg)
    createErrorReport(report)
  }

  /**
    * Reports an omission error, when `end()` is called in a `hot` or `next` state.
    *
    * @param st    the violating hot or next state at the end of the trace.
    * @param trace the trace leading to the error. Includes only events that
    *              triggered transitions leading to this state.
    */

  protected def reportOmissionError(st: state, trace: List[TraceEvent]): Unit = {
    val report = OmissionErrorReport(monitorName, st.getName, st.getInstanceIdString, trace)
    createErrorReport(report)
  }

  /**
    * Reports an error. This is supposed to be called by the user.
    *
    * @param message text string explaining the error. This will be printed as part of the
    *                error message.
    */

  protected def reportError(message: String): Unit = {
    val report = UserErrorReport(
      monitorName,
      context.currentState.getName,
      Monitor.eventNumber,
      context.currentEvent.toString,
      context.currentState.getInstanceIdString,
      context.currentTrace,
      message)
    createErrorReport(report)
  }

  /**
    * Creates an error report by counting up the error count, recording the report,
    * calling the `callback()` method, and potentially stops monitoring
    * if `STOP_ON_ERROR` is true.
    *
    * @param report the error report.
    */

  protected def createErrorReport(report: Report): Unit = {
    if (DautOptions.PRINT_ERROR_BANNER) {
      println(
        s"""
           |███████╗██████╗ ██████╗  ██████╗ ██████╗
           |██╔════╝██╔══██╗██╔══██╗██╔═══██╗██╔══██╗
           |█████╗  ██████╔╝██████╔╝██║   ██║██████╔╝
           |██╔══╝  ██╔══██╗██╔══██╗██║   ██║██╔══██╗
           |███████╗██║  ██║██║  ██║╚██████╔╝██║  ██║
           |╚══════╝╚═╝  ╚═╝╚═╝  ╚═╝ ╚═════╝ ╚═╝  ╚═╝
           |
        """.stripMargin)
    }
    println(s"\n$report")
    addReport(report)
    errorCount += 1
    errorCountDueToLatestEvent += 1
    callBack()
    if (STOP_ON_ERROR) {
      println("\n*** terminating on first error!\n")
      throw MonitorError()
    }
  }

  /**
    * Reports a transition leading to `ok`.
    *
    * @param st    the state in which the success occurred.
    * @param event the triggering event causing the success.
    * @param trace the trace leading to the success. Includes only events that
    *              triggered transitions leading to this state.
    * @param msg   a user provided optional message.
    */

  protected def reportTransitionOk(st: state, event: E, trace: List[TraceEvent], msg: Option[String]): Unit = {
    val instanceIdString = getInstanceIdStringFromStateOrEvent(st, event)
    val report = TransitionOkReport(monitorName, st.getName, Monitor.eventNumber, event.toString, instanceIdString, trace, msg)
    println(s"\n$report")
    addReport(report)
  }

  /**
    * Reports that a transition has taken place, caused by an event in a given state.
    * The method is only called when `DautOptions.SHOW_TRANSITIONS` is true.
    *
    * @param st    the state in which the transition takes place.
    * @param event the triggering event causing the transition.
    */

  protected def reportTransition(st: state, event: E): Unit = {
    val shownEvent = renderEventAs(event) match {
      case None => event.toString
      case Some(s) => s
    }
    val instanceIdString = getInstanceIdStringFromStateOrEvent(st, event)
    val report = TransitionReport(monitorName, st.getName, Monitor.eventNumber, shownEvent, instanceIdString)
    val coloredReport = Monitor.eventColoring.colorString(monitorName, report.toString)
    println(coloredReport)
    addReport(report)
  }
}

/**
  * Used for coloring events that are printed on standard out when they
  * trigger transitions. The coloring is automated, assuming that events
  * are objects of case classes. If they are not, the color is the default
  * green. The color chosen for an event depends on the (simple) name of
  * the corresponding case class. The coloring rotates amongst a predefined
  * collection of colors, in a circular manner, so one color can potentially
  * be used for events of different case classes.
  */

class Coloring {
  /**
    * The colors
    */
  private val RESET = "\u001B[0m"
  private val BLACK = "\u001B[30m"
  private val RED = "\u001B[31m"
  private val GREEN = "\u001B[32m"
  private val YELLOW = "\u001B[33m"
  private val BLUE = "\u001B[34m"
  private val PURPLE = "\u001B[35m"
  private val CYAN = "\u001B[36m"
  private val WHITE = "\u001B[37m"

  /**
    * Mapping from strings to colors.
    */
  private var colorMap: Map[String, String] = Map()

  /**
    * The list of available colors and its length.
    */
  private var colors: Array[String] = Array(RED, GREEN, YELLOW, BLUE, PURPLE, CYAN)
  private var numberOfColors = colors.length

  /**
    * The current next color is at this index.
    */
  private var colorIndex: Int = 0

  /**
    * Returns the next color pointed to by `colorIndex` and counts this index up modulo
    * the length of the color list.
    *
    * @return the next color.
    */
  private def nextColor(): String = {
    val color = colors(colorIndex)
    colorIndex = (colorIndex + 1) % numberOfColors
    color
  }

  /**
    * Returns the color for a string. The same color will be returned
    * for the same string. A string is typically a name, such as e.g.
    * a monitor name.
    *
    * @param string the string to be associated with a color.
    * @return the color for the string.
    */

  def colorForString(string: String): String = {
    colorMap.get(string) match {
      case Some(color) =>
        color
      case None =>
        val color = nextColor()
        colorMap = colorMap + (string -> color)
        color
    }
  }

  /**
    * Colors a string according to to the color of a name.
    *
    * @param name   the name controlling what color the string is colored with.
    * @param string the string to be colored.
    * @return the colored string.
    */

  def colorString(name: String, string: String): String = {
    val color = colorForString(name)
    s"$color$string$RESET"
  }
}

/**
  * The Monitor object supporting the Monitor class, with what otherwise would
  * be called static methods in Java for example.
  */

object Monitor {
  /**
    * Keeps track of what color is associated with each monitor, which
    * is used for printing transitions when `DautOptions.SHOW_TRANSITIONS` is true.
    */

  private val eventColoring: Coloring = Coloring()

  /**
    * Number of event currently verified. First event gets number 1.
    */

  var eventNumber: Long = 0

  /**
    * Set to true when an incoming event triggers a transition in
    * a (sub) monitor. Is used to control whether to print out statys
    * of (sub) monitors in DEBUG mode. It is set to false at the beginning
    * of processing each new event.
    */

  var transitionTriggeredInSomeMonitor: Boolean = false

  /**
    * Name of online jsonl file
    */

  private var onlineFileName: String = null

  /**
    * File writer object for opened jsonl file for online writing of reports.
    */

  private var onlineFileWriter: BufferedWriter = null

  /**
    * Opens a jsonl file for writing result reports online as monitoring progresses.
    * This file can then be piped into another application for further processing.
    *
    * @param fileName the jsonl file to write to.
    */

  def openOnlineFile(fileName: String): Unit = {
    if (!fileName.endsWith(".jsonl")) {
      stopExecution(s"File $fileName does not end with .jsonl")
    }
    try {
      onlineFileName = fileName
      onlineFileWriter = new BufferedWriter(new FileWriter(fileName, false))
    } catch {
      case e: java.io.IOException =>
        stopExecution(s"An error occurred while trying to open the file $fileName: ${e.getMessage}")
      case e: Exception =>
        stopExecution(s"An unexpected error occurred: ${e.getMessage}")
    }
  }

  /**
    * Write a json report to the online jsonl file, if it has been opened.
    *
    * @param json the json object to write.
    */

  def writeOnlineFile(json: Json) : Unit = {
    if (onlineFileWriter != null) {
      try {
        onlineFileWriter.write(json.noSpaces)
        onlineFileWriter.newLine()
        onlineFileWriter.flush()
      } catch {
        case e: Exception =>
          println(s"An unexpected error when writing to online file $onlineFileName: ${e.getMessage}")
          stopExecution()
      }
    }
  }
}

/**
  * This monitor class provides methods for performing abstraction in addition
  * to performing verification. The result of a verification is a new trace.
  *
  * @tparam E the type of events submitted to the monitor.
  */

class Abstract[E] extends Monitor[E] {
  private val abstraction = new scala.collection.mutable.ListBuffer[E]()

  /**
    * If true all events consumed are copied to the abstraction.
    */

  private var recordAll: Boolean = false

  /**
    * Determines whether all consumed events are copied to the abstraction trace.
    *
    * @param flag if true, consumed events are copied to the abstraction trace.
    * @return the monitor itself.
    */

  def record(flag: Boolean): Abstract[E] = {
    recordAll = flag
    this
  }

  /**
    * Adds event to the abstraction trace.
    *
    * @param event event to be added to abstraction trace.
    */

  def push(event: E): Unit = {
    abstraction += event
  }

  /**
    * Returns the produced abstraction.
    *
    * @return the abstraction.
    */

  def trace: List[E] = abstraction.toList

  /**
    * Verifies an event, first adding the event to the abstraction if
    * `recording(true)` has been called. Calls `verify(event)` of the superclass.
    *
    * @param event   the submitted event.
    * @param eventNr number of event is counted externally.
    * @return this monitor (allowing method chaining).
    */

  override def verify(event: E, eventNr: Long = 0): this.type = {
    if (recordAll) push(event)
    super.verify(event)
  }
}

/**
  * This monitor class provides methods for performing abstraction in addition
  * to performing verification. The result of a verification is a new trace.
  *
  * @tparam E1 the type of events submitted to the monitor.
  */

class Translate[E1, E2] extends Monitor[E1] {
  /**
    * Contains the abstraction being produced.
    */

  private val abstraction = new scala.collection.mutable.ListBuffer[E2]()

  /**
    * Adds event to the abstraction trace.
    *
    * @param event event to be added to abstraction trace.
    */

  def push(event: E2): Unit = {
    abstraction += event
  }

  /**
    * Returns the produced abstraction.
    *
    * @return the abstraction.
    */

  def trace: List[E2] = abstraction.toList
}
