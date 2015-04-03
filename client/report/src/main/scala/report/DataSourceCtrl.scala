package client.report

import biz.enef.angulate.Scope

import scala.scalajs.js
import scala.util.{Failure, Success}
import org.scalajs.dom
import java.util.UUID

import client.core.{CoreCtrl, SingleAccountChosen, CoreEvent, ModalScope}
import shared.models._
import js.JSConverters._

trait DataSourceScope extends Scope {
  var ds: js.Array[ProviderInfo] = js.native
  var configureAdvertisers: js.Function = js.native
}

trait DataSourceParams extends js.Object {
	val account: js.UndefOr[String] = js.native
}

class DataSourceCtrl(
  dataSourceService: DataSourceService,
  $scope: DataSourceScope,
  $modal: js.Dynamic,
	$filter: js.Dynamic,
  $stateParams: DataSourceParams,
  $state: js.Dynamic,
	var $cookieStore: js.Dynamic,
  var $timeout: js.Dynamic)
extends CoreCtrl
with SingleAccountChosen {
  var dataSourcesLoaded = false
	$scope.ds = js.Array[ProviderInfo]()
  var accountParam: String = ""

  refresh()

  def refresh(): Unit = {
    accountParam = $stateParams.account.getOrElse("") match {
      case x if x == "" =>
        val accountId = getAccountId()
        accountChanged(accountId)
        accountId.getOrElse("")
      case x =>
        accountUpdated(js.Array[String](x))
        x
    }
  }

  dataSourceService.dataSources() onComplete {
    case Success(resp) =>
      dataSourcesLoaded = true
      $scope.ds = resp.toJSArray
    case Failure(ex) => handleError(ex)
  }

  $scope.configureAdvertisers = (dsInfo: ProviderInfo) => {

    val passAccountId: js.Function = () => accountParam
    val passDataSourceInfo: js.Function = () => dsInfo

    val modalInstance = $modal.open(js.Dictionary(
      "templateUrl" -> s"/reporting/assets/partials/reports/ds/${dsInfo.id}.html",
      "controller" -> "DataSourceConfigureModalCtrl",
      "windowClass" -> "xl-dialog",
      "resolve" -> js.Dictionary[js.Object](
        "accountId" -> passAccountId,
        "dataSourceInfo" -> passDataSourceInfo
      )
    ))
    modalInstance.result.then(() => {
      refresh()
    })
  }

	def accountChanged(accountId: Option[String]): Unit = {
    if ($state.includes("dataSources").asInstanceOf[Boolean] || $state.includes("dataSourceAccount").asInstanceOf[Boolean]) {
      accountId match {
        case Some(x) => $state.go("dataSourceAccount", js.Dictionary("account" -> x))
      }
    }
  }

	def accountUpdated(accountIds: js.Array[String]): Unit = {
		$cookieStore.put("accounts",accountIds.map((x: String) => x))
		dom.document.dispatchEvent(CoreEvent.changeChosenAccounts)
	}
}

trait AdvertiserInfoJS extends js.Object {
  var label$1: String = js.native
  var id$1: Int = js.native
}

object AdvertiserInfoJS {
  def apply(): AdvertiserInfoJS =
    js.Dynamic.literal(label = "", id = 0).asInstanceOf[AdvertiserInfoJS]
}


trait FormAdvertiser extends js.Object {
  var advertiser: AdvertiserInfoJS = js.native
}

object FormAdvertiser {
  def apply(): FormAdvertiser =
    js.Dynamic.literal(advertiser = null).asInstanceOf[FormAdvertiser]
}


trait DataSourceConfigureScope extends ModalScope {
  var accountId: String = js.native
  var dataSourceInfo: ProviderInfo = js.native

  var form: FormAdvertiser = js.native
  var advertisers: js.Array[DartAccountCfg] = js.native

  var allAdvertisers: js.Array[AdvertiserInfo] = js.native
  var deleteAdvertiser: js.Function = js.native
  var addAdvertiser: js.Function = js.native
}

class DataSourceConfigureModalCtrl(
  dataSourceService: DataSourceService,
  $scope:  DataSourceConfigureScope,
  $modalInstance: js.Dynamic,
  accountId: String,
  dataSourceInfo: ProviderInfo
)
extends CoreCtrl {

  $scope.accountId = accountId
  $scope.dataSourceInfo = dataSourceInfo

  $scope.form = FormAdvertiser()

  $scope.focusInput = true

  def refresh(): Unit = {
    dataSourceService.advertisers($scope.dataSourceInfo.id, $scope.accountId) onComplete {
      case Success(resp) =>
        $scope.advertisers = resp.sortWith(_.label < _.label).toJSArray
        case Failure(ex) => handleError(ex)
    }
    dataSourceService.allAdvertisers($scope.dataSourceInfo.id) onComplete {
      case Success(resp) =>
        $scope.allAdvertisers = resp.toJSArray
      case Failure(ex) => handleError(ex)
    }
  }

  refresh()

  $scope.deleteAdvertiser = (dsId: Option[UUID]) => {
    //accountService.removeUsers($scope.account.id, UserIds(userId)) onComplete {
      //case Success(_) => refresh()
      //case Failure(ex) => handleError(ex)
    //}
  }

  $scope.addAdvertiser = () => {
    dataSourceService.addAdvertiser($scope.dataSourceInfo.id, $scope.accountId,
      new DartAccountCfg($scope.form.advertiser.label$1, $scope.form.advertiser.id$1, None)) onComplete {
      case Success(_) => refresh()
      case Failure(ex) => handleError(ex)
    }
    $scope.form.advertiser = null
  }

  $scope.cancel = () => {
    $scope.focusInput = false
    $modalInstance.close()
  }
}
