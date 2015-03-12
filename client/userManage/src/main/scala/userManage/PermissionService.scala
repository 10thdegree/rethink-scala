package userManage

import biz.enef.angulate.Service
import biz.enef.angulate.core.{HttpPromise, HttpService}

class PermissionService($http: HttpService) extends Service {

  def all(): HttpPromise[PermissionsResponse] = $http.get("/permission")

  def allCore(): HttpPromise[PermissionsResponse] = $http.get("/permission/readonly")

  def create(permission: Permission) : HttpPromise[Unit] = $http.post("/permission", permission)

  def rename(permissionId: String, label: String) : HttpPromise[Unit] = $http.post(s"/permission/rename/$permissionId", label)

  def remove(permissionId: String) : HttpPromise[Unit] = $http.delete(s"/permission/$permissionId")

}
