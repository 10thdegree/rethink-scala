package userManage

import scala.scalajs.js

trait AccountsResponse extends js.Object {
  var status: String = js.native
  var accounts: js.Array[Account] = js.native
}

trait Account extends js.Object {
  var id: String = js.native
  var label: String = js.native
  var permissions: js.Array[String] = js.native
}

object Account {
  def apply(): Account =
    js.Dynamic.literal(id = "", label = "", permissions = js.Array[String]()).asInstanceOf[Account]

  def apply(id: String, label: String, permissions: js.Array[String]): Account =
    js.Dynamic.literal(id = id, label = label, permissions = permissions).asInstanceOf[Account]
}

trait AccountAdd extends js.Object {
  var label: String = js.native
  var permissions: js.Array[String] = js.native
}

object AccountAdd {
  def apply(): AccountAdd =
    js.Dynamic.literal(label = "", permissions = js.Array[String]()).asInstanceOf[AccountAdd]

  def apply(label: String): AccountAdd =
    js.Dynamic.literal(label = label, permissions = js.Array[String]()).asInstanceOf[AccountAdd]
}

trait AccountRename extends js.Object {
  var label: String = js.native
}

object AccountRename {
  def apply(label: String): AccountRename =
    js.Dynamic.literal(label = label).asInstanceOf[AccountRename]
}

trait LastAccount extends js.Object {
  var status: String = js.native
  var lastSelectedAccount: String = js.native
}