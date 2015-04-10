package reportGrid

import biz.enef.angulate._

import scala.scalajs.js.JSApp

object ReportGridApp extends JSApp {
  override def main(): Unit = {
    val app = angular.createModule("reportGridApp", Seq("ngTouch", "smart-table", "lrDragNDrop"))

    app.controllerOf[ReportCtrl]("ReportCtrl")

    app.serviceOf[ReportViewsService]
    app.serviceOf[ReportsService]
    app.directiveOf[BarChartDirective]("bvoBarChart")
    app.directiveOf[PieChartDirective]("bvoPieChart")
    app.directiveOf[DateRangePickerDirective]("bvoDateRangePicker")
  }
}