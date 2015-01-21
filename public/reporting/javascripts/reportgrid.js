var app = angular.module('app', ['ngTouch', 'ui.grid', 'ui.grid.resizeColumns']);

app.controller('ReportView', ['$scope', '$http', '$timeout', function ($scope, $http, $timeout) {
    $scope.gridOptions = {
        enableSorting: true
    };

    $scope.range = {
        start: $("#startDate").val(),
        end: $("#endDate").val()
    };

    $scope.getReport = getReport;

    function tellAngular() {
        console.log("tellAngular call");
        //angular.element(document.getElementsByClassName('grid')[0]).css('height', window.innerHeight + 'px');
        angular.element(document.getElementsByClassName('grid')[0]).css('width', window.innerWidth + 'px');
    }

    //first call of tellAngular when the dom is loaded
    document.addEventListener("DOMContentLoaded", tellAngular, false);

    //calling tellAngular on resize event
    window.onresize = tellAngular;

    $timeout(function() { $scope.getReport($scope); }, 1);

}]);


function getReport ($scope) {

    var ws = new WebSocket("ws://localhost:9000/reporting/socket/reportDataRequest");

    ws.onopen = function () {
        console.log("Socket has been opened. Will fetch report for " + $scope.range.start + " to " + $scope.range.end);
        ws.send("{\"startDate\":\"" + $scope.range.start + "\", \"endDate\":\"" + $scope.range.end + "\"}");
    };

    ws.onmessage = jsonDataLoaded;

    function gotAnAlert(alertdata) {
        alert(alertdata.data);
    }

    function jsonDataLoaded(data) {
        console.log("data back");
        var flattened = JSON.parse(data.data).map(function (e) {
            var object = angular.extend({}, {'Key': e.key}, e.values);
            return object;
        });

        $scope.gridOptions.data = flattened;
        angular.element(document.getElementsByClassName('grid')[0]).css('height', '100%');

        var piechartData = flattened.map(function (e) {
            return [e["Key"], parseInt(e["Impressions"])];
        });

        var barchartData = flattened.map(function (e) {
            var cpc = e["CPC"];
            if (cpc == "N/A") cpc = 0;
            else cpc = cpc.substring(1);
            return [e["Key"], parseFloat(cpc)];
        });

        var piechart = c3.generate({
            bindto: "#piechart",
            data: {
                columns: piechartData,
                type: 'donut',
                onclick: function (d, i) {
                    console.log("onclick", d, i);
                },
                onmouseover: function (d, i) {
                    console.log("onmouseover", d, i);
                },
                onmouseout: function (d, i) {
                    console.log("onmouseout", d, i);
                }
            },
            donut: {
                title: "Visitors by Category"
            },
            transition: {duration: 1500},
            legend: {
                position: 'inset'
            }
        });

        var barchart = c3.generate({
            bindto: "#barchart",
            data: {
                columns: barchartData,
                type: "bar"
            },
            legend: {show: false},
            bar: {
                title: "Cost per Visitor"
            },
            axis: {
                y: {
                    label: "Cost per Visitor"
                }
            }
        });
    }
}
