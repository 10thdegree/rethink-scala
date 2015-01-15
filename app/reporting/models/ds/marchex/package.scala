package reporting.models.ds

import java.util.UUID

import reporting.models.ds._

package object marchex {
  case class GlobalCfg(username: String, password: String)

  case class AccountCfg(accountId: UUID, marchexAccountId: String)

  case class MarchexDS(dsId: Option[UUID],
                       label: String,
                       number: String,
                       marchexGroupId: String,
                       accountId: UUID,
                       keySelectors: List[KeySelector],
                       dateSelector: DateSelector) extends DataSource {
    def dsType = DataSourceTypes.Marchex
  }
}
