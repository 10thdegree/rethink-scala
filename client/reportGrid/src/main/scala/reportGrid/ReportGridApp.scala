package reportGrid

import biz.enef.angulate._

import scala.scalajs.js.JSApp

object ReportGridApp extends JSApp {
  override def main(): Unit = {
    val module = angular.createModule("app", Seq("ngTouch", "smart-table", "lrDragNDrop"))

    module.controllerOf[ReportCtrl]("ReportCtrl")

    module.serviceOf[ReportsService]
  }
}
