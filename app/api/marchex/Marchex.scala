package bravo.api.marchex

import scalaz._
import scalaz.\/._
import Scalaz._
import java.net._
import java.util.HashMap
import scala.collection.JavaConversions._
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client._
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import bravo.core.Util._

object Marchex {
  // PUBLIC API
  def getGroups(accountid: String)(implicit c: MarchexCredentials): BravoM[List[MarchexGroup]] = 
    (for {
      results <- makeCall("group.list", List[Object](accountid))
    } yield { 
      results.map(o => parseGroup(o.asInstanceOf[HashMap[String,Object]]).toBravoM).toList.sequenceU
    }).flatMap(identity)
  
  def getAccounts(implicit c: MarchexCredentials): BravoM[List[MarchexAccount]] = 
    (for {
      results <- makeCall("acct.list", List[Object]())
    } yield 
      results.map(o => parseAccount(o.asInstanceOf[HashMap[String,Object]]).toBravoM).toList.sequenceU //saves us a type lambda with the normal sequence
    ).flatMap(identity)

  val frmt = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z")
  
  def getCallLogs(acctid: String, start: DateTime, end:DateTime)(implicit c: MarchexCredentials): BravoM[List[CallLog]] = {
    (for {
      search <- fctry(Map("start" -> start.toString(frmt),"end"  -> end.toString(frmt)))
      results <- makeCall("call.search", List[Object](acctid, search))
    } yield {
      results.map(o => parseCallLog(o.asInstanceOf[HashMap[String,Object]]).toBravoM).toList.sequenceU
    }).flatMap(identity)
  }

  //This is for roundtrips back to a format for XML-RPC.  We're using this for mocking/testing
  def callLogToMap(c: CallLog): Map[String,Object] = {
    val frmt = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z")
    Map[String,Object](
    "acct" -> c.account,
    "assigned_to" -> c.assignedto,
    "call_id" -> c.callid,
    "call_start" -> c.callstart.toDate,
    "call_status" -> c.callstatus,
    "call_end" -> c.callend.toDate,
    "caller_name" -> c.callername,
    "cmpid" -> c.cmpid,
    "disposition" -> c.disposition,
    "forwardno" -> c.forwardnumber,
    "grpid" -> c.groupid,
    "inboundno" -> c.inboundnumber,
    "keyword" -> c.keyword,
    "rating" -> c.rating,
    "recorded" -> c.recorded.asInstanceOf[Object],
    "ring_duration" -> c.ringduration.asInstanceOf[Object]
    )
  }

  //END PUBLIC API

  private def makeCall(methodcall: String, params: List[Object]): BravoM[Array[Object]] = ((c: Config) => {
    val config = new XmlRpcClientConfigImpl()
    config.setServerURL(new URL(c.marchexurl))
    config.setBasicUserName(c.marchexuser)
    config.setBasicPassword(c.marchexpass)
    val client = new XmlRpcClient()
    client.setConfig(config)
    config.setEncoding("ISO-8859-1")
    client.setTypeFactory(new TypeFactoryMarchexIso8601(client))
    client.setTransportFactory(new XmlRpcSunHttpTransportFactory(client));
    client.execute(methodcall, params.toArray).asInstanceOf[Array[Object]]
  }).toBravoM

  
  private def parseCallLog(implicit hm: HashMap[String,Object]): \/[JazelError, CallLog] = {
    import bravo.api.marchex.DataCaster._ 
    for {
      acct <- getVal[String]("acct")
      assigned_to <- getVal[String]("assigned_to")
      call_id <- getVal[String]("call_id")
      call_start <- getVal[DateTime]("call_start")
      call_status <- getVal[String]("call_status")
      call_end <- getVal[DateTime]("call_end")
      caller_name <- getVal[String]("caller_name")
      caller_number <- getVal[String]("caller_number")
      cmpid   <- getVal[String]("cmpid")
      disposition <- getVal[String]("disposition")
      forwardno <- getVal[String]("forwardno")
      grpid   <- getVal[String]("grpid")
      inbound_ext <- getVal[String]("inbound_ext")
      inboundno <- getVal[String]("inboundno")
      keyword <- getVal[String]("keyword")
      rating  <- getVal[String]("rating")
      recorded <- getVal[Boolean]("recorded")
      ringdur <- getVal[Int]("ring_duration")
    } yield 
      CallLog(acct, assigned_to, call_id, call_start, call_status, call_end, caller_name, caller_number, cmpid, disposition, forwardno, grpid, inbound_ext, inboundno, keyword, rating, recorded, ringdur)
  }

    
  private def parseAccount(implicit hm: HashMap[String,Object]): \/[JazelError, MarchexAccount] = 
    for {
       acct <- getVal[String]("acct") 
       customid <- getVal[String]("customid")
       status <- getVal[String]("status")
       name <- getVal[String]("name")
    } yield 
        MarchexAccount(acct, customid, status, name)  
  
  private def parseGroup(implicit hm: HashMap[String,Object]): \/[JazelError, MarchexGroup] = 
    for {
      grpid <- getVal[String]("grpid")
      name <- getVal[String]("name")
      descr <- getVal[String]("descr")
    } yield MarchexGroup(grpid, name, descr)

  private def getVal[A](k: String)(implicit m: HashMap[String,Object], dc: DataCaster[A]): \/[JazelError,A] = 
    for {
      o <- \/.fromTryCatchNonFatal(m.get(k)).mapJazelError
      a <- \/.fromTryCatchNonFatal(dc.cast(o)).mapJazelError
    } yield 
      a
}

