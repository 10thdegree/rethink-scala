var app = angular.module('navBar', ['ui.bootstrap'])
    .factory('NavFactory', function ($http, $location, $window) {
        function setSelectedAccount(accountId, onSuccess) {
            //work around since header must be set to json
            $http({
                method: "POST",
                headers:{'Content-Type':'application/json'},
                url: '/lastselectedaccount/' + accountId,
                data: {}
            })
                .success(onSuccess)
                .error(function () {
                    console.log("Unable to set selected account.");
                });
        }

        function getAccountSelected(onSuccess) {
            $http.get('/lastselectedaccount')
                .success(onSuccess)
                .error(function () {
                    console.log("Unable to get selected account.");
                });
        }

        function getAvailableAccounts(onSuccess) {
            $http.get('/availableaccount ')
                .success(onSuccess)
                .error(function () {
                    console.log("Unable to load accounts.");
                });
        }

        return {
            accountSelected: getAccountSelected,
            selectAccount: setSelectedAccount,
            availableAccounts: getAvailableAccounts
        }
    })
    .controller('NavCtrl', function($scope, $filter, NavFactory) {
        $scope.refresh = function () {
            NavFactory.availableAccounts(function (data) {
                $scope.numAccounts = data.accounts.length;
                $scope.accounts = $filter('orderBy')(data.accounts, "label");

                NavFactory.accountSelected(function (data) {
                    if (!angular.isUndefined(data.lastSelectedAccount) && data.lastSelectedAccount != null) {
                        var selectedAccounts = $filter('filter')($scope.accounts, {id: data.lastSelectedAccount});
                        if (selectedAccounts.length == 0){
                            $scope.selectedAccount = {id: "", label: "Select Account..."};
                        } else {
                            $scope.selectedAccount = selectedAccounts[0];
                        }
                    } else {
                        $scope.selectedAccount = {id: "", label: "Select Account..."};
                    }
                    if ($scope.numAccounts == 0) {
                        console.log("No available accounts.")
                    }
                    else if ($scope.numAccounts == 1){
                        $scope.accountSelected($scope.accounts[0])
                    }
                });
            });
        };
        $scope.refresh();
        $scope.accountSelected = function (account) {
            if (account.id != $scope.selectedAccount.id) {
                $scope.selectedAccount = account;
                NavFactory.selectAccount(account.id, function () {
                    window.location.reload();
                });
            }
        };
    });

angular.element(document).ready(function() {
    angular.bootstrap(document.getElementById("mainNavigation"), ['navBar']);
});
