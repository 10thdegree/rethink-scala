package bravo.api.dart

trait DartInternalAPI {
  import com.google.api.services.dfareporting.{Dfareporting, DfareportingScopes} 
  import com.google.api.services.dfareporting.model._
  import bravo.core.Util._
  import scala.concurrent.{Future,Promise}
  import org.joda.time.format.DateTimeFormat
  import org.joda.time._
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
  import org.joda.time._
  import scalaz._
  import Scalaz._

  sealed trait DartReportData {
    def reportid: Long
  }

  case class ReportDay(retrievedDate: DateTime = new DateTime(), rowDate: LocalDate, rows: List[Map[String,String]])
  
  case class AvailableReport(reportid: Long, name: String, format: String, filename: String, startDate: DateTime, endDate: DateTime) extends DartReportData

  case class DownloadedReport(reportid: Long, startDate: DateTime, endDate: DateTime, data: List[ReportDay]) extends DartReportData

  case class GoogleAuthCred(filepath: String, accountId: String,  userAccount: String)
  
  
  case class ReportDaysWrapper(data: List[ReportDay])

  implicit def toReportDaysWrapper(l: List[ReportDay]): ReportDaysWrapper = ReportDaysWrapper(l)
  
  implicit val rowsSemiGroup: Semigroup[ReportDaysWrapper] = new Semigroup[ReportDaysWrapper] {
    
    override def append(a: ReportDaysWrapper, b: => ReportDaysWrapper): ReportDaysWrapper = {
      val mergedMap = a.data.map(r => (r.rowDate,r)).toMap |+| b.data.map(r => (r.rowDate,r)).toMap //we collapse the matching keys using the ReportDay semigroup which ensures we take the 'newer' value
      mergedMap.values.toList 
    }
  }
  
  implicit val reportDaySemigroup: Semigroup[ReportDay] = new Semigroup[ReportDay] {
    
    override def append(a: ReportDay, b: => ReportDay): ReportDay = 
      a.retrievedDate compareTo b.retrievedDate match {
        case 1 => 
          b
        case -1 => 
          a
        case 0 => 
          if (a.rows.size > b.rows.size)
            a
          else
            b
      }
  }
  
  implicit val equalReportDaysWrap: Equal[ReportDaysWrapper] = new Equal[ReportDaysWrapper] {
    def equal(a: ReportDaysWrapper, b: ReportDaysWrapper) = a.data.equals(b.data)
  }

  implicit val equalReportDay: Equal[ReportDay] = new Equal[ReportDay] {
    def equal(a: ReportDay, b: ReportDay) = a.equals(b)
  }

}


