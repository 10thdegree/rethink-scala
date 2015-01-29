package reporting.engine

import org.joda.time.DateTime

object FormulaEvaluator {

  // Represents the current report
  case class Report(start: DateTime, end: DateTime)

  object EvaluationCxt {

    def formattedRowValues(values: Map[String, Result]) = values.map({case (k,v) => k -> v.formatted})

    trait Row {
      def month: Int

      def year: Int

      def dateStr = s"$year/$month"

      def totalDaysInMonth: Int

      def reportDaysInMonth: Int

      def apply(key: String): Result

      def update(key: String, value: Result)

      def values: Map[String, Result]

      def formattedValues: Map[String, String]
    }
  }

  import reporting.models.Fees.FeesLookup
  import reporting.models.Fees.{ServingFees => MServingFees}
  import reporting.models.Fees.{AgencyFees => MAgencyFees}

  class EvaluationCxt[R](val report: Report)(implicit servingFeesLookup: FeesLookup[MServingFees], agencyFeesLookup: FeesLookup[MAgencyFees]) {

    case class Sum(count: Long, sum: Result) {
      def +(that: Sum): Sum = Sum(this.count + that.count, this.sum + that.sum)
    }

    // Represents the current row being processed
    case class RowCxt(date: DateTime)(implicit report: Report) extends EvaluationCxt.Row {
      def month = date.getMonthOfYear

      def year = date.getYear

      def totalDays = date.dayOfMonth.getMaximumValue

      def currentDays = if (date.getMonthOfYear == report.end.getMonthOfYear) {
        date.getDayOfMonth
      } else if (date.getMonthOfYear == report.start.getMonthOfYear) {
        date.dayOfMonth.getMaximumValue - (report.start.getDayOfMonth - 1)
      } else {
        totalDays
      }

      // Number of days in the month, i.e. 28, 30, 31
      def totalDaysInMonth: Int = totalDays

      // Number of days in the report, e.g. if it's the 15th, 15, if the 3rd, 3.
      // At most, equals total days in month.
      def reportDaysInMonth: Int = currentDays

      private val rowVals = collection.mutable.Map[String, Result]()

      def values = rowVals.toMap

      def formattedValues = values.map({case (k,v) => k -> v.formatted})

      def apply(key: String): Result = rowVals(key)

      def update(key: String, value: Result) = {
        updateSums(this, key, value)
        rowVals(key) = value
      }
    }

    private val rows = collection.mutable.Map[R, EvaluationCxt.Row]()

    def allRows = rows.toMap

    def row(rowKey: R, date: DateTime): EvaluationCxt.Row = rows.getOrElseUpdate(rowKey, RowCxt(date)(report))

    private val monthSums = collection.mutable.Map[String, collection.mutable.Map[String, Sum]]()
    private val sums = collection.mutable.Map[String, Sum]()

    def sum(key: String): Result = sums(key).sum

    // TODO(dk): Rows need to represent spans(?) not single instants in time.
    def monthlySum(row: EvaluationCxt.Row)(key: String): Double = monthSums(row.year + "/" + row.month)(key).sum.toDouble

    // Return a map of dateStrs (yr/mo) to sums for the requested field.
    def monthlySums(key: String) = monthSums.map({ case (dateStr, vals) => dateStr -> vals(key) })

    def updateSums(row: EvaluationCxt.Row, key: String, value: Result) = {
      // Month sums
      val m = monthSums.getOrElseUpdate(row.year + "/" + row.month, collection.mutable.Map[String, Sum]())
      m(key) = m.get(key).map(s => s.copy(count = s.count + 1, sum = s.sum + value)).getOrElse(Sum(1, value))
      // Global sums
      sums(key) = sums.get(key).map(s => s.copy(count = s.count + 1, sum = s.sum + value)).getOrElse(Sum(1, value))
    }

    // TODO: Actually connect to getting the fees
    object ServingFees {
      def cpm = 0d

      def cpc = 0d
    }

    object AgencyFees {
      def fees(label: String) = monthlySums(label).values.reduce(_ + _).sum //agencyFeesLookup()

      def monthly = 0d // agencyFeesLookup(row.month)

      def percentileMonthly(impressions: Int) = 0d //agencyFeesLookup(row.month, Some(impressions))
    }

    // TODO: Use date range from Row, not report!
    import reporting.engine.AST.Fees.ServingFeeTypes.ServingFeeType
    import reporting.engine.AST.Fees.ServingFeeTypes
    def servingFees(label: String, rowCxt: EvaluationCxt.Row, dependsOn: Option[AST.Term], tpe: ServingFeeType) = {
      import reporting.engine.JodaTime.implicits._
      import org.joda.time._
      val lookup = servingFeesLookup
      val span = new Interval(report.start, report.end)
      val fees = for {
        s <- span.intoMonths.headOption // Should support more than 1 month
        year = s.getStart.getYear
        month = s.getStart.getMonthOfYear
        ll <- lookup(label)
        df <- dependsOn
        v = monthlySums(df.label)(year + "/" + month)
      } yield tpe match {
            // XXX(dk): This actually needs to ask for nested date ranges
        case ServingFeeTypes.Cpc => rowCxt(df.label) * ll(s).head.cpc
        case ServingFeeTypes.Cpm => rowCxt(df.label) * ll(s).head.cpm / 1000
      }
      //import Result.implicits._
      fees.getOrElse(Result(0))//.sum(Result.implicits.ResultNumeric)
    }

    def agencyFees(label: String, rowCxt: EvaluationCxt.Row, spend: AST.Term, impressions: AST.Term) = {
      import reporting.engine.JodaTime.implicits._
      import org.joda.time._
      val lookup = agencyFeesLookup
      val span = new Interval(report.start, report.end)
      val fees = for {
        s <- span.intoMonths.headOption // Should support more than 1 month
        year = s.getStart.getYear
        month = s.getStart.getMonthOfYear
        ll <- lookup(label)
        mvImpr = monthlySums(impressions.label)(year + "/" + month).sum
        vImpr = rowCxt(impressions.label)
        mvSpend = monthlySums(spend.label)(year + "/" + month).sum
        vSpend = rowCxt(spend.label)
        fees = ll(s).head(mvImpr.toLong)(mvSpend.value, s)
      } yield {
        (vSpend / mvSpend) * fees
      }
      play.Logger.debug(s"v = $fees")
      fees.getOrElse(Result.Zero)
    }
  }

  import reporting.engine.AST._

  class Result(val value: BigDecimal, val format: Option[String]) {
    def toDouble: Double = value.toDouble

    def toLong: Long = value.toLong

    def toInt: Int = value.toInt

    def asFractional: Result = {
      new Result(this.value + 0.0, format)
    }

    def rounded: Result = {
      new Result(this.value.rounded, format)
    }

    def withFormat(format2: String): Result = new Result(value, Some(format2))

    val dfOpt = format.map(f => new java.text.DecimalFormat(f))

    def formatted = dfOpt.map(_.format(value)).getOrElse(value.rounded.toString())

    def +(that: Result): Result = new Result(this.value + that.value, this.format orElse that.format)

    def -(that: Result): Result = new Result(this.value - that.value, this.format orElse that.format)

    def *(that: Result): Result = new Result(this.value * that.value, this.format orElse that.format)

    def /(that: Result): Result = try {
      new Result(this.value / that.value, this.format orElse that.format)
    } catch {
      case c: java.lang.ArithmeticException => NoResult
    }

    def +[A: Numeric](that: A): Result = this + Result(that)

    def -[A: Numeric](that: A): Result = this - Result(that)

    def *[A: Numeric](that: A): Result = this * Result(that)

    def /[A: Numeric](that: A): Result = this / Result(that)

    override def toString = formatted
  }

  case object NoResult extends Result(0, None) {

    override def formatted = "N/A"

    override def withFormat(format2: String): Result = this

    override def +(that: Result) = NoResult

    override def -(that: Result) = NoResult

    override def *(that: Result) = NoResult

    override def /(that: Result) = NoResult
  }

  object Result {

    case object Zero extends Result(0, None)

    object Formats {
      val WholeNumber = "#,###"
      val FractionalNumber = "#,###.##"
      val Nan = "N/A"
      val Currency = "\u00A4#,##0.00"
    }

    //def
    //bd.signum() == 0 || bd.scale() <= 0 || bd.stripTrailingZeros().scale() <= 0
    def guessFormat(v: BigDecimal): String = {

      if (v.isWhole()) Formats.WholeNumber
      else Formats.FractionalNumber

      //if (v.isValidInt) Formats.WholeNumber
      //else if (v.isValidLong) Formats.WholeNumber
      //else if (v.isExactFloat) Formats.FractionalNumber
      //else if (v.isExactDouble) Formats.FractionalNumber
      //else Formats.FractionalNumber
    }

    def apply[A: Numeric](value: A, format: Option[String] = None): Result = value match {
      case v: Double => new Result(v, format orElse Some(Formats.FractionalNumber))
      case v: Float => new Result(v.toDouble, format orElse Some(Formats.FractionalNumber))
      case v: Long => new Result(v, format orElse Some(Formats.WholeNumber))
      case v: Int => new Result(v, format orElse Some(Formats.WholeNumber))
      case v: BigDecimal => new Result(v, format orElse Some(guessFormat(v)))
      case v: Result => new Result(v.value, format orElse v.format)
      case v => new Result(BigDecimal(v.toString), format)
    }

    object implicits {
      import scala.math.Numeric
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

        override def negate(x: Result): Result = new Result(-x.value, x.format)

        override def fromInt(x: Int): Result = new Result(x, Some(Formats.WholeNumber))

        override def toLong(x: Result): Long = x.toLong

        override def times(x: Result, y: Result): Result = x * y

        override def minus(x: Result, y: Result): Result = x - y

        override def compare(x: Result, y: Result): Int = x.value.compare(y.value)
      }
    }
  }

  import Result.implicits._
  import reporting.engine.AST.Fees

  def eval[A](term: Term)(implicit cxt: EvaluationCxt[A], rcxt: EvaluationCxt.Row): Result = term match {
    case t@Constant(v) => Result(t.toDouble)
    // Operators
    case Add(left, right) => eval(left) + eval(right)
    case Subtract(left, right) => eval(left) - eval(right)
    case Divide(left, right) => eval(left) / eval(right)
    case Multiply(left, right) => eval(left) * eval(right)
    // Deferred lookup
    case Variable(label) => rcxt(label)
    // Row functions
    case AST.Row.TotalDaysInMonth => Result(rcxt.totalDaysInMonth)
    case AST.Row.ReportDaysInMonth => Result(rcxt.reportDaysInMonth)
    // Month functions
    case Month.Sum(n) => Result(cxt.monthlySum(rcxt)(n.label))
    // case MonthlyAvg(n) => cxt.monthlySum(n.label) / cxt.monthlyCount(n.label)
    case ForcedDependancy(n, _) => eval(n) // Ignore dep entirely, just there for ordering
    // Global functions
    case t@WholeNumber(n) => eval(n).rounded.withFormat(Result.Formats.WholeNumber)
    case t@FractionalNumber(n) => eval(n).asFractional.withFormat(Result.Formats.FractionalNumber)
    case t@Format(n, fmt) => eval(n).withFormat(fmt)
    case Sum(n) => cxt.sum(n.label)
    // case Avg(n) => cxt.sum(n.label) / cxt.count(n.label)
    case Max(left, right) => Result(eval(left).value.max(eval(right).value)) // loses format
    // Fees
    case t@Fees.ServingFee(label, ft, ref) => cxt.servingFees(label, rcxt, ref, ft)
    case t@Fees.AgencyFee(label, spend, impressions) => cxt.agencyFees(label, rcxt, spend, impressions)
  }

  def eval[R](row: R, date: DateTime, orderedTerms: List[LabeledTerm])(implicit cxt: EvaluationCxt[R]): Map[String, Result] = {
    implicit val rcxt = cxt.row(row, date)
    (for ((name, termO) <- orderedTerms; term <- termO) yield {
      val res = eval(term)
      rcxt(name) = res
      name -> res
    }).toMap
  }
}

