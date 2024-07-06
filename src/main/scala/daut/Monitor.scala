package daut

import scala.collection.mutable.{Map => MutMap}
import scala.language.{implicitConversions, reflectiveCalls}
import java.io.{BufferedWriter, FileWriter, PrintWriter}

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
    * When true will cause a big ERROR banner to be printed
    * on standard out upon detection of a specification violation, making it easier
    * to quickly see that an error occurred amongst other large output.
    * The default value is false.
    */

  var PRINT_ERROR_BANNER = false
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
}

import daut.Util._

/**
  * If the `STOP_ON_ERROR` flag is set to true, a `MonitorError` exception is thrown
  * if a monitor's specification is violated by the observed event stream.
  */

case class MonitorError() extends RuntimeException

/**
  * Any monitor must sub-class this class. It provides all the DSL constructs for writing
  * a monitor.
  *
  * @tparam E the type of events submitted to the monitor.
  */

class Monitor[E] {
  thisMonitor =>

  /**
    * Used to record the initial event that causes a monitor state to track a behavior.
    * Is printed out when an error is detected.
    *
    * @param eventNr number of event.
    * @param event the event.
    */

  case class InitialEvent(eventNr: Long, event: E)

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
        if (SHOW_TRANSITIONS || Monitor.SHOW_TRANSITIONS) {
          println(s"@[${monitorName}] $event")
        }
        Monitor.logTransition(event)
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

  private val monitorName = this.getClass.getSimpleName

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

  private var abstractMonitors: List[Monitor[_]] = List()

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

  private var errorCount = 0

  /**
    * Number of event currently verified. First event gets number 1.
    */

  var eventNumber: Long = 0

  /**
    * Messages recorded by calls of the `record` method.
    */

  private var recordings: List[String] = List()

  /**
    * Option, which when set to true will cause monitoring to stop the first time
    * a specification violation is encountered. Otherwise monitoring will continue.
    * Default value is false.
    */

  def record(message: String): Unit = {
    recordings = recordings :+ s"- Recording [${monitorName}] $message"
  }

  var STOP_ON_ERROR: Boolean = false

  /**
    * Option, which when set to true will cause events to be printed that trigger transitions in this monitor.
    * Default value is false.
    */

  var SHOW_TRANSITIONS: Boolean = false

  /**
    * When called with `flag` being true, events that trigger transitions in this monitor,
    * and all sub monitors, will be automatically printed.
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

    protected var name: String = "anonymous"

    /**
      * The transitions out of this state, represented as an (initially empty) partial
      * function from events to sets of states.
      */

    private[daut] var transitions: Transitions = noTransitions

    /**
      * True iff. the transition function of the state has been initialized.
      * Used to determine with versions of always, watch, etc, to call:
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
      * Each state is associated with an initial event that caused the sequence
      * of states, of which this is part, to be generated. It is a form of simple
      * error trace.
      */

    var initialEvent: Option[InitialEvent] = None

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

    infix def watch(ts: Transitions): state = {
      if (transitionsInitialized) return thisMonitor.watch(ts)
      transitionsInitialized = true
      name = "watch"
      transitions = ts
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

    infix def always(ts: Transitions): state = {
      if (transitionsInitialized) return thisMonitor.always(ts)
      transitionsInitialized = true
      name = "always"
      transitions = ts andThen (_ + this)
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

    infix def hot(ts: Transitions): state = {
      if (transitionsInitialized) return thisMonitor.hot(ts)
      transitionsInitialized = true
      name = "hot"
      transitions = ts
      isFinal = false
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

    infix def wnext(ts: Transitions): state = {
      if (transitionsInitialized) return thisMonitor.wnext(ts)
      transitionsInitialized = true
      name = "wnext"
      transitions = ts orElse { case _ => error }
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

    infix def next(ts: Transitions): state = {
      if (transitionsInitialized) return thisMonitor.next(ts)
      transitionsInitialized = true
      name = "next"
      transitions = ts orElse { case _ => error }
      isFinal = false
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

    infix def unless(ts1: Transitions): Object {def watch(ts2: Transitions): state} = new {
      def watch(ts2: Transitions): state = {
        if (transitionsInitialized) return thisMonitor.unless(ts1).watch(ts2)
        transitionsInitialized = true
        name = "until"
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

    infix def until(ts1: Transitions): Object {def watch(ts2: Transitions): state} = new {
      def watch(ts2: Transitions): state = {
        if (transitionsInitialized) return thisMonitor.until(ts1).watch(ts2)
        transitionsInitialized = true
        name = "until"
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

    // Original version before initialEvent was introduced:
    // ====================================================
    // def apply(event: E): Option[Set[state]] =
    //   if (transitions.isDefinedAt(event))
    //     Some(transitions(event)) else None

    def apply(event: E): Option[Set[state]] =
      if (transitions.isDefinedAt(event)) {
        val theInitialEvent: Option[InitialEvent] = initialEvent match {
          case None => Some(InitialEvent(eventNumber, event))
          case _ => initialEvent
        }
        val newStates = transitions(event)
        for (ns <- newStates) {
          ns match {
            case `error` => reportErrorOnEvent(event, this.initialEvent)
            case `ok` | `stay` =>
            case ns =>
              if (!ns.isInitial) ns.initialEvent = theInitialEvent
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
      name += values.map(_.toString).mkString("(", ",", ")")
      this
    }

    /**
      * The standard `toString` method overridden.
      *
      * @return text representation of state.
      */

    override def toString: String = name
  }

  /**
    * Special state indicating that we stay in the current state. This is normally
    * achieved by none of the transitions being able to fire. However, there can be
    * situations where we want to provide a transition explicitly, to indicate that
    * we stay in the current state.
    */

  protected case object stay extends state

  /**
    * Special state indicating successful termination.
    */

  protected case object ok extends state

  /**
    * Special state indicating a specification violation.
    */

  protected case object error extends state

  /**
    * Returns an `error` state indicating a specification violation.
    *
    * @param msg message to be printed on standard out.
    * @return the `error` state.
    */

  protected def error(msg: String): state = {
    println("\n*** " + msg + "\n")
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

  protected infix def watch(ts: Transitions): anonymous = new anonymous {
    this.watch(ts)
  }

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

  protected infix def always(ts: Transitions): anonymous = new anonymous {
    this.always(ts)
  }

  /**
    * Returns a hot-state, where the transition function is the transition function provided.
    * This corresponds to a state where the monitor is just waiting (watching) until an event
    * is submitted that makes a transition fire. The state is non-final, meaning
    * that it is an error to be in this state on a call of the `end()` method.
    *
    * @param ts the transition function.
    * @return an anonymous hot-state.
    */

  protected infix def hot(ts: Transitions): anonymous = new anonymous {
    this.hot(ts)
  }

  /**
    * Returns a wnext-state (weak next), where the transition function is the transition function provided,
    * modified to yield an error if it does not fire on the next submitted event.
    * The transition is weak in the sense that a next event does not have to occur (in contrast to strong next).
    * The state is therefore final.
    *
    * @param ts the transition function.
    * @return an anonymous wnext-state.
    */

  protected infix def wnext(ts: Transitions): anonymous = new anonymous {
    this.wnext(ts)
  }

  /**
    * Returns a next-state (strong next), where the transition function is the transition function provided,
    * modified to yield an error if it does not fire on the next submitted event.
    * The transition is strong in the sense that a next event has to occur.
    * The state is therefore non-final.
    *
    * @param ts the transition function.
    * @return an anonymous next-state.
    */

  protected infix def next(ts: Transitions): anonymous = new anonymous {
    this.next(ts)
  }

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

  protected infix def unless(ts1: Transitions): Object { def watch(ts2: Transitions): state } = new {
    infix def watch(ts2: Transitions): anonymous = new anonymous {
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

  protected infix def until(ts1: Transitions): Object { def watch(ts2: Transitions): state } = new {
    infix def watch(ts2: Transitions): anonymous = new anonymous {
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

  protected def map(pf: PartialFunction[state, Set[state]]): Object { def orelse(otherwise: => Set[state]): Set[state]} = new {
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
    * on standard out.
    *
    * @param b the Boolean condition to be checked.
    */

  protected def check(b: Boolean): Unit = {
    if (!b) reportError()
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
    if (eventNr > 0) {
      eventNumber = eventNr
    } else {
      eventNumber += 1
    }
    if (initializing) initializing = false
    verifyBeforeEvent(event)
    if (monitorAtTop) debug("\n===[" + event + "]===\n")
    if (relevant(event)) {
      states.applyEvent(event)
      invariants foreach { case (e, inv) => check(inv(()), e) }
    }
    for (monitor <- monitors) {
      monitor.verify(event)
    }
    if (monitorAtTop && DautOptions.DEBUG) printStates()
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
      debug(s"Ending Daut trace evaluation for $monitorName")
      val theEndStates = states.getAllStates
      val hotStates = theEndStates filter (!_.isFinal)
      if (hotStates.nonEmpty) {
        println()
        println(s"*** Non final Daut $monitorName states:")
        println()
        for (hotState <- hotStates) {
          print(hotState)
          reportErrorAtEnd(hotState.initialEvent)
          println()
        }
      }
      for (monitor <- monitors) {
        monitor.end()
      }
      for (monitor <- abstractMonitors) {
        monitor.end()
      }
      println(s"Monitor $monitorName detected $errorCount errors!")
    }
    this
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
    * Returns the number of errors detected by the monitor, its sub-monitors, their sub-monitors.
    * etc.
    *
    * @return the number of errors of the monitor and its sub-monitors.
    */

  def getErrorCount: Int = {
    var count = errorCount
    for (m <- monitors) count += m.getErrorCount
    count
  }

  /**
    * Prints the current states of the monitor, and its sub-monitors.
    */

  def printStates(): Unit = {
    println(s"--- $monitorName:")
    println("[memory] ")
    for (s <- states.getMainStates) {
      println(s"  $s")
    }
    println
    for (index <- states.getIndexes) {
      println(s"[index=$index]")
      for (s <- states.getIndexedSet(index)) {
        println(s"  $s")
      }
    }
    println()
    for (m <- monitors) m.printStates()
  }

  /**
    * Returns all recordings for this monitor and all of its sub monitors.
    * @return the recordings.
    */

  def getRecordings(): List[String] = {
    var allRecordings : List[String] = recordings
    for (monitor <- monitors) {
      allRecordings = allRecordings ++ monitor.getRecordings()
    }
    allRecordings
  }

  /**
    * Prints error message, triggering event and current event and then calls `reportError()`.
    *
    * @param event current event.
    * @param initialEvent initially triggering event.
    */

  protected def reportErrorOnEvent(event: E, initialEvent: Option[InitialEvent]): Unit = {
    println("\n*** ERROR")
    initialEvent match {
      case None =>
      case Some(trigger) =>
        println(s"trigger event: ${trigger.event} event number ${trigger.eventNr}")
    }
    println(s"current event: $event event number $eventNumber")
    reportError()
  }

  /**
    * Prints end of trace error message, triggering event and then calls `reportError()`.
    *
    * @param initialEvent initially triggering event.
    */

  protected def reportErrorAtEnd(initialEvent: Option[InitialEvent]): Unit = {
    println("\n*** ERROR AT END OF TRACE")
    initialEvent match {
      case None =>
      case Some(trigger) =>
        println(s"trigger event: ${trigger.event} event number ${trigger.eventNr}")
    }
    reportError()
  }

  /**
    * Prints a very visible ERROR banner, in case `PRINT_ERROR_BANNER` is true.
    * Updates error count.
    */

  protected def reportError(): Unit = {
    errorCount += 1
    println(s"$monitorName error # $errorCount")
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
    callBack()
    if (STOP_ON_ERROR) {
      println("\n*** terminating on first error!\n")
      throw MonitorError()
    }
  }

  /**
    * Reports a detected error.
    *
    * @param e text string explaining the error. This will be printed as part of the
    *          error message.
    */

  protected def reportError(e: String): Unit = {
    println("***********")
    println(s"ERROR : $e")
    println("***********")
    reportError()
  }
}

/**
  * The Monitor object supporting the Monitor class, with what otherwise would
  * be called static methods in Java for example.
  */

object Monitor {
  private var jsonWriter: PrintWriter = _
  private var jsonEncoder: Any => Option[String] = _

  /**
    * Option, which when set to true will cause events to be printed that trigger transitions in any monitor.
    * Default value is false.
    */

  var SHOW_TRANSITIONS: Boolean = false

  /**
    * Opens a file for writing events that trigger transitions as JSON objects. It should be a `.jsonl` file.
    * The caller must define the function for mapping events to JSON objects. If this function returns
    * None for an event, the event is not recorded.
    *
    * @param fileName fileName name of file written to.
    * @param encoder  function mapping events to JSON strings.
    */

  def logTransitionsAsJson(fileName: String, encoder: Any => Option[String]): Unit = {
    jsonWriter = new PrintWriter(new BufferedWriter(new FileWriter(fileName, false)))
    jsonEncoder = encoder
  }

  /**
    * Returns true if `logTransitionsAsJson` has been called.
    *
    * @return true if `logTransitionsAsJson` has been called.
    */

  def isWriterInitialized: Boolean = jsonWriter != null

  /**
    * Writes an object as a Json object to the opened `.jsonl` file.
    *
    * @param obj the object to write as a JSON object.
    */

  def logTransition(obj: Any): Unit = {
    if (isWriterInitialized) {
      val json = jsonEncoder(obj)
      json match {
        case None =>
        case Some(string) =>
          jsonWriter.println(string)
          jsonWriter.flush()
      }
    }
  }

  /**
    * Closes the previously opened `.jsonl` file.
    */

  def closeJsonFile(): Unit = {
    if (jsonWriter != null) {
      jsonWriter.close()
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
