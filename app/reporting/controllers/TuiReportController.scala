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
import java.util.concurrent.atomic.AtomicReference
import bravo.api.dart.Data._
import bravo.util.DateUtil._
import bravo.util.Data._

object TuiReportController extends Controller {
  
  private val cache = new AtomicReference(Map[Long,List[ReportDay]]())
  
  def reportGrid() = Action {
    Ok(reporting.views.html.reportgrid("",""))
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
        val viewId = (json \ "viewId").as[String]
        Logger.debug("Got view id: " + viewId)
        reportAsync(viewId, startDate, endDate)
          //.map(_.fold(err => err,json => json.toString))
          .foreach({
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
  def reportDataRequest(viewId: String, startDate: String, endDate: String) = Action.async {
    Logger.error("startDate = " + startDate + " endDate  = " + endDate)
    val parsedReport = reportAsync(viewId, startDate, endDate)
    val futureResult = parsedReport.map(_.fold(errmsg => InternalServerError("ERROR: " + errmsg), r => Ok(r)))
    futureResult
  }

  def reportViews() = Action {
    import reporting.util.json.GeneratedReportWrites._
    val ro = reporting.util.TUIReportHelper.TUISearchPerformanceRO()
    Ok(Json.toJson(ro.views))
  }

  def reportAsync(viewId: String, startDate: String, endDate: String): Future[\/[String, JsValue]] = {
    Logger.error(" reportAsync startDate & endDate = " + startDate + " | " + endDate)
    Logger.error( cache.get.values.map(_.size) + " is the cache size") 
    
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
    val view = ro.views.find(_.id.get.toString == viewId).getOrElse(ro.views(0))
    Logger.debug(s"Will use view ${view.label}")
    val viewFields = view.fieldIds.map(ro.fieldsLookupById).toList

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
        GeneratedReport(viewFields, List(), List())
      } else {
        //Logger.debug(rows.map(_("Paid Search Campaign")).toSet.mkString("\n"))
        Logger.debug("Converting raw key/value string data...")
        val converted = dsf.convertValues(requiredAttributes)(rows: _*)
        val dsRows = dsf.process(converted: _*)
        Logger.debug("Generating report...")
        val res = gen.getReport(ro.ds, dsRows)(start, end) //does this need start/end? we feed in the data?
        Logger.debug(s"Generated report with ${res.size} rows")
        GeneratedReport(viewFields, res, view.charts).sortRowsBy(view.defaultFieldSort)
      }
    }

    Logger.debug("Dart qid = " + ro.ds.queryId)

    import reporting.util.json.GeneratedReportWrites._
    import play.api.libs.json._
    Logger.error("about to fetch the eport from dart, " + start + " end = " + end)
    val report = Dart.getReport(ro.ds.queryId.toInt, start, end)
    val parsedReport = report
      .map(dr => convertResult(ungroupDates(dr.data))) //we can keep the grouping here and remove it from the other stuff
      .map(li => Json.toJson(li))
      .run.run(config)
      .map({
        case (cfg, res) =>
          cache.set(cfg.m)
          res
      }) //feed in the cache again
      
    val finalRes = parsedReport.map(_.leftMap(err => err.msg)) //we could return the transformer stack, but we want to see both erorr/not error so i think this is better?
    Logger.debug("reportAsync(): finished.")
    finalRes
  }
}
