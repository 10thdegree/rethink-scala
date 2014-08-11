package billing.controllers

import core.models.User
import securesocial.core.RuntimeEnvironment

class Application(override implicit val env: RuntimeEnvironment[User]) extends securesocial.core.SecureSocial[User] {

  def index = SecuredAction {
    Ok(billing.views.html.index("billing"))
  }

}