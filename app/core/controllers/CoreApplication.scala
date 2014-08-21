package core.controllers

import java.util.UUID

import com.google.inject.Inject
import core.models.User
import play.api.mvc.Action
import play.twirl.api.Html
import securesocial.core.RuntimeEnvironment

class CoreApplication @Inject() (override implicit val env: RuntimeEnvironment[User]) extends securesocial.core.SecureSocial[User] {

  def index = SecuredAction { implicit request =>
    val userName = request.user.permissions
    Ok(core.views.html.main(Html.apply(""), Html.apply("Permissoins Test: %s".format(userName)), UUID.randomUUID().toString, true))
  }

  def userManagement = Action {
    Ok(core.views.html.main(core.views.html.manage.head(), core.views.html.manage.main(), "Management"))
  }
}