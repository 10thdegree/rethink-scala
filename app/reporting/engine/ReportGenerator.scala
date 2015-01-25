package reporting.engine





object Joda {
  import org.joda.time._
  implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isBefore _)

  case class OverlapMatch(before: Option[Interval] = None, during: Option[Interval] = None, after: Option[Interval] = None)

  def overlapMatch(span1: Interval, span2: Interval):OverlapMatch = {
    import scalaz._, Scalaz._
    val (fst, snd) = if (span1 isBefore span2) (span1, span2) else (span2, span1)
    Option(fst overlap snd)
      // Partial or complete overlap/subsuming
      .map(o => OverlapMatch(
        before = if (fst.getStart isEqual o.getStart) None else fst.withEnd(o.getStart).some,
        during = o.some,
        after = if (snd.getEnd isEqual o.getEnd) None else snd.withStart(o.getEnd).some))
      // No overlap at all
      .getOrElse(OverlapMatch(before = fst.some, after = snd.some))
  }

  def startOfTime: DateTime = {
    new DateTime(Years.MIN_VALUE.getYears,
      Months.MIN_VALUE.getMonths,
      Days.MIN_VALUE.getDays,
      Hours.MIN_VALUE.getHours,
      Minutes.MIN_VALUE.getMinutes)
  }

  def endOfTime: DateTime = {
    new DateTime(Years.MAX_VALUE.getYears,
      Months.MAX_VALUE.getMonths,
      Days.MAX_VALUE.getDays,
      Hours.MAX_VALUE.getHours,
      Minutes.MAX_VALUE.getMinutes)
  }
}

/*

import java.util.UUID

import com.google.inject.Inject
import reporting.models.ds.DataSource.{NestedRow, DataSourceFetcher, DataSourceAggregator}
import reporting.models.ds.{DataSource, DataSourceType}
import reporting.models.{Field, Report}
import scala.concurrent.{Await, Future, ExecutionContext}

trait DataSourceFinder {
  def apply(accountId: UUID)(dsId: UUID): Option[DataSource]
}

trait FieldFinder {
  def apply(accountId: UUID)(fieldId: UUID): Option[Field]

  def byTemplate(templateId: UUID): List[Field]
}

case class ReportDisplay()

//(reportInstance: ReportInstance, rows: DataSource.Row)

object Pivot {

  //
  case class Row(rows: List[DataSource.Row], children: List[Pivot.Row])

}

class ReportGenerator @Inject()(dsFinder: DataSourceFinder, fieldFinder: FieldFinder) {

  import scala.concurrent.duration._
  import DataSource.DataSourceAggregators.implicits._

  def getReport(report: Report)(start: DateTime, end: DateTime)(implicit ec: ExecutionContext): ReportDisplay = {

    // Get a list of futures for each data source
    val dses = for {
      dsBinding <- report.dsBindings
      ds <- dsFinder(report.accountId)(dsBinding.dataSourceId).toList
    } yield ds

    // Get the future
    val dsF = new DataSourceFetcher[NestedRow](dses: _*).forDateRange(start, end)

    val allFields = fieldFinder.byTemplate(report.templateId)
    val allFieldsLookup = allFields.map(f => f.id.get -> f).toMap
    val compiler = new FormulaCompiler(allFields.map(_.label): _*)
    val compiledFields = allFields.map(f => f -> f.formula.map(compiler.apply))
    val labeledTerms = compiledFields.map({ case (field, term) => field.label -> term}).toMap
    val labeledFields = compiledFields.map({ case (field, term) => field.label -> field}).toMap
    val groupedTerms = FormulaCompiler.segment(labeledTerms.toList: _*)(labeledTerms)
    val groupedLabels = groupedTerms.map(grp => grp.map({ case (lbl, term) => lbl}))
    val bindings = report.fieldBindings.map(b => b.fieldId -> b).toMap
    val labeledBindings = allFields.map(f => f.label -> f.id.map(id => bindings(id))).toMap
    val dsa = DataSource.DataSourceAggregators.get[NestedRow]
    // TODO: Rows with partially matched keys need to pull the dependant field for proportionally distributing the values
    // Await the result
    val rowsByDate = dsa
      .groupByDate(Await.result(dsF, 2.minutes): _*)
      .map({case (date, rows) => date -> dsa.aggregate(rows: _*)})

    // Do K iterations over all rows, where K = groupedLabels.length
    // Each of K groups separates dependencies, so we must process A before B where B depends on A.
    val cxt = new FormulaEvaluator.EvaluationCxt[DataSource.NestedRow](FormulaEvaluator.Report(start, end))

    def termFromBinding(nrow: NestedRow)(label: String) =
      labeledBindings(label).map({ b =>
        val nrow2 = (for {
          id <- b.dependantFieldId
          f <- allFieldsLookup.get(id)
          fb <- labeledBindings.get(f.label)
          b2 = fb.map(_.dataSourceAttribute)
          nrow2 = nrow.distributeDown(b.dataSourceAttribute, b2)
          nrow3 = nrow2.distributeUp(b.dataSourceAttribute)
        } yield nrow3) getOrElse nrow
        nrow -> AST.Constant(nrow2(b.dataSourceAttribute).toDouble)
        // TODO: We need to persist state of nrow2 for remainder of this comprehension
      })

    // 1. Take first group (binded fields) and fill context
    // 2. Take second group (distributed fields) if present, and distribute values
    // 3. Process remaining groups
    //val st = StateT[List, NestedRow String]()
    val mergedRows = for {
      (group, groupIdx) <- groupedLabels.zipWithIndex // Columns
      (date, nrows) <- rowsByDate // Sets of rows by date
      nrow <- nrows // Rows; NOTE: each nrow may have many children! Must map over those below
    } yield {
      // XXX: The below is gross; we need to either:
      //      (1) move the transformation logic out of NestedRow and into RowCtx, or
      //      (2) use a state monad so that as we mutate the NestedRow we can propagate those changes.
      val initial = Vector.empty[(String, Option[AST.Term])]
      val (nrow2, groupTerms) = group.foldLeft((NestedRow, initial)) { (accum, label) =>
        val Some((nrow2, term)) = {
          labeledTerms(label).map(accum._1 -> _) // Bound field (formula)
        } orElse {
          termFromBinding(accum._1)(label) // Derived field (data)
        }
        (nrow2, accum._2 :+ (label -> Some(term)))
      }
      for (row <- nrow.terminals) yield {
        FormulaEvaluator.eval(row, date, groupTerms.toList)(cxt)
        row
      }
    }
    // cxt should have all the values at the end of this
    val cxtRows = mergedRows.flatten.map({ case (date, row) => cxt.row(row, date)})

    // If we need footer values, we can pull them out of cxt here.
    // TODO: must compile footer formulae first, then eval them here
    // var footerVals = allFields.map(f => f.footerFormula.map(ff => FormulaEvaluator.eval(footerTerms(f.label)))))

    // TODO: Make a return object encapsulating the resultant rows (cxtRows)
    null
  }
}

*/