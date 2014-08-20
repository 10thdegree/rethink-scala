package reporting.engine

import java.util.UUID

import com.google.inject.Inject
import org.joda.time.DateTime
import reporting.models.DataSources.{DataSource, DataSourceType}
import reporting.models.{Field, Report}

import scala.concurrent.{Await, Future, ExecutionContext}

trait DataSourceFinder {
  def apply(accountId: UUID)(dsId: UUID): Option[DataSource] = { ??? }
}

trait FieldFinder {
  def apply(accountId: UUID)(fieldId: UUID): Option[Field] = { ??? }

  def byTemplate(templateId: UUID): List[Field] = { ??? }
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
    val allFields = fieldFinder.byTemplate(report.templateId)
    val compiler = new FormulaCompiler(allFields.map(_.label): _*)
    val compiledFields = allFields.map(f => f -> f.formula.map(compiler.apply))
    val labeledTerms = compiledFields.map({ case (field, term) => field.label -> term}).toMap
    val labeledFields = compiledFields.map({ case (field, term) => field.label -> field}).toMap
    val orderedTerms = labeledTerms.toList.sorted(FormulaCompiler.TermOrdering(labeledTerms))
    val groupedTerms = FormulaCompiler.segment(orderedTerms: _*)
    val groupedLabels = groupedTerms.map(grp => grp.map({ case (lbl, term) => lbl}))
    val bindings = report.fieldBindings.map(b => b.fieldId -> b).toMap
    val labeledBindings = allFields.map(f => f.label -> f.id.map(id => bindings(id))).toMap

    // Do K iterations over all rows, where K = groupedLabels.length
    // Each of K groups separates dependencies, so we must process A before B where B depends on A.
    val cxt = new FormulaEvaluator.EvaluationCxt[DataSource.Row](FormulaEvaluator.Report(start, end))
    for {
      (group, groupIdx) <- groupedLabels.zipWithIndex
      (date, row) <- mergedRows
    } {
      val groupTerms = group.map { label =>
        val term = labeledTerms(label) orElse {
          // TODO: each row should ideally know how to map a label to its internal ds attribute name
          labeledBindings(label).map(b => AST.Constant(row(b.dataSourceAttribute).toString.toDouble))
        }
        label -> term
      }
      FormulaEvaluator.eval(row, date, groupTerms)(cxt)
    }
    // cxt should have all the values at the end of this
    val resultRows = mergedRows.map({ case (date, row) => cxt.row(row, date)})

    // If we need footer values, we can pull them out of cxt here.
    // TODO: must compile footer formulae first, then eval them here
    // var footerVals = allFields.map(f => f.footerFormula.map(ff => FormulaEvaluator.eval(footerTerms(f.label)))))

    // TODO: Make a return object encapsulating the result rows
    null
  }
}
