package client.nav

import biz.enef.angulate.Service
import biz.enef.angulate.core.{HttpPromise, HttpService}
import prickle.Unpickle
import scala.scalajs.js
import shared._
import client.core.PicklerHttpService
import client.core.PicklerHttpService._

class NavService($http: PicklerHttpService) extends Service {

  implicit val lastAccountPickle = Unpickle[LastAccount]
  implicit val accountsResponsePickle = Unpickle[AccountsResponse]

  def selectAccount(accountId: String) : HttpPromise[Unit] = $http.post(s"/lastselectedaccount/$accountId")

  def availableAccounts() : HttpPromise[AccountsResponse] = $http.getObject("/availableaccount")

  def accountSelected() : HttpPromise[LastAccount] = $http.getObject("/lastselectedaccount")
}