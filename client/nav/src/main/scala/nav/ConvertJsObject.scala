package nav

import biz.enef.angulate.core.{HttpPromise, HttpService}
import prickle.{UnpickledCurry, Unpickler}

import scala.scalajs.js
import scala.scalajs.js.JSON

object ConvertJsObject {
  def convert[T](promise: HttpPromise[js.Object])(implicit unpickler: Unpickler[T], http: HttpService) : HttpPromise[T] = {
    promise.map((x: js.Object) => UnpickledCurry[T](unpickler).fromString(JSON.stringify(x)).get)
  }
}

