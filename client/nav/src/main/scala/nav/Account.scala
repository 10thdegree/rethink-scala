package nav

import scala.scalajs.js

trait AccountsResponse extends js.Object {
  var status: String = js.native
  var accounts: js.Array[Account] = js.native
}

trait Account extends js.Object {
  var id: String = js.native
  var label: String = js.native
}

object Account {
  def apply() : Account =
    js.Dynamic.literal(id="", label="").asInstanceOf[Account]

  def apply(id: String, label: String) : Account =
    js.Dynamic.literal(id=id, label=label).asInstanceOf[Account]
}
//
//trait LastAccount extends js.Object {
//  var status: String = js.native
//  var lastSelectedAccount: String = js.native
//}