package login

import scala.scalajs.js

trait Credential extends js.Object {
  var username: String = js.native
  var password: String = js.native
}

object Credential {
  def apply(username: String, password: String) : Credential =
    js.Dynamic.literal(username=username, password=password).asInstanceOf[Credential]
}

trait Authenticated extends js.Object {
  var redirect: String = js.native
}