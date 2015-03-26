package reporting.controllers

import play.api.Play
import play.api.Play.current
import play.api.i18n.Messages
import play.api.mvc._
import reporting.core._
import securesocial.core.RuntimeEnvironment
import reporting.core._
import core.models._
import scala.concurrent.{ ExecutionContext, Future }
import play.api.libs.{json => pjson}
import com.google.inject.Inject

import scalaz._
import Scalaz._

class DataProviderController @Inject() (override implicit val env: RuntimeEnvironment[User], override implicit val rep: ReportingRuntime)
  extends BaseDataProviderController

trait BaseDataProviderController extends securesocial.core.SecureSocial[User] {
  implicit val rep: ReportingRuntime
  implicit def executionContext: ExecutionContext = rep.executionContext

  def advertisers(dataProvider: String) = handleGetAdvertisers(dataProvider)
  
 

  //so we can use the request to load up the data for which configs to use from the DB, but they will always end up in the GlobalConfig

  //we may want to leftmap the error to an HTML error message and log?
  //STYLE NOTE: if a method is longer than one line, let's ascribe a return type 
  private def handleGetAdvertisers(dataProvider: String) = Action.async { implicit request =>
    rep.dataProviders.get(dataProvider).map(o => {
      val res = o.getAdvertisers.map( {
        advertisers => Ok(pjson.Json.obj("status" -> "Ok", "message" -> advertisers.toString))
      })
      .leftMap(je => Ok(pjson.Json.obj("status" -> "OK", "message" -> je.toString))) //QUESTION do we want an error here or a successful response wtih an error msg to the JSON for friendly display?
      .run
      .run(ReportingRuntime.globalConfig)
      .map(_._2.fold(l => l, r => r)) //folding both sides of our \/ into one mvc REsult
      res
    }).getOrElse {
      Future.successful(NotFound)
    }
  }
}
