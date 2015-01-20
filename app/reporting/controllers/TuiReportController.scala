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
import play.api.Play.current
import scalaz.\/
import akka.pattern.pipe
import scala.concurrent.Await
import scala.concurrent.duration._
object TuiReportController extends Controller {
  
  def reportGrid(startDate: String, endDate: String) = Action {
    Ok(reporting.views.html.reportgrid(startDate, endDate))
  }


  def socket = WebSocket.acceptWithActor[String, String] { request => out =>
    ReportDataActor.props(out)
  }

  import akka.actor._

  object ReportDataActor {
    def props(out: ActorRef) = Props(new ReportDataActor(out))
  }

  class ReportDataActor(out: ActorRef) extends Actor {
    def receive = {
      case msg: String =>
        Logger.debug("blah");
        val json = Json.parse(msg)
        val startDate = (json \ "startDate").as[String]
        val endDate = (json \ "endDate").as[String] //TODO: option, then for comp, handle errors etc.
        reportAsync(startDate, endDate).map(_.fold(err => err,json => json.toString)).foreach(json => {
        Logger.debug("ok sending back to the ws")
        try {
          out ! json
          } catch {
            case ex: Exception => Logger.error(ex.toString)
          }
        })
    }
  }

  //18158200 Trident Report 
  def reportDataRequest(startDate: String, endDate: String) = Action.async { 
    val parsedReport = reportAsync(startDate, endDate)
    val futureResult = parsedReport.map(_.fold(errmsg => InternalServerError("ERROR: " + errmsg), r => Ok(r)))
    futureResult
  }

  def reportAsync(startDate: String, endDate: String): Future[\/[String, JsValue]] = {
    val config = Dart.prodConfig
    val frmt = DateTimeFormat.forPattern("yyyy-mm-dd")
    val start = frmt.parseDateTime(startDate)
    val end = frmt.parseDateTime(endDate)

    //val tui = reporting.util.TUIReportHelper
    val ro = reporting.util.TUIReportHelper.TUISearchPerformanceRO()
    val gen = new SimpleReportGenerator(ro.report, ro.fields)
    val dsf = new ds.DataSource.DataSourceRowFactory(ro.ds) //factorys? 

    // List of fields (numeric) that we need when generating the report.
    val requiredAttributes = gen.requiredFieldBindings.map(_._2.dataSourceAttribute).toSet

    def convertResult(rows:List[Map[String,String]]) = {
      //Logger.debug("==> " + requiredAttributes)
      //Logger.debug("=!> " + rows_(0).keys)
      //Logger.debug("=+> " + requiredAttributes.filter(rows_(0).keys.toList.contains))
      Logger.debug(rows.map(_("Paid Search Campaign")).toSet.mkString("\n"))
      val converted = dsf.convertValues(requiredAttributes)(rows:_*)
      val dsRows = dsf.process(converted:_*)
      val res = gen.getReport(ro.ds, dsRows)(start, end) //does this need start/end? we feed in the data?
      res
    }

    Logger.debug("WHAT??? qid = " + ro.ds.queryId)

    import play.api.libs.json._
    import play.api.libs.functional.syntax._
    import reporting.engine.GeneratedReport

    //TODO: can we move this to a general JSON helper lib?
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
      
    //val futureResult = parsedReport.map(_.fold(l => InternalServerError("ERROR: " + l.msg), r => Ok(r)))
    parsedReport.map(_.leftMap(err => err.msg)) //we could return the transformer stack, but we want to see both erorr/not error so i think this is better?
  }

}

