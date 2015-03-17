package nav

import biz.enef.angulate.Service
import biz.enef.angulate.core.{HttpPromise, HttpService}
import prickle.Unpickle
import scala.scalajs.js
import shared.LastAccount

class NavService($http: HttpService) extends Service {

  implicit val lastAccountPickle = Unpickle[LastAccount]

  def selectAccount(accountId: String) : HttpPromise[Unit] = $http.post(s"/lastselectedaccount/$accountId")

  def accountSelected() : HttpPromise[js.Object] = $http.get("/lastselectedaccount")

  def availableAccounts() : HttpPromise[AccountsResponse] = $http.get("/availableaccount")

  def accountSelected2() : HttpPromise[LastAccount] = ConvertJsObject.convert[LastAccount]($http.get("/lastselectedaccount"))
}