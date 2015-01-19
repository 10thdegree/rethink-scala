package reporting.controllers

import play.api._
import play.api.mvc._
import play.Logger
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

 
  def reportGrid(rid: Int, startDate: String, endDate: String) = Action {
    Ok(reporting.views.html.reportgrid(startDate, endDate, rid))
  }
  
  //18158200 Trident Report 
  def reportData(rid: Int, startDate: String, endDate: String) = Action.async {
    Logger.error("WHAT??? rid = " + rid)
    //validate dates

    val config = Dart.prodConfig
    //val sdate = "2015-01-01"
    //val edate = "2015-01-30"

    val frmt = DateTimeFormat.forPattern("yyyy-mm-dd")
    val report = Dart.getReport(rid, frmt.parseDateTime(startDate), frmt.parseDateTime(endDate))   
    val parsedReport = report
      .map(dr => ReportParser.parse(dr.data))
      .map(li => Json.toJson(li))// we should wrap this
      .run.run(config) 
      
    val futureResult = parsedReport.map(_.fold(l => InternalServerError("ERROR"), r => Ok(r)))
    futureResult
  }
}
