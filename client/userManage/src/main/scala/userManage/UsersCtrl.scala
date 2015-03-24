package userManage

import biz.enef.angulate.Scope

import scala.scalajs.js
import scala.util.{Failure, Success}

import client.core.{CoreCtrl, SearchCtrl, SearchScope, ModalScope}

trait UserScope  extends SearchScope[UserFull] {
	var watchUser: UserWatch = js.native
  var invitedUsers: js.Array[UserInvite] = js.native
  var numInvitedUsers: Int = js.native
  var invite: js.Function = js.native
  var inviteUser: js.Function = js.native
  var deleteUser: js.Function = js.native
  var deleteInvite: js.Function = js.native
  var permissionsUser: js.Function = js.native
}

class UsersCtrl(userService: UserService, $scope: UserScope, $modal: js.Dynamic, $filter: js.Dynamic)
extends SearchCtrl[UserFull]($scope: SearchScope[UserFull], $filter: js.Dynamic) {
  var usersLoaded = false
	$scope.watchUser = UserWatch()
	
  def refresh(): Unit = {
    userService.all() onComplete {
      case Success(resp) =>
        usersLoaded = true
        $scope.numListItems = resp.users.length
        $scope.ogList = resp.users.sortWith(_.main.fullName < _.main.fullName)
        $scope.searchList = $filter("filter")($scope.ogList, $scope.watch.searchTerm).asInstanceOf[js.Array[UserFull]]
        $scope.visibleListItems = $scope.searchList.length
        $scope.filterList = $filter("filter")($scope.searchList, $scope.paginate).asInstanceOf[js.Array[UserFull]]
      case Failure(ex) => handleError(ex)
    }
    userService.allInvited() onComplete {
      case Success(resp) =>
        $scope.numInvitedUsers = resp.invited.length
        $scope.invitedUsers = resp.invited
      case Failure(ex) => handleError(ex)
    }
  }

  refresh()

  $scope.invite = () => {
    userService.invite(Email($scope.watchUser.email)) onComplete {
      case Success(resp) =>
        $scope.watchUser.email = ""
        refresh()
      case Failure(ex) => handleError(ex)
    }
  }

  $scope.inviteUser = () => {
    val modalInstance = $modal.open(js.Dictionary(
      "templateUrl" -> "/assets/partials/users/usersInvite.html",
      "controller" -> "UserInviteModalCtrl"
    ))
    modalInstance.result.then(() => {
      refresh()
    })
  }

  $scope.deleteUser = (user: User) => {
    val passUser: js.Function = () => user

    val modalInstance = $modal.open(js.Dictionary(
      "templateUrl" -> "/assets/partials/users/usersDelete.html",
      "controller" -> "UserDeleteModalCtrl",
      "resolve" -> js.Dictionary[js.Object]("user" -> passUser)
    ))
    modalInstance.result.then(() => {
      refresh()
    })
  }

  $scope.deleteInvite = (user: UserInvite) => {
    val passUser: js.Function = () => user

    val modalInstance = $modal.open(js.Dictionary(
      "templateUrl" -> "/assets/partials/users/usersInviteDelete.html",
      "controller" -> "UserInviteDeleteModalCtrl",
      "resolve" -> js.Dictionary[js.Object]("invite" -> passUser)
    ))
    modalInstance.result.then(() => {
      refresh()
    })
  }

  $scope.permissionsUser = (user: User) => {
    val passUser: js.Function = () => user

    val modalInstance = $modal.open(js.Dictionary(
      "templateUrl" -> "/assets/partials/users/usersPermission.html",
      "controller" -> "UserPermissionModalCtrl",
      "resolve" -> js.Dictionary[js.Object]("user" -> passUser)
    ))
    modalInstance.result.then(() => {
      refresh()
    })
  }
}

trait UserInviteScope extends ModalScope {
  var watch: UserWatch = js.native
}

class UserInviteModalCtrl(userService: UserService, $scope: UserInviteScope, $modalInstance: js.Dynamic) extends CoreCtrl {
  $scope.watch = UserWatch()
  $scope.focusInput = true

  $scope.ok = () => {
    userService.invite(Email($scope.watch.email)) onComplete {
      case Success(_) =>
        $scope.focusInput = false
        $modalInstance.close()
      case Failure(ex) => handleError(ex)
    }
  }

  $scope.cancel = () => {
    $scope.focusInput = false
    $modalInstance.dismiss("cancel")
  }
}

trait UserInviteDeleteScope extends ModalScope {
  var email: String = js.native
}

class UserInviteDeleteModalCtrl(userService: UserService, $scope: UserInviteDeleteScope, $modalInstance: js.Dynamic, invite: UserInvite) extends CoreCtrl {
  $scope.email = invite.email
  $scope.focusInput = true

  $scope.ok = () => {
    userService.removeInvite(invite.id) onComplete {
      case Success(_) =>
        $scope.focusInput = false
        $modalInstance.close()
      case Failure(ex) => handleError(ex)
    }
  }

  $scope.cancel = () => {
    $scope.focusInput = false
    $modalInstance.dismiss("cancel")
  }
}

trait UserDeleteScope extends ModalScope {
  var email: String = js.native
  var fullName: String = js.native
}

class UserDeleteModalCtrl(userService: UserService, $scope: UserDeleteScope, $modalInstance: js.Dynamic, user: UserFull) extends CoreCtrl {
  $scope.email = user.main.email
  $scope.fullName = user.main.fullName
  $scope.focusInput = true

  $scope.ok = () => {
    userService.remove(user.id) onComplete {
      case Success(_) =>
        $scope.focusInput = false
        $modalInstance.close()
      case Failure(ex) => handleError(ex)
    }
  }

  $scope.cancel = () => {
    $scope.focusInput = false
    $modalInstance.dismiss("cancel")
  }
}

trait UserPermissionScope extends ModalScope {
}

class UserPermissionModalCtrl(userService: UserService, $scope: UserPermissionScope, $modalInstance: js.Dynamic, user: UserFull) extends CoreCtrl {
  $scope.cancel = () => {
    $modalInstance.dismiss("cancel")
  }
}
