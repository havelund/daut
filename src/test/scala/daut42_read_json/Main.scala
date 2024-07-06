package daut42_read_json

import scala.collection.mutable.ListBuffer
import org.json4s._
import org.json4s.native.JsonMethods._
import daut.{Monitor}
import scala.io.Source

/****************/
/* JSON Parsing */
/****************/

def jValueToList(value: JValue): List[JValue] =
  value match
    case JArray(items) => items
    case _ => assert(false, s"$value is not a JArray")

def jValueToJObject(value: JValue): JObject = {
  value match {
    case obj: JObject => obj
    case _ => assert(false, s"$value is not a JObject")
  }
}

def jValueToInt(value: JValue): Int = {
  value match {
    case JInt(num) => num.toInt
    case _ => assert(false, s"$value is not an Int")
  }
}

def jValueToString(value: JValue): String = {
  value match {
    case JString(s) => s
    case _ => assert(false, s"$value is not a String")
  }
}

def parseJsonEvent(jValue: JValue): Event =
  val jObj = jValueToJObject(jValue)
  val id = jObj \ "id"
  id.values match
    case "dispatch" =>
      val taskIdJValue = jObj \ "task_id"
      val cmdNrJValue = jObj \ "cmd_nr"
      val cmdTypeJValue = jObj \ "cmd_type"
      val taskId: Int = jValueToInt(taskIdJValue)
      val cmdNr: Int = jValueToInt(cmdNrJValue)
      val cmdType: String = jValueToString(cmdTypeJValue)
      Dispatch(taskId, cmdNr, cmdType)
    case "reply" =>
      val taskIdJValue = jObj \ "task_id"
      val cmdNrJValue = jObj \ "cmd_nr"
      val cmdTypeJValue = jObj \ "cmd_type"
      val taskId: Int = jValueToInt(taskIdJValue)
      val cmdNr: Int = jValueToInt(cmdNrJValue)
      val cmdType: String = jValueToString(cmdTypeJValue)
      Reply(taskId, cmdNr, cmdType)
    case "complete" =>
      val taskIdJValue = jObj \ "task_id"
      val cmdNrJValue = jObj \ "cmd_nr"
      val cmdTypeJValue = jObj \ "cmd_type"
      val taskId: Int = jValueToInt(taskIdJValue)
      val cmdNr: Int = jValueToInt(cmdNrJValue)
      val cmdType: String = jValueToString(cmdTypeJValue)
      Complete(taskId, cmdNr, cmdType)
    case _ =>
      Other(jValue)


def parseJsonFromStdin(monitor: CommandMonitor): Unit =
  val source = Source.stdin.getLines()

  for (line <- source)
    if (line.nonEmpty)
      println(s"Read: $line")
      val jValue = parse(line)
      val event = parseJsonEvent(jValue)
      monitor.apply(event)

/**************/
/* Event Type */
/**************/

trait Event
case class Dispatch(taskId: Int, cmdNr: Int, cmdType: String) extends Event
case class Reply(taskId: Int, cmdNr: Int, cmdType: String) extends Event
case class Complete(taskId: Int, cmdNr: Int, cmdType: String) extends Event
case class Other(json: JValue) extends Event


/***********/
/* Monitor */
/***********/

/*
Requirement:
1. A START Dispatch must be followed by a START Reply,
   with the same task id and command nr.
2. No Reply should occur in between with the same task id.
3. After the Reply, a START Complete should occur, with the same
   task id and command nr.
4. After START Complete, no more START Completes should occur with
   the same task id and command nr.
 */

class CommandMonitor extends Monitor[Event] {
  always {
    case Dispatch(taskId, cmdNr, "START") =>
      hot {
        case Dispatch(`taskId`, `cmdNr`, _) => error
        case Reply(`taskId`, _, _) =>
          hot {
            case Complete(`taskId`, `cmdNr`, "START") =>
              watch {
                case Complete(`taskId`, `cmdNr`, "START") => error
              }
          }
      }
  }
}

/****************/
/* Main program */
/****************/

object Main:
  def main(args: Array[String]): Unit =
    val monitor = new CommandMonitor()
    parseJsonFromStdin(monitor)

