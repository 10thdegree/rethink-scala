package core.controllers

import com.google.inject.Inject
import core.models.User
import play.api.mvc.Action
import securesocial.core.RuntimeEnvironment

class CoreApplication @Inject() (override implicit val env: RuntimeEnvironment[User]) extends securesocial.core.SecureSocial[User] {

  def index = SecuredAction {
    Ok(core.views.html.index("core"))
  }

  def userManagement = Action {
    Ok(core.views.html.manage())
  }
}