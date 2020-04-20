package daut

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
   * to quickly see that an error occurred amongst other output.
   * The default value is true.
   */

  var PRINT_ERROR_BANNER = true
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
    if (DautOptions.DEBUG) println(s"[dau] $msg")
  }

  /**
   * Method for timing the execution of a block of code.
   *
   * @param text  this text is printed as part of the timing information.
   * @param block the code to be executed.
   * @tparam R the result type of the block to be executed.
   * @return the result of ececution the block.
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
    private var indexedStates : Map[Any,Set[state]] = Map()

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

    def getIndexes: Set[Any] = indexedStates.keySet

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

    def initial(s : state): Unit = {
      mainStates = Set(s)
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
      val key = keyOf(event)
      key match {
        case None =>
          mainStates = applyEventToStateSet(event)(mainStates)
          for ((index,ss) <- indexedStates) {
            indexedStates += (index -> applyEventToStateSet(event)(ss))
          }
        case Some(index) =>
          val ss = indexedStates.getOrElse(index, mainStates)
          indexedStates += (index -> applyEventToStateSet(event)(ss))
      }
    }

    /**
     * Applies an event to a set of states and returns the updated set.
     * This method performs the main verification task.
     *
     * @param event the event.
     * @param states the set of states to apply the event to.
     */

    private def applyEventToStateSet(event : E)(states: Set[state]): Set[state] = {
      var statesToRemove: Set[state] = emptyStateSet
      var statesToAdd: Set[state] = emptyStateSet
      var theStates = states
      for (sourceState <- theStates) {
        sourceState(event) match {
          case None =>
          case Some(targetStates) =>
            statesToRemove += sourceState
            for (targetState <- targetStates) {
              targetState match {
                case `error` => reportError()
                case `ok` =>
                case `stay` => statesToAdd += sourceState
                case _ => statesToAdd += targetState
              }
            }
        }
      }
      theStates --= statesToRemove
      theStates ++= statesToAdd
      theStates
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
   * The name of the monitor, derived from its class name.
   */

  private val monitorName = this.getClass().getSimpleName()

  /**
   * A monitor can contain sub-monitors in a hierarchical manner. Any event submitted to the monitor will
   * also be submitted to the sub-monitors. This allows for better organization of many monitors. The effect,
   * however, is the same as defining all monitors flatly at the same level.
   */

  private var monitors: List[Monitor[E]] = List()

  /**
   * The active states of the monitor, excluding those of its sub-monitors.
   */

  private var states = new States()

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

  private var first: Boolean = true

  /**
   * Number of violation of the specification encountered.
   */

  private var errorCount = 0

  /**
   * Option, which when set to true will cause monitoring to stop the first time
   * a specification violation is encountered. Otherwise monitoring will continue.
   * Default value is false.
   */

  var STOP_ON_ERROR: Boolean = false

  /**
   * Launches the monitors provided as var-argument as sub-monitors of this monitor.
   * Being a sub-monitor has no special semantics, it is just a way of grouping
   * monitors in a hierarchical manner for organization purposes.
   *
   * @param monitors the monitors to become sub-monitors of this monitor.
   */

  def monitor(monitors: Monitor[E]*) {
    for (monitor <- monitors) {
      monitor.monitorAtTop = false
    }
    this.monitors ++= monitors
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
    invariants ::= ("", ((x: Unit) => inv))
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
    invariants ::= (e, ((x: Unit) => inv))
    check(inv, e)
  }

  /**
   * A state of the monitor.
   */

  protected trait state {
    thisState =>

    /**
     * String used to print state when not a case class. Used for anonymous states.
     */

    protected var name: String = ""

    /**
     * The transitions out of this state, represented as an (initially empty) partial
     * function from events to sets of states.
     */

    private[daut] var transitions: Transitions = noTransitions

    /**
     * This variable is true for final (acceptance) states: that is states where it is
     * acceptable to end up when the `end()` method is called. This corresponds to
     * acceptance states in standard automaton theory.
     */

    private[daut] var isFinal: Boolean = true

    /**
     * Updates the transition function to exactly the transition function provided.
     * This corresponds to a state where the monitor is just waiting (watching) until an event
     * is submitted that makes a transition fire. The state is final.
     *
     * @param ts the transition function.
     * @return the state itself, allowing for further chained method calls.
     */

    def watch(ts: Transitions): state = {
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
     *
     * @param ts the transition function.
     * @return the state itself, allowing for further chained method calls.
     */

    def always(ts: Transitions): state = {
      name = "always"
      transitions = ts andThen (_ + this)
      this
    }

    /**
     * Updates the transition function to the transition function provided.
     * This corresponds to a state where the monitor is just waiting (watching) until an event
     * is submitted that makes a transition fire. The state is non-final, meaning
     * that it is an error to be in this state on a call of the `end()` method.
     *
     * @param ts the transition function.
     * @return the state itself, allowing for further chained method calls.
     */

    def hot(ts: Transitions): state = {
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
     *
     * @param ts the transition function.
     * @return the state itself, allowing for further chained method calls.
     */

    def wnext(ts: Transitions): state = {
      name = "wnext"
      transitions = ts orElse { case _ => error }
      this
    }

    /**
     * Updates the transition function to the transition function provided,
     * modified to yield an error if it does not fire on the next submitted event.
     * The transition is strong in the sense that a next event has to occur.
     * The state is therefore non-final.
     *
     * @param ts the transition function.
     * @return the state itself, allowing for further chained method calls.
     */

    def next(ts: Transitions): state = {
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
     *
     * @param ts1 the transition function.
     * @return the state itself, allowing for further chained method calls.
     */

    def unless(ts1: Transitions) = new {
      def watch(ts2: Transitions): state = {
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
     *
     * @param ts1 the transition function.
     * @return the state itself, allowing for further chained method calls.
     */

    def until(ts1: Transitions) = new {
      def watch(ts2: Transitions): state = {
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

    def apply(event: E): Option[Set[state]] =
      if (transitions.isDefinedAt(event))
        Some(transitions(event)) else None

    if (first) {
      initial(this)
      first = false
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
     * @param values the values to be incuded in the label.
     * @return the state itself, but updated with the label.
     */

    def label(values: Any*): state = {
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
   * e.g. in invariants, either simply using the during state as a Boolean (the state is lifted to a Booelean
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
     * This method allows us, given a during-state `dur`, to write a Booelean
     * expression of the form `dur ==> condition`, meaning: if `dur` is in the interval
     * then the `condition` must hold.
     *
     * @param b the condition that must hold if the during-state is within the interval.
     * @return true if the during state is not within an interval, or if the condition `b` holds.
     */

    def ==>(b: Boolean) = {
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

    always {
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

  protected def watch(ts: Transitions) = new anonymous {
    watch(ts)
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

  protected def always(ts: Transitions) = new anonymous {
    always(ts)
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

  protected def hot(ts: Transitions) = new anonymous {
    hot(ts)
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

  protected def wnext(ts: Transitions) = new anonymous {
    wnext(ts)
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

  protected def next(ts: Transitions) = new anonymous {
    next(ts)
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

  protected def unless(ts1: Transitions) = new {
    def watch(ts2: Transitions) = new anonymous {
      unless(ts1) watch (ts2)
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

  protected def until(ts1: Transitions) = new {
    def watch(ts2: Transitions) = new anonymous {
      until(ts1) watch (ts2)
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

  protected def exists(pred: PartialFunction[state, Boolean]): Boolean = {
    val alwaysFalse : PartialFunction[state, Boolean] = { case _ => false }
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

  protected def map(pf: PartialFunction[state, Set[state]]) = new {
    def orelse(otherwise: => Set[state]): Set[state] = {
      val matchingStates = states.getAllStates filter (pf.isDefinedAt(_))
      if (!matchingStates.isEmpty) {
        (for (matchingState <- matchingStates) yield pf(matchingState)).flatten
      } else
        otherwise
    }
  }

  /**
   * Returns the state `ok` if the Boolean expression `b` is true, otherwise
   * it returns the `error` state. The mothod can for example be used as the
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

  protected def initial(s: state) {
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

  protected implicit def convState2AndState(s1: state) = new {
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

  protected implicit def conStateSet2AndStateSet(set: Set[state]) = new {
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

  protected implicit def liftBoolean(p: Boolean) = new {
    def ==>(q: Boolean) = !p || q
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
   * @param event the submitted event.
   */

  def verify(event: E): Unit = {
    verifyBeforeEvent(event)
    if (monitorAtTop) debug("\n===[" + event + "]===\n")
    states.applyEvent(event)
    invariants foreach { case (e, inv) => check(inv(), e) }
    for (monitor <- monitors) {
      monitor.verify(event)
    }
    if (monitorAtTop && DautOptions.DEBUG) printStates()
    verifyAfterEvent(event)
  }

  /**
   * Ends the monitoring, reporting on all remaining current non-final states.
   * These represent obligations that have not been fulfilled.
   */

  def end() {
    debug(s"Ending Daut trace evaluation for $monitorName")
    val theEndStates = states.getAllStates
    val hotStates = theEndStates filter (!_.isFinal)
    if (!hotStates.isEmpty) {
      println()
      println(s"*** Non final Daut $monitorName states:")
      println()
      for (hotState <- hotStates) {
        println(hotState)
        reportError()
      }
    }
    for (monitor <- monitors) {
      monitor.end()
    }
  }

  /**
   * Allows applying a monitor `M` to an event `e`, as follows: `M(e)`.
   * This has the same meaning as the longer `M.verify(e)`.
   *
   * @param event the submitted event to be verified.
   */

  def apply(event: E): Unit = {
    verify(event)
  }

  /**
   * This method is called <b>before</b> every call of `verify(event: E)`.
   * It can be overridden by user. Its body is by default empty.
   *
   * @param event the event being verified.
   */

  protected def verifyBeforeEvent(event: E) {}

  /**
   * This method is called <b>after</b> every call of `verify(event: E)`.
   * It can be overridden by user. Its body is by default empty.
   *
   * @param event the event being verified.
   */

  protected def verifyAfterEvent(event: E) {}

  /**
   * This method is called when the monitor encounters an error, be it a safety
   * error or a liveness error.
   * It can be overridden by user. Its body is by detult empty.
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

  private def printStates() {
    println(s"--- $monitorName:")
    println("[main] ")
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
   * Prints a very visible ERROR banner, in case `PRINT_ERROR_BANNER` is true.
   * Updates error count.
   */

  protected def reportError() {
    errorCount += 1
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
           |$monitorName error # $errorCount
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

