app.service('ReportsService', ['$rootScope', '$http', function($rootScope, $http) {

    this.getReport = function (viewId, start, end, callback) {
        $rootScope.$broadcast( 'report.fetch.start' );

        /*var ws = new WebSocket("ws://localhost:9000/reporting/socket/reportDataRequest");
        ws.onopen = function () {
            console.log(
                "report.fetch.start: Socket has been opened. " +
                "Will fetch report for " + start + " to " + end + "." +
                "Using view with id " + viewId);
            ws.send("{\"viewId\":\"" + viewId + "\",\"startDate\":\"" + start + "\", \"endDate\":\"" + end + "\"}");
        };
        ws.onmessage = function (data) {
            $rootScope.$broadcast( 'report.fetch.end' );
            console.log("report.fetch: data received");

            var data2 = JSON.parse(data.data);
            var rows = data2.rows;
            var columns = data2.fields;
            var charts = data2.charts;

            callback({ columns: columns, rows: rows, charts: charts });

            //scope.$broadcast( 'report.fetch.end' );
        };*/

        $http.get("/reporting/reportDataRequest", {params:{viewId: viewId, startDate: start, endDate: end}})
            .success(function(data, status, headers, config) {
                var json = data;//JSON.parse(data);
                $rootScope.$broadcast( 'report.fetch.end' );
                console.log("report.fetch: data received");

                var data2 = json;//JSON.parse(data.data);
                var rows = data2.rows;
                var columns = data2.fields;
                var charts = data2.charts;

                callback({ columns: columns, rows: rows, charts: charts });
            })
            .error(function(data, status, headers, config) {
                console.log("Unable to fetch report!");
            });
    };
}]);
