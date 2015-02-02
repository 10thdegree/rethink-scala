package reporting.util.json

import julienrf.variants.Variants
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Writes}
import reporting.engine.GeneratedReport
import reporting.models.Chart
import reporting.models.Chart._

object GeneratedReportWrites {

  implicit val generatedReportFieldValueWrites: Writes[GeneratedReport.FieldValue] = (
    (JsPath \ "val").write[BigDecimal] and
      (JsPath \ "disp").write[String]
    )((v: GeneratedReport.FieldValue) => (v.value, v.display))

  implicit val chartWrites: Writes[Chart] = Variants.writes[Chart]("type")

  implicit val fieldWrites: Writes[reporting.models.Field] = (
    (JsPath \ "uuid").write[String] and
    (JsPath \ "varName").write[String] and
      (JsPath \ "displayName").write[String] and
      (JsPath \ "format").write[String] and
      (JsPath \ "footerType").write[String]
    )((f: reporting.models.Field) => (
    f.id.map(_.toString).getOrElse("NONE"),
    f.varName,
    f.label,
    f.format.map(_.name).getOrElse(""),
    f.footer.map(_.name).getOrElse("")))

  implicit val generatedReportRowWrites: Writes[GeneratedReport.Row] = (
    (JsPath \ "key").write[String] and
      //(JsPath \ "date").write[String] and
      (JsPath \ "values").write[Map[String, GeneratedReport.FieldValue]]
    )((row: GeneratedReport.Row) => (
    row.keys.mkString("-"),
    //row.date.toString("yyyy-MM-dd"),
    row.fields.map({case (k,v) => k.varName -> v }).toMap))//asInstanceOf[Map[String, GeneratedReport.FieldValue]]

  implicit val generatedReportWrites: Writes[GeneratedReport] = (
    (JsPath \ "fields").write[List[reporting.models.Field]] and
      (JsPath \ "rows").write[List[GeneratedReport.Row]] and
      (JsPath \ "charts").write[List[Chart]]
    )((r:GeneratedReport) => (r.fields.toList, r.filteredRows, r.charts))
}