package reporting.engine.eval

import org.joda.time.{LocalTime, LocalDate}
import reporting.engine.AST

sealed trait ReportType[K] {
  def rowOrdering = CxtRow.CxtRowOrderingByDate
}
object ReportTypes {
  // Collapse dates for unique keys
  case class ByKey(parts: List[Int]) extends ReportType[MultipartKey]

  // Collapse keys for unique dates
  case class ByDate() extends ReportType[LocalTime]

  // Collapse data for unique keys/dates
  case class ByDateAndKey(parts: List[Int]) extends ReportType[(LocalTime,MultipartKey)]
}

private sealed class FlattenedView[K](cxt: RootCxt) {

  import reporting.engine.eval.Result.implicits._

  def dateRange = cxt.dateRange

  val rows = collection.mutable.Map[K, CxtRow]()

  // Flattened totals for underlying month
  val months = collection.mutable
    .Map[MonthCxtKey, CxtRow]()
    .withDefault(k => CxtRow(MultipartKey.empty, k.dateRange))

  val aggregates = collection.mutable
    .Map[String, Result]()

  def sum(fieldLabel: String) = {
    aggregates.getOrElseUpdate(fieldLabel, {
      months.values.map(_.values(fieldLabel)).sum
    })
  }

  def monthFor(row: CxtRow) = months(row.yearMonthKey)

  def month(d: LocalDate) = months.get(MonthCxtKey(d))

}

/*

val evaluator = new Evaluator(new FeesEvaluator(serving, agency))
val root = new RootCxt(inputRows)
val flattenedView = new FlattenedView(root)
val flattener = new CxtFlattenerByKey(ReportTypes.ByKey(List(0)), flattenedView)(root)
val rows = flattener.flatten(orderedTermGroups)(evaluator)

object CxtFlattener {

  // We don't care about keys, only unique dates across all branches
  def flatten(orderedTermGroups: List[List[AST.LabeledTerm]]): Seq[CxtRow] = reportType match {
    case ReportTypes.ByDate() => flattenByDate(orderedTermGroups)
    case ReportTypes.ByDateAndKey(parts) => Seq.empty
    case ReportTypes.ByKey(parts) => flattenByKey(orderedTermGroups)(parts)
  }
}
*/

class CxtFlattenerByDate(reportType: ReportTypes.ByDate, fcxt: FlattenedView[LocalDate])(implicit cxt: RootCxt) {

  import Result.implicits._

  // TODO: Use FlattenedCxt in Evaluator not RootCxt
  def flatten(orderedTermGroups: List[List[AST.LabeledTerm]])
             (implicit evaluator: Evaluator): Seq[CxtRow] = {

    // Get list of all dates for this report, and fill in empty rows.
    for {
      r <- fcxt.dateRange
      d <- r.dates
    } fcxt.rows += d -> CxtRow(MultipartKey.empty, DateRange.fromLocalDate(d))

    // run each term group across the report
    for {
      orderedTerms <- orderedTermGroups
      term <- orderedTerms
    } {
      val rows1 = for {
        (k, row) <- fcxt.rows
      } yield k -> (term match {
          // Bound, so look it up from RootCxt
          // TODO(dk): handle distributable fields
          case (label, None) =>
            // iterate over context, and get summed value
            val monthSums = for ((k, m) <- cxt.months) yield k -> m
              .filterRows(_.dateRange == row.dateRange)
              .map(_.values(label))
              .sum

            // Update monthly totals for this row
            for ((k, v) <- monthSums) {
              val monthRow = fcxt.months(k)
              fcxt.months(k) = monthRow + (label -> v)
            }
            val sum = monthSums.values.sum
            row + (label -> sum)

          // Derived, so apply formula
          //TODO(dk): We probably need these at month level too
          case (label, Some(t)) =>
            val res = evaluator.eval(t)(fcxt, row)
            row + (label -> res)
        })
      // Overwrite the rows from the previous iteration
      fcxt.rows ++= rows1
    }
    fcxt.rows.values.toSeq
  }

}

class CxtFlattenerByKey(reportType: ReportTypes.ByKey, fcxt: FlattenedView[MultipartKey])(implicit cxt: RootCxt) {

  import Result.implicits._

  val parts = reportType.parts

  def flatten(orderedTermGroups: List[List[AST.LabeledTerm]])
             (implicit evaluator: Evaluator): Seq[CxtRow] = {

    // For handle multiple key parts
    def flattenKey(part: String, idx: Int) = parts.contains(idx)

    // IDEA: Within each month, groupBy() all rows, and then
    // process each one in turn; this let's us build up any
    // aggregates we want at the month level. Further, since
    // only bound fields will be present at first, it automatically
    // handles all of these.

    // Get a list of all unique flattened keys, and prefill rows for report.
    for {
      groupsByKey <- cxt
        .filterKeys(kp => kp.length+1 == parts.max)
        .groupBy(k => k.map(flattenKey))
      (flattenedKey, ks) = groupsByKey
    } if (!fcxt.rows.contains(flattenedKey)) {
      fcxt.rows += flattenedKey -> CxtRow(flattenedKey, null)
    }

    // run each term group across the report
    for {
      orderedTerms <- orderedTermGroups
      term <- orderedTerms
    } {
      val rows1 = for {
        (k, row) <- fcxt.rows
      } yield k -> (term match {
          // Bound, so look it up from RootCxt
          // TODO(dk): handle distributable fields
          case (label, None) =>
            // iterate over context, and get summed values per month
            val monthSums = for ((k, m) <- cxt.months) yield k -> m
              .filterRowsByKey(_.matchesPattern(row.key))
              .map(_.values(label))
              .sum

            // Update monthly totals for this row
            for ((k, v) <- monthSums) {
              val monthRow = fcxt.months(k)
              fcxt.months(k) = monthRow + (label -> v)
            }
            val sum = monthSums.values.sum
            row + (label -> sum)

          // Derived, so apply formula
          //TODO(dk): We need these at month level for media type vars
          // i.e. impressions.whereKey("Media Type").is("InStream Default Ad")
          case (label, Some(t)) =>
            val res = evaluator.eval(t)(fcxt, row)
            row + (label -> res)
        })
      // Overwrite the rows from the previous iteration
      fcxt.rows ++= rows1
    }
    fcxt.rows.values.toSeq
  }
}
