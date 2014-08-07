package reporting.controllers

import play.api._
import play.api.mvc._

object Application extends Controller {

  def index = Action {
    Ok(reporting.views.html.index("reporting"))
  }

}