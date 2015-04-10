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
import reporting.models.DataSourceDoc
import reporting.models.ds.dart._
import scalaz.Scalaz._
import scalaz._
import java.util.UUID
import play.api.Logger

class DataSourceController[S <: DataSource] (override implicit val env: RuntimeEnvironment[User], implicit val unpickler: UnpickledCurry[S]) extends securesocial.core.SecureSocial[User]  {

  implicit val c = Connection.connection
  lazy val coreBroker: CoreBroker = new CoreBroker

  def addAccountDataSource() = Action(BodyParsers.parse.json) {
    request => Ok("Generic")
  }


}


class DartDSController @Inject() (override implicit val env: RuntimeEnvironment[User], override implicit val unpickler: UnpickledCurry[DartDS]) extends DataSourceController[DartDS] {
  override def addAccountDataSource() = Action(BodyParsers.parse.json) {
    request =>
      unpickler.fromString(pjson.Json.stringify(request.body)) match {
        case Failure(errors) => {
          BadRequest(pjson.Json.obj("status" -> "error", "message" -> errors.getMessage))
        }
        case Success(addDataSource) => {
          import com.rethinkscala.Blocking._
          coreBroker.datasourcesTable.insert(new DataSourceDoc(addDataSource)).run match {
            case Right(x) => {
              Ok(pjson.Json.obj("status" -> "OK", "datasources" -> pjson.Json.parse(Reflector.toJson(addDataSource))))
            }
            case Left(x) => BadRequest(pjson.Json.obj("status" -> "OK", "message" -> x.getMessage))
          }
        }
      }
  }

  def getAccountDataSource(accountId: String) = Action {
    import com.rethinkscala.Blocking._
    coreBroker.datasourcesTable.filter(f => (f \ "datasource").contains(i => (i \ "accountId" === accountId))).run match {
      case Right(tx) => {
         val dataSources  = tx.map(x => Map("id" -> x.id, "label" -> x.datasource.label, "queryId" -> x.datasource.queryId))
        Ok(pjson.Json.parse(Reflector.toJson(dataSources)))
      }
      case Left(er) => BadRequest(pjson.Json.toJson(Map("error" -> er.getMessage)))
    }
  }

  import reporting.models.ds.dart._
  import core.util.ResponseUtil._

  implicit val dartCfgPickle: Pickler[DartDS] = Pickler.materializePickler[DartDS]

  def sample() = Action {
    implicit request =>
      val keySelector = new SimpleKeySelector("Paid Search Campaign",
      Map[String, String](
          "^(Brand).*$" -> "$1",
          "^(Partners)hips.*$" -> "$1",
          "^(Accredited)$" -> "$1",
          "^(Auto Suggest).*$" -> "$1",
          "^(Bachelors).*" -> "$1",
          "^Online (Bachelors).*$" -> "$1",
          "^(Certificate).*$" -> "$1",
          "^(Anthem College).*$" -> "$1",
          "^(Competitors).*$" -> "Anthem College",
          "^(Content).*$" -> "$1",
          "^Remarketing (Content).*$" -> "$1",
          "^(Distance Learning).*$" -> "$1",
          "^(Doctorate).*" -> "$1",
          "^(Masters).*$" -> "$1",
          "^(Military).*$" -> "$1",
          "^(Degree).*$" -> "$1",
          "^(Veterans).*$" -> "$1",
          "^(PhD).*$" -> "$1",
          "^(Undergraduate).*$" -> "$1",
          "^(Veterans).*$" -> "$1",
          "^(Online).*$" -> "$1"),
      "Other"
    )

    val dateSelector = DateSelector("Date", "yyyy-MM-dd")

    val dartDs = DartDS(
      "Dart DS for Search Performance",
      "18158200", // dart query-id
      UUID.randomUUID(),
      List(keySelector),
      dateSelector,
      UUID.randomUUID().some
    )
    Ok(Pickle.intoString(dartDs))
  }
}
