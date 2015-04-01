package reporting.core

import reporting.core.dataproviders._

import scala.concurrent.ExecutionContext
import scala.collection.immutable.ListMap
import bravo.api.dart._
import bravo.util.DateUtil._
import play.api.libs.concurrent.{ Execution => PlayExecution }

case class GlobalConfig(
  //marchexUsername: String,
  //marchexPass: String,
  api: DartInternalAPI,
  filePath: String,
  accountId: String,
  userAccount: String,
  clientId: Int,
  reportCache: Map[Long, List[ReportDay]] = Map[Long, List[ReportDay]]()
)

//NOTE(Vince) if we only ever have one object, I don't think we need a trait until we want to make it a class
trait ReportingRuntime {

  val dataProviders: Map[String, IdentityDataProvider]

  implicit def executionContext: ExecutionContext
}


/**
This is where we are 'hardcoding' our differing API depdendnecies, so it will also be where GlobalConfig lives.  We'll keep all the 'hardcoded' stuff to one place. This may change
if each API config ends up being associated to a particular account and comes from the database, then we can do something different.
**/
object ReportingRuntime {

  val dc = LiveTest.prodConfig
  //NOTE(Vince) FOR NOW we just want to set livetest with one, but we'll run this through a list of abstracted configs
  val globalConfig: GlobalConfig = GlobalConfig(dc.api, dc.filePath, dc.accountId, dc.userAccount, dc.clientId, dc.reportCache)

  abstract class Default extends ReportingRuntime {
    override implicit def executionContext: ExecutionContext =
      PlayExecution.defaultContext

    protected def include(p: IdentityDataProvider) = p.id -> p

    override lazy val dataProviders = ListMap(
      include(new DartDataProvider())
    )
  }
}
