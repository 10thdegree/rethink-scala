package bravo.api.dart

import scalaz._
import scalaz.Free._
import Scalaz._
import org.joda.time.DateTime

sealed trait DartReportData {
  def reportid: Long
}

//TODO: do we need dimensions?
case class AvailableReport(reportid: Long, name: String, format: String, filename: String, startDate: DateTime, endDate: DateTime) extends DartReportData

case class DownloadedReport(reportid: Long, data: String) extends DartReportData

sealed trait DartRequest[A] 

case class ListReports[B](clientId: Int, b: B) extends DartRequest[List[AvailableReport]]

case class CreateReport[B](clientId: Int, fields: String, b: B) extends DartRequest[AvailableReport]

case class GetReport[B](clientId: Int, reportId: Int, startDate: DateTime, endDate: DateTime, b: B) extends DartRequest[DownloadedReport]
//THOUGHTS:

/*
Instead of polling for specific reports, we hsould just havea a queue of reports waiting to be fulfilled, get all the file handles, and then check them all at once? or no?

*/

object Dart {
  import com.google.api.services.dfareporting.{Dfareporting, DfareportingScopes} 
  import scala.collection.JavaConversions._
  import com.google.api.services.dfareporting.model._
  import java.io.InputStream
  import bravo.core.util.Util._
  import scala.concurrent.Future
  import scala.concurrent.ExecutionContext.Implicits.global
  
  def listReports(clientId: Int): Free[DartRequest, List[AvailableReport]]=  ???// Free.Suspend(

  def getReport(clientId: Int, reportId: Int, startDate: DateTime, endDate: DateTime): Free[DartRequest, DownloadedReport] = ???// Free.Suspend(

  /*
  @tailrec
  def runFreeTest(Free)(repRunner): Task[Blah] = {
    def runFreeThrough(rp: Option[Dfareporting]): Task[Blah] = {
      free match {
        case req =>
        case getReport =>  
    }
  }
  */

  type DartM[A] = EitherT[Future, JazelError, A]

  def viewDartReports(reportApi: Dfareporting, userid: Int, rid: Int ): DartM[List[AvailableReport]] = 
    for {
      reports <- ftry( reportApi.reports().list(userid).execute() )  
      items   = (reports.getItems(): java.util.List[Report])
    } yield 
      items.toList.map(toAvailableReport(_))
  
  def updateDartReport(reportApi: Dfareporting, userid: Int, rid: Long, startDate: DateTime, endDate: DateTime): DartM[Unit]= { 
    for {
      report    <- ftry(reportApi.reports().get(userid, rid).execute())
      criteria  <- ftry( 
                    report.getCriteria().setDateRange(new DateRange().setStartDate(toGoogleDate(startDate)).setEndDate(toGoogleDate(endDate)))
                  )
      _         = report.setCriteria(criteria)
      _         <- ftry(reportApi.reports().update(userid, rid, report).execute())
    } yield
      ()
  }
  
  def toGoogleDate(d: DateTime): com.google.api.client.util.DateTime = 
    new com.google.api.client.util.DateTime(d.toString())  

  def runDartReport(reportApi: Dfareporting, userid: Int, rid: Long): DartM[Long] = 
    for {
      file <- ftry(reportApi.reports().run(userid, rid).setSynchronous(false).execute())
    } yield { 
      file.getId()
    }

  def downloadReport(reportApi: Dfareporting, reportid: Long, fid: Long): DartM[DownloadedReport] = 
    for {
      filehandle  <- ftry(reportApi.files().get(reportid, fid))
      file        <- ftry(filehandle.execute())
      is          <- if (file.getStatus != "REPORT_AVAILABLE") 
                      ("Report " + reportid + "is not available".toJazelError).liftJazelError[InputStream]
                    else
                      ftry(filehandle.executeMediaAsInputStream()) 
    } yield {
      val reportData = scala.io.Source.fromInputStream(is).mkString  
      DownloadedReport(reportid,reportData)
    }
    
  def toAvailableReport(r: Report):  AvailableReport = AvailableReport(r.getId(), 
    r.getName(), 
    r.getFormat(),
    r.getFileName(),
    new DateTime(r.getCriteria().getDateRange().getStartDate().toString),
    new DateTime(r.getCriteria().getDateRange().getEndDate()))
  
  def test() =
    for {
      dfa <- EitherT( Future { DartAuth.unsafeGetReporting().leftMap(_.toJazelError) } )
      _   <- updateDartReport(dfa,1297324,15641682, new DateTime().minusWeeks(1),new DateTime())
      id  <- runDartReport(dfa,1297324, 15641682) 
      downloadedReport <- downloadReport(dfa, 15641682, id)
    } yield {
      print("process the report here?")
    }
}


