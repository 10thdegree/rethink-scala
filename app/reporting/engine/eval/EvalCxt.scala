package reporting.engine.eval

import java.text.DecimalFormat

import org.joda.time.{Interval, LocalDate}

import scalaz.Semigroup

case class Sum(count: Long, sum: Result) {
  def +(that: Sum): Sum = Sum(this.count + that.count, this.sum + that.sum)
  def avg: Result = sum / count
}

class Result(val value: BigDecimal) {
  def toDouble: Double = value.toDouble

  def toLong: Long = value.toLong

  def toInt: Int = value.toInt

  def rounded: Result = new Result(this.value.rounded)

  def +(that: Result): Result = new Result(this.value + that.value)

  def -(that: Result): Result = new Result(this.value - that.value)

  def *(that: Result): Result = new Result(this.value * that.value)

  def /(that: Result): Result = try {
    new Result(this.value / that.value)
  } catch {
    case c: java.lang.ArithmeticException => Result.NaN
  }

  def +[A: Numeric](that: A): Result = this + Result(that)

  def -[A: Numeric](that: A): Result = this - Result(that)

  def *[A: Numeric](that: A): Result = this * Result(that)

  def /[A: Numeric](that: A): Result = this / Result(that)

  override def toString = value.toString()
}

object Result {

  case object Zero extends Result(0)

  // For "N/A" results
  case object NaN extends Result(0) {

    override def +(that: Result) = NaN

    override def -(that: Result) = NaN

    override def *(that: Result) = NaN

    override def /(that: Result) = NaN
  }

  object Formats {
    val WholeNumber = "#,###"
    val FractionalNumber = "#,###.##"
    val Nan = "N/A"
    val Currency = "\u00A4#,##0.00"

    def guessFormat(v: BigDecimal): String = {
      if (v.isWhole()) Formats.WholeNumber
      else Formats.FractionalNumber
    }
  }

  def apply[A: Numeric](value: A): Result = value match {
    case v: Double => new Result(v)
    case v: Float => new Result(v.toDouble)
    case v: Long => new Result(v)
    case v: Int => new Result(v)
    case v: BigDecimal => new Result(v)
    case v: Result => new Result(v.value)
    case v => new Result(BigDecimal(v.toString))
  }

  object implicits {

    import scala.math.Numeric

    // Not used right now, but keeping formatting logic around just in case.
    implicit class FormatResult(res: Result) {
      def format(fmt: String) = res match {
        case Result.NaN => Formats.Nan
        case _ =>
          val decimalFormatter = new DecimalFormat(fmt)
          decimalFormatter.format(res.value)
      }

      def formatted = res match {
        case Result.NaN => Formats.Nan
        case _ =>
          val decimalFormatter = new DecimalFormat(Formats.guessFormat(res.value))
          decimalFormatter.format(res.value)
      }
    }

    implicit class NumericToResult[A: Numeric](val a: A) {
      def +(r: Result): Result = Result(a) + r

      def -(r: Result): Result = Result(a) - r

      def *(r: Result): Result = Result(a) * r

      def /(r: Result): Result = Result(a) / r
    }

    implicit object ResultNumeric extends Numeric[Result] {

      override def plus(x: Result, y: Result): Result = x + y

      override def toDouble(x: Result): Double = x.toDouble

      override def toFloat(x: Result): Float = x.toFloat

      override def toInt(x: Result): Int = x.toInt

      override def negate(x: Result): Result = new Result(-x.value)

      override def fromInt(x: Int): Result = new Result(x)

      override def toLong(x: Result): Long = x.toLong

      override def times(x: Result, y: Result): Result = x * y

      override def minus(x: Result, y: Result): Result = x - y

      override def compare(x: Result, y: Result): Int = x.value.compare(y.value)
    }

    implicit def ResultSemigroup: Semigroup[Result] = new Semigroup[Result] {
      def append(a1: Result, a2: => Result): Result = a1 + a2
    }

  }

}

case class DateRange(start: LocalDate, end: LocalDate) extends Ordered[DateRange] {
  def isWithinMonth =
    start.getYear == end.getYear &&
      start.getMonthOfYear == end.getMonthOfYear

  def +(that: DateRange) =
    if (this == that) this
    else DateRange(
      start = if (this.start isBefore that.start) this.start else that.start,
      end = if (this.end isBefore that.end) this.end else that.end)

  def compare(that: DateRange): Int = {
    this.start compareTo that.start match {
      case 0 => this.end compareTo that.end
      case v => v
    }
  }

  // TODO(dk): switch lookups to use DateRange instead of Interval
  def toInterval = {
    new Interval(start.toDateTimeAtStartOfDay,
      end.toDateTimeAtStartOfDay.plusDays(1).minusMillis(1))
  }

  def dates: Seq[LocalDate] = {
    (0 until toInterval.toPeriod.getDays).map(start.plusDays).toSeq
  }
}

object DateRange {
  def fromLocalDate(d: LocalDate) = DateRange(d, d)
}

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

// TODO: Should use Options instead of nulls
case class MultipartKey(parts: List[String]) extends Ordered[MultipartKey] {
  def length = parts.length

  def shortest(that: MultipartKey) =
    if (this.length < that.length) this
    else that

  def partial(len: Int) = {
    MultipartKey(parts.take(len))
  }

  def apply(idx: Int) = parts(idx)

  def map(p: (String, Int) => Boolean) = {
    MultipartKey(for {
      (pk, idx) <- parts.zipWithIndex
    } yield if (p(pk, idx)) pk else null)
  }

  // e.g.:
  // this      A,B,C
  // pattern   A,-,C
  def matchesPattern(pattern: MultipartKey): Boolean = {
    (pattern.parts zip this.parts).forall({ case (a, b) => a == b || b == null })
  }

  override def compare(that: MultipartKey): Int = {
    val diffs = (this.parts zip that.parts)
      .map({ case (x, y) => x compare y})
    val same = diffs.takeWhile(_ == 0).length
    if (parts.length > same) diffs(same) else 0
  }
}
object MultipartKey {
  def empty = MultipartKey(List.empty)
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
}

class KeyCxt(val key: MultipartKey) extends CxtSums with CxtFilter {
  private var rowsByDate = collection.immutable.SortedMap[DateRange,CxtRow]()
  def rows = rowsByDate.values
  def dates = rowsByDate.keys
  def dateRange = dates.reduceOption(_ + _)

  private[eval] val keys = collection.mutable
    .Map[MultipartKey, KeyCxt]()
    .withDefault(k => new KeyCxt(k))

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
class RootCxt(inputRows: Seq[CxtRow]) extends CxtSums with CxtFilter {

  val months: Map[MonthCxtKey, KeyCxt] = {
    val months = collection.mutable
      .Map[MonthCxtKey, KeyCxt]()
      .withDefault(_ => new KeyCxt(MultipartKey.empty))

    for (r <- inputRows) {
      assert(r.dateRange.isWithinMonth, "Cannot handle rows that span months!")
      val monthKey = MonthCxtKey(r.dateRange.start)
      months(monthKey) += r
    }
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
