package bravo.test

import org.apache.xmlrpc._
import org.scalacheck.Properties
import org.scalacheck.Prop.forAll
import org.scalacheck.Prop._
import org.scalacheck._    
import Gen._               
import Arbitrary.arbitrary 
import bravo.api.marchex._
import org.joda.time._
import java.util.Date
import scala.concurrent.{Future,Await}
import scala.concurrent.duration._
import org.apache.xmlrpc.server.XmlRpcServer
import org.apache.xmlrpc.server.XmlRpcServerConfigImpl
import org.apache.xmlrpc.webserver.WebServer
import org.apache.xmlrpc.server.PropertyHandlerMapping
import scala.collection.JavaConversions._
import bravo.core.Util._
import scalaz._
import Scalaz._

object MarchexDataGenerator {
  import bravo.test.ReportDataGen._

  case class Ascii(s: String) 

  
  val phonenumber = Gen.listOfN(9, arbitrary[Int]).map(l => l.map(_.toString).mkString)
  
    val callLogGen = for {
   acct <- Gen.alphaStr
   assign <- Gen.alphaStr
   callid <- Gen.alphaStr
   callstart <- arbitrary[DateTime]
   callstatus <- Gen.alphaStr //WTF? callstatus is a string? 
   callend <- arbitrary[DateTime]
   name <- Gen.alphaStr
   number <- Gen.alphaStr
   cmpid <- Gen.alphaStr
   disp <- Gen.alphaStr
   forward <- Gen.alphaStr
   groupid <- Gen.alphaStr
   inboundExt <- Gen.alphaStr
   inbound <- Gen.alphaStr
   keyword <- Gen.alphaStr
   rating <- Gen.alphaStr
   recorded <- arbitrary[Boolean]
   ringdur <- arbitrary[Int]
  } yield CallLog(acct, assign, callid, callstart, callstatus, callend, name, 
                  number, cmpid, disp, forward, groupid, inboundExt, inbound, keyword, rating, recorded, ringdur)
}

object MarchexAPITest extends Properties("Bravo API tests") {
  import bravo.test.MarchexDataGenerator._
  
  var callLogs: List[CallLog] = List[CallLog]() 
  
  class Call {
    def search(custId: String, m: java.util.Map[String,String]): Array[java.util.Map[String,Object]] = callLogs.map(Marchex.callLogToMap(_): java.util.Map[String,Object]).toArray
  }   
 
  private def available(port: Int): Boolean = {
    import java.io.IOException
    import java.net.DatagramSocket
    import java.net.ServerSocket
    
    var serverSocket: ServerSocket = null
    var dataSocket: DatagramSocket = null
    try {
      serverSocket = new ServerSocket(port)
      serverSocket.setReuseAddress(true)
      dataSocket = new DatagramSocket(port)
      dataSocket.setReuseAddress(true)
      return true
    } catch {
      case t: Throwable =>
        return false
    } finally {
      if (dataSocket != null) {
        dataSocket.close()
      }
      if (serverSocket != null) {
        serverSocket.close()
      }
    }
  }

  def findFreePort(): Option[Int] = {
    def inner(attempt: Int): Option[Int] = 
      (attempt > 9) match {
        case true => 
          None
        case false =>
          val portAttempt = 9000 + Gen.choose(0,1000).sample.get
          if (available(portAttempt))
            portAttempt.some
          else 
            inner(attempt+1)
      }
    inner(1)
  }
  
  property("service bijection") = forAll { (d: Date) => 
    val port = findFreePort().get 
    val ws = new WebServer(port)
    val xmlServer = ws.getXmlRpcServer()
    val phm = new PropertyHandlerMapping()
    phm.addHandler("call", new Call().getClass) 
    xmlServer.setHandlerMapping(phm)
    
    val serverConfig = xmlServer.getConfig().asInstanceOf[XmlRpcServerConfigImpl]
    serverConfig.setEncoding("ISO-8859-1");
    xmlServer.setTypeFactory(new TypeFactoryMarchexIso8601(xmlServer))
    ws.start()
    Gen.containerOfN[List,CallLog](10,callLogGen).sample.foreach(cl => {
      callLogs = cl
    })
    //val credentials = MarchexCredentials("http://localhost:"+port.toString +"/", "asdf", "asdf")
    val config = DartAPITest.TestConfig()
    
    val dt = DateTime.now()
    val result = Marchex.getCallLogs("asdf", dt.minusWeeks(1), dt)
    val future = result.run.run(config.copy(marchexurl="http://localhost:"+port))
    val either = Await.result(future, scala.concurrent.duration.Duration(30, SECONDS) )
    ws.shutdown()
    either._2.fold(l => {
      println(" l = " + l)
      false
      }, r => true)
  }
}

