package reporting.controllers

import com.google.inject.Inject
import com.rethinkscala.reflect.Reflector
import core.dataBrokers.{Connection, CoreBroker}
import core.models._
import play.api.libs.{json => pjson}
import play.api.mvc.{Action, BodyParsers}
import securesocial.core.RuntimeEnvironment
import prickle.{Pickle, Pickler, UnpickledCurry, Unpickler}

import reporting.models.ds._
import reporting.models.ds.dart._

class DataSourceController[S <: DataSource] @Inject() (override implicit val env: RuntimeEnvironment[User], implicit val dataSourceFormt: pjson.Format[S]) extends securesocial.core.SecureSocial[User]  {
  implicit val keySelectorFormat = pjson.Json.format[KeySelector]
  implicit val dataSelectorFormat = pjson.Json.format[DateSelector]

  implicit val c = Connection.connection
  lazy val coreBroker: CoreBroker = new CoreBroker

  def addAccountDataSource(accountId: String) = Action(BodyParsers.parse.json) {
    request =>
      request.body.validate[S].fold(
        errors => {
          BadRequest(pjson.Json.obj("status" -> "OK", "message" -> pjson.JsError.toFlatJson(errors)))
        },
        addDataSource => {
          import com.rethinkscala.Blocking._
          coreBroker.accountsTable.get(accountId).run match {
            case Right(x) => {
              val existingDataSources= x.datasources
              val updatedDataSources = (addDataSource :: existingDataSources).distinct
              coreBroker.accountsTable.get(accountId).update(
                Map("datasources" -> updatedDataSources.map(_.toString))
              ).run
              Ok(pjson.Json.obj("status" -> "OK", "datasources" -> pjson.Json.parse(Reflector.toJson(updatedDataSources))))
            }
            case Left(x) => BadRequest(pjson.Json.obj("status" -> "OK", "message" -> x.getMessage))
          }
        }
      )
  }
}

class DartDSController @Inject() (override implicit val env: RuntimeEnvironment[User], override implicit val dataSourceFormt: pjson.Format[DartDS]) extends DataSourceController[DartDS] {
  implicit val dataSourceFormat =  pjson.Json.format[DartDS]
}
