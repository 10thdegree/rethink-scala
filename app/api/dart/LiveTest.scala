package bravo.api.dart
import scalaz._
import Scalaz._
import org.joda.time.DateTime
import scala.concurrent.ExecutionContext.Implicits.global
import bravo.core.Util._
import com.google.api.services.dfareporting.Dfareporting
import scala.annotation.tailrec
import bravo.api.dart.Data._
import scala.concurrent.{Future,Await}
import org.joda.time._
import bravo.api.dart.DateUtil._

import play.Logger


object LiveTest {
  //  implicit def defaultC = MarchexCredentials("http://api.voicestar.com/api/xmlrpc/1", "urp@10thdegree.com", "10thdegreee")
  case class ProdConfig(
      val api: DartInternalAPI = LiveDart, 
      val filePath: String = "conf/Bravo-44871094176f.p12",
      val accountId: String = "399851814004-9msbusp4vh24crdgrrltservs4u430uj@developer.gserviceaccount.com",
      val userAccount: String = "bravo@10thdegree.com",
      val clientId: Int =  1297324,
      val marchexpass: String = "10thdegreee",
      val marchexurl: String = "http://api.voicestar.com/api/xmlrpc/1",
      val marchexuser: String = "urp@10thdegree.com",
      val m: Map[Long, List[ReportDay]] = Map()
    ) extends Config 

    val prodConfig = new ProdConfig()

    import com.google.api.services.dfareporting.model._
    
    def prodTest(): \/[JazelError,DownloadedReport] = {
      import scala.concurrent.duration._
      import org.joda.time.format._
      
      //import  
      //r4YUcruz 3981403 //3843876 //16372298 
      val frmt = DateTimeFormat.forPattern("yyyy-mm-dd")
      val reportCall = Dart.getReport(16372298, frmt.parseDateTime("2014-10-01"), frmt.parseDateTime("2014-10-31"))
      //val reportCall = Dart.getReport(16372298, new DateTime().minusWeeks(1), new DateTime())
      //val reportCall = Dart.getReport(15641682, new DateTime().minusWeeks(1), new DateTime())
      val future = reportCall.run.run(prodConfig)
      Await.result(future, scala.concurrent.duration.Duration(30, SECONDS))._2
    }

    def prodActivitiesTest(): \/[JazelError, List[String]] = {
      import scala.concurrent.duration._
      val result = 
        for {
          dfa       <- DartAuth.getCredentialService.toBravoM
          activites <- LiveDart.getActivities(dfa, 16372298)
        } yield
          activites
        val future = result.run.run(prodConfig)
        Await.result(future, scala.concurrent.duration.Duration(30, SECONDS))._2
    }

    def saveProdTest(filename:String): Unit = {
      import java.nio.file.{Paths, Files}
      import java.nio.charset.StandardCharsets
      val report = prodTest().fold(err => "ERROR", report => ReportParser.unparse(ungroupDates(report.data))) 
      val onlyreport = ReportParser.findTable(report.split("\\r?\\n").toList,"")
      Files.write(Paths.get(filename), onlyreport.getBytes(StandardCharsets.UTF_8))
    }
}
