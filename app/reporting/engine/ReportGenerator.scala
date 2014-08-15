package reporting.engine

import java.util.UUID

import com.google.inject.Inject
import org.joda.time.DateTime
import reporting.models.DataSources.{DataSource, DataSourceType}
import reporting.models.{Field, Report}

import scala.concurrent.{Await, Future, ExecutionContext}

object Groovy {

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

  // Represents the current row being processed
  case class Row(date: DateTime)(implicit report: Report) {
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
  }

  object EvaluationCxt {
    class RowEvaluationCxt()
  }

  // TODO: Modified EvaluationCxt to be the global evaluation context, and
  // TODO: Create a RowEvalCxt() that gets emitted from the global context via "def row(date)"
  class EvaluationCxt(val report: Report, val row: Row) {

    case class Sum(count: Long, sum: Double)

    private val rowVals = collection.mutable.Map[String, Double]()
    private val monthSums = collection.mutable.Map[String, collection.mutable.Map[String, Sum]]()
    private val sums = collection.mutable.Map[String, Sum]()

    def sum(key: String): Double = sums(key).sum

    def monthlySum(key: String): Double = monthSums(row.year + "/" + row.month)(key).sum

    def apply(key: String): Double = rowVals(key)

    def update(key: String, value: Double) = {
      // Month sums
      val m = monthSums.getOrElseUpdate(row.year + "/" + row.month, collection.mutable.Map[String, Sum]())
      m(key) = m.get(key).map(s => s.copy(count = s.count + 1, sum = s.sum + value)).getOrElse(Sum(1, value))
      // Global sums
      sums(key) = sums.get(key).map(s => s.copy(count = s.count + 1, sum = s.sum + value)).getOrElse(Sum(1, value))
      // Row value
      rowVals(key) = value
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

  import AST._

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

  // Should instead return a Result() instead
  def eval(term: Term)(implicit cxt: EvaluationCxt): Double = term match {
    case t@Constant(v) => t.toDouble
    // Operators
    case Add(left, right) => eval(left) + eval(right)
    case Subtract(left, right) => eval(left) + eval(right)
    case Divide(left, right) => eval(left) + eval(right)
    case Multiply(left, right) => eval(left) + eval(right)
    // Deferred lookup
    case Variable(label) => cxt(label)
    // Row functions
    case AST.Row.TotalDaysInMonth => cxt.row.totalDaysInMonth
    case AST.Row.ReportDaysInMonth => cxt.row.reportDaysInMonth
    // Month functions
    case Month.Sum(n) => cxt.monthlySum(n.label)
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
      cxt.agencyFees(label).percentileMonthly(ref.map(eval).getOrElse(0d).toInt)
  }

  type LabeledTerm = (String, Option[Term])

  def eval(orderedTerms: List[LabeledTerm])(implicit cxt: EvaluationCxt): Map[String, Double] = {
    // TODO: update cxt with results of each eval()
    (for ((name, termO) <- orderedTerms) yield {
      val term = termO.get
      val res = eval(term)
      cxt(name) = res // Update context with result so it can be used in subsequent loops
      name -> res
    }).toMap
  }

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

object AST {

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

  }

  object Month {

    case class Sum(term: Term) extends WrappedTerm

  }

}

case class ReportDisplay()

//(reportInstance: ReportInstance, rows: DataSource.Row)

// These is our evaluation environment; it knows about state when processing rows.
class FormulaEvaluator(/* TODO: Pass in fees and other needed state */) {

  import javax.script.{ScriptException, ScriptContext, ScriptEngineManager, ScriptEngine}

  val engine = {
    val e = new ScriptEngineManager().getEngineByName("groovy")
    env(e)("foo" -> new AST.Constant(10), "bar" -> new AST.Constant(5))
    e
  }

  def env(e: ScriptEngine)(props: (String, AnyRef)*): ScriptEngine = {
    import collection.JavaConverters._
    val b = e.createBindings()
    b.putAll(props.toMap.asJava)
    e
  }

  //case class FEInput(date: DateTime, formulae: List[(String, String)])

  // Convert formula to an AST, referencing needed aggregates for deferred computation
  def eval(formula: String)(row: DataSource.Row): AST.Term = {
    // TODO: Set row specific properties here.
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

  // Take a discrete value and wrap it in an AST node
  //def wrap[A: Numeric](attr: String, value: A): AST.Term = new AST.Variable(attr, value)
}

trait DataSourceFinder {
  def apply(accountId: UUID)(dsId: UUID): Option[DataSource] = { ??? }
}

trait FieldFinder {
  def apply(accountId: UUID)(fieldId: UUID): Option[Field] = { ??? }
}

object Joda {
  implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isBefore _)
}

class ReportGenerator @Inject()(dsFinder: DataSourceFinder, fieldFinder: FieldFinder) {

  import scalaz._, Scalaz._

  import scala.concurrent.duration._

  def getReport(report: Report)(start: DateTime, end: DateTime)(implicit ec: ExecutionContext): ReportDisplay = {

    // Get a list of futures for each data source
    val dsRowsF: Seq[Future[(DataSource, Seq[DataSource.Row])]] = for {
      dsBinding <- report.dsBindings
      ds <- dsFinder(report.accountId)(dsBinding.dataSourceId).toList
      data = ds.dataForRange(start, end) // applies filters and merges rows by keyselectors
    } yield data.map(ds -> _)

    // Get a single future for all of them, and await the result
    val dsRows = Await
      .result(dsRowsF.toList.sequence[Future, (DataSource, Seq[DataSource.Row])], 2.minutes)
      .flatMap({ case (key, list) => list.map(i => key -> i)})

    // Group the results by date, with a map of data sources to rows
    import Joda._
    val rows: Seq[(DateTime, Map[DataSource, DataSource.Row])] =
      dsRows.groupBy(_._2.date).toSeq
        .sortBy(x => x._1)
        .map({ case (date, tuples) => date -> tuples.toMap})

    // TODO: Merge DSes for each date, if attributes collide, sum them or error.
    val mergedRows = rows.map({ case (date, dses) => date -> dses.head._2})

    val fe = new FormulaEvaluator(/* TODO: PASS IN NEEDED ENV INFO */)

    // TODO: Apply rules engine to report fields with data
    val rowsAst = for ((date, row) <- mergedRows) yield {
      // For each row, replace the attributes with an AST of the formulae
      row.copy(attributes = (for {
      // TODO: We need to iterate over the template/view fields,
      // and _optionally_ pull binded fields in the case we have one
        fieldBinding <- report.fieldBindings
        field <- fieldFinder(report.accountId)(fieldBinding.fieldId)
        attr = fieldBinding.dataSourceAttribute
      } yield field.formula match {
          case Some(formula) => attr -> fe.eval(formula)(row)
          //case None => attr -> fe.wrap(attr) //row.attributes(attr))
        }).toMap)
    }

    // TODO
    rowsAst

    null
  }
}
