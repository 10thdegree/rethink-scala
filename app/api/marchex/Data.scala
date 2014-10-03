package bravo.api.marchex
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat


trait DataCaster[A] {
  def cast(o: Object): A
}

object DataCaster {
  implicit def StringCaster: DataCaster[String] = new DataCaster[String] {
    def cast(o: Object) = o.asInstanceOf[String]
  }
  
  implicit def IntCaster: DataCaster[Int] = new DataCaster[Int] {
    def cast(o: Object) = o.asInstanceOf[Int]
  }

  implicit def DateCaster: DataCaster[DateTime] = new DataCaster[DateTime] {
    def cast(o: Object) = new DateTime(o.asInstanceOf[java.util.Date])
  }

  implicit def BoolCaster: DataCaster[Boolean] = new DataCaster[Boolean] {
    def cast(o: Object) = o.asInstanceOf[Boolean]
  }
}

object MarchexCredentials {
  implicit def defaultC = MarchexCredentials("http://api.voicestar.com/api/xmlrpc/1", "urp@10thdegree.com", "10thdegreee")
}
sealed trait MarchexData 

case class MarchexAccount(account: String, customerid: String, status: String, name: String) extends MarchexData

case class MarchexGroup(groupid: String, name: String, description: String)

case class MarchexAdCampaign(name: String, cmpid: String, customid: String, desc: String, inboundno: String)

case class CallLog(
  acct: String, 
  assigned_to: String, 
  call_id: String, 
  call_start: DateTime, 
  call_status: String, 
  call_end: DateTime, 
  caller_name: String, 
  caller_number: String, 
  cmpid: String, 
  disposition: String, 
  forwardno: String, 
  grpid: String, 
  inbound_ext: String, 
  indoundno: String, 
  keyword: String, 
  rating: String, 
  recorded: Boolean, 
  ring_duration: Int)

case class MarchexCredentials(url: String, user: String, pass: String)


