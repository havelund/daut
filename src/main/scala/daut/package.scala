
/**
 * Daut (Data automata) is an internal Scala DSL for writing event stream monitors. It
 * supports flavors of state machines, temporal logic, and rule-based programming, all in one
 * unified formalism. The underlying concept is that at any point during monitoring there is an
 * active set of states, the _state soup_. States can be added and removed from this soup.
 * Each state in the soup either monitors the incoming event stream, or is used by other states to record
 * data (as in rule-based programming).
 *
 * The specification language specifically supports:
 *
 *  - Automata, represented by states, parameterized with data (thereby the name Daut: Data automata).
 *  - Temporal operators which generate states, resulting in more succinct specifications.
 *  - Rule-based programming in that one can test for the presence of states and one can add states.
 *  - General purpose programming in Scala when the other specification features fall short.
 *
 * The DSL is a simplification of the TraceContract ([[https://github.com/havelund/tracecontract]]) internal Scala DSL by an order of magnitude less code.
 *
 * The general idea is to create a monitor as a class sub-classing the `Monitor` class,
 * create an instance of it, and then feed it with events with the `verify(event: Event)` method,
 * one by one, and in the case of a finite sequence of observations, finally calling the
 * `end()` method on it. If `end()` is called, it will be determined whether
 * there are any outstanding obligations that have not been satisfied (expected events that did not occur).
 *
 * This can schematically be illustrated as follows:
 *
 * {{{
 * class MyMonitor extends Monitor[SomeType] {
 * ...
 * }
 *
 * object Main {
 *   def main(args: Array[String]): Unit = {
 *     val m = new MyMonitor()
 *     m.verify(event1)
 *     m.verify(event2)
 *     ...
 *     m.verify(eventN)
 *     m.end()
 *   }
 * }
 * }}}
 */

package object daut {}
