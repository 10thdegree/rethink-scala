package reporting.engine.eval

import java.text.DecimalFormat

import scalaz.Semigroup

// TODO: Extend Result instead, e.g. ResultWithHistory
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

