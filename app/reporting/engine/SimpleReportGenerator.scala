package reporting.engine

import java.util.UUID

import com.google.inject.Inject
import org.joda.time.DateTime
import reporting.models.ds.DataSource.{NestedRow, DataSourceFetcher, DataSourceAggregator}
import reporting.models.ds.{DataSource, DataSourceType}
import reporting.models.{Field, Report}

import scala.concurrent.ExecutionContext
import scala.concurrent.{Await, Future, ExecutionContext}

class SimpleReportGenerator(report: Report, fields: List[Field]) {

  import scala.concurrent.duration._

  def getReport(ds: DataSource, dsRows: Seq[DataSource.Row])(start: DateTime, end: DateTime)(implicit ec: ExecutionContext) = {
     val dsF = new DataSourceFetcher(ds).forDateRange(start, end)
    //val dsRows = ds.dataForRange(start, end)

    val allFields = fields
    val allFieldsLookup = allFields.map(f => f.id.get -> f).toMap
    val compiler = new FormulaCompiler(allFields.map(_.label): _*)
    val compiledFields = allFields.map(f => f -> f.formula.map(compiler.apply))
    val labeledTerms = compiledFields.map({ case (field, term) => field.label -> term}).toMap
    val labeledFields = compiledFields.map({ case (field, term) => field.label -> field}).toMap
    val groupedTerms = FormulaCompiler.segment(labeledTerms.toList: _*)(labeledTerms)
    val groupedLabels = groupedTerms.map(grp => grp.map({ case (lbl, term) => lbl}))
    val bindings = report.fieldBindings.map(b => b.fieldId -> b).toMap
    val labeledBindings = allFields.map(f => f.label -> f.id.map(id => bindings(id))).toMap

    // Await the result
    val rowsByDate = DataSourceAggregator
      //.groupByDate(Await.result(dsF, 2.minutes): _*)
      .groupByDate(ds -> dsRows)
      .map({case (date, rows) => date -> DataSourceAggregator.nestAndCoalesce(rows: _*)})

    // Do K iterations over all rows, where K = groupedLabels.length
    // Each of K groups separates dependencies, so we must process A before B where B depends on A.
    val cxt = new FormulaEvaluator.EvaluationCxt[DataSource.NestedRow](FormulaEvaluator.Report(start, end))

    // TODO: Run over groups of fields, processing rows.
    // TODO: Compute footers
    // TODO: Return result
  }
}
