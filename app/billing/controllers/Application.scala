package billing.controllers

import play.api.mvc._

object Application extends Controller {

  def index = Action {
    Ok(billing.views.html.index("billing"))
  }

}