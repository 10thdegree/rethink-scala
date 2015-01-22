package reporting.engine

import javax.script.ScriptContext

// Extensions for Groovy-interop to get nice operators for formulae.
private[engine] object GroovyInterop {

  trait Closure0[+O] {
    def call(): O = apply()

    def apply(): O
  }

  trait Closure1[-A, +O] {
    def call(arg: A): O = apply(arg)

    def apply(arg: A): O
  }

  trait Closure2[-A, -B, +O] {
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

object AST {

  /*
   * We need to be able to support the following operations:
   *
   * constants: 7 + 1
   * bindable fields: somefield or anotherfield
   * aliases: foo = 5; foo * 2
   * global aggregate functions:
   *   - sum(field)
   *   - max(field)
   *   - avg(field).[mean|median|mode]
   * global functions:
   *   - round(expression)
   *   - fractional(expression)
   *   - format(expression, "#,###.00")
   * month-based aggregate functions:
   *   - month.sum(field)
   *   - month.avg(field).[mean|median|mode]
   * row-based constants:
   *   - row.totalDaysInMonth
   *   - row.reportDaysInMonth
   * fee functions:
   *   - fees.agency("label").monthly
   *   - fees.agency("label").percentileMonth(impressions)
   *   - fees.serving("label").cpc
   *   - fees.serving("label").cpm
   */

  type LabeledTerm = (String, Option[Term])

  type TermLookup = String => Option[Term]

  trait Term extends GroovyInterop.Ops[Term] {
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

    final def has(f: Term => Boolean)(implicit tl: TermLookup): Boolean = {
      f(this) || contains(f)
    }

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

  object Fees {

    def agency(label: String): AgencyFees = AgencyFees(label)

    def serving(label: String): ServingFees = ServingFees(label)

    object ServingFeeTypes {

      case class ServingFeeType(name: String)

      val Cpc = ServingFeeType("cpc")

      val Cpm = ServingFeeType("cpm")
    }

    case class ServingFee(override val label: String, feeType: ServingFeeTypes.ServingFeeType) extends AST.Term

    case class ServingFees(label: String) {
      val cpc: AST.Term = ServingFee(label, ServingFeeTypes.Cpc)
      val cpm: AST.Term = ServingFee(label, ServingFeeTypes.Cpm)
    }

    object AgencyFeeTypes {

      case class AgencyFeeType(name: String)

      val Monthly = AgencyFeeType("monthly")

      val PercentileMonth = AgencyFeeType("percentileMonth")
    }

    case class AgencyFee(override val label: String, feeType: AgencyFeeTypes.AgencyFeeType, ref: Option[AST.Term] = None) extends AST.Term

    case class AgencyFees(label: String) {
      val monthly: AST.Term = AgencyFee(label, AgencyFeeTypes.Monthly)

      def percentileMonth(impressions: AST.Term): AST.Term = AgencyFee(label, AgencyFeeTypes.PercentileMonth, Some(impressions))
    }

  }

  object Row {

    case object TotalDaysInMonth extends Term

    case object ReportDaysInMonth extends Term

    val totalDaysInMonth = TotalDaysInMonth

    val reportDaysInMonth = ReportDaysInMonth
  }

  object Month {

    case class Sum(term: Term) extends WrappedTerm

    def sum(term: Term): Sum = Sum(term)
  }

  object Functions {

    def max(left: Term, right: Term) = Max(left, right)

    def fractional(term: Term) = FractionalNumber(term)

    def round(term: Term) = WholeNumber(term)

    def format(term: Term, format: String) = Format(term, format)

    val CurrencyFormat = "\u00A4#,##0.00"

    def currency(term: Term) = Format(term, CurrencyFormat)

    val PercentageFormat = "#%"

    def percentage(term: Term) = Format(term, PercentageFormat)

    def sum(term: Term): Sum = Sum(term)

    // TODO: Autogenerate this from the above
    object functions {
      val sum = new Function1[AST.Term, AST.Term] with GroovyInterop.Closure1[AST.Term, AST.Term] {
        def apply(arg: AST.Term): AST.Term = AST.Functions.sum(arg)
      }

      val format = new Function2[AST.Term, String, AST.Term] with GroovyInterop.Closure2[AST.Term, String, AST.Term] {
        def apply(arg: AST.Term, fmt: String): AST.Term = AST.Functions.format(arg, fmt)
      }

      val currency = new Function1[AST.Term, AST.Term] with GroovyInterop.Closure1[AST.Term, AST.Term] {
        def apply(arg: AST.Term): AST.Term = AST.Functions.currency(arg)
      }

      val percentage = new Function1[AST.Term, AST.Term] with GroovyInterop.Closure1[AST.Term, AST.Term] {
        def apply(arg: AST.Term): AST.Term = AST.Functions.percentage(arg)
      }

      val max = new Function2[AST.Term, AST.Term, AST.Term] with GroovyInterop.Closure2[AST.Term, AST.Term, AST.Term] {
        def apply(arg: AST.Term, arg2: AST.Term): AST.Term = AST.Functions.max(arg, arg2)
      }

      val round = new Function1[AST.Term, AST.Term] with GroovyInterop.Closure1[AST.Term, AST.Term] {
        def apply(arg: AST.Term): AST.Term = AST.Functions.round(arg)
      }

      val fractional = new Function1[AST.Term, AST.Term] with GroovyInterop.Closure1[AST.Term, AST.Term] {
        def apply(arg: AST.Term): AST.Term = AST.Functions.fractional(arg)
      }
    }

  }

}

object FormulaCompiler {

  import reporting.engine.AST.{TermLookup, LabeledTerm}

  // XXX: This can't be used as a normal Ordering for Seq.sorted(), probably because of the sort alg.
  def termOrdering(implicit tl: TermLookup): Ordering[LabeledTerm] = Ordering fromLessThan {
    (a, b) => !a._2.exists(_.has(_.label == b._1))
  }

  def insertSort[X](list: List[X])(implicit ord: Ordering[X]) = {
    def insert(list: List[X], value: X) = list.span(x => ord.lt(x, value)) match {
      case (lower, upper) => lower ::: value :: upper
    }
    list.foldLeft(List.empty[X])(insert)
  }

  def sort(terms: LabeledTerm*)(implicit tl: TermLookup) = insertSort(terms.toList)(termOrdering)

  def segment(terms: LabeledTerm*)(implicit tl: TermLookup): List[List[(String, Option[AST.Term])]] = {
    case class State(groups: List[List[LabeledTerm]] = List())
    def toLookup(list: List[LabeledTerm]) = list.toMap.withDefaultValue(None)
    def hasDependency(cur: List[LabeledTerm])(t: LabeledTerm) = {
      cur.exists(c => t._2.exists(_.has(_.label == c._1)(toLookup(cur))))
    }

    sort(terms: _*).foldLeft(State()) { (accum, t) =>
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
    b.put("fees", AST.Fees)

    // TODO: Use reflection to automatically generate this
    import AST.Functions.{functions => funcs}
    b.put("sum", funcs.sum)
    b.put("display", funcs.format)
    b.put("currency", funcs.currency)
    b.put("percentage", funcs.percentage)
    b.put("max", funcs.max)
    b.put("round", funcs.round)
    b.put("fractional", funcs.round)

    for ((k, v) <- props) b.put(k, v)
    e.setBindings(b, ScriptContext.ENGINE_SCOPE)
  }

  def initVars(e: ScriptEngine)(vars: String*) = env(e)(vars.map(v => v -> AST.Variable(v)): _*)

  initVars(engine)(varNames: _*)

  def apply(formula: String) = compile(formula)

  // Convert formula to an AST, referencing needed aggregates for deferred computation
  def compile(formula: String): AST.Term = try {
    engine.eval(formula).asInstanceOf[Any] match {
      case t: AST.Term => t
      case n: Int => new AST.Constant(n)
      case n: Long => new AST.Constant(n)
      case n: Float => new AST.Constant(n)
      case n: Double => new AST.Constant(n)
      case n: scala.math.BigDecimal => new AST.Constant(n)
      case n: java.math.BigDecimal => new AST.Constant(BigDecimal(n))
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
