package reporting.engine

import javax.script.ScriptContext

import org.joda.time.DateTime

// Extensions for Groovy-interop to get nice operators for formulae.
private[engine] object Groovy {

  trait Closure0[O] {
    def call(): O = apply()

    def apply(): O
  }

  trait Closure1[A, O] {
    def call(arg: A): O = apply(arg)

    def apply(arg: A): O
  }

  trait Closure2[A, B, O] {
    def call(arg: A, arg2: B): O = apply(arg, arg2)

    def apply(arg: A, arg2: B): O
  }

  trait Accessor[K, A] {
    def apply(key: K): A

    def getAt(key: K): A = apply(key)
  }

  trait Ops[T] {
    def +(that: T): T

    def -(that: T): T

    def /(that: T): T

    def *(that: T): T

    def unary_-(): T

    def plus(that: T): T = this + that

    def minus(that: T): T = this - that

    def div(that: T): T = this / that

    def multiply(that: T): T = this * that

    def negative(): T = this.unary_-()
  }

}

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

    case class Sum(count: Long, sum: Double)

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

    def sum(key: String): Double = sums(key).sum

    def monthlySum(row: EvaluationCxt.Row)(key: String): Double = monthSums(row.year + "/" + row.month)(key).sum

    def updateSums(row: EvaluationCxt.Row, key: String, value: Double) = {
      // Month sums
      val m = monthSums.getOrElseUpdate(row.year + "/" + row.month, collection.mutable.Map[String, Sum]())
      m(key) = m.get(key).map(s => s.copy(count = s.count + 1, sum = s.sum + value)).getOrElse(Sum(1, value))
      // Global sums
      sums(key) = sums.get(key).map(s => s.copy(count = s.count + 1, sum = s.sum + value)).getOrElse(Sum(1, value))
    }

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

  case class Fees() {
    def agency(label: String): AgencyFees = AgencyFees(label)

    def serving(label: String): ServingFees = ServingFees(label)
  }

  object ServingFeeTypes {

    case class ServingFeeType(name: String)

    val Cpc = ServingFeeType("cpc")

    val Cpm = ServingFeeType("cpm")
  }

  case class ServingFee(override val label: String, feeType: ServingFeeTypes.ServingFeeType) extends AST.Term

  case class ServingFees(label: String) {
    def cpc: AST.Term = ServingFee(label, ServingFeeTypes.Cpc)

    def cpm: AST.Term = ServingFee(label, ServingFeeTypes.Cpm)
  }

  object AgencyFeeTypes {

    case class AgencyFeeType(name: String)

    val Monthly = AgencyFeeType("monthly")

    val PercentileMonth = AgencyFeeType("percentileMonth")
  }

  case class AgencyFee(override val label: String, feeType: AgencyFeeTypes.AgencyFeeType, ref: Option[AST.Term] = None) extends AST.Term

  case class AgencyFees(label: String) {
    def monthly: AST.Term = AgencyFee(label, AgencyFeeTypes.Monthly)

    def percentileMonth(impressions: AST.Term): AST.Term = AgencyFee(label, AgencyFeeTypes.Monthly, Some(impressions))
  }

  import reporting.engine.AST._

  // TODO: Use this
  abstract class Result[A: Numeric] {
    val ops = implicitly[Numeric[A]]

    def value: A

    def format: Option[String]

    def +[B: Numeric](that: Result[B]): Result[A]

    def formatted = format.map(_.format(value)).getOrElse(value.toString)
  }

  object Result {

    case class WholeNumber(value: Long, format: Option[String] = None) extends Result[Long] {
      def +[B: Numeric](that: Result[B]) = WholeNumber(value = ops.plus(this.value, that.ops.toLong(that.value)), format = format)
    }

    case class FractionalNumber(value: Double, format: Option[String] = None) extends Result[Double] {
      def +[B: Numeric](that: Result[B]) = FractionalNumber(value = ops.plus(this.value, that.ops.toDouble(that.value)), format = format)
    }

  }

  // XXX: Should instead return a Result()
  def eval[A](term: Term)(implicit cxt: EvaluationCxt[A], rcxt: EvaluationCxt.Row): Double = term match {
    case t@Constant(v) => t.toDouble
    // Operators
    case Add(left, right) => eval(left) + eval(right)
    case Subtract(left, right) => eval(left) + eval(right)
    case Divide(left, right) => eval(left) + eval(right)
    case Multiply(left, right) => eval(left) + eval(right)
    // Deferred lookup
    case Variable(label) => rcxt(label)
    // Row functions
    case AST.Row.TotalDaysInMonth => rcxt.totalDaysInMonth
    case AST.Row.ReportDaysInMonth => rcxt.reportDaysInMonth
    // Month functions
    case Month.Sum(n) => cxt.monthlySum(rcxt)(n.label)
    // case MonthlyAvg(n) => cxt.monthlySum(n.label) / cxt.monthlyCount(n.label)
    // Global functions
    case t@WholeNumber(n) => eval(n).toLong
    case t@FractionalNumber(n) => eval(n)
    // case t@Format(n, fmt) => eval(n) // TODO: handle format
    case Sum(n) => cxt.sum(n.label)
    // case Avg(n) => cxt.sum(n.label) / cxt.count(n.label)
    case Max(left, right) => math.max(eval(left), eval(right))
    // Fees
    case ServingFee(label, ft) if ft == ServingFeeTypes.Cpc => cxt.servingFees(label).cpc
    case ServingFee(label, ft) if ft == ServingFeeTypes.Cpm => cxt.servingFees(label).cpm
    case AgencyFee(label, ft, ref) if ft == AgencyFeeTypes.Monthly => cxt.agencyFees(label).monthly
    case AgencyFee(label, ft, ref) if ft == AgencyFeeTypes.PercentileMonth =>
      cxt.agencyFees(label).percentileMonthly(ref.map(r => eval(r)).getOrElse(0d).toInt)
  }

  def eval[R](row: R, date: DateTime, orderedTerms: List[LabeledTerm])(implicit cxt: EvaluationCxt[R]): Map[String, Double] = {
    (for ((name, termO) <- orderedTerms; term <- termO) yield {
      implicit val rcxt = cxt.row(row, date)
      val res = eval(term)
      rcxt(name) = res
      name -> res
    }).toMap
  }
}

object AST {

  type LabeledTerm = (String, Option[Term])

  /*
   * We need to be able to support the following operations:
   *
   * constants: 7 + 1
   * bindable fields: somefield + anotherfield
   * aliases: foo = 5; foo * 2
   * global aggregate functions:
   *   - sum(field)
   *   - max(field)
   *   - avg(field).[mean|median|mode]
   * global functions:
   *   - round(expression)
   *   - fractional(expression)
   *   - format(expression, "%2.4f")
   * month-based aggregate functions:
   *   - month.sum(field)
   *   - month.avg(field).[mean|median|mode]
   * row-based constants:
   *   - row.totalDaysInMonth
   *   - row.reportDaysInMonth
   * fee functions:
   *   - fees.agency("label").monthly
   *   - fees.agency("label").percentileMonth
   *   - fees.serving("label").cpc
   *   - fees.serving("label").cpm
   */

  type TermLookup = String => Option[Term]

  trait Term extends Groovy.Ops[Term] {
    def +(that: Term): Term = Add(this, that)

    def -(that: Term): Term = Subtract(this, that)

    def /(that: Term): Term = Divide(this, that)

    def *(that: Term): Term = Multiply(this, that)

    def unary_-() = Multiply(this, new Constant(-1))

    def +[A: Numeric](that: A): Term = this + new Constant(that)

    def -[A: Numeric](that: A): Term = this - new Constant(that)

    def /[A: Numeric](that: A): Term = this / new Constant(that)

    def *[A: Numeric](that: A): Term = this * new Constant(that)

    def label: String = ""

    final def has(f: Term => Boolean)(implicit tl: TermLookup): Boolean = f(this) || contains(f)

    protected[this] def contains(f: Term => Boolean)(implicit tl: TermLookup): Boolean = false
  }

  trait OpTerm extends Term {
    def left: Term

    def right: Term

    override def contains(f: Term => Boolean)(implicit tl: TermLookup): Boolean = (left has f) || (right has f)
  }

  trait WrappedTerm extends Term {
    def term: Term

    override def contains(f: Term => Boolean)(implicit tl: TermLookup): Boolean = term has f
  }

  case class Constant[A: Numeric](v: A) extends Term {
    val ops = implicitly[Numeric[A]]
    val toDouble = ops.toDouble(v)
    val toLong = ops.toLong(v)
  }

  case class WholeNumber(term: Term) extends WrappedTerm

  case class FractionalNumber(term: Term) extends WrappedTerm

  case class Format(term: Term, fmt: String) extends WrappedTerm

  case class Max(left: Term, right: Term) extends OpTerm

  case class Sum(term: Term) extends WrappedTerm

  case class Variable(override val label: String) extends Term {
    override def contains(f: Term => Boolean)(implicit tl: TermLookup): Boolean = tl(label) exists (_ has f)
  }

  case class Add(left: Term, right: Term) extends OpTerm

  case class Subtract(left: Term, right: Term) extends OpTerm

  case class Divide(left: Term, right: Term) extends OpTerm

  case class Multiply(left: Term, right: Term) extends OpTerm

  // Represents the current row being processed
  object Row {

    case object TotalDaysInMonth extends Term

    case object ReportDaysInMonth extends Term

    def totalDaysInMonth = TotalDaysInMonth

    def reportDaysInMonth = ReportDaysInMonth
  }

  object Month {

    case class Sum(term: Term) extends WrappedTerm

    def sum(term: Term): Sum = Sum(term)
  }

  object Functions {

    def max(left: Term, right: Term) = Max(left, right)

    def fractionalNumber(term: Term) = FractionalNumber(term)

    def wholeNumber(term: Term) = WholeNumber(term)

    def format(term: Term, format: String) = Format(term, format)

    def sum(term: Term): Sum = Sum(term)

    object functions {
      val sum = new Function1[AST.Term, AST.Term] with Groovy.Closure1[AST.Term, AST.Term] {
        def apply(arg: AST.Term): AST.Term = AST.Functions.sum(arg)
      }

      val format = new Function2[AST.Term, String, AST.Term] with Groovy.Closure2[AST.Term, String, AST.Term] {
        def apply(arg: AST.Term, fmt: String): AST.Term = AST.Functions.format(arg, fmt)
      }

      val max = new Function2[AST.Term, AST.Term, AST.Term] with Groovy.Closure2[AST.Term, AST.Term, AST.Term] {
        def apply(arg: AST.Term, arg2: AST.Term): AST.Term = AST.Functions.max(arg, arg2)
      }

      val wholeNumber = new Function1[AST.Term, AST.Term] with Groovy.Closure1[AST.Term, AST.Term] {
        def apply(arg: AST.Term): AST.Term = AST.Functions.wholeNumber(arg)
      }

      val fractionalNumber = new Function1[AST.Term, AST.Term] with Groovy.Closure1[AST.Term, AST.Term] {
        def apply(arg: AST.Term): AST.Term = AST.Functions.wholeNumber(arg)
      }
    }

  }

}

case class ReportDisplay()

//(reportInstance: ReportInstance, rows: DataSource.Row)

object FormulaCompiler {

  import reporting.engine.AST._

  implicit def TermOrdering(implicit tl: TermLookup): Ordering[LabeledTerm] = Ordering fromLessThan {
    (a, b) => !a._2.exists(_.has(_.label == b._1))
  }

  def segment(terms: LabeledTerm*) = {
    case class State(groups: List[List[LabeledTerm]] = List())
    def toLookup(list: List[LabeledTerm]) = list.toMap.withDefaultValue(None)
    def hasDependency(cur: List[LabeledTerm])(t: LabeledTerm) = {
      cur.exists(c => t._2.exists(_.has(_.label == c._1)(toLookup(cur))))
    }

    terms.foldLeft(State()) { (accum, t) =>
      accum.groups.headOption match {
        case None => accum.copy(groups = List(t) :: Nil)
        case Some(cur) if hasDependency(cur)(t) => accum.copy(groups = List(t) :: accum.groups)
        case Some(cur) => accum.copy(groups = (t :: cur) :: accum.groups.tail)
      }
    }.groups.map(_.reverse).reverse
  }
}

// varNames will be accessible and able-to-be-referenced variables when compiling expressions.
class FormulaCompiler(varNames: String*) {

  import javax.script.{ScriptEngine, ScriptEngineManager, ScriptException}

  val engine = new ScriptEngineManager().getEngineByName("groovy")

  def env(e: ScriptEngine)(props: (String, AnyRef)*) {
    val b = e.createBindings()
    b.put("month", AST.Month)
    b.put("row", AST.Row)
    b.put("func", AST.Functions)

    import AST.Functions.{functions => funcs}
    b.put("sum", funcs.sum)
    b.put("format", funcs.format)
    b.put("max", funcs.max)
    b.put("wholeNumber", funcs.wholeNumber)
    b.put("fractionalNumber", funcs.wholeNumber)
    // TODO: fees
    for ((k, v) <- props) b.put(k, v)
    e.setBindings(b, ScriptContext.ENGINE_SCOPE)
  }

  def initVars(e: ScriptEngine)(vars: String*) = env(e)(vars.map(v => v -> AST.Variable(v)): _*)

  initVars(engine)(varNames: _*)

  def apply(formula: String) = compile(formula)

  // Convert formula to an AST, referencing needed aggregates for deferred computation
  def compile(formula: String): AST.Term = {
    try {
      engine.eval(formula).asInstanceOf[Any] match {
        case t: AST.Term => t
        case n: Int => new AST.Constant(n)
        case n: Long => new AST.Constant(n)
        case n: Float => new AST.Constant(n)
        case n: Double => new AST.Constant(n)
        case n => throw new ClassCastException(s"${n.getClass} is not supported as the result of an expression.")
      }
    } catch {
      case ex: ClassCastException =>
        ex.printStackTrace()
        throw ex
      case ex: ScriptException =>
        ex.printStackTrace()
        throw ex
    }
  }
}
