package client.report

import biz.enef.angulate._
import scala.scalajs.js
import scala.scalajs.js.JSApp

import client.core.{NgEnterDirective, FocusMeDirective, SubNavCtrl}

object ReportApp extends JSApp {
  override def main(): Unit = {
		val module = angular.createModule("reportApp", Seq("ui.bootstrap","ngCookies", "ui.router"))

    module.controllerOf[ReportCtrl]("ReportCtrl")

    module.directiveOf[NgEnterDirective]
    module.directiveOf[FocusMeDirective]

    module.serviceOf[ReportService]

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
    }

    module.config(stateConfig)
	}
}
