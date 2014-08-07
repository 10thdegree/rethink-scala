package core.controllers

import play.api.mvc._

object Application extends Controller {

  def index = Action {
    Ok(core.views.html.index("core"))
  }

}