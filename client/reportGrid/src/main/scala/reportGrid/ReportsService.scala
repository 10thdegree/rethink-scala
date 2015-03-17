package reportGrid

import biz.enef.angulate.Service
import biz.enef.angulate.core.{HttpPromise, HttpService}
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

trait CellValue extends js.Object {
  def display: String
  def `val`: String
}

trait Row extends js.Object {
  def values: js.Array[CellValue]
}

trait Chart extends js.Object {
  def `type`: String
  def label: String
  def domainLabel: String
}

trait ReportView extends js.Object {
  def columns: js.Array[Column]
  def rows: js.Array[Row]
  def charts: js.Array[Chart]
}

class ReportsService($rootScope: Scope, $http: HttpService) extends Service {

  def getReport(viewId: Int, start: String, end: String, callback: ReportView => ()) = {
    $rootScope.$broadcast("report.fetch.start")

    /*
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

    $http.get[ReportView]("/reporting/reportDataRequest")
      .onSuccess(data => {
        $rootScope.$broadcast("report.fetch.end")
        console.log("report.fetch: data received")
        callback(data)
      })
      .onFailure(error => {
        console.log("Unable to fetch report!")
      })
  }

}
