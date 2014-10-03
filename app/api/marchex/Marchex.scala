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
/*

tatic string username = "urp@10thdegree.com";
        static string password = "10thdegreee";bject Marchex {
  
*/
object Marchex {
    //makeCall(methodcall, params)
    
    //
  private def makeCall(methodcall: String, params: List[Object])(implicit c: MarchexCredentials) : Array[Object] = {
    val config = new XmlRpcClientConfigImpl();
    config.setServerURL(new URL(c.url));
    config.setBasicUserName(c.user);
    config.setBasicPassword(c.pass);
    val client = new XmlRpcClient()
    client.setConfig(config)
    config.setEncoding("ISO-8859-1");
    client.setTypeFactory(new TypeFactoryMarchexIso8601(client))
    client.setTransportFactory(new XmlRpcSunHttpTransportFactory(client));
    
    //AdCampaign [] campaigns = client.adList(grpid);
    val grpid = "CA6ph0_FxHhCSADr";
    val result = client.execute(methodcall, params.toArray).asInstanceOf[Array[Object]]
    result
  }

  def getAccounts(implicit c: MarchexCredentials): \/[Throwable, List[MarchexAccount]] = {
    try {
      val results = makeCall("acct.list", List[Object]())
      results.map(o => parseAccount(o.asInstanceOf[HashMap[String,Object]])).toList.sequenceU //saves us a type lambda with the normal sequence
    } catch {
      case ex: Exception => 
        ex.left[List[MarchexAccount]]
     }
  }
//case class CallLog(acct: String, assigned_to: String, call_id: String, call_start: DateTime, call_status: String, call_end: DateTime, caller_name: String, caller_number: String, cmpid: String, 
//disposition: String, forwardno: String, grpid: String, inbound_ext: String, indoundno: String, keyword: String, rating: String, recorded: Boolean, ring_duration: String)


  def getCallLogs(acctid: String, start: DateTime, end:DateTime)(implicit c: MarchexCredentials): \/[Throwable, List[CallLog]] = {
    try {
      
      val frmt = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z")
      val search: java.util.Map[String,String] = 
        Map("start" -> start.toString(frmt),
            "end"  -> end.toString(frmt))
      val results = makeCall("call.search", List[Object](acctid, search))
      println("got results")
      results.map(o => parseCallLog(o.asInstanceOf[HashMap[String,Object]])).toList.sequenceU
    } catch {
      case ex: Exception => 
        ex.left[List[CallLog]]
    }
  }

  private def parseCallLog(implicit hm: HashMap[String,Object]): \/[Throwable, CallLog] = 
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
      ringdur <- getVal[String]("ring_duration")
    } yield 
      CallLog(acct, assigned_to, call_id, call_start, call_status, call_end, caller_name, caller_number, cmpid, disposition, forwardno, grpid, inbound_ext, inboundno, keyword, rating, recorded, ringdur)

  private def parseAccount(implicit hm: HashMap[String,Object]): \/[Throwable, MarchexAccount] = 
    for {
       acct <- getVal[String]("acct") 
       customid <- getVal[String]("customid")
       status <- getVal[String]("status")
       name <- getVal[String]("name")
    } yield 
        MarchexAccount(acct, customid, status, name)  


  def getGroups(accountid: String)(implicit c: MarchexCredentials): \/[Throwable, List[MarchexGroup]] = {
    try {
      //val hm: HashMap[String,Object] = Map("accid" -> accountid).toMap
      val results = makeCall("group.list", List[Object](accountid))
      results.map(o => parseGroup(o.asInstanceOf[HashMap[String,Object]])).toList.sequenceU
    } catch {
      case ex: Exception =>
        ex.left[List[MarchexGroup]]
    }
  }

  def parseGroup(implicit hm: HashMap[String,Object]): \/[Throwable, MarchexGroup] = 
    for {
      grpid <- getVal[String]("grpid")
      name <- getVal[String]("name")
      descr <- getVal[String]("descr")
    } yield MarchexGroup(grpid, name, descr)

  private def getVal[A](k: String)(implicit m: HashMap[String,Object]): \/[Throwable,A] = 
    for {
      o <- \/.fromTryCatchNonFatal(m.get(k))
      a <- \/.fromTryCatchNonFatal(o.asInstanceOf[A])
    } yield 
      a
}

trait DataCaster[A] {
  def cast(o: Object): A
}

case class StringCaster() extends DataCaster[String] {
  def cast(o: Object) = o.asInstanceOf[String]
}

case class IntCaster() extends DataCaster[Int] {
  def cast(o: Object) = o.asInstanceOf[Int]
}

case class DateTimeCaster() extends DataCaster[DateTime] {
  def cast(o: Object) = DateTime.now() 
}

case class BoolCaster() extends DataCaster[Boolean] {
  def cast(o: Object) = o.asInstanceOf[Boolean]
}


object MarchexCredentials {
  implicit def defaultC = MarchexCredentials("http://api.voicestar.com/api/xmlrpc/1", "urp@10thdegree.com", "10thdegreee")
}
sealed trait MarchexData 

case class MarchexAccount(account: String, customerid: String, status: String, name: String) extends MarchexData

case class MarchexGroup(groupid: String, name: String, description: String)

case class MarchexAdCampaign(name: String, cmpid: String, customid: String, desc: String, inboundno: String)

case class CallLog(acct: String, assigned_to: String, call_id: String, call_start: DateTime, call_status: String, call_end: DateTime, caller_name: String, caller_number: String, cmpid: String, 
disposition: String, forwardno: String, grpid: String, inbound_ext: String, indoundno: String, keyword: String, rating: String, recorded: Boolean, ring_duration: String)

case class MarchexCredentials(url: String, user: String, pass: String)

//CtjSZlGyBocq2QDF//
    /*
 CtjSZlGyBocq2QDF

[XmlRpcMethod("acct.name.get")]
string acctName(string accid);

        [XmlRpcMethod("ad.list")]
                AdCampaign[] adList(string grpId);

                        //[XmlRpcMethod("acct.list")]
                                //string accountList(string accid);

                                        [XmlRpcMethod("group.list")]
                                                Group[] groupList(string accid);

                                                        [XmlRpcMethod("acct.list")]
                                                                Account[] accountList();

                                                                        [XmlRpcMethod("call.search")]
                                                                                CallLog [] callSearch(string accid, CallLogQuery search);




 String accountNumber = new String("CmyGG0sUPGcROABU");
 
         HashMap search = new HashMap();
                 csearch.put("start", "20070917T000000Z");
                         search.put("end", "20091231T000000Z");
                                 
                                  // Create an array to hold the parameters 
          Object[] params = new Object[] {accountNumber, search};


    */
