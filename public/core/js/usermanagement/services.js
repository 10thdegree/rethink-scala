/*global define */

'use strict';

angular
    .module('myUserManagement.services', [])
    .factory('Accounts', function ($http) {
        function getAll(onSuccess) {
            $http.get('/account')
                .success(onSuccess)
                .error(function () {
                    alert("Unable to load accounts.");
                });
        }

        function get(accountId, onSuccess) {
            $http.get('/account/' + accountId)
                .success(onSuccess)
                .error(function () {
                    alert("Unable to load account.");
                });
        }

        function getAllUsers(accountId, onSuccess) {
            $http.get('/account/users/' + accountId)
                .success(onSuccess)
                .error(function () {
                    alert("Unable to load users from account.");
                });
        }

        function create(account, onSuccess) {
            $http.post('/account', account)
                .success(onSuccess)
                .error(function () {
                    alert("Unable to create Account.");
                });
        }

        function rename(accountId, label, onSuccess) {
            $http.post('/account/rename/' + accountId, label)
                .success(onSuccess)
                .error(function () {
                    alert("Unable to rename Account.");
                });
        }

        function remove(accountId, onSuccess) {
            $http.delete('/account/' + accountId, {})
                .success(onSuccess);
        }

        function addPermission(accountId, permission, onSuccess) {
            $http.post('/account/permissions/' + accountId, permission)
                .success(onSuccess)
                .error(function () {
                    alert("Unable to add permission to Account.");
                });
        }

        function removePermission(accountId, permission, onSuccess) {
            //work around since header must be set to json
            $http({
                method: "DELETE",
                headers:{'Content-Type':'application/json'},
                url: '/account/permissions/' + accountId,
                data: permission
            })
                .success(onSuccess)
                .error(function () {
                    alert("Unable to remove permission from Account.");
                });;
        }

        function removeUsers(accountId, userIds, onSuccess) {
            //work around since header must be set to json
            $http({
                method: "DELETE",
                headers:{'Content-Type':'application/json'},
                url: '/account/users/' + accountId,
                data: userIds
            })
                .success(onSuccess)
                .error(function () {
                    alert("Unable to remove users from Account.");
                });;
        }

        function addUsers(accountId, userIds, onSuccess) {
            //work around since header must be set to json
            console.log('/account/users/' + accountId);
            console.log(userIds)
            $http.post('/account/users/' + accountId, userIds)
                .success(onSuccess)
                .error(function () {
                    alert("Unable to add users to Account.");
                });
        }

        return {
            all: getAll,
            get: get,
            users: getAllUsers,
            create: create,
            addPermission: addPermission,
            removePermission: removePermission,
            removeUsers: removeUsers,
            addUsers: addUsers,
            rename: rename,
            remove: remove
        }
    })
    .factory('Users', function ($http) {
        function getAll(onSuccess) {
            $http.get('/user')
                .success(onSuccess)
                .error(function () {
                    alert("Unable to load users.");
                });
        }

        function getAllInvited(onSuccess) {
            $http.get('/user/invited')
                .success(onSuccess)
                .error(function () {
                    alert("Unable to load invited users.");
                });
        }

        function addPermission(userId, permissionIds, onSuccess) {
            $http.post('/user/permissions/'+userId, permissionIds)
                .success(onSuccess)
                .error(function () {
                    alert("Unable to add permission to user.");
                });
        }
        function removePermission(userId, permissionIds, onSuccess) {
            //work around since header must be set to json
            $http({
                method: "DELETE",
                headers:{'Content-Type':'application/json'},
                url: '/user/permissions/' + userId,
                data: permissionIds
            })
                .success(onSuccess)
                .error(function () {
                    alert("Unable to remove permission from user.");
                });;
        }
        function invite(email, onSuccess) {
            $http.post('/invite', email)
                .success(onSuccess)
                .error(function () {
                    alert("Unable to invite user.");
                });
        }

        function removeInvite(inviteId, onSuccess) {
            $http.delete('/user/invited/' + inviteId, {})
                .success(onSuccess);
        }

        function remove(userId, onSuccess) {
            $http.delete('/user/' + userId, {})
                .success(onSuccess);
        }

        return {
            all: getAll,
            invite: invite,
            addPermission: addPermission,
            removePermission: removePermission,
            allInvited: getAllInvited,
            removeInvite: removeInvite,
            remove: remove
        }
    })
    .factory('Permissions', function ($http) {
        function getAll(onSuccess) {
            $http.get('/permission')
                .success(onSuccess)
                .error(function () {
                    alert("Unable to load permissions.");
                });
        }

        function getAllCore(onSuccess) {
            $http.get('/permission/readonly')
                .success(onSuccess)
                .error(function () {
                    alert("Unable to load core permissions.");
                });
        }

        function create(permission, onSuccess) {
            $http.post('/permission', permission)
                .success(onSuccess)
                .error(function () {
                    alert("Unable to create permission.");
                });
        }

        function rename(permissionId, label, onSuccess) {
            $http.post('/permission/rename/' + permissionId, label)
                .success(onSuccess)
                .error(function () {
                    alert("Unable to rename permission.");
                });
        }

        function remove(permissionId, onSuccess) {
            $http.delete('/permission/' + permissionId, {})
                .success(onSuccess);
        }

        return {
            all: getAll,
            allCore: getAllCore,
            create: create,
            rename: rename,
            remove: remove
        }
    });
