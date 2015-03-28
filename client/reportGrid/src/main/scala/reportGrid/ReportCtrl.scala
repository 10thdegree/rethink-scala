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
    var reloadForView: () => () = js.native
  }
}

class Footer(var sum: Int,
             var min: UndefOr[Float],
             var max: UndefOr[Float]) extends js.Object {
  var value: String = js.native
}

class ChartInstance(val chart: Chart,
                    val id: String,
                    val `type`: String,
                    val targetFieldName: String,
                    val label: String) extends js.Object {
}

object ChartInstance {
  def apply(chart: Chart, idx: Int)(colsById: js.Dictionary[Column]): ChartInstance = chart.`type` match {
    case "Bar" => new ChartInstance(chart, "chart-" + idx, "Bar", colsById(chart.domainField).name, chart.label)
    case "Pie" => new ChartInstance(chart, "chart-" + idx, "Pie", colsById(chart.rangeField).name, chart.label)
  }
}

class ReportCtrl(scope: ReportCtrl.ReportCtrlScope,
                 views: ReportViewsService,
                 $filter: Filter,
                 reports: ReportsService,
                 $timeout: Timeout,
                 $window: js.Dynamic) extends Controller {

  import org.widok.moment.Moment
  import org.widok.moment.Units

  scope.isLoading = true
  scope.range = new ReportCtrl.DateRange()
  scope.range.start = Moment().subtract(1, Units.Day).toDate()
  scope.range.start = Moment().subtract(1, Units.Day).toDate()
  scope.views = js.Array()
  scope.columns = js.Array()
  scope.rows = js.Array()
  scope.footers = js.Array()
  scope.charts = js.Array()


  views.getViews(vs => {
    scope.views = vs
    scope.selectedView = vs(0).uuid.toString
    scope.$apply()
  })

  scope.$on("report.fetch.start", () => {
    console.log("Report will load...")
    scope.isLoading = true
  })

  scope.$on("report.fetch.end", () => {
    console.log("Report done loading!")
    scope.isLoading = false
  })

  def formatValue(format: String, value: js.Any): String = {
    (format match {
      case "currency" => $filter("currency")(value)
      case "percentage" => $filter("number")(value * 100, 2) + "%"
      case "fractional" => $filter("number")(value, 2)
      case "whole" => $filter("number")(value, 0)
      case _ => value
    }).toString()
  }

  def reportLoadedCallback(report: ReportView) {
    scope.columns = report.columns

    val colsById = js.Dictionary.empty[Column]
    scope.columns.foreach(c => {
      colsById(c.uuid) = c
      colsById(c.name) = c
    })

    val flattened = report.rows.map(r => {
      r.values.keys.foreach(k => {
        r.values(k).disp = formatValue(colsById(k).format, r.values(k).`val`)
      })
      r.values("key") = js.Dynamic.literal("key" -> r.key, "val" -> r.key).asInstanceOf[CellValue]
      r.values
    })
    scope.rows = flattened

    import js.JSConverters._
    scope.charts = for {
      i <- (0 until report.charts.length).toJSArray
      c = report.charts(i)
    } yield ChartInstance(c, i)(colsById)

    // Initialise footers
    scope.footers = scope.columns.map(e => new Footer(0, js.UndefOr[Float], js.UndefOr[Float]))

    // Compute footer aggregate functions
    for {
      r <- scope.rows
      i <- 1 until scope.columns.length
      c = scope.columns(i)
    } {
      val v = r(c.name).`val`.toFloat
      val f = scope.footers(i)
      scope.footers(i).sum += v
      if (!f.min.isDefined || v < f.min.get) f.min = v
      if (!f.max.isDefined || v > f.max.get) f.max = v
    }

    // Format footer display values
    for {
      ci <- 1 until scope.columns.length
      c = scope.columns(ci)
      f = scope.footers(ci)
    } f.value = formatValue(c.format, c.footerType match {
      case "avg" => f.sum / flattened.length
      case "min" => f.min
      case "max" => f.max
      case "sum" => f.sum
      case _ => ""
    })

    scope.reloadForView = () => {
      scope.rows = js.Array()
      scope.columns = js.Array()
      loadReport(scope.selectedView)
    }

    scope.$apply()

    console.log("Finished loading table and charts.")
  }

  def loadReport(viewId: String): Unit = {
    scope.isLoading = true
    scope.reportTitle = "Report for " + $filter("date")(scope.range.start, "MMM d, yyyy") + " to " + $filter("date")(scope.range.end, "MMM d, yyyy")
    $timeout(() => {
      val start = $filter("date")(scope.range.start, "yyyy-MM-dd").toString()
      val end = $filter("date")(scope.range.end, "yyyy-MM-dd").toString()
      console.log("start/end: " + start + "/" + end)
      reports.getReport(viewId, start, end, reportLoadedCallback)
    }, 1)
  }

  loadReport("")
}

@JSName("c3")
object C3 extends js.Object {
  def generate(opts: js.Object): js.Any = js.native
}

object ScalaJsOps {
  implicit class AttributeExtensions(attr: Attributes) {
    def getOrElse(key: String, ifMissing: => UndefOr[String]) = {
      if (attr.hasOwnProperty(key)) attr(key)
      else ifMissing
    }
  }
}

class ReportViewsService($rootScope: Scope, $http: HttpService) extends Service {
  def getViews(callback: js.Array[js.Dynamic] => ()): Unit = {
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
  import ScalaJsOps._
  override type ScopeType = Scope
  override type ControllerType = js.Any
  override val template = """<div id="{{id}}" style="{{style}}"></div>"""
  override val restrict = "E"
  override def postLink(scope: ScopeType,
                        element: biz.enef.angulate.core.JQLite,
                        attrs: biz.enef.angulate.core.Attributes,
                        controller: ControllerType) {

    var rowData = js.Array[js.Dynamic]()
    val targetFieldName = attrs.getOrElse("targetField", "impressions")
    def updateChart(): Unit = {
      val piechartData = rowData.map(e => js.Array(e("Key")("display"), e(targetFieldName)("val").toInt))
      val piechart = C3.generate(js.Dynamic.literal(
        bindto = "#" + attrs("id"),
        data = js.Dynamic.literal(columns = piechartData, `type` = "donut"),
        size = js.Dynamic.literal(width = $window.innerWidth * 0.48), // TODO: Make better
        donut = js.Dynamic.literal(title = attrs("title")),
        transition = js.Dynamic.literal(duration = 1500),
        legend = js.Dynamic.literal(position = "inset")
      ))
    }
    scope.$watch(attrs("rawData"), (v:js.Array[js.Dynamic]) => {
      rowData = v
      updateChart()
    })
  }
}

class BarChartDirective($window: Window) extends Directive {
  import ScalaJsOps._
  override type ScopeType = Scope
  override type ControllerType = js.Any
  override val template = """<div id="{{id}}" style="{{style}}"></div>"""
  override val restrict = "E"
  override def postLink(scope: ScopeType,
                        element: biz.enef.angulate.core.JQLite,
                        attrs: biz.enef.angulate.core.Attributes,
                        controller: ControllerType) {

    var rowData = js.Array[js.Dynamic]()
    val targetFieldName = attrs.getOrElse("targetField", "cpc")
    def updateChart(): Unit = {
      val barchartData = rowData.map(e => js.Array(e("Key")("display"), e(targetFieldName)("val").toFloat))
      val piechart = C3.generate(js.Dynamic.literal(
        bindto = "#" + attrs("id"),
        data = js.Dynamic.literal(columns = barchartData, `type` = "bar"),
        size = js.Dynamic.literal(width = $window.innerWidth * 0.48), // TODO: Make better
        legend = js.Dynamic.literal(show = false),
        axis = js.Dynamic.literal(y = js.Dynamic.literal(label = attrs("title")))
      ))
    }
    scope.$watch(attrs("rawData"), (v:js.Array[js.Dynamic]) => {
      rowData = v
      updateChart()
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

class ModelController[T <: js.Any] extends Controller {
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
  def daterangepicker(config: DateRangePickerConfig, callback: (Date, Date) => ()): this.type = js.native
}

object JQueryDateRangePickerMaker {
  implicit def jq2drp(jq: org.scalajs.jquery.JQuery): JQueryDateRangePickerMaker =
    jq.asInstanceOf[JQueryDateRangePickerMaker]
}

class DateRangePickerDirective($window: Window, $filter: Filter, $timeout: Timeout) extends Directive {

  trait DRScope extends Scope {
    var display: String = js.native
  }

  import ScalaJsOps._
  import org.widok.moment._
  override type ScopeType = DRScope
  override type ControllerType = ModelController[js.Dynamic]
  override val template = """|<div id="{{id}}" class="btn btn-default">
                             | <span><span class="class="glyphicon glyphicon-calendar" span="margin-right: 10px"></span>
                             |   {{display}}
                             | </span>
                             |</div>""".stripMargin
  override val restrict = "E"
  override val require = "ngModel"
  override def postLink(scope: ScopeType,
                        element: biz.enef.angulate.core.JQLite,
                        attrs: biz.enef.angulate.core.Attributes,
                        controller: ControllerType) {

    def display(): Unit = {
      val range = controller.$modelValue//
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
      Moment().subtract(1, Units.Day)), handleSelection)

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