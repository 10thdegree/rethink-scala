package client.nav

import biz.enef.angulate._
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement

import scala.scalajs.js.JSApp

object NavApp extends JSApp {
  override def main(): Unit = {
    val module = angular.createModule("navBar", Seq("ui.bootstrap"))

    module.controllerOf[NavCtrl]("NavCtrl")

    module.serviceOf[NavService]

    dom.document.addEventListener("DOMContentLoaded", {(e: dom.Event) =>
      angular.bootstrap(dom.document.getElementById("mainNavigation").asInstanceOf[HTMLElement], Seq("navBar"))
    })
  }
}
