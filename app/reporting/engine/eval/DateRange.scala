package reporting.engine.eval

import org.joda.time.{Interval, LocalDate}

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
