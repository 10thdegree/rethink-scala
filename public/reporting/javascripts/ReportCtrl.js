app.controller('ReportCtrl', ['$timeout', 'ReportsService', 'ReportViews', '$scope', '$filter', function ($timeout, reports, views, scope, $filter) {
    var vm = this;

    vm.isLoading = true;

    vm.range = {
        start: '',
        end: ''
    };

    vm.columns = [];

    vm.rows = [];

    vm.footers = [];

    vm.charts = [];

    vm.reportTitle = "Report for " + vm.range.start + " to " + vm.range.end;

    scope.$on('report.fetch.start', function () {
        console.log("Report will load...");
        vm.isLoading = true;
    });

    scope.$on('report.fetch.end', function () {
        console.log("Report done loading!");
        vm.isLoading = false;
    });

    vm.views = [];
    views.getViews(function (viewList) {
        vm.views = viewList;
        vm.selectedView = viewList[0].uuid;
        scope.$apply();
    });

    function ColumnDesc(uuid, name, display, sort, format, footerType) {
        this.uuid = uuid;
        this.name = name;
        this.display = display;
        this.sort = sort;
        this.format = format;
        this.footerType = footerType;
    }

    function formatValue(format, ret) {
        var formatted = ret;
        switch (format) {
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
        return formatted;
    }

    function reportLoadedCallback(report) {

        var cols = report.columns.map(function (e) {
            return new ColumnDesc(e.uuid, e.varName, e.displayName, e.varName, e.format, e.footerType);
        });
        cols.unshift(new ColumnDesc("", "Key", "Key", "Key", ""));
        var colsById = {};
        cols.forEach(function (c, ci) {
            colsById[c.uuid] = c;
            colsById[c.name] = c;
        });

        var footers = cols.map(function (e, i) { return { sum: 0, min: undefined, max: undefined}; });

        var flattened = report.rows.map(function (e) {
            Object.keys(e.values).forEach(function (k, ki) {
                e.values[k].display = formatValue(colsById[k].format, e.values[k].val);
            });
            return angular.extend(
                {},
                {'Key': { 'val':e.key, 'display': e.key}},
                e.values);
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

        vm.charts = report.charts.map(function (c, ci) {
            switch (c.type) {
                case "Bar":
                    return {
                        id: 'chart-' + ci,
                        type: "Bar",
                        targetFieldName: colsById[c.domainField].name,
                        label: c.label
                    };
                case "Pie":
                    return {
                        id: 'chart-' + ci,
                        type: "Pie",
                        targetFieldName: colsById[c.rangeField].name,
                        label: c.label
                    };
            }
        });
        console.log(vm.charts);

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
            return { value: formatValue(c.format, ret) };
        });

        vm.columns = cols;
        vm.rows = flattened;

        vm.reloadForView = function() {

            vm.rows = [];
            vm.columns = [];

            scope.$apply();

            loadReport(vm.selectedView);
        };

        // Update watchers //
        scope.$apply();
        // Update watchers //

        console.log("Finished loading table and charts.");
    }

    function loadReport(viewId) {
        vm.isLoading = true;
        $timeout(function() {
            if (vm.range.start == "") {
                var d = new Date();
                d.setDate(1);
                vm.range.start = $filter('date')(d,'yyyy-MM-dd');
            }
            if (vm.range.end == "") {
                vm.range.end = $filter('date')(new Date(),'yyyy-MM-dd');
            }
            console.log("start/end: " + vm.range.start + "/" + vm.range.end)
            reports.getReport(viewId, vm.range.start, vm.range.end, reportLoadedCallback);
        }, 1);
    }

    loadReport("");
}])
    .service('ReportViews', ['$http', function ($http) {
        this.getViews = function(callback) {
            console.log("Fetching views for report...");
            $http
                .get('/reporting/reportViews')
                .success(function(data, status, headers, config) {
                    var json = data;//JSON.parse(data);
                    console.log("Got views for report back.");
                    console.log(json);
                    callback(json);
                })
                .error(function(data, status, headers, config) {
                   console.log("Unable to fetch views!");
                });
        }
    }])
    .directive('bvoBarChart', ['$window', function ($window) {
        function link(scope, element, attrs) {
            var rowData = [];
            var targetFieldName = attrs.targetField || "cpc";
            var title = attrs.title;

            function updateChart() {

                var barchartData = rowData.map(function (e) {
                    var v = e[targetFieldName].val;
                    return [e["Key"].display, parseFloat(v)];
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
                    return [e["Key"].display, parseInt(e[targetFieldName].val)];
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