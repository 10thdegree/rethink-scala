package reporting.core.dataproviders

import org.joda.time.DateTime
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc._
import reporting.core._
import bravo.util.Util._
import scala.concurrent.{ ExecutionContext, Future }
import bravo.api.dart._
import scalaz.Scalaz._
import scalaz._


class DartDataProvider()(implicit val executionContext: ExecutionContext)
    extends IdentityDataProvider with Controller {

  override val id = DartDataProvider.Dart

  def getAdvertisers: Future[(Data.DartConfig, \/[JazelError,List[(String, Int)]])] = {
    Dart.getAdvertisers.run(LiveTest.prodConfig)
  }
}

object DartDataProvider {
  val Dart = "dart"
}
