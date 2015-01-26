app.controller('ReportCtrl', ['$timeout', 'ReportsService', '$scope', function ($timeout, reports, scope) {
    var vm = this;

    vm.isLoading = true;

    vm.range = {
        start: $("#startDate").val(),
        end: $("#endDate").val()
    };

    vm.columns = [];

    vm.rows = [];

    vm.reportTitle = "Report for " + vm.range.start + " to " + vm.range.end;

    scope.$on('report.fetch.start', function () {
        console.log("Report will load...");
        vm.isLoading = true;
    });

    scope.$on('report.fetch.end', function () {
        console.log("Report done loading!");
        vm.isLoading = false;
    });

    function ColumnDesc(name, display, sort) {
        this.name = name;
        this.display = display;
        this.sort = sort;
    }

    function reportLoadedCallback(report) {
        var flattened = report.rows.map(function (e) {
            return angular.extend({}, {'Key': { 'val':e.key, 'disp': e.key}}, e.values);
        });

        var cols = report.columns.map(function (e) {
            return new ColumnDesc(e.varName, e.displayName, e.varName);
        });
        cols.unshift(new ColumnDesc("Key", "Key", "Key"));

        vm.columns = cols;
        vm.rows = flattened;

        // Update watchers //
        scope.$apply();
        // Update watchers //

        var piechartData = flattened.map(function (e) {
            return [e["Key"].disp, parseInt(e["Impressions"].val)];
        });

        var barchartData = flattened.map(function (e) {
            var cpc = e["CPC"].val;
            return [e["Key"].disp, parseFloat(cpc)];
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
        console.log("Finished loading table and charts.");
    }

    $timeout(function() { reports.getReport(vm.range.start, vm.range.end, reportLoadedCallback); }, 1);
}]);

