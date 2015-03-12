package userManage

import biz.enef.angulate.Scope

import scala.scalajs.js
import scala.util.{Failure, Success}

trait AccountScope extends Scope {
  var filterAccounts: js.Array[Account] = js.native
  var watch: AccountWatch = js.native
  var numAccounts: Int = js.native
  var numPerPage: Int = js.native
  var visibleAccounts: Int = js.native
  var paginate: js.Function = js.native
  var addAccount: js.Function = js.native
  var renameAccount: js.Function = js.native
  var deleteAccount: js.Function = js.native
  var permissionsAccount: js.Function = js.native
  var usersAccount: js.Function = js.native
}

class AccountsCtrl(accountService: AccountService, $scope: AccountScope, $modal: js.Dynamic, $filter: js.Dynamic) extends CoreCtrl {
  var accounts = js.Array[Account]()
  var searchAccounts = js.Array[Account]()
  $scope.filterAccounts = js.Array[Account]()

  var accountsLoaded = false

  $scope.watch = AccountWatch()
  $scope.numAccounts = 0
  $scope.numPerPage = 10
  $scope.visibleAccounts = 10

  def refresh(): Unit = {
    accountService.all() onComplete {
      case Success(resp) =>
        accountsLoaded = true
        $scope.numAccounts = resp.accounts.length
        accounts = resp.accounts.sortWith(_.label < _.label)
        searchAccounts = $filter("filter")(accounts, $scope.watch.searchAccount).asInstanceOf[js.Array[Account]]
        $scope.visibleAccounts = searchAccounts.length
        $scope.filterAccounts = $filter("filter")(searchAccounts, $scope.paginate).asInstanceOf[js.Array[Account]]
      case Failure(ex) => handleError(ex)
    }
  }

  refresh()

  $scope.paginate = (account: Account) => {
    val begin = ($scope.watch.currentPage - 1) * $scope.numPerPage
    val end = begin + $scope.numPerPage
    val index = searchAccounts.indexOf(account)
    begin <= index && index < end
  }

  $scope.addAccount = () => {
    val modalInstance = $modal.open(js.Dictionary(
      "templateUrl" -> "/assets/partials/accounts/accountsAdd.html",
      "controller" -> "AccountAddModalCtrl"
    ))
    modalInstance.result.then(() => {
      refresh()
    })
  }

  $scope.renameAccount = (account: Account) => {
    val passAccount: js.Function = () => account

    val modalInstance = $modal.open(js.Dictionary(
      "templateUrl" -> "/assets/partials/accounts/accountsRename.html",
      "controller" -> "AccountRenameModalCtrl",
      "resolve" -> js.Dictionary[js.Object]("account" -> passAccount)
    ))
    modalInstance.result.then(() => {
      refresh()
    })
  }

  $scope.deleteAccount = (account: Account) => {
    val passAccount: js.Function = () => account

    val modalInstance = $modal.open(js.Dictionary(
      "templateUrl" -> "/assets/partials/accounts/accountsDelete.html",
      "controller" -> "AccountDeleteModalCtrl",
      "resolve" -> js.Dictionary[js.Object]("account" -> passAccount)
    ))
    modalInstance.result.then(() => {
      refresh()
    })
  }

  $scope.permissionsAccount = (account: Account) => {
    val passAccount: js.Function = () => account

    val modalInstance = $modal.open(js.Dictionary(
      "templateUrl" -> "/assets/partials/accounts/accountsPermission.html",
      "controller" -> "AccountPermissionModalCtrl",
      "resolve" -> js.Dictionary[js.Object]("account" -> passAccount)
    ))
    modalInstance.result.then(() => {
      refresh()
    })
  }

  $scope.usersAccount = (account: Account) => {
    val passAccount: js.Function = () => account

    val modalInstance = $modal.open(js.Dictionary(
      "templateUrl" -> "/assets/partials/accounts/accountsUsers.html",
      "controller" -> "AccountUsersModalCtrl",
      "windowClass" -> "xl-dialog",
      "resolve" -> js.Dictionary[js.Object]("account" -> passAccount)
    ))
    modalInstance.result.then(() => {
      refresh()
    })
  }

  $scope.$watch(() => $scope.watch.searchAccount, (query: String) => {
    searchAccounts = $filter("filter")(accounts, $scope.watch.searchAccount).asInstanceOf[js.Array[Account]]
    if (!searchAccounts.isEmpty) {
      $scope.visibleAccounts = searchAccounts.length
      $scope.filterAccounts = $filter("filter")(searchAccounts, $scope.paginate).asInstanceOf[js.Array[Account]]
    }
  })

  $scope.$watch(() => $scope.watch.currentPage, () => {
    $scope.filterAccounts = $filter("filter")(searchAccounts, $scope.paginate).asInstanceOf[js.Array[Account]]
  })
}

trait AccountAddScope extends ModalScope {
  var formData: AccountAdd = js.native
}

class AccountAddModalCtrl(accountService: AccountService, $scope: AccountAddScope, $modalInstance: js.Dynamic) extends CoreCtrl {
  $scope.formData = AccountAdd()
  $scope.focusInput = true

  $scope.ok = () => {
    accountService.create($scope.formData) onComplete {
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

trait AccountRenameScope extends ModalScope {
  var formData: AccountRename = js.native
}

class AccountRenameModalCtrl(accountService: AccountService, $scope: AccountRenameScope, $modalInstance: js.Dynamic, account: Account) extends CoreCtrl {
  $scope.formData = AccountRename(account.label)
  $scope.focusInput = true

  $scope.ok = () => {
    accountService.rename(account.id, $scope.formData) onComplete {
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

trait AccountDeleteScope extends ModalScope {
  var label: String = js.native
}

class AccountDeleteModalCtrl(accountService: AccountService, $scope: AccountDeleteScope, $modalInstance: js.Dynamic, account: Account) extends CoreCtrl {
  $scope.label = account.label
  $scope.focusInput = true

  $scope.ok = () => {
    accountService.remove(account.id) onComplete {
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

trait AccountPermissionScope extends Scope {
  var account: Account = js.native
  var permissions: js.Array[Permission] = js.native
  var togglePermission: js.Function = js.native
  var cancel: js.Function = js.native
}

class AccountPermissionModalCtrl(accountService: AccountService, permissionService: PermissionService, $scope: AccountPermissionScope,
                                 $modalInstance: js.Dynamic, account: Account) extends CoreCtrl {
  $scope.account = account

  def refresh(): Unit = {
    permissionService.all() onComplete {
      case Success(resp) =>
        $scope.permissions = resp.permissions.sortWith(_.label < _.label)
      case Failure(ex) => handleError(ex)
    }
  }

  refresh()

  $scope.togglePermission = (permissionId: String) => {
    if ($scope.account.permissions.indexOf(permissionId) == -1) {
      accountService.addPermission(account.id, PermissionIds(permissionId)) onComplete {
        case Success(_) =>
        case Failure(ex) => handleError(ex)
      }
    }
    else {
      accountService.removePermission(account.id, PermissionIds(permissionId)) onComplete {
        case Success(_) =>
        case Failure(ex) => handleError(ex)
      }
    }
  }

  $scope.cancel = () => {
    $modalInstance.close()
  }
}

trait AccountUsersScope extends ModalScope {
  var account: Account = js.native
  var form: FormUser = js.native
  var users: js.Array[User] = js.native
  var permissions: js.Array[Permission] = js.native
  var allUsers: js.Array[UserFull] = js.native
  var filterPermissions: js.Function = js.native
  var togglePermissionToUser: js.Function = js.native
  var deleteUserFromAccount: js.Function = js.native
  var addUser: js.Function = js.native
}

class AccountUsersModalCtrl(accountService: AccountService, permissionService: PermissionService, userService: UserService,
                            $scope: AccountUsersScope, $modalInstance: js.Dynamic, account: Account) extends CoreCtrl {
  $scope.account = account
  $scope.form = FormUser()

  $scope.focusInput = true

  def refresh(): Unit = {
    accountService.users($scope.account.id) onComplete {
      case Success(resp) =>
        $scope.users = resp.users.sortWith(_.fullName < _.fullName)
      case Failure(ex) => handleError(ex)
    }
    permissionService.all() onComplete {
      case Success(resp) =>
        $scope.permissions = resp.permissions
      case Failure(ex) => handleError(ex)
    }
    userService.all() onComplete {
      case Success(resp) =>
        $scope.allUsers = resp.users
      case Failure(ex) => handleError(ex)
    }
  }

  refresh()

  $scope.filterPermissions = (permission: Permission) => {
    $scope.account.permissions.indexOf(permission.id) > -1
  }

  $scope.togglePermissionToUser = (userId: String, permissionId: String, pressed: Boolean) => {
    if (pressed) {
      userService.removePermission(userId, AccountPermissionIds($scope.account.id, permissionId)) onComplete {
        case Success(_) => refresh()
        case Failure(ex) => handleError(ex)
      }
    }
    else {
      userService.addPermission(userId, AccountPermissionIds($scope.account.id, permissionId)) onComplete {
        case Success(_) => refresh()
        case Failure(ex) => handleError(ex)
      }
    }
  }

  $scope.deleteUserFromAccount = (userId: String) => {
    accountService.removeUsers($scope.account.id, UserIds(userId)) onComplete {
      case Success(_) => refresh()
      case Failure(ex) => handleError(ex)
    }
  }

  $scope.addUser = () => {
    accountService.addUsers($scope.account.id, UserIds($scope.form.user.id)) onComplete {
      case Success(_) => refresh()
      case Failure(ex) => handleError(ex)
    }
    $scope.form.user = null
  }

  $scope.cancel = () => {
    $scope.focusInput = false
    $modalInstance.close()
  }
}