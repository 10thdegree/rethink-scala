package reporting.controllers

import com.google.inject.Inject
import com.rethinkscala.reflect.Reflector
import core.dataBrokers.{Connection, CoreBroker}
import core.models._
import play.api.libs.{json => pjson}
import play.api.mvc.{Action, BodyParsers}
import securesocial.core.RuntimeEnvironment
import prickle._
import scala.util.{Try, Success, Failure}
import reporting.models.ds._
import reporting.models.ds.dart._
import scalaz.Scalaz._
import scalaz._

class DataSourceController[S <: DataSource] (override implicit val env: RuntimeEnvironment[User], implicit val unpickler: UnpickledCurry[S]) extends securesocial.core.SecureSocial[User]  {

  implicit val c = Connection.connection
  lazy val coreBroker: CoreBroker = new CoreBroker

  def addAccountDataSource(accountId: String) = Action(BodyParsers.parse.json) {
    request =>
      unpickler.fromString(pjson.Json.stringify(request.body)) match {
        case Failure(errors) => {
          BadRequest(pjson.Json.obj("status" -> "error", "message" -> errors.getMessage))
        }
        case Success(addDataSource) => {
          //import com.rethinkscala.Blocking._
          //coreBroker.accountsTable.get(accountId).run match {
            //case Right(x) => {
              //val updatedDataSources = x.datasources match {
                //case null | Nil => List(addDataSource)
                //case li => (addDataSource :: li).distinct
              //}
              //coreBroker.accountsTable.get(accountId).update(
                //Map("datasources" -> updatedDataSources.map(Reflector.toMap(_)))
              //).run
              Ok(pjson.Json.obj("status" -> "OK", "datasources" -> pjson.Json.parse(Reflector.toJson(addDataSource))))
            //}
            //case Left(x) => BadRequest(pjson.Json.obj("status" -> "OK", "message" -> x.getMessage))
          //}
        }
      }
  }
}


class DartDSController @Inject() (override implicit val env: RuntimeEnvironment[User], override implicit val unpickler: UnpickledCurry[DartDS]) extends DataSourceController[DartDS]
