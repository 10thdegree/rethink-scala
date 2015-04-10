package reporting.core

import concurrent.Future
import com.rethinkscala.reflect.Reflector
import core.dataBrokers.{Connection, CoreBroker}
import prickle._
import play.api.libs.{json => pjson}
import scala.concurrent.{ExecutionContext, Future}

import bravo.util.Util._
import bravo.api.dart._
import reporting.models.ds._
import scala.util.{Try, Success, Failure}
import scalaz._
import scalaz.Scalaz._
import reporting.models.ds.dart.DartAccountCfg
import shared.models.ProviderInfo
//abstract class IdentityDataProvider(implicit val executionContext: ExecutionContext) {

trait IdentityDataProvider {
  implicit val executionContext: ExecutionContext

  implicit val c = Connection.connection
  lazy val coreBroker: CoreBroker = new CoreBroker

  val id: String

  val info: ProviderInfo
  val imagePath = "/reporting/assets/images/ds/"

  implicit val accountCfgUnpickler: UnpickledCurry[_ <: DSAccountCfg]

  override def toString = id

  def getAdvertisers: BravoM[GlobalConfig,List[(String, Int)]]
  //def getAdvertisers: Future[(Data.DartConfig, \/[JazelError,List[(String, Int)]])]
  def getSearchReport(advertiserId: Long): BravoM[GlobalConfig,Long]
  def getDisplayReport(advertiserId: Long): BravoM[GlobalConfig,Long]

  def addAccountCfg(accountId: String, jsValue: pjson.JsValue): Future[\/[String,List[DSAccountCfg]]] = Future {
    accountCfgUnpickler.fromString(pjson.Json.stringify(jsValue)) match {
      case Failure(errors) => {
        -\/(errors.getMessage)
      }
      case Success(addAccountCfg) => {
        import com.rethinkscala.Blocking._
        coreBroker.accountsTable.get(accountId).run match {
          case Right(x) => {
            val updatedAccountCfgs = x.dsCfg match {
              case null | Nil => List(addAccountCfg)
              case li => (addAccountCfg :: li).distinct
            }
            coreBroker.accountsTable.get(accountId).update(
              Map("dsCfg" -> updatedAccountCfgs.map(Reflector.toMap(_)))
            ).run
            \/-(updatedAccountCfgs)
          }
          case Left(x) =>
            -\/(x.getMessage)
        }
      }
    }
  }

  def getAccountCfg(accountId: String): Future[\/[String,String]]
  //def getAccountCfg(accountId: String): Future[\/[String,String]] = Future {
    //import com.rethinkscala.Blocking._
    //coreBroker.accountsTable.get(accountId).run match {
      //case Right(x) => {
        //\/-(x.dsCfg)
      //}
      //case Left(x) =>
        //-\/(x.getMessage)
    //}
  //}
}
