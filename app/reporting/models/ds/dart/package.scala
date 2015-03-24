package reporting.models.ds

import java.util.UUID

import reporting.models.ds._
import com.rethinkscala.Document

package object dart {

  case class GlobalCfg(developerKey: String, secretKey: String)

  case class AccountCfg(accountId: UUID, dartAccountId: String)

  case class DartDS(label: String,
                    queryId: String,
                    accountId: UUID,
                    keySelectors: List[KeySelector],
                    dateSelector: DateSelector,
                    dsId: Option[UUID] = None) extends DataSource with Document {
    def dsType = DataSourceTypes.Dart
  }

}
