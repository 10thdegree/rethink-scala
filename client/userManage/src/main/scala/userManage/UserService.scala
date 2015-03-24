package userManage

import biz.enef.angulate.Service
import biz.enef.angulate.core.{HttpConfig, HttpPromise, HttpService}

import scala.scalajs.js
import scala.scalajs.js.JSON

class UserService($http: HttpService) extends Service {

  def all(): HttpPromise[UsersFullResponse] = $http.get("/user")

  def allInvited(): HttpPromise[UsersInviteResponse] = $http.get("/user/invited")

  def addPermission(userId: String, accountPermission: AccountPermissionIds): HttpPromise[Unit] = $http.post(s"/user/permissions/$userId", accountPermission)

  def removePermission(userId: String, accountPermission: AccountPermissionIds): HttpPromise[Unit] =
    $http.delete(s"/user/permissions/$userId", HttpConfig(data = JSON.stringify(accountPermission), headers = js.Dictionary[js.Any]("Content-Type"->"application/json")))

  def invite(email: Email): HttpPromise[Unit] = $http.post("/invite", email)

  def removeInvite(inviteId: String): HttpPromise[Unit] = $http.delete(s"/user/invited/$inviteId")

  def remove(userId: String): HttpPromise[Unit] = $http.delete(s"/user/$userId")

}
