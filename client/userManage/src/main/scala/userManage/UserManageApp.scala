package userManage

import biz.enef.angulate._

import scala.scalajs.js
import scala.scalajs.js.JSApp

object UserManageApp extends JSApp {
  override def main(): Unit = {
    val module = angular.createModule("myUserManagement", Seq("ui.bootstrap", "ui.router"))

    module.controllerOf[AccountsCtrl]("AccountsCtrl")
    module.controllerOf[AccountAddModalCtrl]("AccountAddModalCtrl")
    module.controllerOf[AccountRenameModalCtrl]("AccountRenameModalCtrl")
    module.controllerOf[AccountDeleteModalCtrl]("AccountDeleteModalCtrl")
    module.controllerOf[AccountPermissionModalCtrl]("AccountPermissionModalCtrl")
    module.controllerOf[AccountUsersModalCtrl]("AccountUsersModalCtrl")

    module.controllerOf[UsersCtrl]("UsersCtrl")
    module.controllerOf[UserInviteModalCtrl]("UserInviteModalCtrl")
    module.controllerOf[UserInviteDeleteModalCtrl]("UserInviteDeleteModalCtrl")
    module.controllerOf[UserDeleteModalCtrl]("UserDeleteModalCtrl")
    module.controllerOf[UserPermissionModalCtrl]("UserPermissionModalCtrl")

    module.controllerOf[PermissionsCtrl]("PermissionsCtrl")

    module.controllerOf[SubNavCtrl]("SubNavCtrl")

    module.directiveOf[NgEnterDirective]
    module.directiveOf[FocusMeDirective]

    module.serviceOf[UserService]
    module.serviceOf[AccountService]
    module.serviceOf[PermissionService]

    val stateConfig: AnnotatedFunction = ($stateProvider: js.Dynamic, $urlRouterProvider: js.Dynamic) => {
      $urlRouterProvider.otherwise("/accounts")

      $stateProvider
        .state("accounts", js.Dictionary(
        "url" -> "/accounts",
        "templateUrl" -> "/assets/partials/accounts/accounts.html",
        "controller" -> "AccountsCtrl"
      ))
        .state("users", js.Dictionary(
        "url" -> "/users",
        "templateUrl" -> "/assets/partials/users/users.html",
        "controller" -> "UsersCtrl"
      ))
        .state("permissions", js.Dictionary(
        "url" -> "/permissions",
        "templateUrl" -> "/assets/partials/permissions/permissions.html",
        "controller" -> "PermissionsCtrl"
      ))
    }

    module.config(stateConfig)
  }
}
