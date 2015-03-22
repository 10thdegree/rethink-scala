package client.nav

import scala.scalajs.js

trait NavModel extends js.Object {
	var multiple: Boolean = js.native
	var show: Boolean = js.native
}

object NavModel {
  def apply(multiple: Boolean,show: Boolean): NavModel =
    js.Dynamic.literal(multiple = multiple, show = show).asInstanceOf[NavModel]
}