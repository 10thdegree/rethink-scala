package userManage

import biz.enef.angulate.Service
import biz.enef.angulate.core.{HttpConfig, HttpPromise, HttpService}

import scala.scalajs.js
import scala.scalajs.js.JSON

class AccountService($http: HttpService) extends Service {

  def all(): HttpPromise[AccountsResponse] = $http.get("/account")

  def get(accountId: String): HttpPromise[Account] = $http.get(s"/account/$accountId")

  def users(accountId: String): HttpPromise[UsersResponse] = $http.get(s"/account/users/$accountId")

  def create(account: AccountAdd): HttpPromise[Unit] = $http.post("/account", account)

  def rename(accountId: String, label: AccountRename): HttpPromise[Unit] = $http.post(s"/account/rename/$accountId", label)

  def remove(accountId: String): HttpPromise[Unit] = $http.delete(s"/account/$accountId")

  def addPermission(accountId: String, permission: PermissionIds): HttpPromise[Unit] = $http.post(s"/account/permissions/$accountId", permission)

  def removePermission(accountId: String, permission: PermissionIds): HttpPromise[Unit] =
    $http.delete(s"/account/permissions/$accountId", HttpConfig(data = JSON.stringify(permission), headers = js.Dictionary[js.Any]("Content-Type"->"application/json")))

  def addUsers(accountId: String, userIds: UserIds): HttpPromise[Unit] = $http.post(s"/account/users/$accountId", userIds)

  def removeUsers(accountId: String, userIds: UserIds): HttpPromise[Unit] =
    $http.delete(s"/account/users/$accountId", HttpConfig(data = JSON.stringify(userIds), headers = js.Dictionary[js.Any]("Content-Type"->"application/json")))

}
