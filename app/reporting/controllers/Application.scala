package reporting.controllers

import play.api._
import play.api.mvc._
import play.Logger
import reporting.engine.SimpleReportGenerator
import reporting.models.ds
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global 
import org.joda.time._
import bravo.api.dart._ 
import org.joda.time.format.DateTimeFormat
import bravo.core.Util._
import play.api.libs.json._

object Application extends Controller {

  def index = Action {
    Ok(reporting.views.html.index("reporting"))
  }

  def noargs = Action {
    Logger.error("BLah")
    Ok("noargs")
  }

 
  def reportGrid(startDate: String, endDate: String) = Action {
    Ok(reporting.views.html.reportgrid(startDate, endDate))
  }

  //18158200 Trident Report 
  def reportData(startDate: String, endDate: String) = Action.async {
    val config = Dart.prodConfig
    val frmt = DateTimeFormat.forPattern("yyyy-mm-dd")
    val start = frmt.parseDateTime(startDate)
    val end = frmt.parseDateTime(endDate)

    val tui = reporting.util.TUIReportHelper
    val ro = tui.TUISearchPerformanceRO()
    val gen = new SimpleReportGenerator(ro.report, ro.fields)
    val dsf = new ds.DataSource.DataSourceRowFactory(ro.ds)

    // List of fields (numeric) that we need when generating the report.
    val requiredAttributes = gen.requiredFieldBindings.map(_._2.dataSourceAttribute).toSet

    def convertResult(rows:List[Map[String,String]]) = {
      // XXX(dk): Last row has invalid data of "---" for all fields.
      val rows_ = rows.dropRight(1)
      //Logger.debug("==> " + requiredAttributes)
      //Logger.debug("=!> " + rows_(0).keys)
      //Logger.debug("=+> " + requiredAttributes.filter(rows_(0).keys.toList.contains))
      Logger.debug(rows.map(_("Paid Search Campaign")).toSet.mkString("\n"))
      val converted = dsf.convertValues(requiredAttributes)(rows_ :_*)
      val dsRows = dsf.process(converted:_*)
      val res = gen.getReport(ro.ds, dsRows)(start, end)
      res
    }

    Logger.debug("WHAT??? qid = " + ro.ds.queryId)

    import play.api.libs.json._
    import play.api.libs.functional.syntax._
    import reporting.engine.GeneratedReport

    implicit val generatedReportRowWrites: Writes[GeneratedReport.Row] = (
      (JsPath \ "key").write[String] and
        //(JsPath \ "date").write[String] and
        (JsPath \ "values").write[Map[String, String]]
      )((row: GeneratedReport.Row) => (
      row.keys.mkString("-"),
      //row.date.toString("yyyy-MM-dd"),
      row.fields.map({case (k,v) => k.label -> v })))

    val report = Dart.getReport(ro.ds.queryId.toInt, start, end)
    val parsedReport = report
      .map(dr => ReportParser.parse(dr.data))
      .map(convertResult)
      .map(li => Json.toJson(li))
      .run.run(config) 
      
    val futureResult = parsedReport.map(_.fold(l => InternalServerError("ERROR: " + l.msg), r => Ok(r)))
    futureResult
  }
}
