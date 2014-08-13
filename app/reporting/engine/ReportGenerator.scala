package reporting.engine

import java.util.UUID

import com.google.inject.Inject
import org.joda.time.DateTime
import reporting.models.DataSources.{DataSource, DataSourceType}
import reporting.models.{Field, Report}

import scala.concurrent.{Await, Future, ExecutionContext}

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
   * month-based aggregate functions:
   *   - month.sum(field)
   *   - month.max(field)
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

  // TODO: All terms probably need an implicit environment reference

  // Represents the current report
  case class Report(start: DateTime, end: DateTime)

  // Represents the current row being processed
  case class Row(date: DateTime)(implicit report: Report) {
    def month = date.getMonthOfYear

    def totalDays = date.dayOfMonth.getMaximumValue

    def currentDays = if (date.getMonthOfYear == report.end.getMonthOfYear) {
      date.getDayOfMonth
    } else if (date.getMonthOfYear == report.start.getMonthOfYear) {
      date.dayOfMonth.getMaximumValue - (report.start.getDayOfMonth - 1)
    } else {
      totalDays
    }

    // Number of days in the month, i.e. 28, 30, 31
    def totalDaysInMonth: Value[Int] = new Value(totalDays)

    // Number of days in the report, e.g. if it's the 15th, 15, if the 3rd, 3.
    // At most, equals total days in month.
    def reportDaysInMonth: Value[Int] = new Value(currentDays)

  }

  case class Fees(agencyLookup: String => AgencyFee, servingLookup: String => ServingFee)(implicit row: Row, report: Report) {
    def agency(label: String): AgencyFee = agencyLookup(label)

    def serving(label: String): ServingFee = servingLookup(label)
  }

  case class ServingFee(_cpc: Double, _cpm: Double) {
    def cpc: Value[Double] = new Value(_cpc)

    def cpm: Value[Double] = new Value(_cpm)
  }

  case class AgencyFee(percentileLookup: Int => Double, monthlyLookup: Int => Double)(implicit row: Row) {
    def monthly: Value[Double] = new Value(monthlyLookup(row.month))

    def percentileMonth(impressions: Term): Value[Double] = new Value(percentileLookup(impressions.eval.toInt))
  }

  trait GroovyAccessor[K, A] {
    def apply(key: K): A
    def getAt(key: K): A = apply(key)
  }

  trait GroovyOps[T] {
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

  trait Term extends GroovyOps[Term] {
    def +(that: Term): Term = Add(this, that)
    def -(that: Term): Term = Subtract(this, that)
    def /(that: Term): Term = Divide(this, that)
    def *(that: Term): Term = Multiply(this, that)

    def unary_-() = Multiply(this, new Value(-1))

    def +[A:Numeric](that: A): Term = this + new Value(that)
    def -[A:Numeric](that: A): Term = this - new Value(that)
    def /[A:Numeric](that: A): Term = this / new Value(that)
    def *[A:Numeric](that: A): Term = this * new Value(that)

    def eval: Double
  }

  class Value[A: Numeric](v: => A) extends Term {
    val ops = implicitly[Numeric[A]]

    def eval = ops.toDouble(v)
  }

  case class WholeNumber(t: Term) extends Term {
    def eval = t.eval.toLong.toDouble
  }

  case class FractionalNumber(t: Term) extends Term {
    def eval = t.eval
  }

  class Variable[A: Numeric](label: String, v: => A) extends Term {
    val value = new Value[A](v)

    def eval = value.eval
  }

  case class Add(left: Term, right: Term) extends Term {
    def eval = left.eval + right.eval
  }

  case class Subtract(left: Term, right: Term) extends Term {
    def eval = left.eval - right.eval
  }

  case class Divide(left: Term, right: Term) extends Term {
    def eval = left.eval / right.eval
  }

  case class Multiply(left: Term, right: Term) extends Term {
    def eval = left.eval * right.eval
  }

}

case class ReportDisplay()

//(reportInstance: ReportInstance, rows: DataSource.Row)

// These is our evaluation environment; it knows about state when processing rows.
// TODO: Create scripting environment for evaluating scala expressions using our AST
class FormulaEvaluator(/* TODO: Pass in fees and other needed state */) {

  import javax.script.{ScriptException, ScriptContext, ScriptEngineManager, ScriptEngine}

  val engine = {
    val e = new ScriptEngineManager().getEngineByName("groovy")
    //val settings = engine.asInstanceOf[scala.tools.nsc.interpreter.IMain].settings
    // MyScalaClass is just any class in your project
    env(e)("foo" -> new AST.Value(10), "bar" -> new AST.Value(5))
    //settings.embeddedDefaults[MyScalaClass]
    e
  }

  def env(e: ScriptEngine)(props: (String, Any)*): (ScriptEngine, () => Unit) = {
    for {
      (key, value) <- props
    } e.getContext.setAttribute(key, value, ScriptContext.ENGINE_SCOPE)

    val cleanup = () => for ((key, _) <- props) e.getContext.removeAttribute(key, ScriptContext.ENGINE_SCOPE)
    e -> cleanup
  }

  //case class FEInput(date: DateTime, formulae: List[(String, String)])

  // Convert formula to an AST, referencing needed aggregates for deferred computation
  def eval(formula: String)(row: DataSource.Row): AST.Term = {
    // TODO: Set row specific properties here.
    try {
      engine.eval(formula).asInstanceOf[Any] match {
        case t: AST.Term => t
        case n: Int => new AST.Value(n)
        case n: Long => new AST.Value(n)
        case n: Float => new AST.Value(n)
        case n: Double => new AST.Value(n)
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
  def wrap[A:Numeric](attr: String, value: A): AST.Term = new AST.Variable(attr, value)
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

class ReportGenerator @Inject() (dsFinder: DataSourceFinder, fieldFinder: FieldFinder) {

  import scalaz._, Scalaz._

  import scala.concurrent.duration._

  def getReport(report: Report)(start: DateTime, end: DateTime)(implicit ec: ExecutionContext): ReportDisplay = {

    val dsRowsF: Seq[Future[(DataSource, Seq[DataSource.Row])]] = for {
      dsBinding <- report.dsBindings
      ds <- dsFinder(report.accountId)(dsBinding.dataSourceId).toList
      data = ds.dataForRange(start, end) // applies filters and merges rows by keyselectors
    } yield data.map(ds -> _)

    val dsRows = Await
      .result(dsRowsF.toList.sequence[Future, (DataSource, Seq[DataSource.Row])], 5.minutes)
      .flatMap({case (key, list) =>  list.map(i => key -> i)})

    import Joda._
    // A map of DSes to optional rows coalesced together by date
    val rows: Seq[(DateTime, Map[DataSource, DataSource.Row])] =
      dsRows.groupBy(_._2.date).toSeq
        .sortBy(x => x._1)
        .map({ case (date, tuples) => date -> tuples.toMap})

    // TODO: Merge DSes for each date, if attributes collide, sum them or error.
    val mergedRows = rows.map({ case (date, dses) => date -> dses.head._2})

    val fe = new FormulaEvaluator(/* TODO: PASS IN NEEDED ENV INFO */)

    // TODO: Apply rules engine to report fields with data
    val rowsAst = for ((date, row) <- mergedRows) yield
    // For each row, replace the attributes with an AST of the formulae
      row.copy(attributes = (for {
      // TODO: We need to iterate over the template/view fields,
      // and _optionally_ pull binded fields in the case we have one
        fieldBinding <- report.fieldBindings
        field <- fieldFinder(report.accountId)(fieldBinding.fieldId)
        attr = fieldBinding.dataSourceAttribute
      } yield field.formula match {
          case Some(formula) => attr -> fe.eval(formula)(row)
          case None => attr -> fe.wrap(attr, 0)//row.attributes(attr))
        }).toMap)

    // TODO
    rowsAst

    null
  }
}
