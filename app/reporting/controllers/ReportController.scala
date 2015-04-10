package reporting.controllers

import play.api._
import play.api.mvc._
import play.Logger
import reporting.engine.SimpleReportGenerator
import reporting.models.{Fees, ds, DataSourceDoc}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global 
import org.joda.time._
import org.joda.time.format.DateTimeFormat
import play.api.libs.json._
import play.api.Play.current
import scalaz.{-\/, \/, \/-}
import akka.pattern.pipe
import java.util.concurrent.atomic.AtomicReference
//import bravo.api.dart.Data._
import bravo.api.dart.Data._
import bravo.api.dart._
import bravo.util.Util._
import bravo.util.DateUtil._
import securesocial.core.RuntimeEnvironment
import scala.concurrent.{ ExecutionContext, Future }
import com.google.inject.Inject
import core.models._
import core.dataBrokers.{Connection, CoreBroker}

class ReportController @Inject() (override implicit val env: RuntimeEnvironment[User]) extends securesocial.core.SecureSocial[User] {

  implicit val c = Connection.connection
  lazy val coreBroker: CoreBroker = new CoreBroker

  private val cache = new AtomicReference(Map[Long,List[ReportDay]]())

  /////////// ACTOR STUFF MOVE TO ANOTHER CLASS ///////////////////
  //def socket = WebSocket.acceptWithActor[String, String] { request => out =>
    //ReportDataActor.props(out)
  //}

  //import akka.actor._

  //object ReportDataActor {
    //def props(out: ActorRef) = Props(new ReportDataActor(out))
  //}

  //class ReportDataActor(out: ActorRef) extends Actor {
    //def receive = {
      //case msg: String =>
        //Logger.debug("blah");
        //val json = Json.parse(msg)
        //val startDate = (json \ "startDate").as[String]
        //val endDate = (json \ "endDate").as[String] //TODO: option, then for comp, handle errors etc.
        //val viewId = (json \ "viewId").as[String]
        //Logger.debug("Got view id: " + viewId)
        //reportAsync(viewId, startDate, endDate)
          ////.map(_.fold(err => err,json => json.toString))
          //.foreach({
            //case -\/(err) =>
              //Logger.error("Report generation failed: " + err)
            //case \/-(json) =>
              //Logger.debug("ok sending back to the ws")
              //try {
                //out ! json.toString
              //} catch {
                //case ex: Exception => Logger.error(ex.toString)
              //}
          //})
    //}
  //}
  /////////////////END ACTOR STUFF /////////////

  def reportDataRequest(viewId: String, startDate: String, endDate: String, queryId: String) = Action.async {
    Logger.error("startDate = " + startDate + " endDate  = " + endDate)
    val parsedReport = reportAsync(viewId, startDate, endDate, queryId)
    val futureResult = parsedReport.map(_.fold(errmsg => InternalServerError("ERROR: " + errmsg), r => Ok(r)))
    futureResult
  }

  private def getDsDart(dartDsId: String): DataSourceDoc = {
    import com.rethinkscala.Blocking._
    coreBroker.datasourcesTable.get(dartDsId).run match {
      case Right(tx) => tx
      case Left(err) => new DataSourceDoc()
    }
  }

  def reportViews(queryId: String) = Action {
    import reporting.util.json.GeneratedReportWrites._
    val ro = reporting.util.ReportHelper.SearchPerformanceRO(queryId)
    Ok(Json.toJson(ro.views))
  }

  def reportAsync(viewId: String, startDate: String, endDate: String, queryId: String): Future[\/[String, JsValue]] = {
    Logger.error( cache.get.values.map(_.size) + " is the cache size")

    val config = LiveTest.prodConfig.copy(reportCache = cache.get)

    val frmt = DateTimeFormat.forPattern("yyyy-MM-dd")
    val start = frmt.parseDateTime(startDate)
    val end = frmt.parseDateTime(endDate)
    val ro = reporting.util.ReportHelper.SearchPerformanceRO(queryId)
    implicit val servingFeesLookup = new Fees.FeesLookup(ro.servingFees)
    implicit val agencyFeesLookup = new Fees.FeesLookup(ro.agencyFees)
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
    val report = Dart.getReport(ro.ds.queryId.toInt, start, end)
    val parsedReport = report
      .map(dr => convertResult(ungroupDates(dr.data))) //we can keep the grouping here and remove it from the other stuff
      .map(li => Json.toJson(li))
      .run.run(config)
      .map({
        case (cfg, res) =>
          println(" SETTING THE CACHE")
          cache.set(cfg.reportCache)
          res
      }) //feed in the cache again

    val finalRes = parsedReport.map(_.leftMap(err => err.msg)) //we could return the transformer stack, but we want to see both erorr/not error so i think this is better?
    Logger.debug("reportAsync(): finished.")
    finalRes
  }
}
