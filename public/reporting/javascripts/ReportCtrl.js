app.controller('ReportCtrl', ['$timeout', 'ReportsService', '$scope', '$filter', function ($timeout, reports, scope, $filter) {
    var vm = this;

    vm.isLoading = true;

    vm.range = {
        start: $("#startDate").val(),
        end: $("#endDate").val()
    };

    vm.columns = [];

    vm.rows = [];

    vm.footers = [];

    vm.reportTitle = "Report for " + vm.range.start + " to " + vm.range.end;

    scope.$on('report.fetch.start', function () {
        console.log("Report will load...");
        vm.isLoading = true;
    });

    scope.$on('report.fetch.end', function () {
        console.log("Report done loading!");
        vm.isLoading = false;
    });

    function ColumnDesc(name, display, sort, format, footerType) {
        this.name = name;
        this.display = display;
        this.sort = sort;
        this.format = format;
        this.footerType = footerType;
    }

    function reportLoadedCallback(report) {

        var cols = report.columns.map(function (e) {
            return new ColumnDesc(e.varName, e.displayName, e.varName, e.format, e.footerType);
        });
        cols.unshift(new ColumnDesc("Key", "Key", "Key", ""));

        var footers = cols.map(function (e, i) { return { sum: 0, min: undefined, max: undefined}; });

        var flattened = report.rows.map(function (e) {
            return angular.extend({}, {'Key': { 'val':e.key, 'disp': e.key}}, e.values);
        });

        flattened.forEach(function (r, ri) {
            cols.forEach(function (c, ci) {
                if (ci > 0) {
                    var v = r[c.name].val;
                    var f = footers[ci];
                    footers[ci].sum += v;
                    if (f.min == undefined || v < f.min) f.min = v;
                    if (f.max == undefined || v > f.min) f.max = v;
                }
            });
        });

        vm.footers = cols.map(function (c, ci) {
            var ret = "";
            if (ci > 0) {
                var f = footers[ci];
                switch (c.footerType) {
                    case "avg":
                        ret = f.sum / flattened.length;
                        break;
                    case "min":
                        ret = f.min;
                        break;
                    case "max":
                        ret = f.max;
                        break;
                    case "sum":
                        ret = f.sum;
                        break;
                    default:
                        ret = "";
                        break;
                }
            }
            var formatted = "";
            switch (c.format) {
                case "currency":
                    formatted = $filter('currency')(ret);
                    break;
                case "percentage":
                    formatted = $filter('number')(ret * 100, 0) + "%";
                    break;
                case "fractional":
                    formatted = $filter('number')(ret, 2);
                    break;
                case "whole":
                    formatted = $filter('number')(ret, 0);
                    break;
                default:
                    formatted = ret;
            }
            return { value: formatted };
        });

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
            var targetFieldName = attrs.targetField || "cpc";
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
            var targetFieldName = attrs.targetField || "impressions";
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