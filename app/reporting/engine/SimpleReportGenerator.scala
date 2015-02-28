package reporting.engine

import org.joda.time.DateTime
import reporting.models.ds.DataSource
import DataSource.{BasicRow, Attributes}
import reporting.models._

import scala.collection.SortedMap

class SimpleReportGenerator(report: Report, fields: List[Field])(implicit servingFees: Fees.FeesLookup[Fees.ServingFees], agencyFees: Fees.FeesLookup[Fees.AgencyFees]) {

  import DataSource.DataSourceAggregators.implicits._

  val allFields = fields
  val allFieldsLookup = allFields.map(f => f.id.get -> f).toMap
  val compiler = new FormulaCompiler(allFields.map(_.varName): _*)
  val compiledFields = allFields.map(f => f -> f.formulaValue.map(compiler.apply))
  val labeledTerms = compiledFields.map({ case (field, term) => field.varName -> term}).toMap
  val labeledFields = compiledFields.map({ case (field, term) => field.varName -> field}).toMap
  val groupedTerms = FormulaCompiler.segment(labeledTerms.toList: _*)(labeledTerms)
  val groupedLabels = groupedTerms.map(grp => grp.map({ case (lbl, term) => lbl}))
  val bindings = report.fieldBindings.groupBy(b => b.fieldId)
  val labeledBindings = allFields.map(f => f.varName -> bindings.getOrElse(f.id.get, List())).toMap

  lazy val requiredFieldBindings: List[(String, FieldBinding)] = labeledBindings
    .toList
    .flatMap({case (k,vo) => vo.map(k -> _) })

  def findMissingFieldBindings(foundAttributes: Set[String]): List[(String, FieldBinding)] = {
    requiredFieldBindings.filterNot(b => foundAttributes.contains(b._2.dataSourceAttribute))
  }

  import JodaTime.implicits._

  def getReport(ds: DataSource, dsRows: Seq[BasicRow])(start: DateTime, end: DateTime): List[GeneratedReport.Row] = {
    play.Logger.debug("getReport() running...")
    val dsa = DataSource.DataSourceAggregators.get[BasicRow]
    val rowsByDate = dsa
      .groupByDate(ds -> dsRows)
      .map({ case (date, rows) => date -> dsa.flattenByKeys(rows: _*)})

    // Do K iterations over all rows, where K = groupedLabels.length
    // Each of K groups separates dependencies, so we must process A before B where B depends on A.
    val cxt = new FormulaEvaluator.EvaluationCxt[BasicRow](FormulaEvaluator.Report(start, end.withTimeAtEndOfDay))

    def termFromBinding(nrow: BasicRow)(label: String) = if (labeledBindings(label).nonEmpty) {
      Some(nrow -> AST.Constant((for (b <- labeledBindings(label)) yield {
        nrow(b.dataSourceAttribute).toDouble
      }).sum))
    } else None

    val evals = for {
      (group, groupIdx) <- groupedLabels.zipWithIndex // Columns
      (date, rows) <- rowsByDate // Sets of rows by date
      row <- rows
    } yield {
      val initial = Vector.empty[(String, Option[AST.Term])]
      val groupTerms = group.foldLeft(initial) { (accum, label) =>
        val Some(term) = {
          labeledTerms(label) // Bound field (formula)
        } orElse {
          termFromBinding(row)(label).map(_._2) // Derived field (data)
        }
        accum :+ (label -> Some(term))
      }
      FormulaEvaluator.eval[BasicRow](row, date, groupTerms.toList)(cxt)
    }

    // Extract the map of values from each row
    for {
      (row, computed) <- cxt.allRows.toList
      attrs = computed.values.map({ case (kk,vv) => labeledFields(kk) -> GeneratedReport.FieldValue(vv.value, vv.toString) })
    } yield GeneratedReport.Row(row.keys, row.date, fields = attrs)
  }
}
