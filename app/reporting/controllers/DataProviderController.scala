package reporting.controllers

import play.api.Play
import play.api.Play.current
import play.api.i18n.Messages
import play.api.mvc._
import reporting.core._
import securesocial.core.RuntimeEnvironment
import core.models._
import scala.concurrent.{ ExecutionContext, Future }
import play.api.libs.{json => pjson}
import com.google.inject.Inject
import prickle._
import java.util.UUID
import shared.models.AdvertiserInfo

import scalaz._
import Scalaz._

class DataProviderController @Inject() (override implicit val env: RuntimeEnvironment[User], override implicit val rep: ReportingRuntime)
  extends BaseDataProviderController

trait BaseDataProviderController extends securesocial.core.SecureSocial[User] {
  import core.util.ResponseUtil._
  implicit val rep: ReportingRuntime
  implicit def executionContext: ExecutionContext = rep.executionContext

  def dataSources() = Action.async { implicit request =>
      Future.successful(Ok(Pickle.intoString(rep.dataProviders.values.map(_.info))))
  }

  def advertisers(dataProvider: String) = handleGetAdvertisers(dataProvider)

  def addDSAccountCfg(dataProvider: String, accountId: String) = handleAddDSAccountCfg(dataProvider, accountId)

  def getDSAccountCfg(dataProvider: String, accountId: String) = handleGetDSAccountCfg(dataProvider, accountId)

  //NOTE(VINCE) so we can use the request to load up the data for which configs to use from the DB, but they will always end up in the GlobalConfig
  //we may want to leftmap the error to an HTML error message and log?
  //STYLE NOTE: if a method is longer than one line, let's ascribe a return type
  private def handleGetAdvertisers(dataProvider: String) = Action.async { implicit request =>
    rep.dataProviders.get(dataProvider).map(o => {
      val res = o.getAdvertisers.map( {
        advertisers => Ok(Pickle.intoString(advertisers.map(info => new AdvertiserInfo(info._1,info._2))))
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

  private def handleAddDSAccountCfg(dataProvider: String, accountId: String) = Action.async(BodyParsers.parse.json) {
    implicit request =>
      rep.dataProviders.get(dataProvider).map {
        _.addAccountCfg(accountId, request.body).map {
          config => Ok(config.toString)
        }
      } getOrElse {
        Future.successful(NotFound)
      }
    }

  private def handleGetDSAccountCfg(dataProvider: String, accountId: String) = Action.async {
    implicit request =>
      rep.dataProviders.get(dataProvider).map {
        _.getAccountCfg(accountId).map {
          config => config match {
            case -\/(_) => NotFound
            case \/-(x) => Ok(x)
          }
        }
      } getOrElse {
        Future.successful(NotFound)
      }
    }

  // SAMPLE (OHDE) so I can see pickled object response
  import reporting.models.ds.dart._
  import core.util.ResponseUtil._

  implicit val dartCfgPickle: Pickler[DartAccountCfg] = Pickler.materializePickler[DartAccountCfg]

  def sampleDSAccountCfg() = Action {
    implicit request =>
      Ok(Pickle.intoString(new DartAccountCfg("Trident",123213,Some(UUID.randomUUID()))))
  }
}
