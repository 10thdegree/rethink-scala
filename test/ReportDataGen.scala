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
import org.joda.time.DateTime
import java.util.Date

object ReportDataGen {
 
 implicit val datetime: Arbitrary[DateTime] = Arbitrary( arbitrary[Date].map(new DateTime(_)) )
 
  implicit def reportDataStr: Arbitrary[RawDartReport] = Arbitrary(for {
    reports  <- arbitrary[List[SDartReport]]
    strs <- Gen.containerOf[List,String](Gen.alphaStr) 
    reportsMap = reports.map(_.toMap)
  } yield {
    reportsMap match {
      case x :: xs =>
        val headers = x.keys.toList.mkString(",")
        val values:List[String] = headers :: (xs.map(m => x.keys.map(k => m(k)).mkString(",")))
        val csvdata = values.mkString("\n")
        RawDartReport(strs.mkString("\n") + "\nReport Fields\n" + csvdata)
      case Nil => RawDartReport("")
    }
  })

  implicit def sampleReports: Arbitrary[List[SDartReport]] =   //Arbitrary(Gen.containerOf[List,SDartReport](sampleReportGen)) 
    Arbitrary(
    for {
      numb <- Gen.choose(0,1000)
      li   <- Gen.containerOfN[List,SDartReport](numb, sampleReportGen) //(numb, sampleReportGen)
    } yield 
      li)
  case class SDartReport  (
    date: DateTime,
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

  def sampleReportGen: Gen[SDartReport] = for {
      date <- arbitrary[DateTime]
      paidSCamp <- arbitrary[BigDecimal]
      paidSCost <- arbitrary[BigDecimal]
      psrchImp  <- arbitrary[BigDecimal]
      homepage  <- arbitrary[BigDecimal]
      confirm   <- arbitrary[BigDecimal]
      applyo    <- arbitrary[BigDecimal]
    } yield {
      SDartReport(date, paidSCamp, paidSCost, psrchImp, homepage, confirm, applyo)  
    }
  
  
  case class RawDartReport(data: String)

  trait SampleReport {
    def toMap: Map[String,String]

  def ss[A](a: A): String =
      Option(a).fold("")(o => o.toString())

  }

}
