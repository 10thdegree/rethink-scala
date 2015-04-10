package client.report

import biz.enef.angulate.Scope

import scala.scalajs.js
import scala.util.{Failure, Success}
import org.scalajs.dom

import client.core.{SearchCtrl, SearchScope, MultipleAccountChosen, CoreEvent}
import shared.models._
import js.JSConverters._

trait ReportScope extends SearchScope[DartAccountCfg] {
  var activeAccount: Int = js.native
  var searchReport: js.Function = js.native
}

trait ReportParams extends js.Object {
	val accounts: js.UndefOr[String] = js.native
}

class ReportCtrl(reportService: ReportService, dataSourceService: DataSourceService, $scope: ReportScope,
	$filter: js.Dynamic, $stateParams: ReportParams, $state: js.Dynamic, $window: js.Dynamic,
	var $cookieStore: js.Dynamic, var $timeout: js.Dynamic)
extends SearchCtrl[DartAccountCfg]($scope: SearchScope[DartAccountCfg], $filter: js.Dynamic)
with MultipleAccountChosen {
  var reportsLoaded = false
  refresh()
  $scope.activeAccount = 0

  def refresh(): Unit = {
		val accountParams = $stateParams.accounts.getOrElse("") match {
			case x if x == "" => {
				val accountIds = getAccountIds()
				accountChanged(accountIds)
				accountIds
			}
			case x => 
				val accountIds: js.Array[String] = x.split("&").toJSArray
				accountUpdated(accountIds)
				accountIds
		}
    accountParams.map(x => dataSourceService.advertisers("dart",x) onComplete {
      case Success(resp) =>
      resp.toJSArray.map(x => $scope.ogList += x)
        reportsLoaded = true
        $scope.numListItems = $scope.ogList.length
        $scope.searchList = $filter("filter")($scope.ogList, $scope.watch.searchTerm).asInstanceOf[js.Array[DartAccountCfg]]
        $scope.visibleListItems = $scope.searchList.length
        $scope.filterList = $filter("filter")($scope.searchList, $scope.paginate).asInstanceOf[js.Array[DartAccountCfg]]

      case Failure(ex) => handleError(ex)
    })
    //$scope.ogList = reportService.getReports(accountParams)
	
    // reportService.getReports($stateParams.accounts.split("&").asInstanceOf[js.Array[String]]) onComplete {
    //   case Success(resp) =>
    //     reportsLoaded = true
    //     $scope.numListItems = resp.reports.length
    //     $scope.ogList = resp.reports.sortWith(_.label < _.label)
    //     $scope.searchList = $filter("filter")($scope.ogList, $scope.watch.searchTerm).asInstanceOf[js.Array[Report]]
    //     $scope.visibleListItems = $scope.searchList.length
    //     $scope.filterList = $filter("filter")($scope.searchList, $scope.paginate).asInstanceOf[js.Array[Report]]
    //   case Failure(ex) => handleError(ex)
    // }
  }

  $scope.searchReport = (advertiserId: Int) => {
    $scope.activeAccount = advertiserId;
    reportService.searchReport(advertiserId) onComplete {
      case Success(resp) =>
        js.Dynamic.global.console.error(resp)
        $window.location.href = "/reporting/reportGrid#/?rid="+resp+"&startDate=2015-04-02&endDate=2015-04-06"
        //$window.location.reload()
      case Failure(ex) => handleError(ex)
    }
  }

	def accountChanged(accountIds: js.Array[String]): Unit = {
    if ($state.includes("reports").asInstanceOf[Boolean] || $state.includes("accounts").asInstanceOf[Boolean]) {
		  $state.go("accounts", js.Dictionary("accounts" -> accountIds.join("&")))
    }
	}
	
	def accountUpdated(accountIds: js.Array[String]): Unit = {
		$cookieStore.put("accounts",accountIds.map((x: String) => x))
		dom.document.dispatchEvent(CoreEvent.changeChosenAccounts)
	}
}
