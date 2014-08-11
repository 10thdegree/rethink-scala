/*global define, angular */

'use strict';

var myUserManagement = angular
    .module('myUserManagement', ["ui.router", "myUserManagement.controllers", "myUserManagement.services", "ui.bootstrap"])
    .config(function ($stateProvider, $urlRouterProvider) {
        $urlRouterProvider.otherwise("/accounts");

        $stateProvider
            .state('accounts', {
                url: "/accounts",
                templateUrl: "/assets/partials/accounts/accounts.html",
                controller: "AccountsCtrl"
            })
            .state('users', {
                url: "/users",
                templateUrl: "/assets/partials/users/users.html",
                controller: "UsersCtrl"
            })
            .state('permissions', {
                url: "/permissions",
                templateUrl: "/assets/partials/permissions/permissions.html",
                controller: "PermissionsCtrl"
            })
    })
    .directive('ngEnter', function () {
        return function (scope, element, attrs) {
            element.bind("keydown keypress", function (event) {
                if(event.which === 13) {
                    scope.$apply(function (){
                        scope.$eval(attrs.ngEnter);
                    });

                    event.preventDefault();
                }
            });
        };
    })
    .directive('focusMe', function($timeout) {
        return function(scope, element, attrs) {
            scope.$watch(attrs.focusMe, function(value) {
                if(value) {
                    $timeout(function() {
                        element.focus();
                    }, 100);
                }
            });
        };
    });
