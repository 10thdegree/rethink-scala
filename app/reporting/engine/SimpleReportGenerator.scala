package reporting.engine

import org.joda.time.DateTime
import reporting.models.ds.DataSource
import DataSource.{BasicRow, Attributes}
import reporting.models.{FieldBinding, Field, Report}

class SimpleReportGenerator(report: Report, fields: List[Field]) {

  import DataSource.DataSourceAggregators.implicits._

  val allFields = fields
  val allFieldsLookup = allFields.map(f => f.id.get -> f).toMap
  val compiler = new FormulaCompiler(allFields.map(_.label): _*)
  val compiledFields = allFields.map(f => f -> f.formula.map(compiler.apply))
  val labeledTerms = compiledFields.map({ case (field, term) => field.label -> term}).toMap
  val labeledFields = compiledFields.map({ case (field, term) => field.label -> field}).toMap
  val groupedTerms = FormulaCompiler.segment(labeledTerms.toList: _*)(labeledTerms)
  val groupedLabels = groupedTerms.map(grp => grp.map({ case (lbl, term) => lbl}))
  val bindings = report.fieldBindings.map(b => b.fieldId -> b).toMap
  val labeledBindings = allFields.map(f => f.label -> bindings.get(f.id.get)).toMap

  lazy val requiredFieldBindings: List[(String, FieldBinding)] = labeledBindings
      .map({case (k,vo) => vo.map(k -> _) })
      .flatten
      .toList

  def getReport(ds: DataSource, dsRows: Seq[BasicRow])(start: DateTime, end: DateTime) = {
    val dsa = DataSource.DataSourceAggregators.get[BasicRow]
    val rowsByDate = dsa
      .groupByDate(ds -> dsRows)
      .map({case (date, rows) => date -> dsa.aggregate(rows: _*)})

    // Do K iterations over all rows, where K = groupedLabels.length
    // Each of K groups separates dependencies, so we must process A before B where B depends on A.
    val cxt = new FormulaEvaluator.EvaluationCxt[BasicRow](FormulaEvaluator.Report(start, end))

    def termFromBinding(nrow: BasicRow)(label: String) =
      for (b <- labeledBindings(label)) yield {
        nrow -> AST.Constant(nrow(b.dataSourceAttribute).toDouble)
      }

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

    // TODO: Compute footers

    // Extract the map of values from each row
    for {
      (row, computed) <- cxt.allRows.toList
      attrs = computed.values.map({ case (kk,vv) => labeledFields(kk) -> BigDecimal(vv) })
    } yield GeneratedReport.Row(row.keys, row.date, fields = attrs)
  }
}

object GeneratedReport {
  case class Row(keys: List[String], date: DateTime, fields: Map[Field, BigDecimal])

  object implicits {
    import scalaz._, Scalaz._
    implicit def RowSemigroup: Semigroup[Row] = new Semigroup[Row] {
      def append(a1: Row, a2: => Row): Row = {
        import scalaz.Scalaz.ToSemigroupOps
        import scalaz._, Scalaz._
        Row(a1.keys, a1.date, a1.fields |+| a2.fields)
      }
    }
  }
}
