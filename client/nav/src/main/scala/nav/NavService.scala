package nav

import biz.enef.angulate.Service
import biz.enef.angulate.core.{HttpPromise, HttpService}

class NavService($http: HttpService) extends Service {

  def selectAccount(accountId: String) : HttpPromise[Unit] = $http.post(s"/lastselectedaccount/$accountId")

  def accountSelected() : HttpPromise[LastAccount] = $http.get("/lastselectedaccount")

  def availableAccounts() : HttpPromise[AccountsResponse] = $http.get("/availableaccount")
}
