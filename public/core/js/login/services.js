/*global define */

'use strict';

angular
    .module('login.services', [])
    .factory('Auth', function ($http, $location, $window) {
        function authenticate(credentials, onFail) {
            $http.post('/authenticate', credentials)
                .success(function (data) {
                    $window.location = data.redirect;
                })
                .error(function () {
                    onFail();
                });
        }

        return {
            authenticate: authenticate
        }
    });
