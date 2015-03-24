package bravo.api.dart 

import bravo.util.DateUtil._


trait DartInternalAPI {
  import com.google.api.services.dfareporting.{Dfareporting, DfareportingScopes} 
  import com.google.api.services.dfareporting.model._
  import bravo.util.Util._
  import scala.concurrent.{Future,Promise}
  import org.joda.time.format.DateTimeFormat
  import org.joda.time._
  import bravo.api.dart.Data._

  private val formatter = DateTimeFormat.forPattern("yyyy-MM-dd")

  def getDartAuth: BravoM[DartConfig, Dfareporting]
  
  def updateDartReport(r: Dfareporting, userid: Int, rid: Long, s: DateTime, e: DateTime): BravoM[DartConfig, Unit]

  def runDartReport(r: Dfareporting, userid: Int, rid: Long): BravoM[DartConfig, Long]

  def downloadReport(r: Dfareporting, rid: Long, fid: Long): BravoM[DartConfig, String]

  def getDimensions(r: Dfareporting, n: String, s: DateTime, e: DateTime, aid: Option[Long]): BravoM[DartConfig, List[(String, Int)]]

  def createDartReport(r: Dfareporting, advertiserId: Long): BravoM[DartConfig, Long]

  def getAvailableReports(r: Dfareporting, advertiserId: Long): BravoM[DartConfig, List[AvailableReport]]
  
  def getFilesForReport(r: Dfareporting, reportId: Long): BravoM[DartConfig, List[AvailableFile]]
  
  protected def toGoogleDate(dt: DateTime): com.google.api.client.util.DateTime =  
    new com.google.api.client.util.DateTime(dt.toString(formatter)) 

}

object DartData {
  trait ReportType
  case class PaidSearch() extends ReportType
  case class Display()  extends ReportType

  case class ReportTemplate(activityIds: List[Int], dimensions: List[String], metrics: List[String])

  def getReportTemplate(reportType: ReportType): ReportTemplate = reportType match {
    case PaidSearch() => 
      val dimensions = List("dfa:campaign")
      val metrics = List("dfa:paidSearchAveragePosition", "dfa:paidSearchClickRate", "dfa:paidSearchClicks", "dfa:paidSearchImpressions", "dfa:paidSearchCost", "dfa:paidSearchVisits", "dfa:paidSearchActions")
      ReportTemplate(List(), dimensions, metrics)
    //case Display() =>
      //bad we shouldn't do this? should we have this be a trait with the parametrs nad type as a parametr?
    //  ReportTemplate(List(), List(), List()) 
  }
  
 
}



object Data {
  import org.joda.time._
  import scalaz._
  import Scalaz._
  import bravo.util.DateUtil._

  val BRAVO_PREFIX = "BRAVO-"

  case class DartConfig(
    api: DartInternalAPI,
    filePath: String,
    accountId: String,
    userAccount: String,
    clientId: Int,
    reportCache: Map[Long, List[ReportDay]] = Map[Long, List[ReportDay]]())
 
  case class DartProfile(accountName: String, user: String, accountId: Long, profileId: Long)

  sealed trait DartReportData {
    def reportid: Long
  }

  case class AvailableReport(reportid: Long, name: String, format: String, filename: String, startDate: DateTime, endDate: DateTime) extends DartReportData

  case class DownloadedReport(reportid: Long, startDate: DateTime, endDate: DateTime, data: List[ReportDay]) extends DartReportData

  case class GoogleAuthCred(filepath: String, accountId: String,  userAccount: String)
  
  case class AvailableFile(id: Long, name: String, startDate: DateTime, endDate: DateTime)

  implicit val rdOrdering = new scala.math.Ordering[ReportDay] {
    def compare(a: ReportDay, b: ReportDay): Int = 
      a.rowDate compareTo b.rowDate
  }
  
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


