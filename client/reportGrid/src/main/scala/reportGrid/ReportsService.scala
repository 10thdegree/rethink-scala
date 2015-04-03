package reportGrid

import biz.enef.angulate.Service
import biz.enef.angulate.core.{HttpConfig, HttpPromise, HttpService}
import biz.enef.angulate.Scope

import scala.scalajs.js
import org.scalajs.dom.console

trait Column extends js.Object {
  def uuid: String
  def name: String
  def display: String
  def sort: String
  def format: String
  def footerType: String
}

object Column {
  def apply(uuid: String, name: String, display: String, sort: String, format: String, footerType: String) = {
    js.Dynamic.literal(
      uuid = uuid,
      name = name,
      display = display,
      sort = sort,
      format = format,
      footerType = footerType
    ).asInstanceOf[Column]
  }
}

trait CellValue extends js.Object {
  var display: String
  val `val`: js.Any
}

trait Row extends js.Object {
  val key: String
  val values: js.Dictionary[CellValue]
}

trait Chart extends js.Object {
  def `type`: String
  def label: String
  def domainLabel: String
  val domainField: js.Any
  val rangeField: js.Any
}

trait ReportView extends js.Object {
  def fields: js.Array[Column]
  def rows: js.Array[Row]
  def charts: js.Array[Chart]
}

class ReportsService($rootScope: Scope, $http: HttpService) extends Service {

  def getReport(viewId: String, start: String, end: String, callback: js.Function1[ReportView,_]) = {
    $rootScope.$broadcast("report.fetch.start")

    /* XXX: This is the websocket version.
    import org.scalajs.dom.{MessageEvent, Event}
    import org.scalajs.dom.raw.WebSocket
    import scala.scalajs.js.JSON
    val ws = new WebSocket("ws://localhost:9000/reporting/socket/reportDataRequest")
    ws.onopen = (e: Event) => {
      console.log(
        "report.fetch.start: Socket has been opened. " +
        "Will fetch report for " + start + " to " + end + "." +
        "Using view with id " + viewId)
      ws.send("")
    }
    ws.onmessage = (e: MessageEvent) => {
      $rootScope.$broadcast("report.fetch.end")
      console.log("report.fetch: data received")
      callback(JSON.parse(e.data.toString).asInstanceOf[ReportView])
    }
    */

    $http.get[ReportView]("/reporting/reportDataRequest", HttpConfig("viewId" -> viewId, "startDate" -> start, "endDate" -> end))
      .onSuccess((data: ReportView) => {
        $rootScope.$broadcast("report.fetch.end")
        console.log("report.fetch: data received")
        callback(data)
      })
      .onFailure(error => {
        console.log("Unable to fetch report!")
      })
  }

}
