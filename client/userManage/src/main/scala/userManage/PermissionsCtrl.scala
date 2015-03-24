package userManage

import biz.enef.angulate.Scope

import scala.scalajs.js
import scala.util.{Failure, Success}

import client.core.{SearchCtrl, SearchScope}

trait PermissionScope extends SearchScope[Permission]

class PermissionsCtrl(permissionService: PermissionService, $scope: PermissionScope, $modal: js.Dynamic, $filter: js.Dynamic)
extends SearchCtrl[Permission]($scope: SearchScope[Permission], $filter: js.Dynamic) {
  var permissionsLoaded = false

  def refresh(): Unit = {
    permissionService.allCore() onComplete {
      case Success(resp) =>
        permissionsLoaded = true
        $scope.numListItems = resp.permissions.length
        $scope.ogList = resp.permissions.sortWith(_.label < _.label)
				$scope.searchList = $scope.ogList
        $scope.visibleListItems = $scope.searchList.length
        $scope.filterList = $filter("filter")($scope.searchList, $scope.paginate).asInstanceOf[js.Array[Permission]]
      case Failure(ex) => handleError(ex)
    }
  }

  refresh()
}

