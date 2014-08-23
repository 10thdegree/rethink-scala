package billing.controllers

import com.google.inject.Inject
import core.models.User
import securesocial.core.RuntimeEnvironment

class Application @Inject() (override implicit val env: RuntimeEnvironment[User]) extends securesocial.core.SecureSocial[User] {

  def index = SecuredAction {
    Ok(billing.views.html.index("billing"))
  }

}