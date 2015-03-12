package userManage

import biz.enef.angulate.Scope

import scala.scalajs.js
import scala.util.{Failure, Success}


  trait PermissionScope extends Scope {
    var filterPermissions: js.Array[Permission] = js.native
    var watch: PermissionWatch = js.native
    var numPermissions: Int = js.native
    var numPerPage: Int = js.native
    var visiblePermissions: Int = js.native
    var paginate: js.Function = js.native
  }

  class PermissionsCtrl(permissionService: PermissionService, $scope: PermissionScope, $modal: js.Dynamic, $filter: js.Dynamic) extends CoreCtrl {
    var permissions = js.Array[Permission]()
    $scope.filterPermissions = js.Array[Permission]()

    var permissionsLoaded = false

    $scope.watch = PermissionWatch()
    $scope.numPermissions = 0
    $scope.numPerPage = 10
    $scope.visiblePermissions = 10

    def refresh(): Unit = {
      permissionService.allCore() onComplete {
        case Success(resp) =>
          permissionsLoaded = true
          $scope.numPermissions = resp.permissions.length
          permissions = resp.permissions.sortWith(_.label < _.label)
          $scope.visiblePermissions = permissions.length
          $scope.filterPermissions = $filter("filter")(permissions, $scope.paginate).asInstanceOf[js.Array[Permission]]
        case Failure(ex) => handleError(ex)
      }
    }

    refresh()

    $scope.paginate = (permission: Permission) => {
      val begin = ($scope.watch.currentPage - 1) * $scope.numPerPage
      val end = begin + $scope.numPerPage
      val index = permissions.indexOf(permission)
      begin <= index && index < end
    }

    $scope.$watch(() => $scope.watch.currentPage, () => {
      $scope.filterPermissions = $filter("filter")(permissions, $scope.paginate).asInstanceOf[js.Array[Permission]]
    })
  }

