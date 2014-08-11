/*global define */

'use strict';

/* Controllers */
angular
    .module("myUserManagement.controllers", [])
    .controller("NavCtrl", function ($scope,  $location) {
        $scope.isActive = function (viewLocation) {
            return viewLocation === $location.path();
        };
    })
    .controller("AccountsCtrl", function ($scope, $state, $modal, $filter, Accounts) {
        $scope.form = {};
        $scope.numAccounts = 0;
        $scope.visibleAccounts = 0;
        $scope.currentPage = 1;
        $scope.numPerPage = 10;

        $scope.paginate = function(value) {
            var begin = ($scope.currentPage - 1) * $scope.numPerPage;
            var end = begin + $scope.numPerPage;
            var index = $scope.searchAccounts.indexOf(value);
            return (begin <= index && index < end);
        };

        $scope.$watch("form.searchAccount", function(query){
            $scope.searchAccounts = $filter('filter')($scope.accounts, query);
            if($scope.searchAccounts){
                $scope.visibleAccounts = $scope.searchAccounts.length;
                $scope.filterAccounts = $filter('filter')($scope.searchAccounts, $scope.paginate);
            }
        });

        $scope.$watch("currentPage", function(){
            $scope.filterAccounts = $filter('filter')($scope.searchAccounts, $scope.paginate);
        });

        $scope.refresh = function () {
            Accounts.all(function (data) {
                $scope.accountsLoaded = true;
                $scope.numAccounts = data.accounts.length;
                $scope.accounts = $filter('orderBy')(data.accounts, "label");
                $scope.searchAccounts = $filter('filter')($scope.accounts, $scope.form.searchAccount);
                $scope.visibleAccounts = $scope.searchAccounts.length;
                $scope.filterAccounts = $filter('filter')($scope.searchAccounts, $scope.paginate);
            });
        };
        $scope.refresh();

        $scope.addAccount = function () {

            var modalInstance = $modal.open({
                templateUrl: '/assets/partials/accounts/accountsAdd.html',
                controller: "AccountAddModalCtrl"
            });

            modalInstance.result.then(function () {
                $scope.refresh();
            });
        };

        $scope.renameAccount = function (account) {
            var modalInstance = $modal.open({
                templateUrl: '/assets/partials/accounts/accountsRename.html',
                controller: "AccountRenameModalCtrl",
                resolve: {
                    account: function () {
                        return account;
                    }
                }
            });

            modalInstance.result.then(function () {
                $scope.refresh();
            });
        }

        $scope.deleteAccount = function (account) {
            var modalInstance = $modal.open({
                templateUrl: '/assets/partials/accounts/accountsDelete.html',
                controller: "AccountDeleteModalCtrl",
                resolve: {
                    account: function () {
                        return account;
                    }
                }
            });

            modalInstance.result.then(function () {
                $scope.refresh();
            });
        }

        $scope.permissionsAccount = function (account) {

            var modalInstance = $modal.open({
                templateUrl: '/assets/partials/accounts/accountsPermission.html',
                controller: "AccountPermissionModalCtrl",
                resolve: {
                    account: function () {
                        return account;
                    }
                }
            });

            modalInstance.result.then(function () {
                $scope.refresh();
            });
        };

        $scope.usersAccount = function (account) {

            var modalInstance = $modal.open({
                templateUrl: '/assets/partials/accounts/accountsUsers.html',
                controller: "AccountUsersModalCtrl",
                windowClass: 'xl-dialog',
                resolve: {
                    account: function () {
                        return account;
                    }
                }
            });

            modalInstance.result.then(function () {
                $scope.refresh();
            });
        };
    })
    .controller("AccountAddModalCtrl", function ($scope, $modalInstance, Accounts) {
        $scope.focusInput = true;
        $scope.formData = {};
        $scope.ok = function () {
            Accounts.create({label: $scope.formData.accountLabel, permissions: []}, function() {
                $scope.focusInput = false;
                $modalInstance.close();
            });
        };

        $scope.cancel = function () {
            $scope.focusInput = false;
            $modalInstance.dismiss('cancel');
        };
    })
    .controller("AccountRenameModalCtrl", function ($scope, $modalInstance, Accounts, account) {
        $scope.focusInput = true;
        $scope.formData = {};
        $scope.formData.id = account.id;
        $scope.formData.accountLabel = account.label;
        $scope.ok = function () {
            Accounts.rename($scope.formData.id, {label: $scope.formData.accountLabel}, function () {
                $scope.focusInput = false;
                $modalInstance.close();
            });
        };

        $scope.cancel = function () {
            $scope.focusInput = false;
            $modalInstance.dismiss('cancel');
        };
    })
    .controller("AccountDeleteModalCtrl", function ($scope, $modalInstance, Accounts, account) {
        $scope.focusInput = true;
        $scope.label = account.label;
        $scope.id = account.id;
        $scope.ok = function () {
            Accounts.remove($scope.id, function () {
                $scope.focusInput = false;
                $modalInstance.close();
            });
        };

        $scope.cancel = function () {
            $scope.focusInput = false;
            $modalInstance.dismiss('cancel');
        };
    })
    .controller("AccountPermissionModalCtrl", function ($scope, $filter, $modalInstance, Accounts, Permissions, account) {
        $scope.account = account;

        $scope.refresh = function () {
            Permissions.all(function (data) {
                $scope.permissions = $filter('orderBy')(data.permissions, "label");
            });
        };
        $scope.refresh();

        $scope.togglePermission = function(permissionId) {
            var idx = $scope.account.permissions.indexOf(permissionId);
            if (idx == -1) {
                Accounts.addPermission($scope.account.id, {permissionIds: [permissionId]}, function () {});
            } else {
                Accounts.removePermission($scope.account.id, {permissionIds: [permissionId]}, function () {});
            }
        };

        $scope.cancel = function () {
            $modalInstance.close();
        };
    })
    .controller("AccountUsersModalCtrl", function ($scope, $filter, $modalInstance, Accounts,Permissions, Users, account) {
        $scope.form = {};
        $scope.focusInput = true;
        $scope.account = account;

        $scope.refresh = function () {
            Accounts.users($scope.account.id, function (data) {
                $scope.users = $filter('orderBy')(data.users, "fullName");
            });
            Permissions.all(function (data) {
                $scope.permissions = data.permissions;
            });
            Users.all(function (data) {
                $scope.allUsers = data.users;
            })
        };
        $scope.refresh();

        $scope.filterPermissions = function(item) {
            return $scope.account.permissions.indexOf(item.id) > -1;
        };

        $scope.togglePermissionToUser = function(userId, permissionId, pressed) {
            if(pressed) {
                Users.removePermission(userId,{accountId: $scope.account.id, permissionIds: [permissionId]}, function() {
                    $scope.refresh();
                })
            }
            else {
                Users.addPermission(userId,{accountId: $scope.account.id, permissionIds: [permissionId]}, function() {
                    $scope.refresh();
                })
            }
        };

        $scope.deleteUserFromAccount = function(userId) {
            Accounts.removeUsers($scope.account.id, {userIds: [userId]}, function() {
                $scope.refresh();
            })
        };

        $scope.addUser = function () {
            Accounts.addUsers($scope.account.id, {userIds: [$scope.form.user.id]}, function() {
                $scope.refresh();
            })
            $scope.form.user = "";
        }

        $scope.cancel = function () {
            $scope.focusInput = false;
            $modalInstance.close();
        };
    })
    .controller("UsersCtrl", function ($scope, $state,  $modal, $filter, Users) {
        $scope.form = {};
        $scope.numUsers = 0;
        $scope.numInvitedUsers = 0;
        $scope.visibleUsers = 0;
        $scope.currentPage = 1;
        $scope.numPerPage = 10;

        $scope.paginate = function(value) {
            var begin = ($scope.currentPage - 1) * $scope.numPerPage;
            var end = begin + $scope.numPerPage;
            var index = $scope.searchUsers.indexOf(value);
            return (begin <= index && index < end);
        };

        $scope.$watch("form.searchUser", function(query){
            $scope.searchUsers = $filter('filter')($scope.users, query);
            if($scope.searchUsers){
                $scope.visibleUsers = $scope.searchUsers.length;
                $scope.filterUsers = $filter('filter')($scope.searchUsers, $scope.paginate);
            }
        });

        $scope.$watch("currentPage", function(){
            $scope.filterUsers = $filter('filter')($scope.searchUsers, $scope.paginate);
        });

        $scope.refresh = function () {
            Users.all(function (data) {
                $scope.usersLoaded = true;
                $scope.numUsers = data.users.length;
                $scope.users = $filter('orderBy')(data.users, "main.fullName");
                $scope.searchUsers = $filter('filter')($scope.users, $scope.form.searchUser);
                $scope.visibleUsers = $scope.searchUsers.length;
                $scope.filterUsers = $filter('filter')($scope.searchUsers, $scope.paginate);
            });
            Users.allInvited(function (data) {
                $scope.numInvitedUsers = data.invited.length;
                $scope.invitedUsers = data.invited;
            })
        };
        $scope.refresh();

        $scope.invite = function() {
            Users.invite({email: $scope.form.email}, function() {
                $scope.form.email = "";
                $scope.refresh();
            });
        }

        $scope.inviteUser = function () {

            var modalInstance = $modal.open({
                templateUrl: '/assets/partials/users/usersInvite.html',
                controller: "UserInviteModalCtrl"
            });

            modalInstance.result.then(function () {
                $scope.refresh();
            });
        };

        $scope.deleteUser = function (user) {
            var modalInstance = $modal.open({
                templateUrl: '/assets/partials/users/usersDelete.html',
                controller: "UserDeleteModalCtrl",
                resolve: {
                    user: function () {
                        return user;
                    }
                }
            });

            modalInstance.result.then(function () {
                $scope.refresh();
            });
        }

        $scope.deleteInvite = function (invite) {
            var modalInstance = $modal.open({
                templateUrl: '/assets/partials/users/usersInviteDelete.html',
                controller: "UserInviteDeleteModalCtrl",
                resolve: {
                    invite: function () {
                        return invite;
                    }
                }
            });
            modalInstance.result.then(function () {
                $scope.refresh();
            });
        }

        $scope.permissionsUser = function (user) {

            var modalInstance = $modal.open({
                templateUrl: '/assets/partials/users/usersPermission.html',
                controller: "UserPermissionModalCtrl",
                resolve: {
                    user: function () {
                        return user;
                    }
                }
            });

            modalInstance.result.then(function () {
                $scope.refresh();
            });
        };
    })
    .controller("UserInviteModalCtrl", function ($scope, $modalInstance, Users) {
        $scope.focusInput = true;
        $scope.formData = {};
        $scope.ok = function () {
            Users.invite({email: $scope.formData.email}, function() {
                $scope.focusInput = false;
                $modalInstance.close();
            });
        };

        $scope.cancel = function () {
            $scope.focusInput = false;
            $modalInstance.dismiss('cancel');
        };
    })
    .controller("UserInviteDeleteModalCtrl", function ($scope, $modalInstance, Users, invite) {
        $scope.focusInput = true;
        $scope.email = invite.email;
        $scope.id = invite.id;
        $scope.ok = function () {
            Users.removeInvite($scope.id, function () {
                $scope.focusInput = false;
                $modalInstance.close();
            });
        };

        $scope.cancel = function () {
            $scope.focusInput = false;
            $modalInstance.dismiss('cancel');
        };
    })
    .controller("UserDeleteModalCtrl", function ($scope, $modalInstance, Users, user) {
        $scope.focusInput = true;
        $scope.fullName = user.main.fullName;
        $scope.email = user.main.email;
        $scope.id = user.id;
        $scope.ok = function () {
            Users.remove($scope.id, function () {
                $scope.focusInput = false;
                $modalInstance.close();
            });
        };

        $scope.cancel = function () {
            $scope.focusInput = false;
            $modalInstance.dismiss('cancel');
        };
    })
    .controller("UserPermissionModalCtrl", function ($scope, $modalInstance, Users, user) {
        $scope.user = user;

        $scope.cancel = function () {
            $modalInstance.dismiss('cancel');
        };
    })
    .controller("PermissionsCtrl", function ($scope, $state, $filter, Permissions) {
        $scope.numPermissions = 0;
        $scope.numInvitedPermissions = 0;
        $scope.visiblePermissions = 0;
        $scope.currentPage = 1;
        $scope.numPerPage = 10;

        $scope.paginate = function(value) {
            var begin = ($scope.currentPage - 1) * $scope.numPerPage;
            var end = begin + $scope.numPerPage;
            var index = $scope.permissions.indexOf(value);
            return (begin <= index && index < end);
        };

        $scope.$watch("currentPage", function(){
            $scope.filterPermissions = $filter('filter')($scope.permissions, $scope.paginate);
        });

        $scope.refresh = function () {
            Permissions.allCore(function (data) {
                $scope.permissionsLoaded = true;
                $scope.numPermissions = data.permissions.length;
                $scope.permissions = $filter('orderBy')(data.permissions, "label");
                $scope.visiblePermissions = $scope.permissions.length;
                $scope.filterPermissions = $filter('filter')($scope.permissions, $scope.paginate);
            });
        };
        $scope.refresh();
    });
