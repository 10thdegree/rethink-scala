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
import bravo.util.Util._
import play.api.libs.json._

object Application extends Controller {

  def index = Action {
    Ok(reporting.views.html.index("reporting"))
  }

  def noargs = Action {
    Logger.error("BLah")
    Ok("noargs")
  }

  def reports = Action {
    Ok(core.views.html.main(reporting.views.html.reports.head(), reporting.views.html.reports.main(), "Reporting",true))
  }
}
