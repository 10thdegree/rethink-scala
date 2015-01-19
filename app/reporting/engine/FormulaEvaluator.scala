package reporting.engine

import org.joda.time.DateTime

object FormulaEvaluator {

  // Represents the current report
  case class Report(start: DateTime, end: DateTime)

  object EvaluationCxt {

    trait Row {
      def month: Int

      def year: Int

      def totalDaysInMonth: Int

      def reportDaysInMonth: Int

      def apply(key: String): Double

      def update(key: String, value: Double)

      def values: Map[String, Double]
    }
  }

  class EvaluationCxt[R](val report: Report) {

    case class Sum(count: Long, sum: BigDecimal)

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

      private val rowVals = collection.mutable.Map[String, Double]()

      def values = rowVals.toMap

      def apply(key: String): Double = rowVals(key)

      def update(key: String, value: Double) = {
        updateSums(this, key, value)
        rowVals(key) = value
      }
    }

    private val rows = collection.mutable.Map[R, EvaluationCxt.Row]()

    def allRows = rows.toMap

    def row(rowKey: R, date: DateTime): EvaluationCxt.Row = rows.getOrElseUpdate(rowKey, RowCxt(date)(report))

    private val monthSums = collection.mutable.Map[String, collection.mutable.Map[String, Sum]]()
    private val sums = collection.mutable.Map[String, Sum]()

    def sum(key: String): Double = sums(key).sum.toDouble

    def monthlySum(row: EvaluationCxt.Row)(key: String): Double = monthSums(row.year + "/" + row.month)(key).sum.toDouble

    def updateSums(row: EvaluationCxt.Row, key: String, value: Double) = {
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
      def monthly = 0d // agencyFeesLookup(row.month)

      def percentileMonthly(impressions: Int) = 0d //agencyFeesLookup(row.month, Some(impressions))
    }

    def servingFees(label: String) = ServingFees

    def agencyFees(label: String) = AgencyFees
  }

  import reporting.engine.AST._

  class Result(val value: BigDecimal, val format: Option[String]) {
    def toDouble: Double = value.toDouble

    def toLong: Long = value.toLong

    def toInt: Int = value.toInt

    val dfOpt = format.map(f => new java.text.DecimalFormat(f))

    def formatted = dfOpt.map(_.format(value)).getOrElse(value.toString())

    def +(that: Result) = new Result(this.value + that.value, this.format orElse that.format)

    def -(that: Result) = new Result(this.value - that.value, this.format orElse that.format)

    def *(that: Result) = new Result(this.value * that.value, this.format orElse that.format)

    def /(that: Result) = try {
      new Result(this.value / that.value, this.format orElse that.format)
    } catch {
      case c: java.lang.ArithmeticException => NoResult
    }
  }

  // Can't really extend a case class.
  case object NoResult extends Result(0, None) {
    override def +(that: Result) = NoResult

    override def -(that: Result) = NoResult

    override def *(that: Result) = NoResult

    override def /(that: Result) = NoResult
  }

  object Result {
    def apply[A: Numeric](value: A, format: Option[String] = None): Result = value match {
      case v: Double => new Result(v, format)
      case v: Float => new Result(v.toDouble, format)
      case v: Long => new Result(v, format)
      case v: Int => new Result(v, format)
      case v: BigDecimal => new Result(v, format)
      case v => new Result(BigDecimal(v.toString), format)
    }
  }

  def eval[A](term: Term)(implicit cxt: EvaluationCxt[A], rcxt: EvaluationCxt.Row): Result = term match {
    case t@Constant(v) => Result(t.toDouble)
    // Operators
    case Add(left, right) => eval(left) + eval(right)
    case Subtract(left, right) => eval(left) - eval(right)
    case Divide(left, right) => eval(left) / eval(right)
    case Multiply(left, right) => eval(left) * eval(right)
    // Deferred lookup
    case Variable(label) => Result(rcxt(label))
    // Row functions
    case AST.Row.TotalDaysInMonth => Result(rcxt.totalDaysInMonth)
    case AST.Row.ReportDaysInMonth => Result(rcxt.reportDaysInMonth)
    // Month functions
    case Month.Sum(n) => Result(cxt.monthlySum(rcxt)(n.label))
    // case MonthlyAvg(n) => cxt.monthlySum(n.label) / cxt.monthlyCount(n.label)
    // Global functions
    case t@WholeNumber(n) => Result(eval(n).toDouble)
    case t@FractionalNumber(n) => eval(n)
    // TODO (add copy back): case t@Format(n, fmt) => eval(n).copy(format = Some(fmt))
    case Sum(n) => Result(cxt.sum(n.label))
    // case Avg(n) => cxt.sum(n.label) / cxt.count(n.label)
    case Max(left, right) => Result(eval(left).value.max(eval(right).value)) // loses format
    // Fees
    case t@Fees.ServingFee(label, ft) if ft == Fees.ServingFeeTypes.Cpc => Result(cxt.servingFees(label).cpc)
    case t@Fees.ServingFee(label, ft) if ft == Fees.ServingFeeTypes.Cpm => Result(cxt.servingFees(label).cpm)
    case t@Fees.AgencyFee(label, ft, ref) if ft == Fees.AgencyFeeTypes.Monthly => Result(cxt.agencyFees(label).monthly)
    case t@Fees.AgencyFee(label, ft, ref) if ft == Fees.AgencyFeeTypes.PercentileMonth =>
      Result(cxt.agencyFees(label).percentileMonthly(ref.map(r => eval(r).toInt).getOrElse(0)))
  }

  def eval[R](row: R, date: DateTime, orderedTerms: List[LabeledTerm])(implicit cxt: EvaluationCxt[R]): Map[String, Double] = {
    implicit val rcxt = cxt.row(row, date)
    (for ((name, termO) <- orderedTerms; term <- termO) yield {
      val res = eval(term)
      rcxt(name) = res.toDouble // TODO: Should keep Result() instead of converting to Double
      name -> res.toDouble
    }).toMap
  }
}

