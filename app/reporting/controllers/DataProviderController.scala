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


  private def handleGetAdvertisers(dataProvider: String) = Action.async { implicit request =>
    rep.dataProviders.get(dataProvider).map {
      _.getAdvertisers.map {
        advertisers => Ok(pjson.Json.obj("status" -> "Ok", "message" -> advertisers._2.toString))
      }
    } getOrElse {
      Future.successful(NotFound)
    }
  }
}
