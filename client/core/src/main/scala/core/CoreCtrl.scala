package client.core

import biz.enef.angulate.{Controller, Scope}

import scala.scalajs.js

class CoreCtrl extends Controller {
  protected def handleError(ex: Throwable): Unit = js.Dynamic.global.console.error(s"An error has occurred: $ex")
}

trait ModalScope extends Scope {
  var ok: js.Function = js.native
  var cancel: js.Function = js.native
  var focusInput: Boolean = js.native
}

class SearchCtrl[T]($scope: SearchScope[T], $filter: js.Dynamic) extends CoreCtrl {
	$scope.ogList = js.Array[T]()
  $scope.searchList = js.Array[T]()
  $scope.filterList = js.Array[T]()

  $scope.watch = SearchWatch()
  $scope.numListItems = 0
  $scope.numPerPage = 10
  $scope.visibleListItems = 10

  $scope.paginate = (item: T) => {
    val begin = ($scope.watch.currentPage - 1) * $scope.numPerPage
    val end = begin + $scope.numPerPage
    val index =  $scope.searchList.indexOf(item)
    begin <= index && index < end
  }

  $scope.$watch(() => $scope.watch.searchTerm, (query: String) => {
    $scope.searchList = $filter("filter")($scope.ogList, $scope.watch.searchTerm).asInstanceOf[js.Array[T]]
    if (!$scope.searchList.isEmpty) {
      $scope.visibleListItems = $scope.searchList.length
      $scope.filterList = $filter("filter")($scope.searchList, $scope.paginate).asInstanceOf[js.Array[T]]
    }
  })

  $scope.$watch(() => $scope.watch.currentPage, () => {
    $scope.filterList = $filter("filter")($scope.searchList, $scope.paginate).asInstanceOf[js.Array[T]]
  })
}

trait SearchScope[T] extends Scope {
	var ogList: js.Array[T] = js.native
  var searchList: js.Array[T] = js.native
  var filterList: js.Array[T] = js.native
  var watch: SearchWatch = js.native
  var numListItems: Int = js.native
  var numPerPage: Int = js.native
  var visibleListItems: Int = js.native
  var paginate: js.Function = js.native
}

trait SearchWatch extends js.Object {
  var searchTerm: String = js.native
  var currentPage: Int = js.native
}

object SearchWatch {
  def apply(): SearchWatch =
    js.Dynamic.literal(searchTerm = "", currentPage = 1).asInstanceOf[SearchWatch]

  def apply(searchAccount: String, currentPage: Int): SearchWatch =
    js.Dynamic.literal(searchTerm = searchAccount, currentPage = currentPage).asInstanceOf[SearchWatch]
}