package reportGrid

import biz.enef.angulate.Controller

import scala.scalajs.js
import scala.util.{Failure, Success}

class ReportCtrl(loginService: LoginService, $window: js.Dynamic) extends Controller {
  var alerts = js.Array[Alert]()
  var email = ""
  var password = ""

  def login(): Unit = {
    if (email == "")
      addAlert(Alert("danger", "Please enter email."))
    else if (password == "")
      addAlert(Alert("danger", "Please enter password."))
    else {
      loginService.authenticate(Credential(email, password)) onComplete {
        case Success(auth) => $window.location = auth.redirect
        case Failure(ex) => {
          addAlert(Alert("danger", "Sorry, you are unable to login."))
          handleError(ex)
        }
      }
    }
  }

  private def addAlert(alert: Alert): Unit = {
    alerts :+ alert
  }

  def closeAlert(index: Int): Unit = {
    alerts.splice(index, 1)
    alerts = alerts.zipWithIndex.filter(_._2 != index).map(_._1)
  }

  private def handleError(ex: Throwable): Unit = js.Dynamic.global.console.error(s"An error has occurred: $ex")

}
