package reporting.controllers

import play.api._
import play.api.mvc._
import play.Logger
import reporting.engine.SimpleReportGenerator
import reporting.models.{Fees, ds}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global 
import org.joda.time._
import bravo.api.dart._ 
import org.joda.time.format.DateTimeFormat
import bravo.core.Util._
import play.api.libs.json._
import play.api.Play.current
import scalaz.{-\/, \/, \/-}
import akka.pattern.pipe
import scala.concurrent.Await
import scala.concurrent.duration._
import java.util.concurrent.atomic.AtomicReference

object TuiReportController extends Controller {
  
  private val cache = new AtomicReference(Map[String,List[Map[String,String]]]())
  

  def reportGrid(startDate: String, endDate: String) = Action {
    Ok(reporting.views.html.reportgrid(startDate, endDate))
  }

  /////////// ACTOR STUFF MOVE TO ANOTHER CLASS ///////////////////
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
        reportAsync(startDate, endDate)
          //.map(_.fold(err => err,json => json.toString))
          .foreach(_ match {
            case -\/(err) =>
              Logger.error("Report generation failed: " + err)
            case \/-(json) =>
              Logger.debug("ok sending back to the ws")
              try {
                out ! json.toString
              } catch {
                case ex: Exception => Logger.error(ex.toString)
              }
          })
    }
  }
  /////////////////END ACTOR STUFF /////////////

  //18158200 Trident Report 
  def reportDataRequest(startDate: String, endDate: String) = Action.async { 
    val parsedReport = reportAsync(startDate, endDate)
    val futureResult = parsedReport.map(_.fold(errmsg => InternalServerError("ERROR: " + errmsg), r => Ok(r)))
    futureResult
  }

  def reportAsync(startDate: String, endDate: String): Future[\/[String, JsValue]] = {
    Logger.error( cache.get.size + " is the cache size") 
    
    val config = LiveTest.prodConfig.copy(m = cache.get)

    val frmt = DateTimeFormat.forPattern("yyyy-mm-dd")
    val start = frmt.parseDateTime(startDate)
    val end = frmt.parseDateTime(endDate)

    //val tui = reporting.util.TUIReportHelper
    val ro = reporting.util.TUIReportHelper.TUISearchPerformanceRO()
    implicit val servingFeesLookup = new Fees.FeesLookup(ro.servingFees)
    implicit val agencyFeesLookup = new Fees.FeesLookup(ro.agencyFees)
    Logger.debug("Compiling report fields...")
    val gen = new SimpleReportGenerator(ro.report, ro.fields)
    Logger.debug("Building DS row factory...")
    val dsf = new ds.DataSource.DataSourceRowFactory(ro.ds) //factorys?
    val viewFields = ro.view.fieldIds.map(ro.fieldsLookupById).toList

    Logger.debug("Looking up required DS attributes...")
    // List of fields (numeric) that we need when generating the report.
    val requiredAttributes = gen.requiredFieldBindings.map(_._2.dataSourceAttribute).toSet

    import reporting.engine.GeneratedReport

    def convertResult(rows:List[Map[String,String]]): GeneratedReport = {
      //Logger.debug("==> " + requiredAttributes)
      //Logger.debug("=!> " + rows_(0).keys)
      Logger.debug("Verifying returned data...")
      val missingFbs = gen.findMissingFieldBindings(rows(0).keys.toSet)
      if (missingFbs.nonEmpty) {
        val missing = missingFbs.map(x => x._1 -> x._2.dataSourceAttribute).map({case (field, attr) => "Field \"%s\" needs attribute \"%s\"".format(field, attr) })
        Logger.error("Missing required attributes from field bindings: " + missing)
        GeneratedReport(viewFields, List())
      } else {
        //Logger.debug(rows.map(_("Paid Search Campaign")).toSet.mkString("\n"))
        Logger.debug("Converting raw key/value string data...")
        val converted = dsf.convertValues(requiredAttributes)(rows: _*)
        val dsRows = dsf.process(converted: _*)
        Logger.debug("Generating report...")
        val res = gen.getReport(ro.ds, dsRows)(start, end) //does this need start/end? we feed in the data?
        Logger.debug(s"Generated report with ${res.size} rows")
        GeneratedReport(viewFields, res).sortRowsBy(ro.view.defaultFieldSort)
      }
    }

    Logger.debug("WHAT??? qid = " + ro.ds.queryId)

    import play.api.libs.json._
    import play.api.libs.functional.syntax._
    import scala.collection.SortedMap

    //TODO: can we move this to a general JSON helper lib?

    implicit val generatedReportFieldValueWrites: Writes[GeneratedReport.FieldValue] = (
      (JsPath \ "val").write[BigDecimal] and
        (JsPath \ "disp").write[String]
      )((v: GeneratedReport.FieldValue) => (v.value, v.display))

    implicit val fieldWrites: Writes[reporting.models.Field] = (
      (JsPath \ "varName").write[String] and
        (JsPath \ "displayName").write[String]
      )((f: reporting.models.Field) => (f.varName, f.label))

    implicit val generatedReportRowWrites: Writes[GeneratedReport.Row] = (
      (JsPath \ "key").write[String] and
        //(JsPath \ "date").write[String] and
        (JsPath \ "values").write[Map[String, GeneratedReport.FieldValue]]
      )((row: GeneratedReport.Row) => (
      row.keys.mkString("-"),
      //row.date.toString("yyyy-MM-dd"),
      row.orderedFields(viewFields).map({case (k,v) => k.varName -> v }).toMap))//asInstanceOf[Map[String, GeneratedReport.FieldValue]]

    implicit val generatedReportWrites: Writes[GeneratedReport] = (
      (JsPath \ "fields").write[List[reporting.models.Field]] and
        (JsPath \ "rows").write[List[GeneratedReport.Row]]
      )((r:GeneratedReport) => (r.fields.toList, r.rows))

    val report = Dart.getReport(ro.ds.queryId.toInt, start, end)
    val parsedReport = report
      .map(dr => convertResult(dr.data))
      .map(li => Json.toJson(li))
      .run.run(config)
      .map(t => {
        cache.set(t._1.m)
        t._2
      }) //feed in the cache again
      
    parsedReport.map(_.leftMap(err => err.msg)) //we could return the transformer stack, but we want to see both erorr/not error so i think this is better?
  }

}

