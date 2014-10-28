import bravo.api.marchex._
import org.joda.time._
import com.google.api.services.dfareporting.Dfareporting
import bravo.api.dart.Data._
import bravo.core.util.Util._
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
object DartTest extends Properties("Dart API test") {
  
  property("nonblocking test") = forAll { (i:Int) =>
   val reportCall = Dart.getReport(233, 444, new DateTime(), new DateTime())(internal())
   val result = Await.result(reportCall.run, scala.concurrent.duration.Duration(1, SECONDS) )
   true
  }

  def internal():DartInternalAPI = new DartInternalAPI {
    
    def viewDartReports(r: Dfareporting, userid: Int): BravoM[List[AvailableReport]] = ???

    def updateDartReport(r: Dfareporting, userid: Int, rid: Long, s: DateTime, e: DateTime): BravoM[Unit] = (Future { ().right[JazelError] }).toBravoM

    def runDartReport(r: Dfareporting, userid: Int, rid: Long): BravoM[Long] = (Future { 1L.right[JazelError] }).toBravoM

    def downloadReport(r: Dfareporting, rid: Long, fid: Long): BravoM[DownloadedReport] = (Future { DownloadedReport(1, "BLAH").right[JazelError] }).toBravoM
  
  }
  




}
/*
  private val formatter = DateTimeFormat.forPattern("yyyy-MM-dd")

  */

