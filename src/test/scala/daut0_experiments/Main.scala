
package daut0_experiments

case class FlightPos(x1: Int, x2: Int, x3: Int, x4: Int, x5: Int, x6: Int, x7: Int, x8: Int)

class Config {
  def getInt(name: String): Int =  name.length
}

trait MesaMonitor {
  def verifyEvent(event: Any): Unit
}

class DautMonitor extends daut.Monitor[Any] with MesaMonitor {
  def verifyEvent(event: Any) : Unit = {
    // something happens here
    verify(event)
    // something happens here
  }
}

class sfdpsSeqOrderMonitor extends DautMonitor {
  always {
    case FlightPos(_, cs, _, _, _, _, date1, _) =>
      watch {
        case FlightPos(_, `cs`, _, _, _, _, date2, _) => {
          ensure(date2 > date1)
        }
      }
  }
}

class sfdpsSeqOrderMultiMonitor(conﬁg: Config) extends DautMonitor {
  val size = conﬁg.getInt("sub−monitor−count") - 1
  val ms = for(i <- 0 to size) yield new sfdpsSeqOrderMonitor
  monitor(ms: _ * )
}

object Main {
  def main(args: Array[String]): Unit = {
    val m = new sfdpsSeqOrderMultiMonitor(new Config)
    m.verify(FlightPos(0,1,0,0,0,0,10,0))
    m.verify(FlightPos(0,2,0,0,0,0,20,0))
    m.verify(FlightPos(0,1,0,0,0,0,30,0))
    m.verify(FlightPos(0,2,0,0,0,0,40,0))
    m.verify(FlightPos(0,3,0,0,0,0,50,0))
  }
}