package reporting.models

import java.text.DateFormat
import java.util.UUID
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}

import org.joda.time.DateTime
import org.joda.time.format.{DateTimeFormat, DateTimeFormatterBuilder, DateTimeFormatter}
import reporting.engine.JodaTime.implicits._

import scala.concurrent.{Await, ExecutionContext, Future}
import scalaz.Semigroup

import prickle._
import scala.util.{Success, Failure, Try}
import collection.mutable
import scala.collection.GenIterableLike
import scala.util.matching.Regex

package object ds {

  // "expression" here builds a simple AST.
  // It can use any attribute via attrs("attribute")
  // e.g. regex(attrs["foo"], "^.*-(.*?)$", "$1") + attrs["bar"]
  // Something like this is necessary because multiple attributes
  // may need to be combined to create partial keys
  trait KeySelector {
    def select(attrs: Map[String,String]): Option[String]
  }

  case class RegexKeySelector(expression: String) extends KeySelector {
    def select(attrs: Map[String, String]) = ???
  }

  // How to map date attribute values to actual dates.
  case class DateSelector(attributeName: String /* e.g. "DATE" */ ,
                          dateFormat: String /* e.g. mm/dd/YYYY */) {
  }

  case class SimpleKeySelector(targetAttribute: String,
                               transformations: Map[String,String],
                               otherwise: String) extends KeySelector {
    import bravo.util.matching.RegexOps.implicits._

    import scalaz.Scalaz._
    import scalaz._

    implicit class CollectionOps[+A, B <: Iterable[A]](col: GenIterableLike[A, B]) {
      def findFirst[B](f: A => Option[B]): Option[B] = {
        col.repr.view.map(f).collectFirst({
          case Some(x) => x
        })
      }
    }

    def select(attrs: Map[String, String]): Option[String] = {
      val resultList = transformations.map{case(k,v) => (k,v)}(collection.breakOut): List[(String,String)]
      val patterns = resultList.map(p => p._1.r -> p._2)

      val campaign = attrs.getOrElse(targetAttribute, "")

      patterns
        .findFirst({case (regex,replacement) =>
          regex.maybeReplaceFirstIn(campaign, replacement)
        })
        .orElse(otherwise.some)
    }
  }

  sealed case class DataSourceType(label: String)

  object DataSourceTypes {
    val Dart = DataSourceType("Dart")
    val Marchex = DataSourceType("Marchex")
    var Unknown = DataSourceType("N/A")
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY)
  trait DSAccountCfg {

    def dsId: Option[UUID]

    def label: String

    def dsAccountId: Int

    def dsType: DataSourceType
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY)
  trait DataSource {

    def dsId: Option[UUID]

    def label: String

    def accountId: UUID

    def keySelectors: List[KeySelector] // The order of these determine the order of the multi-part key

    def dateSelector: DateSelector

    def dsType: DataSourceType

    // DS is responsible for loading cached or non-cached versions internally
    def dataForRange(startDate: DateTime, endDate: DateTime)(implicit ec: ExecutionContext): Future[Seq[DataSource.Row]] = {
      Future {???}
    }
  }

  object DataSource {

    implicit object UUIDUnpickler extends Unpickler[UUID] {
      def unpickle[P](pickle: P, state: mutable.Map[String, Any])(implicit config: PConfig[P]) = config.readString(pickle).flatMap(s => Try(UUID.fromString(s)))
    }

    implicit object UUIDPickler extends Pickler[UUID] {
      def pickle[P](x: UUID, state: PickleState)(implicit config: PConfig[P]): P = config.makeString(x.toString)
    }

    implicit def AttributesSemigroup: Semigroup[Attributes] = new Semigroup[Attributes] {
      def append(a1: Attributes, a2: => Attributes): Attributes = {
        import scalaz.Scalaz.ToSemigroupOps
        import scalaz._, Scalaz._
        Attributes.fromMap(a1.map |+| a2.map)
      }
    }

    case class Attributes(map: Map[String, BigDecimal] = Map()) {
      def contains(s: String): Boolean = map.contains(s)

      def +(that: Attributes): Attributes = {
        import scalaz.Scalaz._
        this |+| that
      }

      def apply(key: String) = map(key)

      def get(key: String) = map.get(key)

      def getOrElse(key: String, alt: => BigDecimal) = map.getOrElse(key, alt)
    }

    object Attributes {
      def fromMap(m: Map[String, BigDecimal]): Attributes = new Attributes(m)

      def fromList(vs: (String, BigDecimal)*): Attributes = new Attributes(Map(vs: _*))
    }

    trait Row {
      type R <: Row

      def keys: List[String]

      def date: DateTime

      def attributes: Attributes

      def apply(attr: String): BigDecimal = attributes.getOrElse(attr, throw new Exception(s"$attr not found for $keys: " + attributes.map.keys))

      def get(attr: String): Option[BigDecimal] = attributes.get(attr)

      def +(other: Row): R
    }

    case class BasicRow(keys: List[String],
                        date: DateTime,
                        attributes: Attributes = Attributes()) extends Row {

      type R = BasicRow

      def +(other: Row): R = {
        BasicRow(keys, date, attributes + other.attributes)
      }
    }

    object RowOrderings {
      implicit def keysOrdering[A](implicit o: Ordering[A]): Ordering[List[A]] = new Ordering[List[A]] {
        def compare(o1: List[A], o2: List[A]) = {
          (o1 zip o2)
            .map({ case (a, b) => o.compare(a, b)})
            .collectFirst({ case c if c != 0 => c})
            .getOrElse(o1.length - o2.length)
        }
      }

      implicit def BasicRowOrdering(implicit o: Ordering[List[String]]) = new Ordering[BasicRow] {
        def compare(o1: BasicRow, o2: BasicRow) = o.compare(o1.keys, o2.keys)
      }
    }

    /**
     * Responsible for returning a future of a list of datasources.
     */
    /*
    class DataSourceFetcher[T <: DataSource.Row](dataSources: DataSource*) {
      def forDateRange(start: DateTime, end: DateTime): Future[List[(DataSource, Seq[T])]] = {
        import scalaz._, Scalaz._

        // Get a list of futures for each DataSource
        val dsRowsF: Seq[Future[(DataSource, Seq[T])]] = for {
          ds <- dataSources
          data = ds.dataForRange(start, end).asInstanceOf[Future[Seq[T]]] // applies filters and merges rows by keyselectors
        } yield data.map(ds -> _)

        // Get a single future for all of them, and return that
        dsRowsF.toList.sequence[Future, (DataSource, Seq[T])]
      }
    }*/

    trait DataSourceAggregator[T <: Row] {
      //      def groupByDate(dses: Seq[DataSource.Row]): Map[DateTime, Seq[DataSource.Row]] = dses.groupBy(_.date)

      def groupByDate(dses: (DataSource, Seq[T])*): Seq[(DateTime, Seq[T])] = {
        //        import scalaz.Scalaz.ToSemigroupOps
        //        val maps = dses.map(_._2.groupBy(_.date)).toList
        //        maps.reduce(_ |+| _).toSeq.sortBy(x => x._1)

        val dsRows = dses.flatMap({ case (key, list) => list})
        val rows = dsRows.groupBy(_.date).toSeq.sortBy(x => x._1)
        rows
      }

      def flattenByKeys(rows: T*): List[T]

      def flattenByKeysAndDate(rows: T*): List[T]

    }

    object DataSourceAggregators {
      def get[T <: Row](implicit dsa: DataSourceAggregator[T]) = dsa

      object implicits {

        implicit object DataSourceAggregatorBasicRows extends DataSourceAggregator[BasicRow] {

          // Merge all values for unique keys (so dates get merged together)
          // For reports by key
          def flattenByKeys(rows: BasicRow*) = {
            val rr = rows
              .groupBy(r => r.keys)
              .map({ case (key, subrows) => subrows.reduce(_ + _)})
              .toList
            rr.foreach(r => play.Logger.debug(r.keys + ": " + r("Paid Search Impressions")))
            rr
          }

          // Merge values for any rows that have matching keys/dates
          // For reports by date (keys are still separate though)
          def flattenByKeysAndDate(rows: BasicRow*) = {
            rows
              .groupBy(r => r.keys -> r.date)
              .map({ case (key, subrows) => subrows.reduce(_ + _)})
              .toList
          }

        }

      }

    }


    class DataSourceRowFactory(ds: DataSource) {

      import DataSourceAggregators.implicits._

      val dsa = DataSourceAggregators.get[BasicRow]

      // Convert the map of values to DataSource.Rows, and merge duplicate keys' values
      // TODO: Currently expects all values to be either String or BigDecimal already
      def process(rows: Map[String, Any]*): List[BasicRow] = {
        dsa.flattenByKeys(rows.map(convertRow): _*).toList
      }

      // Converts all fields to BigDecimal, and leaves the rest as Strings.
      def convertValues(fields: Set[String])(rows: Map[String, String]*): List[Map[String, Any]] = {
        def safe0(v: String) = if (v == null || v.isEmpty) "0" else v

        def convertMap(row: Map[String, String]) = {
          //play.Logger.debug("R: " + row)
          for {
            (k, v) <- row
            cv = if (fields.contains(k)) BigDecimal(safe0(v)) else v
          } yield k -> cv
        }
        rows.map(convertMap).toList
      }

      def apply = process _

      def convertRow(data: Map[String, Any]): BasicRow = {
        val strData = data
          .filter({ case (k, v) => v.isInstanceOf[String]})
          .map({ case (k, v) => k -> v.asInstanceOf[String]})
        val numericData = data
          .filter({ case (k, v) => v.isInstanceOf[BigDecimal]})
          .map({ case (k, v) => k -> v.asInstanceOf[BigDecimal]})
        val dtf = DateTimeFormat.forPattern(ds.dateSelector.dateFormat)
        val date = DateTime.parse(data(ds.dateSelector.attributeName).toString, dtf)

        BasicRow(
          // TODO: Use Options instead of nulls for missing partial keys
          keys = ds.keySelectors.map(_.select(strData)).map(_.orNull), //getOrElse(strData("Paid Search Campaign"))),
          date = date,
          attributes = Attributes(numericData)
        )
      }
    }

  }

}
