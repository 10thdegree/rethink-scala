package reporting.models

import java.util.UUID

import org.joda.time.DateTime

import scala.concurrent.{ExecutionContext, Future}

object DataSources {

  // "pattern" here can use any attribute via attrs("attribute")
  // Something like this is necessary because multiple attributes
  // may need to be combined to create the primary key.
  case class PrimaryKeySelector(isRegex: Boolean,
                                pattern: String)

  // "usePK" matches to the output of PK if set.
  case class KeyFilter(usePK: Boolean,
                       isRegex: Boolean,
                       pattern: String)

  // How to map date attribute values to actual dates.
  case class DateSelector(attributeName: String /* e.g. "DATE" */ ,
                          dateFormat: String /* e.g. mm/dd/YYYY */)

  sealed case class DataSourceType(label: String)

  object DataSourceTypes {
    val Dart = DataSourceType("Dart")
    val Marchex = DataSourceType("Marchex")
  }

  trait DataSource {

    def dsId: Option[UUID]

    def label: String

    def accountId: UUID

    def primaryKey: PrimaryKeySelector

    def keyFilters: List[KeyFilter]

    def dateSelector: DateSelector

    def impressionsField: String = "impressions"// TODO: Need configuration for selecting "impressions" field

    def dsType: DataSourceType

    // DS is responsible for loading cached or non-cached versions internally
    def dataForRange(startDate: DateTime, endDate: DateTime)(implicit ec: ExecutionContext): Future[Seq[DataSource.Row]] = {
      Future { ??? }
    }
  }

  object DataSource {

    case class Row(key: String, date: DateTime, attributes: Map[String, Any]) {
      def +(that:Row): Row = ???
      def apply(attr: String): Any = ???
    }

  }

  object Dart {

    case class GlobalCfg(developerKey: String, secretKey: String)

    case class AccountCfg(accountId: UUID, dartAccountId: String)

    case class DartDS(dsId: Option[UUID],
                      label: String,
                      queryId: String,
                      accountId: UUID,
                      primaryKey: PrimaryKeySelector,
                      keyFilters: List[KeyFilter],
                      dateSelector: DateSelector) extends DataSource {
      def dsType = DataSourceTypes.Dart
    }

  }

  object Marchex {

    case class GlobalCfg(username: String, password: String)

    case class AccountCfg(accountId: UUID, marchexAccountId: String)

    case class MarchexDS(dsId: Option[UUID],
                         label: String,
                         number: String,
                         marchexGroupId: String,
                         accountId: UUID,
                         primaryKey: PrimaryKeySelector,
                         keyFilters: List[KeyFilter],
                         dateSelector: DateSelector) extends DataSource {
      def dsType = DataSourceTypes.Marchex
    }

  }

}
