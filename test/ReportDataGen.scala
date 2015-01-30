package bravo.test

import com.google.api.services.dfareporting.Dfareporting
import bravo.api.dart.Data._
import bravo.core.Util._
import scalaz._
import Scalaz._
import bravo.api.dart._
import org.scalacheck.Properties
import org.scalacheck.Prop.forAll
import org.scalacheck.Prop._
import org.scalacheck._    
import Gen._               
import Arbitrary.arbitrary 
import org.joda.time._
import java.util.Date
import bravo.api.dart.DateUtil._
import bravo.api.dart.Data._

object ReportDataGen {
  
  implicit val dateGen: Arbitrary[(DateTime,DateTime)] = Arbitrary(for {
    year <- Gen.choose(2010, 2020)
    month <- Gen.choose(1, 12)
    day <- Gen.choose(1, month match {
      case 2 => 28
      case 4 | 6 | 9 | 11 => 30
      case _ => 31
    })
    hour <- Gen.choose(0, 23)
    minute <- Gen.choose(0, 59)
    second <- Gen.choose(0, 59)
    start  = new DateTime(year, month, day, hour, minute, second)
    days  <- Gen.choose(1, 365) //probably the longest report someone would want
  } yield 
    (start, start.plusDays(days))
  )
  
  implicit val datetime: Arbitrary[DateTime] = Arbitrary( arbitrary[Date].map(new DateTime(_)) )

  implicit def arbSampleReport: Arbitrary[DownloadedReport] =  Arbitrary(sampleReport)
  
  implicit def sampleReport: Gen[DownloadedReport] =
    for {
      numb                <- Gen.choose(2,1000)
      (startDate,endDate) <- arbitrary[(DateTime,DateTime)]
      reportId            <- arbitrary[Long]
      li   <- Gen.containerOfN[List, DartReportRow](numb, sampleReportRowGen(startDate.getMillis(), endDate.getMillis())) //(numb, sampleReportGen)
    } yield 
       DownloadedReport(reportId, startDate, endDate, groupDates(li.map(_.toMap))) 
  
  def sampleReportRowGen(startD: Long, endD: Long) = for {
      long      <- Gen.choose(startD, endD)
      date      = new DateTime(long)
      paidSCamp <- arbitrary[BigDecimal]
      paidSCost <- arbitrary[BigDecimal]
      psrchImp  <- arbitrary[BigDecimal]
      homepage  <- arbitrary[BigDecimal]
      confirm   <- arbitrary[BigDecimal]
      applyo    <- arbitrary[BigDecimal]
    } yield {
      DartReportRow(date.toLocalDate(), paidSCamp, paidSCost, psrchImp, homepage, confirm, applyo)  
    }

  case class DartReportRow  (
    date: LocalDate,
    paidSearchCampaign: BigDecimal,
    paidSearchCost: BigDecimal,
    paidSearchImpressions: BigDecimal,
    tuiHomePage: BigDecimal, 
    tuiConfirmation: BigDecimal,
    tuiApplyOnline: BigDecimal
    ) {
      def toMap: Map[String,String] = Map("Date" -> date.toString(), "Paid Search Campaign" -> paidSearchCampaign.toString, "Paid Search Cost" -> paidSearchCost.toString, 
                                       "paidSearchImpressions"-> paidSearchImpressions.toString, "TUI Home Page" -> tuiHomePage.toString, "TUI Confirmation" -> tuiConfirmation.toString,
                                       "TUI Apply Online" -> tuiApplyOnline.toString)
    }

  case class RawDartReport(data: String)

  trait SampleReport {
    def toMap: Map[String,String]

  def ss[A](a: A): String =
      Option(a).fold("")(o => o.toString())

  }

  def toDartReportString(downloadedReport: DownloadedReport): String = 
    ungroupDates(downloadedReport.data) match {
      case x :: xs =>
        val headers = x.keys.toList.mkString(",")
        val values:List[String] = headers :: (xs.map(m => x.keys.map(k => m(k)).mkString(",")))
        val csvdata = values.mkString("\n")
        downloadedReport.reportid.toString + "\nReport Fields\n" + csvdata
      case Nil => "" 
    }
  

}
