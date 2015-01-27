import bravo.api

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


object DartCSVParsingTest extends Properties("Dart Parsing Test") {
  import bravo.test.ReportDataGen._
  
  property("Dart Parsing roundtrip") = forAll { (r: DownloadedReport) => {
    val blah = ReportParser.parse(toDartReportString(r))
    true
    }
  }
 
}


