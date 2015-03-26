package reporting.core

import reporting.core.dataproviders._

import scala.concurrent.ExecutionContext
import scala.collection.immutable.ListMap
import bravo.api.dart._
import bravo.util.DateUtil._
import play.api.libs.concurrent.{ Execution => PlayExecution }

case class GlobalConfig(
  marchexUsername: String,
  marchexPass: String,
  api: DartInternalAPI,
  filePath: String,
  accountId: String,
  userAccount: String,
  clientId: Int,
  reportCache: Map[Long, List[ReportDay]] = Map[Long, List[ReportDay]]()
)

//NOTE: if we only ever have one object, I don't think we need a trait until we want to make it a class
trait ReportingRuntime {
  
  val dataProviders: Map[String, IdentityDataProvider]

  implicit def executionContext: ExecutionContext
}

object ReportingRuntime {
  abstract class Default extends ReportingRuntime {
    override implicit def executionContext: ExecutionContext =
      PlayExecution.defaultContext

    protected def include(p: IdentityDataProvider) = p.id -> p

    override lazy val dataProviders = ListMap(
      include(new DartDataProvider())
    )
  }
}
