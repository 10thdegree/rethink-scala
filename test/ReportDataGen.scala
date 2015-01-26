package bravo

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

object ReportDataGen {
  
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

/*
def sampleRawRow() = Map(
    "Paid Search Campaign" -> "Brand",
    "Date" -> "2014-01-01") ++ sampleNumericRow()

  def sampleNumericRow() = Map(
    "Paid Search Cost" -> BigDecimal(201.03),
    "Paid Search Impressions" -> BigDecimal(6000),
    "Paid Search Clicks" -> BigDecimal(20),
    "TUI  Home Page : Arrival: Paid Search Actions" -> BigDecimal(10),
    "TUI Confirmation : PHD Request Info: Paid Search Actions" -> BigDecimal(5),
    "TUI Apply Online : Step 0 - New Application: Paid Search Actions" -> BigDecimal(1)
  )
*/

  def sampleReportGen: Gen[SDartReport] = for {
      adType <- Gen.alphaStr //arbitrary[String] 
      mediaCost <- arbitrary[Float]
      creativeGrp1 <- Gen.alphaStr //arbitrary[String]
      creativeGrp2 <- Gen.alphaStr //arbitrary[String]
      date      <- arbitrary[Long]
      clicks     <- arbitrary[Int]
      impressions<- arbitrary[Int]
    } yield {
      SDartReport(adType, mediaCost, creativeGrp1, creativeGrp2, new DateTime(date), clicks, impressions)
    }
  
  case class SDartReport  (
    adType: String,
    mediaCost: Float,
    creativeGrp1: String,
    creativeGrp2: String,
    date: DateTime,
    clickThroughConversions: Int,
    impressions: Int) extends SampleReport {
    
    def toMap: Map[String,String] = Map("adType" -> adType, "mediaCost" -> ss(mediaCost), "creativeGrp1" -> creativeGrp1, "creativeGrp2"-> creativeGrp2, "date" -> ss(date), "clickThroughConversions" -> ss(clickThroughConversions), "impressions" -> ss(impressions))
  }

  case class RawDartReport(data: String)

  trait SampleReport {
    def toMap: Map[String,String]

  def ss[A](a: A): String =
      Option(a).fold("")(o => o.toString())

  }

}
