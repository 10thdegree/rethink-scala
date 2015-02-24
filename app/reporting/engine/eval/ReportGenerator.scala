package reporting.engine.eval

import org.joda.time.DateTime
import reporting.engine.{AST, GeneratedReport, FormulaCompiler}
import reporting.models._
import reporting.models.ds.DataSource
import reporting.models.ds.DataSource.BasicRow
/*
class ReportGenerator(report: Report, fields: List[Field])(implicit servingFees: Fees.FeesLookup[Fees.ServingFees], agencyFees: Fees.FeesLookup[Fees.AgencyFees]) {

  val allFields = fields
  val allFieldsLookup = allFields.map(f => f.id.get -> f).toMap
  val compiler = new FormulaCompiler(allFields.map(_.varName): _*)
  val compiledFields = allFields.map(f => f -> f.formulaValue.map(compiler.apply))
  val labeledTerms = compiledFields.map({ case (field, term) => field.varName -> term}).toMap
  val labeledFields = compiledFields.map({ case (field, term) => field.varName -> field}).toMap
  val groupedTerms = FormulaCompiler.segment(labeledTerms.toList: _*)(labeledTerms)
  val groupedLabels = groupedTerms.map(grp => grp.map({ case (lbl, term) => lbl}))
  val bindings = report.fieldBindings.map(b => b.fieldId -> b).toMap
  val labeledBindings = allFields.map(f => f.varName -> bindings.get(f.id.get)).toMap

  lazy val requiredFieldBindings: List[(String, FieldBinding)] = labeledBindings
      .map({case (k,vo) => vo.map(k -> _) })
      .flatten
      .toList

  def findMissingFieldBindings(foundAttributes: Set[String]): List[(String, FieldBinding)] = {
    requiredFieldBindings.filterNot(b => foundAttributes.contains(b._2.dataSourceAttribute))
  }

  def getReport(ds: DataSource, dsRows: Seq[BasicRow])(start: DateTime, end: DateTime): List[GeneratedReport.Row] = {
    play.Logger.debug("getReport() running...")
    // TODO: generate report

    val inputRows: Iterable[CxtRow] = null

    def termFromBinding(nrow: BasicRow)(label: String) =
      for (b <- labeledBindings(label)) yield {
        nrow -> AST.Constant(nrow(b.dataSourceAttribute).toDouble)
      }

    for ((group, groupIdx) <- groupedLabels.zipWithIndex) // Columns
      yield {
      termFromBinding(row)(label).map(_._2)
    }

    val evaluator = new Evaluator(new FeesEvaluator(servingFees, agencyFees))
    val root = new RootCxt(inputRows)
    val flattenedView = new FlattenedView[MultipartKey](root)
    val flattener = new CxtFlattenerByKey(ReportTypes.ByKey(List(0)), flattenedView)(root)
    val rows = flattener.flatten(orderedTermGroups)(evaluator)

    // Extract the map of values from each row
    //for {
    //  (row, computed) <- cxt.allRows.toList
    //  attrs = computed.values.map({ case (kk,vv) => labeledFields(kk) -> GeneratedReport.FieldValue(vv.value, vv.toString) })
    //} yield GeneratedReport.Row(row.keys, row.date, fields = attrs)
    null
  }

}*/
