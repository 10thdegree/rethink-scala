package client.report

import biz.enef.angulate._
import scala.scalajs.js
import scala.scalajs.js.JSApp

import client.core.{NgEnterDirective, FocusMeDirective, SubNavCtrl}

object ReportApp extends JSApp {
  override def main(): Unit = {
		val module = angular.createModule("reportApp", Seq("ui.bootstrap","ngCookies", "ui.router"))

    module.controllerOf[ReportCtrl]("ReportCtrl")
    module.controllerOf[DataSourceCtrl]("DataSourceCtrl")
    module.controllerOf[DataSourceConfigureModalCtrl]("DataSourceConfigureModalCtrl")

    module.directiveOf[NgEnterDirective]
    module.directiveOf[FocusMeDirective]

    module.serviceOf[ReportService]
    module.serviceOf[DataSourceService]

    module.controllerOf[SubNavCtrl]("SubNavCtrl")

    val stateConfig: AnnotatedFunction = ($stateProvider: js.Dynamic, $urlRouterProvider: js.Dynamic) => {
      $urlRouterProvider.otherwise("/reports")

      $stateProvider
        .state("reports", js.Dictionary(
        "url" -> "/reports",
        "templateUrl" -> "/reporting/assets/partials/reports/reports.html",
        "controller" -> "ReportCtrl"
      ))
			.state("accounts", js.Dictionary(
			    "url" -> "/reports/:accounts",
	        "templateUrl" -> "/reporting/assets/partials/reports/reports.html",
	        "controller" -> "ReportCtrl"
			))
      $stateProvider
        .state("dataSources", js.Dictionary(
        "url" -> "/datasources",
        "templateUrl" -> "/reporting/assets/partials/reports/datasources.html",
        "controller" -> "DataSourceCtrl"
      ))
			.state("dataSourceAccount", js.Dictionary(
			    "url" -> "/datasources/:account",
	        "templateUrl" -> "/reporting/assets/partials/reports/datasources.html",
	        "controller" -> "DataSourceCtrl"
			))
    }

    module.config(stateConfig)
	}
}
