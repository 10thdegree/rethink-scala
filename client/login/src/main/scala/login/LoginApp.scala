package login

import biz.enef.angular.Angular

import scala.scalajs.js.JSApp

object LoginApp extends JSApp {
  override def main(): Unit = {
    val module = Angular.module("myLogin", Seq("ui.bootstrap"))

    module.controllerOf[LoginCtrl]("LoginCtrl")

    module.serviceOf[LoginService]

  }
}
