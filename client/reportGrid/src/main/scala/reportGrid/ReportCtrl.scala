package reportGrid

import biz.enef.angulate.core.{Timeout, HttpService, Attributes}
import biz.enef.angulate._
import org.scalajs.dom.raw.Window

import scala.scalajs.js
import scala.scalajs.js.UndefOr
import scala.scalajs.js.annotation.JSName
import scala.util.{Failure, Success}
import org.scalajs.dom.console
import org.scalajs.jquery.jQuery

object ReportCtrl {
  class DateRange extends js.Object {
    var start: js.Date = js.native
    var end: js.Date = js.native
  }

  object DateRange {
    def apply(start: js.Date, end: js.Date): DateRange =
      js.Dynamic.literal(start = start, end = end).asInstanceOf[DateRange]
  }

  trait ReportCtrlScope extends Scope {
    var isLoading: Boolean = js.native
    var reportTitle: String = js.native
    var range: DateRange = js.native
    var columns: js.Array[Column] = js.native
    var rows: js.Array[js.Dictionary[CellValue]] = js.native
    var footers: js.Array[Footer] = js.native
    var charts: js.Array[ChartInstance] = js.native
    var views: js.Array[js.Dynamic] = js.native
    var selectedView: String = js.native
    var reloadForView: js.Function = js.native
  }
}

class Footer(var sum: Float,
             var min: UndefOr[Float],
             var max: UndefOr[Float]) extends js.Object {
  def this() {
    this(0, js.undefined, js.undefined)
  }
  var value: String = js.native
}

object Footer {
  def apply(sum: Float) = js.Dynamic.literal(sum = 0, min = js.undefined, max = js.undefined).asInstanceOf[Footer]
}

class ChartInstance(val chart: Chart,
                    val id: String,
                    val `type`: String,
                    val targetFieldName: String,
                    val label: String) extends js.Object {
}

object ChartInstance {
  def apply(chart: Chart, idx: Int, colsById: js.Dictionary[Column]): ChartInstance = chart.`type` match {
    //case "Bar" => new ChartInstance(chart, "chart-" + idx, "Bar", colsById(chart.domainField + "").name, chart.label)
    //case "Pie" => new ChartInstance(chart, "chart-" + idx, "Pie", colsById(chart.rangeField + "").name, chart.label)
    case "Bar" => js.Dynamic.literal(chart = chart, id = "chart-" + idx, `type` = "Bar", targetFieldName = colsById(chart.domainField + "").name, label = chart.label).asInstanceOf[ChartInstance]
    case "Pie" => js.Dynamic.literal(chart = chart, id = "chart-" + idx, `type` = "Pie", targetFieldName = colsById(chart.rangeField + "").name, label = chart.label).asInstanceOf[ChartInstance]
  }
}

class ReportCtrl($scope: ReportCtrl.ReportCtrlScope,
                 reportViewsService: ReportViewsService,
                 $filter: Filter,
                 reportsService: ReportsService,
                 $timeout: Timeout,
                 $window: js.Dynamic) extends ScopeController {

  import org.widok.moment.Moment
  import org.widok.moment.Units
  $scope.isLoading = true
  $scope.range = ReportCtrl.DateRange(
    Moment().subtract(1, Units.Day).toDate(),
    Moment().subtract(1, Units.Day).toDate()
  )
  $scope.views = js.Array()
  $scope.columns = js.Array()
  $scope.rows = js.Array()
  $scope.footers = js.Array()
  $scope.charts = js.Array()


  reportViewsService.getViews((vs: js.Array[js.Dynamic]) => {
    $scope.views = vs
    $scope.selectedView = vs(0).uuid.toString
    $scope.$apply()
  })

  $scope.$on("report.fetch.start", () => {
    console.log("Report will load...")
    $scope.isLoading = true
  })

  $scope.$on("report.fetch.end", () => {
    console.log("Report done loading!")
    $scope.isLoading = false
  })

  def formatValue(format: String, value: js.Any): String = {
    (format match {
      case "currency" => $filter("currency")(value)
      case "percentage" => $filter("number")(value.asInstanceOf[Double] * 100, 2) + "%"
      case "fractional" => $filter("number")(value, 2)
      case "whole" => $filter("number")(value, 0)
      case _ => value
    }) + ""
  }

  def reportLoadedCallback(report: ReportView) {
    $scope.columns = report.fields
    $scope.columns.unshift(Column("", "Key", "Key", "Key", "", ""))

    val colsById = js.Dictionary.empty[Column]
    $scope.columns.foreach(c => {
      colsById(c.uuid) = c
      colsById(c.name) = c
    })

    val flattened = report.rows.map(r => {
      r.values.keys.foreach(k => {
        r.values(k).display = formatValue(colsById(k).format, r.values(k).`val`)
      })
      r.values("Key") = js.Dynamic.literal("val" -> r.key, "display" -> r.key).asInstanceOf[CellValue]
      r.values
    })
    $scope.rows = flattened

    import js.JSConverters._
    $scope.charts = for {
      i <- (0 until report.charts.length).toJSArray
      c = report.charts(i)
    } yield {
        console.log(c.`type`)
        if (c.domainField != js.undefined) console.log(colsById(c.domainField + "") + " domain")
        if (c.rangeField != js.undefined) console.log(colsById(c.rangeField + "") + " range")
        ChartInstance(c, i, colsById)
      }

    // Initialise footers
    $scope.footers = $scope.columns.map(e => Footer(0))

    // Compute footer aggregate functions
    for {
      r <- $scope.rows
      i <- 1 until $scope.columns.length
      c = $scope.columns(i)
    } {
      val v = ("" + r(c.name).`val`).toFloat
      val f = $scope.footers(i)
      $scope.footers(i).sum += v
      if (!f.min.isDefined || v < f.min.get) f.min = v
      if (!f.max.isDefined || v > f.max.get) f.max = v
    }

    // Format footer display values
    for {
      ci <- 1 until $scope.columns.length
      c = $scope.columns(ci)
      f = $scope.footers(ci)
    } f.value = formatValue(c.format, c.footerType match {
      case "avg" => f.sum / flattened.length
      case "min" => f.min
      case "max" => f.max
      case "sum" => f.sum
      case _ => ""
    })

    $scope.reloadForView = () => {
      $scope.rows = js.Array()
      $scope.columns = js.Array()
      loadReport($scope.selectedView)
    }

    $scope.$apply()

    console.log("Finished loading table and charts.")
  }

  def loadReport(viewId: String): Unit = {
    $scope.isLoading = true
    $scope.reportTitle = "Report for " + $filter("date")($scope.range.start, "MMM d, yyyy") + " to " + $filter("date")($scope.range.end, "MMM d, yyyy")
    $timeout(() => {
      val start = $filter("date")($scope.range.start, "yyyy-MM-dd").toString()
      val end = $filter("date")($scope.range.end, "yyyy-MM-dd").toString()
      console.log("start/end: " + start + "/" + end)
      reportsService.getReport(viewId, start, end, reportLoadedCallback _)
    }, 1)
  }

  loadReport("")
}

@JSName("c3")
object C3 extends js.Object {
  def generate(opts: js.Object): js.Any = js.native
}

class ReportViewsService($http: HttpService) extends Service {
  def getViews(callback: js.Function1[js.Array[js.Dynamic],_]): Unit = {
    console.log("Fetching views for report...")
    $http.get[js.Array[js.Dynamic]]("/reporting/reportViews")
      .success((data: js.Array[js.Dynamic]) => {
        console.log("Got views for report back.")
        callback(data)
      })
      .error(() => console.log("Unable to fetch views!"))
  }
}

class PieChartDirective($window: Window) extends Directive {
  override type ScopeType = Scope
  override type ControllerType = js.Any
  override val template = """<div id="{{id}}" style="{{style}}"></div>"""
  override val restrict = "E"
  override def postLink(scope: ScopeType,
                        element: biz.enef.angulate.core.JQLite,
                        attrs: biz.enef.angulate.core.Attributes,
                        controller: ControllerType) {

    var rowData = js.Array[js.Dictionary[CellValue]]()
    val targetFieldName = attrs("targetField").getOrElse("impressions")
    def updateChart(): Unit = {
      val piechartData = rowData.map(e => js.Array(
          e("Key").display,
          ("" + e(targetFieldName).`val`).toInt))
      console.log("2")
      val piechart = C3.generate(js.Dynamic.literal(
        bindto = "#" + attrs("id"),
        data = js.Dynamic.literal(columns = piechartData, `type` = "donut"),
        size = js.Dynamic.literal(width = $window.innerWidth * 0.48), // TODO: Make better
        donut = js.Dynamic.literal(title = attrs("title")),
        transition = js.Dynamic.literal(duration = 1500),
        legend = js.Dynamic.literal(position = "inset")
      ))
      console.log("3")
    }
    scope.$watch(attrs("rowData"), (v:js.Array[js.Dictionary[CellValue]]) => {
      if (v != js.undefined) {
        rowData = v
        updateChart()
      }
    })
  }
}

class BarChartDirective($window: Window) extends Directive {
  override type ScopeType = Scope
  override type ControllerType = js.Any
  override val template = """<div id="{{id}}" style="{{style}}"></div>"""
  override val restrict = "E"
  override def postLink(scope: ScopeType,
                        element: biz.enef.angulate.core.JQLite,
                        attrs: biz.enef.angulate.core.Attributes,
                        controller: ControllerType) {

    var rowData = js.Array[js.Dictionary[CellValue]]()
    val targetFieldName = attrs("targetField").getOrElse("cpc")
    def updateChart(): Unit = {
      val barchartData = rowData.map(e => js.Array(
        e("Key").display,
        ("" + e(targetFieldName).`val`).toFloat))
      val barchart = C3.generate(js.Dynamic.literal(
        bindto = "#" + attrs("id"),
        data = js.Dynamic.literal(columns = barchartData, `type` = "bar"),
        size = js.Dynamic.literal(width = $window.innerWidth * 0.48), // TODO: Make better
        legend = js.Dynamic.literal(show = false),
        axis = js.Dynamic.literal(y = js.Dynamic.literal(label = attrs("title")))
      ))
    }
    scope.$watch(attrs("rowData"), (v:js.Array[js.Dictionary[CellValue]]) => {
      if (v != js.undefined) {
        rowData = v
        updateChart()
      }
    })
  }
}

object DataRangePicker {
  import org.widok.moment.Date
  trait DateRangePicker extends js.Object {
    def setStartDate(moment: Date) = js.native
    def setEndDate(moment: Date) = js.native
  }
}

class ModelController[T <: js.Any] extends js.Object {
  def $modelValue: T = js.native
  def $render() = js.native
  def $setViewValue(newVal: T) = js.native
}

trait Filter extends js.Object {
  def apply(typ: String): js.Dynamic = js.native
}

class DateRangePickerConfig(val ranges: js.Dynamic,
                            val startDate: org.widok.moment.Date,
                            val endDate: org.widok.moment.Date,
                            val maxDate: org.widok.moment.Date
                             ) extends js.Object

trait JQueryDateRangePickerMaker extends org.scalajs.jquery.JQuery {
  import org.widok.moment._
  def daterangepicker(config: DateRangePickerConfig, callback: js.Function2[Date,Date,_]): this.type = js.native
}

object JQueryDateRangePickerMaker {
  implicit def jq2drp(jq: org.scalajs.jquery.JQuery): JQueryDateRangePickerMaker =
    jq.asInstanceOf[JQueryDateRangePickerMaker]
}

trait DRScope extends Scope {
  var display: String = js.native
}

class DateRangePickerDirective($window: Window, $filter: Filter, $timeout: Timeout) extends Directive {

  import org.widok.moment._
  override type ScopeType = DRScope
  override type ControllerType = ModelController[ReportCtrl.DateRange]
  override val template = """|<div id="{{id}}" class="btn btn-default">
                             | <span><span class="class="glyphicon glyphicon-calendar" span="margin-right: 10px"></span>
                             |   {{display}}
                             | </span>
                             |</div>""".stripMargin
  override val restrict = "E"
  override def require = "ngModel"
  override def postLink(scope: ScopeType,
                        element: biz.enef.angulate.core.JQLite,
                        attrs: biz.enef.angulate.core.Attributes,
                        controller: ControllerType) {

    def display(): Unit = {
      val range = controller.$modelValue
      val start = $filter("date")(range.start, "MMM d, yyyy")
      val end = $filter("date")(range.end, "MMM d, yyyy")
      scope.display = start + " - " + end
    }

    def handleSelection(start: Date, end: Date): Unit = {
      val range = controller.$modelValue
      range.start = start.toDate()
      range.end = end.toDate()
      controller.$setViewValue(range)
      controller.$render()
      scope.$apply()
      $timeout(display _, 1)
      scope.$eval(attrs("ngChange").asInstanceOf[js.Object])
    }

    import JQueryDateRangePickerMaker._
    jQuery("#" + attrs("id")).daterangepicker(new DateRangePickerConfig(
      js.Dynamic.literal(
        "MTD" -> js.Array(Moment().startOf("month"), Moment()),
        "This Week" -> js.Array(Moment().startOf("week"), Moment()),
        "Last Week" -> js.Array(Moment().startOf("week").subtract(6, "days"), Moment().startOf("week").subtract(1, "days")),
        "Last Month" -> js.Array(Moment().subtract(1, "month").startOf("month"), Moment().subtract(1, "month").endOf("month")),
        //"Today" -> js.Array(Moment(), Moment()),
        "Yesterday" -> js.Array(Moment().subtract(1, "days"), Moment()),
        "Last 7 Days" -> js.Array(Moment().subtract(6, "days"), Moment()),
        "Last 30 Days" -> js.Array(Moment().subtract(30, "days"), Moment()),
        "Last 60 Days" -> js.Array(Moment().subtract(60, "days"), Moment()),
        "Last 90 Days" -> js.Array(Moment().subtract(90, "days"), Moment())),
      Moment(),
      Moment(),
      Moment().subtract(1, Units.Day)), handleSelection _)

    display()

    scope.$watch("model", () => {
      console.log("model updated!")
      val drp = jQuery("#" + attrs("id")).data("datarangepicker").asInstanceOf[DataRangePicker.DateRangePicker]
      drp.setStartDate(Moment())
      drp.setEndDate(Moment())
      display()
    })
  }
}
