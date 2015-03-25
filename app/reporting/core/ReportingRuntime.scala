package reporting.core

import reporting.core.dataproviders._

import scala.concurrent.ExecutionContext
import scala.collection.immutable.ListMap

import play.api.libs.concurrent.{ Execution => PlayExecution }

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
