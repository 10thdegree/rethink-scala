package bravo.test.api

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
import org.apache.xmlrpc.server.XmlRpcServer
import org.apache.xmlrpc.server.XmlRpcServerConfigImpl
import org.apache.xmlrpc.webserver.WebServer
import org.apache.xmlrpc.server.PropertyHandlerMapping
import scala.collection.JavaConversions._

object MarchexDataGenerator {
 
  case class Ascii(s: String) 

  
  val phonenumber = Gen.listOfN(9, arbitrary[Int]).map(l => l.map(_.toString).mkString)
  
  implicit val datetime: Arbitrary[DateTime] = Arbitrary( arbitrary[Date].map(new DateTime(_)) )

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



object server extends Properties("Bravo API tests") {
  import bravo.test.api.MarchexDataGenerator._
  
  var callLogs: List[CallLog] = List[CallLog]() 
  
  class Call {
    def search(custId: String, m: java.util.Map[String,String]): Array[java.util.Map[String,Object]] = callLogs.map(Marchex.callLogToMap(_): java.util.Map[String,Object]).toArray
  }   
  
  property("service bijection") = forAll { (d: Date) => 
    val port = 8000 + Gen.choose(0,1000).sample.get
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
    val credentials = MarchexCredentials("http://localhost:"+port.toString +"/", "asdf", "asdf")
    val dt = DateTime.now()
    val result = Marchex.getCallLogs("asdf", dt.minusWeeks(1), dt)(credentials)
    ws.shutdown()
    result.fold(error => false, logs => result == logs)
    //"blah" == "blah"
  }
}


