package reporting.engine.eval

import java.text.DecimalFormat

import org.joda.time.{Interval, LocalDate}

import scalaz.Semigroup

case class CxtRow(key: MultipartKey,
                  dateRange: DateRange,
                  values: Map[String, Result] = Map()) {
  def apply(field: String) = values(field)
  lazy val yearMonthKey = MonthCxtKey(dateRange.start)
  def hasField(field: String) = values.contains(field)

  import scalaz.Scalaz.ToSemigroupOps
  import scalaz._, Scalaz._
  import Result.implicits._

  def +(that: CxtRow): CxtRow = CxtRow(
      key = this.key.shortest(that.key),
      dateRange = if (dateRange == null) that.dateRange else this.dateRange + that.dateRange,
      values = this.values |+| that.values)

  def +(newValues: Map[String, Result]): CxtRow = {
    this.copy(values = this.values |+| newValues)
  }

  def +(newValues: (String, Result)*): CxtRow = {
    this.copy(values = this.values |+| newValues.toMap)
  }
}

object CxtRow {
  implicit object CxtRowOrderingByDate extends Ordering[CxtRow] {
    override def compare(x: CxtRow, y: CxtRow): Int = {
      x.dateRange compare y.dateRange
    }
  }

  import scalaz.Semigroup
  implicit object CxtRowSemigroup extends Semigroup[CxtRow] {
    def append(a: CxtRow, b: => CxtRow): CxtRow = a + b
  }
}

case class MonthCxtKey(d: LocalDate) {
  val year = d.getYear
  val month = d.getMonthOfYear

  lazy val dateRange = {
    val start = d.withDayOfMonth(1)
    val end = d.withDayOfMonth(d.monthOfYear.getMaximumValue)
    DateRange(start, end)
  }

  def contains(d2: LocalDate): Boolean = {
    d2.getYear == year && d2.getMonthOfYear == month
  }
}

sealed trait CxtFilter {
  // This lets us walk the entire tree with a predicate
  def filterRows(p: CxtRow => Boolean): Iterable[CxtRow]

  // Short circuit branches of the tree by a key predicate too
  def filterRowsByKey(kp: MultipartKey => Boolean): Iterable[CxtRow]

  // Get a sequence of all unique keys in this cxt
  def filterKeys(kp: MultipartKey => Boolean): Iterable[MultipartKey]
}

sealed trait CxtSums { this: CxtFilter =>
  val aggregates = collection.mutable.Map[String, Sum]()
  def sums(field: String) = aggregates.getOrElseUpdate(field, {
    import Result.implicits._
    val fs = filterRows(_.hasField(field)).map(_(field)).toList
    Sum(fs.length, fs.sum)
  })

  def sum(field: String) = sums(field).sum
  def avg(field: String) = sums(field).avg

  // Uncached
  //def sumsForKey(kp: MultipartKey, field: String) = {
  //  import Result.implicits._
  //  val fs = filterRowsByKey(_.matchesPattern(kp)).map(_(field)).toList
  // Sum(fs.length, fs.sum)
  //}
}

class KeyCxt(val key: MultipartKey) extends CxtSums with CxtFilter {
  private var rowsByDate = collection.immutable.SortedMap[DateRange,CxtRow]()
  def rows = rowsByDate.values
  def dates = rowsByDate.keys
  def dateRange = dates.reduceOption(_ + _)

  private[eval] val keys = collection.mutable
    .Map[MultipartKey, KeyCxt]()
    .withDefault(k => new KeyCxt(k))

  def cxts = keys.values

  // Only mutable to this package
  private[eval] def +=(row: CxtRow):Unit =
    if (row.key == key) {
      rowsByDate +=
        (if (rowsByDate.contains(row.dateRange)) {
          row.dateRange -> (rowsByDate(row.dateRange) + row)
        } else {
          row.dateRange -> row
        })
    } else {
      // XXX(dk): Stepwise branch into the key
      keys(row.key.partial(key.length + 1)) += row
    }

  def filterRows(p: CxtRow => Boolean): Iterable[CxtRow] = {
    rows.view.filter(p) ++ keys.values.view.flatMap(_.filterRows(p))
  }

  def filterRowsByKey(kp: MultipartKey => Boolean): Iterable[CxtRow] = {
    (if (kp(key)) rows else Iterable.empty) ++
      keys.values.view.flatMap(_.filterRowsByKey(kp))
  }

  def filterKeys(kp: MultipartKey => Boolean): Iterable[MultipartKey] = {
    keys.keys.view.filter(kp) ++ keys.values.view.flatMap(_.filterKeys(kp))
  }

  private[eval] var rolledUp: CxtRow = _

  // XXX(dk): Ignores leaves of non-temrinal nodes; those are distritutable values
  def rollup(): CxtRow = {
    rolledUp = CxtRow(key, null) // Reset values
    if (cxts.nonEmpty) {
      rolledUp = cxts.foldLeft(rolledUp)(_ + _.rollup())
      rolledUp
    } else {
      rolledUp = rows.reduce(_ + _)
      rolledUp
    }
  }

  def sumForKey(sumKey: MultipartKey, field: String): Result = {
    import MultipartKey.KeyMatch._
    key.matchPattern(sumKey) match {
      case ExactMatch     => rolledUp(field) // Will explode if rollup() wasn't called.
      case MayMatch => cxts.map(_.sumForKey(sumKey, field)).reduce(_ + _)
      case NoMatch    => Result.Zero
    }
  }
}

object KeyCxt {
  implicit object KeyCxtOrdering extends Ordering[KeyCxt] {
    override def compare(x: KeyCxt, y: KeyCxt): Int = x.key compare y.key
  }
}

// Builds a hierarchy for CxtRows, so we can perform operations on it.
//
// This hierarchy looks like:
//
// ROOT
//   \
// Month(s): one node per month
//   \
// Partial Key(s): one level for each partial key
//   \
// CxtRow(s): each partial key level may have leaf nodes with data
class RootCxt(inputRows: Iterable[CxtRow]) extends CxtSums with CxtFilter {

  val months: Map[MonthCxtKey, KeyCxt] = {
    val months = collection.mutable
      .Map[MonthCxtKey, KeyCxt]()
      .withDefault(_ => new KeyCxt(MultipartKey.empty))

    for (r <- inputRows) {
      assert(r.dateRange.isWithinMonth, "Cannot handle rows that span months!")
      val monthKey = MonthCxtKey(r.dateRange.start)
      months(monthKey) += r
    }
    months.values.foreach(_.rollup())
    months.toMap
  }

  def dates = collection.SortedSet(months.values.flatMap(_.dates).toSeq: _*)
  def dateRange = dates.reduceOption(_ + _)

  def monthFor(row: CxtRow) = months(row.yearMonthKey)

  def month(d: LocalDate) = months.get(MonthCxtKey(d))

  def filterRows(p: CxtRow => Boolean): Iterable[CxtRow] = {
    months.values.view.flatMap(_.filterRows(p))
  }

  def filterRowsByKey(kp: MultipartKey => Boolean): Iterable[CxtRow] = {
    months.values.view.flatMap(_.filterRowsByKey(kp))
  }

  def filterKeys(kp: MultipartKey => Boolean): Iterable[MultipartKey] = {
    import collection.SortedSet
    SortedSet(months.values.toSeq:_*).view.flatMap(_.filterKeys(kp))
  }
}
