package reporting.models

import java.text.DateFormat
import java.util.UUID

import org.joda.time.DateTime
import org.joda.time.format.{DateTimeFormat, DateTimeFormatterBuilder, DateTimeFormatter}
import reporting.engine.Joda

import scala.concurrent.{Await, ExecutionContext, Future}
import scalaz.Semigroup

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

  sealed case class DataSourceType(label: String)

  object DataSourceTypes {
    val Dart = DataSourceType("Dart")
    val Marchex = DataSourceType("Marchex")
    var Unknown = DataSourceType("N/A")
  }

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

      def apply(attr: String): BigDecimal = attributes(attr)

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

    case class NestedRow(keys: List[String],
                         dateRange: (DateTime, DateTime), // TODO: Handle nested structures with various ranges
                         attributes: Attributes = Attributes(),
                         children: List[NestedRow] = Nil) extends Row {

      type R = NestedRow

      def +(other: Row) = ???

      // Takes values at higher nodes and distributes them down to leaf nodes
      def distributeDown(attr: String, dependantAttr: Option[String] = None, value: Option[BigDecimal] = None): NestedRow =
        (this.children, attributes.get(attr), value, dependantAttr) match {
          // End of the line, do we have a value?
          case (Nil, _, None, _) => this
          case (Nil, _, Some(nv), _) => this.copy(attributes = attributes + Attributes.fromList(attr -> nv))

          // Continue: distribute by dependant attribute
          case (_, None, Some(nv), Some(d)) =>
            this.copy(
              attributes = attributes + Attributes.fromList(attr -> nv),
              children = children.map(c => c.distributeDown(attr, dependantAttr, Some(nv / c(d)))))

          // Continue: even distribution
          case (_, None, Some(nv), None) =>
            this.copy(
              attributes = attributes + Attributes.fromList(attr -> nv),
              children = children.map(_.distributeDown(attr, dependantAttr, Some(nv / children.size))))

          // Start: distribute by dependant attribute
          case (_, Some(v), None, Some(d)) =>
            this.copy(children = children.map(c => c.distributeDown(attr, dependantAttr, Some(v / c(d)))))

          // Start: even distribution
          case (_, Some(v), None, None) =>
            this.copy(children = children.map(_.distributeDown(attr, dependantAttr, Some(v / children.size))))

          // Haven't found the node with the value yet
          case (_, _, _, _) =>
            this.copy(children = children.map(_.distributeDown(attr, dependantAttr)))
        }

      // Takes values at leaf nodes and pushes them up to the top
      def distributeUp(attr: String): NestedRow = {
        (this.children, this.get(attr)) match {
          case (Nil, None) => throw new RuntimeException(s"Value for ttribute $attr expected.")
          case (Nil, Some(v)) => this // We already have the value, so just return
          case (_, _) =>
            val nchildren = children.map(_.distributeUp(attr))
            val sum = nchildren.map(_(attr)).sum
            this.copy(
              attributes = attributes + Attributes.fromList(attr -> sum),
              children = nchildren)
        }
      }

      def terminals: List[NestedRow] =
        if (children.isEmpty) List(this)
        else children.flatMap(_.terminals)

      val date = dateRange._1
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

      implicit def NestedRowOrdering(implicit o: Ordering[List[String]]) = new Ordering[NestedRow] {
        def compare(o1: NestedRow, o2: NestedRow) = o.compare(o1.keys, o2.keys)
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
        import Joda._
        //        import scalaz.Scalaz.ToSemigroupOps
        //        val maps = dses.map(_._2.groupBy(_.date)).toList
        //        maps.reduce(_ |+| _).toSeq.sortBy(x => x._1)

        val dsRows = dses.flatMap({ case (key, list) => list})
        val rows = dsRows.groupBy(_.date).toSeq.sortBy(x => x._1)
        rows
      }

      def flattenByKeys(rows: T*): List[T]

      def flattenByKeysAndDate(rows: T*): List[T]

      def apply(rows: T*): List[T]

      def aggregate(rows: T*) = apply(rows:_*)
    }

    object DataSourceAggregators {
      def get[T <: Row](implicit dsa: DataSourceAggregator[T]) = dsa

      object implicits {
        implicit object DataSourceAggregatorNestedRows extends DataSourceAggregator[NestedRow] {
          def nestAndCoalesce(rows: NestedRow*): List[NestedRow] = {
            val nested = DataSource.NestedRow.nest(rows.toList)
            val coalesced = DataSource.NestedRow.coalesce(nested)
            coalesced
          }

          // Merge all values for unique keys (so dates get merged together)
          def flattenByKeys(rows: NestedRow*) = {
            rows
              .groupBy(r => r.keys)
              .map({ case (key, subrows) => subrows.reduce(_ + _)})
              .toList
          }

          // Merge values for any rows that have matching keys/dates
          def flattenByKeysAndDate(rows: NestedRow*) = {
            rows
              .groupBy(r => r.keys -> r.date)
              .map({ case (key, subrows) => subrows.reduce(_ + _)})
              .toList
          }

          def apply(rows: NestedRow*) = nestAndCoalesce(rows:_*)
        }

        implicit object DataSourceAggregatorBasicRows extends DataSourceAggregator[BasicRow] {

          // Merge all values for unique keys (so dates get merged together)
          def flattenByKeys(rows: BasicRow*) = {
            rows
              .groupBy(r => r.keys)
              .map({ case (key, subrows) => subrows.reduce(_ + _)})
              .toList
          }

          // Merge values for any rows that have matching keys/dates
          def flattenByKeysAndDate(rows: BasicRow*) = {
            rows
              .groupBy(r => r.keys -> r.date)
              .map({ case (key, subrows) => subrows.reduce(_ + _)})
              .toList
          }

          def apply(rows: BasicRow*): List[BasicRow] = flattenByKeysAndDate(rows:_*)
        }
      }
    }


    class DataSourceRowFactory(ds: DataSource) {

      import DataSourceAggregators.implicits._
      val dsa = DataSourceAggregators.get[BasicRow]

      // Convert the map of values to DataSource.Rows, and merge duplicate keys' values
      // TODO: Currently expects all values to be either String or BigDecimal already
      def process(rows: Map[String, Any]*): List[BasicRow] = {
        dsa.aggregate(rows.map(convertRow):_*).toList
      }

      def apply = process _

      def convertRow(data: Map[String, Any]): BasicRow = {
        val strData = data
          .filter({case (k,v) => v.isInstanceOf[String]})
          .map({case (k,v) => k -> v.asInstanceOf[String]})
        val numericData = data
          .filter({case (k,v) => v.isInstanceOf[BigDecimal]})
          .map({case (k,v) => k -> v.asInstanceOf[BigDecimal]})
        val dtf = DateTimeFormat.forPattern(ds.dateSelector.dateFormat)
        val date = DateTime.parse(data(ds.dateSelector.attributeName).toString, dtf)

        BasicRow(
          // TODO: Use Options instead of nulls for missing partial keys
          keys = ds.keySelectors.map(_.select(strData)).map(_.orNull),
          date = date,
          attributes = Attributes(numericData)
        )
      }
    }

    object NestedRow {

      import reporting.models.ds.DataSource.RowOrderings._

      // XXX: Currently ignores date; expects all BasicRows to have same date!
      def nest(cs: List[Row]): List[NestedRow] = {
        def go(c: Row, beg: Vector[String], end: List[String]): NestedRow = end match {
          case Nil => new NestedRow(
            keys = c.keys,
            dateRange = (c.date, c.date),
            attributes = c.attributes)
          case h :: tail => new NestedRow(
            keys = beg.toList,
            dateRange = (c.date, c.date),
            children = go(c, beg :+ h, tail) :: Nil)
        }
        cs.map(c => go(c, Vector(c.keys.head), c.keys.tail))
      }

      // TODO: Should handle dates in some capacity
      // TODO: This can be a semigroup, define it.
      def coalesce(cs: List[NestedRow]): List[NestedRow] = {
        cs
          .foldLeft(List.empty[NestedRow])(
            (accum, r) => accum.headOption match {
              case Some(h) if h.date != r.date => throw new RuntimeException("Coalescing of rows with different dates not supported.")
              case Some(h) if h.keys == r.keys => h.copy(
                dateRange = r.dateRange,
                attributes = r.attributes + h.attributes,
                children = coalesce((r.children ::: h.children).sorted)) :: accum.tail
              case _ => r :: accum
            })
          .reverse
      }

      /*
      // TODO: Parameter specifying for what date range to do the collapsing, by day, week, whole range, etc.
      def flattenByKeysAndDate(rows: List[Row]) = {
        def go(accum: List[Row], next: Row) = accum.headOption match {
          case Some(prev) if next.keys == prev.keys && next.date == prev.date => (next + prev) :: accum.tail
          case _ => next :: accum
        }
        rows
          .sortBy(r => r.date)
          .foldLeft(List.empty[Row])(go)
          .reverse
      }

      def flattenByKeys(rows: List[Row]) = {
        def go(accum: List[Row], next: Row) = accum.headOption match {
          case Some(prev) if next.keys == prev.keys => (next + prev) :: accum.tail
          case _ => next :: accum
        }
        rows
          .sortBy(r => r.keys)
          .foldLeft(List.empty[Row])(go)
          .reverse
      }*/
    }

  }

}