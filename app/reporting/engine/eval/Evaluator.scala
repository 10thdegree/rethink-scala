package reporting.engine.eval

class Evaluator(fees: FeesEvaluator) {

  import reporting.engine.AST
  import reporting.engine.AST._
  import reporting.engine.eval.Result.implicits._

  def eval[A](term: Term)(implicit cxt: FlattenedView[_], rcxt: CxtRow): Result = term match {

    case t@Constant(v) => Result(t.toDouble)

    // Operators
    case Add(left, right) => eval(left) + eval(right)
    case Subtract(left, right) => eval(left) - eval(right)
    case Divide(left, right) => eval(left) / eval(right)
    case Multiply(left, right) => eval(left) * eval(right)

    // Deferred lookup
    case Variable(label) => rcxt(label)

    // Month functions
    case Month.Sum(n) => Result(cxt.monthFor(rcxt).values(n.label))
    //case Month.Avg(n) => Result(cxt.monthFor(rcxt).avg(n.label))

    // Ignore dep entirely, just there for ordering
    case ForcedDependancy(n, _) => eval(n)

    // Global functions
    case Round(n) => eval(n).rounded
    case Sum(n) => cxt.sum(n.label)
    //case Avg(n) => cxt.avg(n.label)
    case Max(left, right) => Result(eval(left).value max eval(right).value)

    // Fees
    case AST.Fees.ServingFee(label, ft, ref) => fees.servingFees(label, ref, ft)
    case AST.Fees.AgencyFee(label, spend, impr) => fees.agencyFees(label, spend, impr)

    // TODO(dk): branch filters: This needs to be aware of the current key
    //case KeyFilter(n, KeyFilter.Eq(l, v)) =>
    // val vIdx = cxt.lookupIndexForKeyLabel(l)
    // cxt // Maybe dont need this: .monthFor(rcxt)
    //   .filterByKey(rcxt.key.composite(vIdx,v))
    //   .map(_.values(n.label))
    //   .sum
  }

  def eval(orderedTerms: List[LabeledTerm])(cxt: FlattenedView[_], rcxt: CxtRow): CxtRow = {
    rcxt + (for {
      (name, termO) <- orderedTerms
      term <- termO
    } yield name -> eval(term)(cxt, rcxt)).toMap
  }

}

import reporting.models.Fees.FeesLookup
import reporting.models.Fees.{ServingFees => MServingFees}
import reporting.models.Fees.{AgencyFees => MAgencyFees}

class FeesEvaluator(servingFeesLookup: FeesLookup[MServingFees],
                    agencyFeesLookup: FeesLookup[MAgencyFees]) {

  import reporting.engine.AST.Fees.ServingFeeTypes.ServingFeeType
  import reporting.engine.AST.Fees.ServingFeeTypes
  import reporting.engine.AST
  import reporting.engine.JodaTime.implicits._
  import org.joda.time._
  import reporting.engine.eval.Result.implicits._

  def servingFees(feeLabel: String,
                  dependsOn: Option[AST.Term],
                  tpe: ServingFeeType)
                 (implicit cxt: FlattenedView[_], rowCxt: CxtRow) = {
    val span = rowCxt.dateRange.toInterval
    val fees = for {
      monthSpan <- span.intoMonths
      ll <- servingFeesLookup(feeLabel)
      fee <- ll(monthSpan).headOption
      df <- dependsOn
      rowValue = rowCxt(df.label)
    } yield tpe match {
        case ServingFeeTypes.Cpc => rowValue * fee.cpc
        case ServingFeeTypes.Cpm => rowValue * fee.cpm / 1000
      }
    import Result.implicits._
    fees.sum
  }

  def agencyFees(feeLabel: String,
                 spend: AST.Term,
                 impressions: AST.Term)
                (implicit cxt: FlattenedView[_], rowCxt: CxtRow) = {
    val span = rowCxt.dateRange.toInterval
    val fees = for {
      monthSpan <- span.intoMonths
      ll <- agencyFeesLookup(feeLabel)
      fee <- ll(monthSpan).headOption
      monthCxt <- cxt.month(new LocalDate(monthSpan))
      monthImpressions = monthCxt.values(impressions.label)
      rowImpressions = rowCxt(impressions.label)
      monthSpend = monthCxt.values(spend.label)
      rowSpend = rowCxt(spend.label)
      fees = fee(monthImpressions.toLong)(monthSpend.value, monthSpan)
    } yield (rowSpend / monthSpend) * fees
    play.Logger.debug(s"v = $fees")
    fees.sum
  }
}
