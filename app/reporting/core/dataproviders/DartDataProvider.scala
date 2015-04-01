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
import bravo.api.dart.Data._
import scalaz.Scalaz._
import scalaz._
import reporting.models.ds.DataSource._
import reporting.models.ds.dart._
import prickle._


class DartDataProvider()(implicit override val executionContext: ExecutionContext)
    extends IdentityDataProvider with Controller {

  override val id = DartDataProvider.Dart

  override val accountCfgUnpickler = Unpickle[DartAccountCfg]

  //NOTE(VINCE) each provider will have an internal lens to help <~> between the bigger configs and the specific data requirements.
  //We can have one global config or N number, nad decide which one to pull by a DB
  //or account config.
  def getAdvertisers: BravoM[GlobalConfig, List[(String, Int)]] = {
    Dart.getAdvertisers.zoom(DartDataProvider.glens) //.run(LiveTest.prodConfigbb)
  }
  //def getAdvertisers: Future[(Data.DartConfig, \/[JazelError,List[(String, Int)]])] = {
    //Dart.getAdvertisers.run(LiveTest.prodConfig)
}

object DartDataProvider {
  val Dart = "dart"

  val glens: Lens[GlobalConfig, DartConfig] = Lens.lensu( (a,b) => a.copy(api = b.api, filePath = b.filePath, accountId = b.accountId, userAccount = b.userAccount, clientId = b.clientId, reportCache = b.reportCache),
    b => DartConfig(api = b.api, filePath = b.filePath, accountId = b.accountId, userAccount = b.userAccount, clientId = b.clientId, reportCache = b.reportCache))
}
