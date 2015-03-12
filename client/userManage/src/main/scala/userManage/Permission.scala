package userManage

import scala.scalajs.js

trait PermissionsResponse extends js.Object {
  var status: String = js.native
  var permissions: js.Array[Permission] = js.native
}

trait Permission extends js.Object {
  var id: String = js.native
  var label: String = js.native
  var readonly: Boolean = js.native
}

trait AccountPermissionIds extends js.Object {
  var accountId: String = js.native
  var permissionIds: js.Array[String] = js.native
}

object AccountPermissionIds {
  def apply(accountId: String, permissionId: String): AccountPermissionIds =
    js.Dynamic.literal(accountId = accountId, permissionIds = js.Array[String](permissionId)).asInstanceOf[AccountPermissionIds]
}

trait PermissionIds extends js.Object {
  var permissionIds: js.Array[String] = js.native
}

object PermissionIds {
  def apply(permissionId: String): PermissionIds =
    js.Dynamic.literal(permissionIds = js.Array[String](permissionId)).asInstanceOf[PermissionIds]
}

object Permission {
  def apply(): Permission =
    js.Dynamic.literal(id = "", label = "", readonly = false).asInstanceOf[Permission]

  def apply(id: String, label: String, readonly: Boolean): Permission =
    js.Dynamic.literal(id = id, label = label, readonly = readonly).asInstanceOf[Permission]
}

trait PermissionWatch extends js.Object {
  var searchPermission: String = js.native
  var currentPage: Int = js.native
}

object PermissionWatch {
  def apply(): PermissionWatch =
    js.Dynamic.literal(searchPermission = "", currentPage = 1).asInstanceOf[PermissionWatch]

  def apply(searchPermission: String, currentPage: Int): PermissionWatch =
    js.Dynamic.literal(searchPermission = searchPermission, currentPage = currentPage).asInstanceOf[PermissionWatch]
}