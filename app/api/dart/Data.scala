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

  def getDartAuth: BravoM[Dfareporting]
  
  def viewDartReports(r: Dfareporting, userid: Int): BravoM[List[AvailableReport]]

  def updateDartReport(r: Dfareporting, userid: Int, rid: Long, s: DateTime, e: DateTime): BravoM[Unit]

  def runDartReport(r: Dfareporting, userid: Int, rid: Long): BravoM[Long]

  def downloadReport(r: Dfareporting, rid: Long, fid: Long): BravoM[String]

  protected def toGoogleDate(dt: DateTime): com.google.api.client.util.DateTime =  
    new com.google.api.client.util.DateTime(dt.toString(formatter)) 
}

object Data {
  import org.joda.time.DateTime
  
  sealed trait DartReportData {
    def reportid: Long
  }

  case class AvailableReport(reportid: Long, name: String, format: String, filename: String, startDate: DateTime, endDate: DateTime) extends DartReportData

  case class DownloadedReport(reportid: Long, data: List[Map[String,String]]) extends DartReportData //TODO: nomore list, should be traversable

  case class GoogleAuthCred(filepath: String, accountId: String,  userAccount: String)
}


