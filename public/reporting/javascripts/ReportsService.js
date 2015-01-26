app.service('ReportsService', ['$rootScope', function(scope) {

    this.getReport = function (start, end, callback) {
        scope.$broadcast( 'report.fetch.start' );

        var ws = new WebSocket("ws://localhost:9000/reporting/socket/reportDataRequest");

        ws.onopen = function () {
            console.log("report.fetch.start: Socket has been opened. Will fetch report for " + start + " to " + end);
            ws.send("{\"startDate\":\"" + start + "\", \"endDate\":\"" + end + "\"}");
        };

        ws.onmessage = function (data) {
            scope.$broadcast( 'report.fetch.end' );
            console.log("report.fetch: data received");

            var data2 = JSON.parse(data.data);
            var rows = data2.rows;
            var columns = data2.fields;

            callback({ columns: columns, rows: rows });

            //scope.$broadcast( 'report.fetch.end' );
        };
    };
}]);
