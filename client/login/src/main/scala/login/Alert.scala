package login

import scala.scalajs.js

trait Alert extends js.Object {
  var `type`: String = js.native
  var msg: String = js.native
}

object Alert {
  def apply(`type`: String, msg: String) : Alert =
    js.Dynamic.literal(`type`=`type`, msg=msg).asInstanceOf[Alert]
}
