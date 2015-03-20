package bravo.test

import bravo.api

import org.joda.time._
import com.google.api.services.dfareporting.Dfareporting
import bravo.api.dart.Data._
import bravo.util.Util._
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
import org.joda.time._

 
import org.specs2.scalaz._
import scalaz.scalacheck.ScalazProperties._
import scalaz.scalacheck.ScalazArbitrary._ 
import bravo.api.dart.Data._  
import ReportDataGen._
import bravo.util.DateUtil._

class ReportDayLaws extends Spec {
  import bravo.api.dart.Data._
  checkAll(equal.laws[ReportDaysWrapper])  
  checkAll(semigroup.laws[ReportDaysWrapper])
}

object DartAPITest extends Properties("Dart API test") {
   
  property("nonblocking test") = forAll { (r: DownloadedReport) => 
    val size = r.data.size
    val reportCall = Dart.getReport(444, new DateTime(), new DateTime())
    val mockedConfig = config.copy(api = internal( toDartReportString(r) ) )  
    val future = reportCall.run.run(mockedConfig).map(_._2) 
    val result = Await.result(future, scala.concurrent.duration.Duration(10, SECONDS) )
    //compare what we sent in (r.data set to what we got out)
    val boolres = result.fold(l => false, apid => {
       r.data.map(_.rows.toSet) equals apid.data.map(_.rows.toSet)
      })
    boolres
  }

  //property("test grouping") = forAll { (r: DwonloadedReport) => 
    

  //}
 
  

 /* 
  property("test Cache") = forAll { (r: DownloadedReport) =>
    if (r.data.size > 1) { 
      val (la, lb) = r.data.splitAt(r.data.size/2)
      //assert a is greater than b
    
      val startDate = r.data.head.rowDate
      val endDate  = r.data.reverse.head.rowDate
      val halfStart = la.head.rowDate

      val isordered = ((startDate compareTo endDate) == -1 ) 
      val cachedRange = DateUtil.findLargestRange(r.data, halfStart.toDateTimeAtCurrentTime, endDate.toDateTimeAtCurrentTime)
      val missingrange = DateUtil.findMissingDates(cachedRange, halfStart, endDate)
      val same = (missingrange == r.data)
      //todo: compare missing ranges and original startDates to make sure we encompass the full amount
      //println("Missing range is = " + missingrange.size + " original size = " + r.data.size)
      isordered
    } else 
      true
  }
  */
  
/*
   case class DartConfig(
    api: DartInternalAPI,
    filePath: String,
    accountId: String,
    userAccount: String,
    clientId: Int,
    reportCache: Map[Long, List[ReportDay]] = Map[Long, List[ReportDay]]())
*/  
  val config = DartConfig(internal("asdf"), "", "", "", 4)

  //mocks a download call and returns a string as a report
  def internal(s: String):DartInternalAPI = new DartInternalAPI {
   
    def getDartAuth: BravoM[DartConfig, Dfareporting] = Dart.dartMonad.point(null)
    
    def getActivities(r: Dfareporting, s: DateTime, e: DateTime): BravoM[DartConfig, List[String]] = ???

    def getAvailableReports(r: Dfareporting, advertiserId: Long): BravoM[DartConfig, List[AvailableReport]] = ???

    def updateDartReport(r: Dfareporting, userid: Int, rid: Long, s: DateTime, e: DateTime): BravoM[DartConfig, Unit] = Dart.dartMonad.point(())

    def runDartReport(r: Dfareporting, userid: Int, rid: Long): BravoM[DartConfig, Long] = Dart.dartMonad.point(1L)

    def downloadReport(r: Dfareporting, rid: Long, fid: Long): BravoM[DartConfig, String] = Dart.dartMonad.point(s)
    
    def getDimensions(r: Dfareporting, n: String, s: DateTime, e: DateTime, aid: Option[Long]): BravoM[DartConfig, List[(String, Int)]] = ???
  
    def createDartReport(r: Dfareporting, advertiserId: Long): BravoM[DartConfig, Long] = Dart.dartMonad.point(1L)
  
    def getFilesForReport(r: Dfareporting, reportId: Long): BravoM[DartConfig, List[AvailableFile]] = Dart.dartMonad.point(List[AvailableFile]())
  }  


}

