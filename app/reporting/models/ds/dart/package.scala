package reporting.models.ds

import java.util.UUID

import reporting.models.ds._

package object dart {

  case class GlobalCfg(developerKey: String, secretKey: String)

  case class AccountCfg(accountId: UUID, dartAccountId: String)

  case class DartDS(dsId: Option[UUID],
                    label: String,
                    queryId: String,
                    accountId: UUID,
                    keySelectors: List[KeySelector],
                    dateSelector: DateSelector) extends DataSource {
    def dsType = DataSourceTypes.Dart
  }

}
