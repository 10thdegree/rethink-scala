package bravo.api.dart

trait DartInternalAPI {
  import com.google.api.services.dfareporting.{Dfareporting, DfareportingScopes} 
  import com.google.api.services.dfareporting.model._
  import bravo.core.Util._
  import scala.concurrent.{Future,Promise}
  import org.joda.time.format.DateTimeFormat
  import org.joda.time.DateTime
  import bravo.api.dart.Data._

  private val formatter = DateTimeFormat.forPattern("yyyy-MM-dd")

  def viewDartReports(r: Dfareporting, userid: Int): BravoM[List[AvailableReport]]

  def updateDartReport(r: Dfareporting, userid: Int, rid: Long, s: DateTime, e: DateTime): BravoM[Unit]

  def runDartReport(r: Dfareporting, userid: Int, rid: Long): BravoM[Long]

  def downloadReport(r: Dfareporting, rid: Long, fid: Long): BravoM[DownloadedReport]

  protected def toGoogleDate(dt: DateTime): com.google.api.client.util.DateTime =  
    new com.google.api.client.util.DateTime(dt.toString(formatter)) 
}

object Data {
  import org.joda.time.DateTime
  
  sealed trait DartReportData {
    def reportid: Long
  }

  case class AvailableReport(reportid: Long, name: String, format: String, filename: String, startDate: DateTime, endDate: DateTime) extends DartReportData

  case class DownloadedReport(reportid: Long, data: String) extends DartReportData

  case class GoogleAuthCred(filepath: String, accountId: String,  userAccount: String)
}
  //case 

//NOT CURRENTLY USED
/*
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
*/
