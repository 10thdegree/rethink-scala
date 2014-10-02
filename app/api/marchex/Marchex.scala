package bravo.api.marchex

import scalaz._
import scalaz.\/._
import Scalaz._
import java.net._
import java.util.HashMap
import scala.collection.JavaConverters._
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client._
import org.joda.time.DateTime
/*

tatic string username = "urp@10thdegree.com";
        static string password = "10thdegreee";bject Marchex {
  
*/
object Marchex {
  private def makeCall(methodcall: String, params: List[Object], user: String, pass: String): Array[Object] =
    makeCall(methodcall, params, "http://api.voicestar.com/api/xmlrpc/1", user, pass)

  private def makeCall(methodcall: String, params: List[Object], url: String, user: String, pass: String): Array[Object] = {
    val config = new XmlRpcClientConfigImpl();
    config.setServerURL(new URL(url));
    config.setBasicUserName(user);
    config.setBasicPassword(pass);
    val client = new XmlRpcClient()
    client.setConfig(config)
    config.setEncoding("ISO-8859-1");
    //client.setTypeFacotry(new TypeFactoryIso8601(client))
    client.setTransportFactory(new XmlRpcSunHttpTransportFactory(client));
    
    //AdCampaign [] campaigns = client.adList(grpid);
    val grpid = "CA6ph0_FxHhCSADr";
    val result = client.execute(methodcall, params.toArray).asInstanceOf[Array[Object]]
    result
  }

  def getAccounts(user: String, pass: String): \/[Throwable, List[MarchexAccount]] = {
    try {
      val results = makeCall("acct.list", List[Object](), user, pass) //.asInstanceOf[Array[HashMap[String,Object]]]
      results.map(o => parseAccount(o.asInstanceOf[HashMap[String,Object]])).toList.sequenceU //saves us a type lambda with the normal sequence
    } catch {
      case ex: Exception => 
        ex.left[List[MarchexAccount]]
     }
  }

  def getCallLogs(start: DateTime, end:DateTime, user: String, pass: String): \/[Throwable, List[CallLog]] = {
    
  }

  private def parseAccount(implicit hm: HashMap[String,Object]): \/[Throwable, MarchexAccount] = 
    for {
       acct <- getVal[String]("acct") 
       customid <- getVal[String]("customid")
       status <- getVal[String]("status")
       name <- getVal[String]("name")
    } yield 
        MarchexAccount(acct, customid, status, name)  


  def getGroups(user: String, pass: String, accountid: String): \/[Throwable, List[MarchexGroup]] = {
    try {
      //val hm: HashMap[String,Object] = Map("accid" -> accountid).toMap
      val results = makeCall("group.list", List[Object](accountid), user, pass)
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
