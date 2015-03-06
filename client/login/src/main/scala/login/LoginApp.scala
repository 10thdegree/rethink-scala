package login

import biz.enef.angulate._

import scala.scalajs.js.JSApp

object LoginApp extends JSApp {
  override def main(): Unit = {
    val module = angular.createModule("myLogin", Seq("ui.bootstrap"))

    module.controllerOf[LoginCtrl]("LoginCtrl")

    module.serviceOf[LoginService]
  }
}
