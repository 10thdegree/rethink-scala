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

        console.log("Finished loading table and charts.");
    }

    $timeout(function() { reports.getReport(vm.range.start, vm.range.end, reportLoadedCallback); }, 1);
}])
    .directive('bvoBarChart', ['$window', function ($window) {
        function link(scope, element, attrs) {
            var rowData = [];
            var targetFieldName = attrs.targetField || "CPC";
            var title = attrs.title;

            function updateChart() {

                var barchartData = rowData.map(function (e) {
                    var v = e[targetFieldName].val;
                    return [e["Key"].disp, parseFloat(v)];
                });

                var barchart = c3.generate({
                    bindto: "#" + element.attr('id'),
                    data: {
                        columns: barchartData,
                        type: "bar"
                    },
                    size: {
                        width: $window.innerWidth * 0.48 // TODO: Make better
                    },
                    legend: {show: false},
                    axis: {
                        y: {
                            label: title
                        }
                    }
                });
            }

            scope.$watch(attrs.rowData, function(value) {
                rowData = value;
                updateChart();
            });
        }
        return {
            link: link,
            restrict: 'E',
            template: '<div id="{{id}}" style="{{style}}"></div>'
        };
    }])
    .directive('bvoPieChart', ['$window', function ($window) {
        function link(scope, element, attrs) {
            var rowData = [];
            var targetFieldName = attrs.fieldName || "Impressions";
            var title = attrs.title;

            function updateChart() {

                var piechartData = rowData.map(function (e) {
                    return [e["Key"].disp, parseInt(e[targetFieldName].val)];
                });

                var piechart = c3.generate({
                    bindto: '#' + element.attr('id'),
                    data: {
                        columns: piechartData,
                        type: 'donut'
                    },
                    size: {
                        width: $window.innerWidth * 0.48 // TODO: Make better
                    },
                    donut: {
                        title: title
                    },
                    transition: { duration: 1500 },
                    legend: { position: 'inset' }
                });
            }

            scope.$watch(attrs.rowData, function(value) {
                rowData = value;
                updateChart();
            });
        }

        return {
            link: link,
            restrict: 'E',
            template: '<div id="{{id}}" style="{{style}}"></div>'
        };
    }]);