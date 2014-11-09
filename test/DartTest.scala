import bravo.api

import org.joda.time._
import com.google.api.services.dfareporting.Dfareporting
import bravo.api.dart.Data._
import bravo.core.Util._
import scalaz._
import Scalaz._
import scala.concurrent.{Future,Await}
import scala.concurrent.duration._
import bravo.api.dart._
import scala.concurrent.ExecutionContext.Implicits.global 
import org.scalacheck.Properties
import org.scalacheck.Prop.forAll
import org.scalacheck.Prop._
import org.scalacheck._    
import Gen._               
import Arbitrary.arbitrary 

object DartAPITest extends Properties("Dart API test") {
  
  property("nonblocking test") = forAll { (i:Int) =>
   val reportCall = Dart.getReport(444, new DateTime(), new DateTime())
   val future = reportCall.run.run(config)
   val result = Await.result(future, scala.concurrent.duration.Duration(1, SECONDS) )
   true
  }

  def config(): Config = new Config {
    val api = internal()
    val  filePath = ""
    val accountId = ""
    val userAccount = ""
    val clientId = 0
  }

  def internal():DartInternalAPI = new DartInternalAPI {
   
    def getDartAuth: BravoM[Dfareporting] = Monad[BravoM].point(null)

    def viewDartReports(r: Dfareporting, userid: Int): BravoM[List[AvailableReport]] = ???

    def updateDartReport(r: Dfareporting, userid: Int, rid: Long, s: DateTime, e: DateTime): BravoM[Unit] = Monad[BravoM].point(())

    def runDartReport(r: Dfareporting, userid: Int, rid: Long): BravoM[Long] = Monad[BravoM].point(1L)

    def downloadReport(r: Dfareporting, rid: Long, fid: Long): BravoM[DownloadedReport] = Monad[BravoM].point(DownloadedReport(1, "blah")) 
  
  }

}

