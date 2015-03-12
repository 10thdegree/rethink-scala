package userManage

import biz.enef.angulate.{Controller, Scope}

import scala.scalajs.js

class CoreCtrl extends Controller {
  protected def handleError(ex: Throwable): Unit = js.Dynamic.global.console.error(s"An error has occurred: $ex")
}

trait ModalScope extends Scope {
  var ok: js.Function = js.native
  var cancel: js.Function = js.native
  var focusInput: Boolean = js.native
}
