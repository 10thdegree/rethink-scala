package reporting.engine

import java.util.UUID

import com.google.inject.Inject
import org.joda.time.DateTime
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

object Joda {
  implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isBefore _)
}

case class ReportDisplay()

//(reportInstance: ReportInstance, rows: DataSource.Row)

object Pivot {

  //
  case class Row(rows: List[DataSource.Row], children: List[Pivot.Row])

}

class ReportGenerator @Inject()(dsFinder: DataSourceFinder, fieldFinder: FieldFinder) {

  import scala.concurrent.duration._

  def getReport(report: Report)(start: DateTime, end: DateTime)(implicit ec: ExecutionContext): ReportDisplay = {

    // Get a list of futures for each data source
    val dses = for {
      dsBinding <- report.dsBindings
      ds <- dsFinder(report.accountId)(dsBinding.dataSourceId).toList
    } yield ds

    // Get the future
    val dsF = new DataSourceFetcher(dses: _*).forDateRange(start, end)

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

    // TODO: Rows with partially matched keys need to pull the dependant field for proportionally distributing the values
    // Await the result
    val rowsByDate = DataSourceAggregator
      .groupByDate(Await.result(dsF, 2.minutes): _*)
      .map({case (date, rows) => date -> DataSourceAggregator.nestAndCoalesce(rows: _*)})

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
      val (nrow2, groupTerms) = group.foldLeft((NestedRow, Vector.empty[(String, Option[AST.Term])])) { (accum, label) =>
        val Some((nrow2, term)) = labeledTerms(label).map(accum._1 -> _) orElse termFromBinding(accum._1)(label)
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
