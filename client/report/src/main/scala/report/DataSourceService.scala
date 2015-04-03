package client.report

import biz.enef.angulate.Service
import biz.enef.angulate.core.{HttpPromise, HttpService}
import prickle.Unpickle
import scala.scalajs.js
import shared.models._
import client.core.PicklerHttpService
import client.core.PicklerHttpService._
import java.util.UUID

class DataSourceService($http: PicklerHttpService) extends Service {

  def dataSources() : HttpPromise[List[ProviderInfo]] = $http.getObject("/reporting/ds")

  def allAdvertisers(dataSourceId: String) : HttpPromise[List[AdvertiserInfo]] = $http.getObject(s"/reporting/ds/$dataSourceId/advertisers")

  import prickle._
  import java.util.UUID
  import collection.mutable
  import scala.util.Try

  implicit object UUIDUnpickler extends Unpickler[UUID] {
    def unpickle[P](pickle: P, state: mutable.Map[String, Any])(implicit config: PConfig[P]) = config.readString(pickle).flatMap(s => Try(UUID.fromString(s)))
  }

  implicit object UUIDPickler extends Pickler[UUID] {
    def pickle[P](x: UUID, state: PickleState)(implicit config: PConfig[P]): P = config.makeString(x.toString)
  }

  def addAdvertiser(dataSourceId: String, accoundId: String, config: DartAccountCfg) : HttpPromise[String] = $http.postObject(s"/reporting/ds/$dataSourceId/$accoundId/cfg", config)

  def advertisers(dataSourceId: String, accoundId: String) : HttpPromise[List[DartAccountCfg]] = $http.getObject(s"/reporting/ds/$dataSourceId/$accoundId/cfg")
}
