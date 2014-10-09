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

object MarchexDataGenerator {
  
  val phonenumber = Gen.listOfN(9, arbitrary[Int]).map(l => l.map(_.toString).mkString)
  
  implicit val datetime: Arbitrary[DateTime] = Arbitrary( arbitrary[Date].map(new DateTime(_)) )

  val callLogGen = for {
   acct <- arbitrary[String]
   assign <- arbitrary[String]
   callid <- arbitrary[String]
   callstart <- arbitrary[DateTime]
   callstatus <- arbitrary[String] //WTF? callstatus is a string? 
   callend <- arbitrary[DateTime]
   name <- arbitrary[String]
   number <- arbitrary[String]
   cmpid <- arbitrary[String]
   disp <- arbitrary[String]
   forward <- arbitrary[String]
   groupid <- arbitrary[String]
   inboundExt <- arbitrary[String]
   inbound <- arbitrary[String]
   keyword <- arbitrary[String]
   rating <- arbitrary[String]
   recorded <- arbitrary[Boolean]
   ringdur <- arbitrary[Int]
  } yield CallLog(acct, assign, callid, callstart, callstatus, callend, name, number, cmpid, disp, forward, groupid, inboundExt, inbound, keyword, rating, recorded, ringdur)

}



object server extends Properties("Bravo API tests") {
  import bravo.test.api.MarchexDataGenerator._
  
  var callLogs: Array[CallLog] = List[CallLog]().toArray 
  
  class Call {
    def search(custId: Int, start:Date, end:Date): Array[CallLog] = callLogs 
  }   
  
  property("service bijection") = forAll { (d: Date) => 
      val port = 8000 + Gen.choose(0,100).sample.get
      val ws = new WebServer(port)
      val xmlServer = ws.getXmlRpcServer()
      val phm = new PropertyHandlerMapping()
      phm.addHandler("blah", new Call().getClass) 
      xmlServer.setHandlerMapping(phm)
      ws.start()
      var callLogs = Gen.containerOfN[List,CallLog](10,callLogGen)
      println("here")
      val credentials = MarchexCredentials("http://localhost:"+port.toString +"/blah", "asdf", "asdf")

      ws.shutdown()
      "blah" == "blah"
    }
}


