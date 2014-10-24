package bravo.api.dart

import scalaz._
import scalaz.Free._
import Scalaz._
import org.joda.time.DateTime

sealed trait DartReportData {
  def reportid: Long
}
case class AvailableReport(reportid: Long, name: String, format: String, filename: String, startDate: DateTime, endDate: DateTime) extends DartReportData

case class DownloadedReport(reportid: Long, data: String) extends DartReportData


object DartFree {
  sealed trait DartRequest[A,B] 

  case class ListReports[B](clientId: Int, f: (List[AvailableReport]) => B) extends DartRequest[List[AvailableReport],B]

  case class GetReport[B](clientId: Int, reportId: Int, startDate: DateTime, endDate: DateTime, f: (DownloadedReport) => B) extends DartRequest[DownloadedReport,B]

  implicit def dartRequestFunctor[A]: Functor[({type l[a] = DartRequest[A,a]})#l] = new Functor[({ type l[a] = DartRequest[A,a]})#l] {
    def map[B,C](dr: DartRequest[A,B])(f: B => C): DartRequest[A,C] = 
     dr match {
        case lr: ListReports[B] => lr.copy(f = (l) => f(lr.f(l)))
        case gr: GetReport[B] => gr.copy(f = (r) => f(gr.f(r)))
      }
  }
}

/*
Instead of polling for specific reports, we hsould just havea a queue of reports waiting to be fulfilled, get all the file handles, and then check them all at once? or no?
//THOUGHTS:
*/

object Dart {



}

trait DartInternalAPI {
  import com.google.api.services.dfareporting.{Dfareporting, DfareportingScopes} 
  import com.google.api.services.dfareporting.model._
  import bravo.core.util.Util._
  import scala.concurrent.Future
  import org.joda.time.format.DateTimeFormat

  private val formatter = DateTimeFormat.forPattern("yyyy-MM-dd")
  
  type DartM[A] = EitherT[Future, JazelError, A]

  def viewDartReports(r: Dfareporting, userid: Int): DartM[List[AvailableReport]]

  def updateDartReport(r: Dfareporting, userid: Int, rid: Long, s: DateTime, e: DateTime): DartM[Unit]

  def runDartReport(r: Dfareporting, userid: Int, rid: Long): DartM[Long]

  def downloadReport(r: Dfareporting, rid: Long, fid: Long): DartM[DownloadedReport]

  protected def toGoogleDate(dt: DateTime): com.google.api.client.util.DateTime =  
    new com.google.api.client.util.DateTime(dt.toString(formatter)) 
}

object LiveDart extends DartInternalAPI {
  import com.google.api.services.dfareporting.{Dfareporting, DfareportingScopes} 
  import scala.collection.JavaConversions._
  import com.google.api.services.dfareporting.model._
  import java.io.InputStream
  import bravo.core.util.Util._
  import scala.concurrent.Future
  import scala.concurrent.ExecutionContext.Implicits.global
  import org.joda.time.format.DateTimeFormat

 
  override def viewDartReports(reportApi: Dfareporting, userid: Int): DartM[List[AvailableReport]] = 
    for {
      reports <- ftry( reportApi.reports().list(userid).execute() )  
      items   = (reports.getItems(): java.util.List[Report])
    } yield 
      items.toList.map(toAvailableReport(_))
  
  override def updateDartReport(reportApi: Dfareporting, userid: Int, rid: Long, startDate: DateTime, endDate: DateTime): DartM[Unit]= { 
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
 
  
  override def runDartReport(reportApi: Dfareporting, userid: Int, rid: Long): DartM[Long] = 
    for {
      file <- ftry(reportApi.reports().run(userid, rid).setSynchronous(false).execute())
    } yield { 
      file.getId()
    }

  override def downloadReport(reportApi: Dfareporting, reportid: Long, fid: Long): DartM[DownloadedReport] = 
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
     _    = Thread.sleep(2000)
     downloadedReport <- downloadReport(dfa, 15641682, id)
    } yield {
      println("id = " + id)
      print("process the report here?")
    }
}


