package reporting.engine

import org.joda.time._
import org.joda.time.format.DateTimeFormat

// XXX(dk): Intervals are technically end-exclusive, so we are excluding
// the last millisecond of the month in this class by not using the first
// moment of the following month; however, it is easier to do it this way
// than have code somewhere else mistakenly think the end date is
// inclusive and has an additional day.
object JodaTime {

  object implicits {
    implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isBefore _)

    implicit class DateTimeOps(dt: DateTime) {
      def withTimeAtEndOfDay = dt.plusDays(1).withTimeAtStartOfDay.minusMillis(1)
    }

    implicit class IntervalOps(span: Interval) {
      def intoMonths = JodaTime.breakIntoMonths(span)
    }
  }

  import implicits._

  def mkDateTime(str: String, fmt: String = "yyyy-MM-dd") =
    DateTime.parse(str, DateTimeFormat.forPattern(fmt))

  def mkSpan(a: DateTime, b: DateTime) = new Interval(a, b.withTimeAtEndOfDay)

  def mkSpan(a: DateTime) = new Interval(a, a.withTimeAtEndOfDay)

  case class OverlapMatch(before: Option[Interval] = None, during: Option[Interval] = None, after: Option[Interval] = None)

  def overlapMatch(span1: Interval, span2: Interval):OverlapMatch = {
    import scalaz._, Scalaz._
    val (fst, snd) = if (span1 isBefore span2) (span1, span2) else (span2, span1)
    Option(fst overlap snd)
      // Partial or complete overlap/subsuming
      .map(o => OverlapMatch(
      before = if (fst.getStart isEqual o.getStart) None else fst.withEnd(o.getStart).some,
      during = o.some,
      after = if (snd.getEnd isEqual o.getEnd) None else snd.withStart(o.getEnd).some))
      // No overlap at all
      .getOrElse(OverlapMatch(before = fst.some, after = snd.some))
  }

  def withinSameMonth(a: DateTime, b: DateTime): Boolean = {
    a.getYear == b.getYear && a.getMonthOfYear == b.getMonthOfYear
  }

  // Return the given span as a list of spans broken up by month boundaries
  def breakIntoMonths(span: Interval): List[Interval] = {
    if (!withinSameMonth(span.getStart, span.getEnd)) { // Use this to properly support exclusive end-dates: .minusMillis(1))) {
      val head = span.withEnd(span.getStart.plusMonths(1).withDayOfMonth(1).withTimeAtStartOfDay.minusMillis(1))
      val tail = span.withStart(head.getEnd.plusMillis(1))
      head +: breakIntoMonths(tail)
    } else List(span)
  }

  def startOfTime: DateTime = {
    new DateTime(0,1,1,0,0).withTimeAtStartOfDay
  }

  def endOfTime: DateTime = {
    new DateTime(3000,1,1,0,0).withTimeAtEndOfDay
  }
}
