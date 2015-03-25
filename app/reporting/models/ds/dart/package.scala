package reporting.models.ds

import java.util.UUID

import reporting.models.ds._
import com.rethinkscala.Document

package object dart {

  case class GlobalCfg(developerKey: String, secretKey: String)

  case class DartAccountCfg(label: String,
                            dsAccountId: String,
                            dsId: Option[UUID] = None) extends DSAccountCfg {
    def dsType = DataSourceTypes.Dart
  }

  case class DartDS(label: String,
                    queryId: String,
                    accountId: UUID,
                    keySelectors: List[SimpleKeySelector],
                    dateSelector: DateSelector,
                    dsId: Option[UUID] = None) extends DataSource with Document {
    def dsType = DataSourceTypes.Dart
  }
}
