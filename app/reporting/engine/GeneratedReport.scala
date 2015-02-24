package reporting.engine

import org.joda.time.DateTime
import reporting.models.{Chart, Field}

import scala.collection.immutable.SortedMap

object GeneratedReport {
  case class FieldValue(value: BigDecimal, display: String)

  case class Row(keys: List[String], date: DateTime, fields: Map[Field, FieldValue]) {

    def orderedFields(visible: List[Field]) = {
      val fieldOrder = visible.zipWithIndex.toMap
      SortedMap[Field, FieldValue](fields.filter(x => visible.contains(x._1)).toSeq:_*)(Ordering.by(f => fieldOrder(f)))
    }

    def filterFields(visible: List[Field]) = {
      this.copy(fields = orderedFields(visible).toMap)
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

case class GeneratedReport(fields: List[Field],
                           rows: List[GeneratedReport.Row],
                           charts: List[Chart]) {

  var fieldsLookup = fields.map(f => f.id.get -> f).toMap

  import GeneratedReport.implicits._

  def sortRowsBy(fieldSort: Option[reporting.models.FieldSort]) =
    fieldSort.map(fs => fieldsLookup(fs.fieldId) -> fs.ascending) match {
      case Some((f, true)) => this.copy(rows = this.rows.sortBy(r => r.fields(f)))
      case Some((f, false)) => this.copy(rows = this.rows.sortBy(r => r.fields(f)).reverse)
      case _ => this.copy(rows = this.rows.sortBy(r => r.keys.mkString("-")))
    }

  lazy val filteredRows = rows.map(_.filterFields(fields))
}