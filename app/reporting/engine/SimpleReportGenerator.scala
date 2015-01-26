package reporting.engine

import org.joda.time.DateTime
import reporting.models.ds.DataSource
import DataSource.{BasicRow, Attributes}
import reporting.models.{FieldBinding, Field, Report}

import scala.collection.SortedMap

class SimpleReportGenerator(report: Report, fields: List[Field]) {

  import DataSource.DataSourceAggregators.implicits._

  val allFields = fields
  val allFieldsLookup = allFields.map(f => f.id.get -> f).toMap
  val compiler = new FormulaCompiler(allFields.map(_.varName): _*)
  val compiledFields = allFields.map(f => f -> f.formula.map(compiler.apply))
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
      attrs = computed.values.map({ case (kk,vv) => labeledFields(kk) -> GeneratedReport.FieldValue(vv.value, vv.formatted) })
    } yield GeneratedReport.Row(row.keys, row.date, fields = attrs)
  }
}

case class GeneratedReport(fields: List[Field], rows: List[GeneratedReport.Row]) {

  var fieldsLookup = fields.map(f => f.id.get -> f).toMap

  import GeneratedReport.implicits._

  def sortRowsBy(fieldSort: Option[reporting.models.FieldSort]) =
    fieldSort.map(fs => fieldsLookup(fs.fieldId) -> fs.ascending) match {
      case Some((f, true)) => this.copy(rows = this.rows.sortBy(r => r.fields(f)))
      case Some((f, false)) => this.copy(rows = this.rows.sortBy(r => r.fields(f)).reverse)
      case _ => this.copy(rows = this.rows.sortBy(r => r.keys.mkString("-")))
    }
}

object GeneratedReport {
  case class FieldValue(value: BigDecimal, display: String)

  case class Row(keys: List[String], date: DateTime, fields: Map[Field, FieldValue]) {

    def orderedFields(visible: List[Field]) = {
      val fieldOrder = visible.zipWithIndex.toMap
      SortedMap[Field, FieldValue](fields.filter(x => visible.contains(x._1)).toSeq:_*)(Ordering.by(f => fieldOrder(f)))
    }
  }

  object implicits {
    implicit def FieldValueOrdering: Ordering[FieldValue] = Ordering.fromLessThan((a, b) => a.value < b.value)

    /*implicit def RowSemigroup: Semigroup[Row] = new Semigroup[Row] {
      def append(a1: Row, a2: => Row): Row = {
        import scalaz.Scalaz.ToSemigroupOps
        import scalaz._, Scalaz._
        Row(a1.keys, a1.date, a1.fields |+| a2.fields)
      }
    }*/
  }
}
