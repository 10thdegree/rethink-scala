/*global define */

'use strict';

/* Controllers */
angular
    .module("login.controllers", [])
    .controller("LoginCtrl", function ($scope, Auth) {
        $scope.alerts = [];
        $scope.login = function () {
            if ($scope.email == null) {
                $scope.addAlert('danger', 'Please enter email.');
            }
            else if ($scope.password == null) {
                $scope.addAlert('danger', 'Please enter password.');
            }
            else {
                Auth.authenticate({username: $scope.email, password: $scope.password},
                    function() {$scope.addAlert('danger', 'Sorry, you are unable to login.')});
            }
        };

        $scope.addAlert = function(type, message) {
            $scope.alerts.push({type: type, msg: message});
        };

        $scope.closeAlert = function(index) {
            $scope.alerts.splice(index, 1);
        };
    });
