package core.controllers

import core.models.User
import play.api.mvc.Action
import securesocial.core.RuntimeEnvironment

class CoreApplication(override implicit val env: RuntimeEnvironment[User]) extends securesocial.core.SecureSocial[User] {

  def index = SecuredAction {
    Ok(core.views.html.index("core"))
  }

  def userManagement = Action {
    Ok(core.views.html.manage())
  }
}