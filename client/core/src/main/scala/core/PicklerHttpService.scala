package client.core

import biz.enef.angulate.core._
import prickle.{Pickle, Pickler, UnpickledCurry, Unpickler}

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.{UndefOr, JSON}
import scala.util.Try

object ConvertJsObject {
  def convert[T](promise: HttpPromise[js.Object])(implicit unpickler: Unpickler[T]): HttpPromise[T] = {
    promise.map((x: js.Object) => UnpickledCurry[T](unpickler).fromString(JSON.stringify(x)).get)
  }
}

trait PicklerHttpService extends js.Object {
  def get[T](url: String): HttpPromise[T] = js.native
  def get[T](url: String, config: HttpConfig): HttpPromise[T] = js.native
  def post[T](url: String): HttpPromise[T] = js.native
  def post[T](url: String, data: js.Any): HttpPromise[T] = js.native
  def post[T](url: String, data: js.Any, config: HttpConfig): HttpPromise[T] = js.native
  def jsonp[T](url: String, config: HttpConfig): HttpPromise[T] = js.native
  def put[T](url: String): HttpPromise[T] = js.native
  def put[T](url: String, data: js.Any): HttpPromise[T] = js.native
  def put[T](url: String, data: js.Any, config: HttpConfig): HttpPromise[T] = js.native
  def delete[T](url: String): HttpPromise[T] = js.native
  def delete[T](url: String, data: js.Any): HttpPromise[T] = js.native
  def delete[T](url: String, data: js.Any, config: HttpConfig): HttpPromise[T] = js.native
}

object PicklerHttpService {
  implicit class RichPicklerHttpService(val self: PicklerHttpService) extends AnyVal {
    def some: String = "test"
    def getObject[T](url: String)(implicit unpickler: Unpickler[T]): HttpPromise[T] =
        ConvertJsObject.convert[T](self.get(url))
  }
}