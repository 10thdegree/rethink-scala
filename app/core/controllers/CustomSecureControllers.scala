package core.controllers

import com.google.inject.Inject
import play.api.Play
import play.api.Play.current
import play.api.i18n.Messages
import play.api.libs.json.Json
import play.api.mvc._
import play.filters.csrf._
import securesocial.controllers.BaseRegistration._
import securesocial.controllers._
import securesocial.core._
import securesocial.core.authenticator.CookieAuthenticator
import securesocial.core.providers.UsernamePasswordProvider
import securesocial.core.services.{RoutesService, SaveMode}
import securesocial.core.utils._

import scala.concurrent.Future

class CustomLoginPage @Inject() (override implicit val env: RuntimeEnvironment[BasicProfile]) extends BaseLoginPage[BasicProfile] {
  /**
   * Renders the login page
   * @return
   */
  override def login =  CSRFAddToken {
    UserAwareAction { implicit request =>
      val to = ProviderControllerHelper.landingUrl
      if (request.user.isDefined) {
        // if the user is already logged in just redirect to the app
        Redirect(to)
      } else {
        if (SecureSocial.enableRefererAsOriginalUrl) {
          SecureSocial.withRefererAsOriginalUrl(Ok(env.viewTemplates.getLoginPage(UsernamePasswordProvider.loginForm)))
        } else {
          Ok(core.views.html.nonuser(core.views.html.login.head(), core.views.html.login.main(), "Login"))
        }
      }
    }
  }
}

class CustomRoutesService @Inject() extends RoutesService.Default {
  override def loginPageUrl(implicit req: RequestHeader): String =
    core.controllers.routes.CustomLoginPage.login().absoluteURL(IdentityProvider.sslEnabled)

  override def startSignUpUrl(implicit req: RequestHeader): String =
    core.controllers.routes.CustomRegistration.startSignUp().absoluteURL(IdentityProvider.sslEnabled)

  override def signUpUrl(token: String)(implicit req: RequestHeader): String =
    core.controllers.routes.CustomRegistration.signUp(token).absoluteURL(IdentityProvider.sslEnabled)

  override def handleSignUpUrl(mailToken: String)(implicit req: RequestHeader): String =
    core.controllers.routes.CustomRegistration.handleSignUp(mailToken).absoluteURL(IdentityProvider.sslEnabled)

  override def startResetPasswordUrl(implicit request: RequestHeader): String =
    core.controllers.routes.PasswordReset.startResetPassword().absoluteURL(IdentityProvider.sslEnabled)

  override def resetPasswordUrl(mailToken: String)(implicit req: RequestHeader): String =
    core.controllers.routes.PasswordReset.resetPassword(mailToken).absoluteURL(IdentityProvider.sslEnabled)

  override def handleStartResetPasswordUrl(implicit req: RequestHeader): String =
    core.controllers.routes.PasswordReset.handleStartResetPassword().absoluteURL(IdentityProvider.sslEnabled)

  override def handleResetPasswordUrl(mailToken: String)(implicit req: RequestHeader): String =
    core.controllers.routes.PasswordReset.handleResetPassword(mailToken).absoluteURL(IdentityProvider.sslEnabled)

  override def authenticationUrl(provider: String, redirectTo: Option[String] = None)(implicit req: RequestHeader): String =
    core.controllers.routes.CustomProviderController.authenticateByPost(redirectTo).absoluteURL(IdentityProvider.sslEnabled)
}

class CustomRegistration @Inject() (override implicit val env: RuntimeEnvironment[BasicProfile]) extends BaseRegistration[BasicProfile] {
  override def handleStartSignUp =  CSRFCheck {
    Action.async {
      implicit request =>
        startForm.bindFromRequest.fold(
          errors => {
            Future.successful(BadRequest(env.viewTemplates.getStartSignUpPage(errors)))
          },
          e => {
            val email = e.toLowerCase
            // check if there is already an account for this email address
            import scala.concurrent.ExecutionContext.Implicits.global
            env.userService.findByEmailAndProvider(email, UsernamePasswordProvider.UsernamePassword).map {
              maybeUser =>
                maybeUser match {
                  case Some(user) =>
                    // user signed up already, send an email offering to login/recover password
                    //env.mailer.sendAlreadyRegisteredEmail(user)
                  case None =>
                    import scala.concurrent.ExecutionContext.Implicits.global
                    createToken(email, isSignUp = true).flatMap { token =>
                      env.mailer.sendSignUpEmail(email, token.uuid)
                      env.userService.saveToken(token)

                    }
                }
                handleStartResult().flashing(Success -> Messages(ThankYouCheckEmail), Email -> email)
            }
          }
        )
    }
  }

  /**
   * Renders the sign up page
   * @return
   */
  override def signUp(token: String) = CSRFAddToken {
      Action.async {
      implicit request =>
        executeForToken(token, true, {
          _ =>
            Future.successful(Ok(env.viewTemplates.getSignUpPage(form, token)))
        })
    }
  }
}

/**
 * A default controller that uses the BasicProfile as the user type
 */
class CustomProviderController @Inject() (override implicit val env: RuntimeEnvironment[BasicProfile])
  extends BaseProviderController[BasicProfile]

/**
 * A trait that provides the means to authenticate users for web applications
 *
 * @tparam U the user type
 */
trait BaseProviderController[U] extends SecureSocial[U]
{
  import securesocial.controllers.ProviderControllerHelper.{logger, toUrl}

  /**
   * The authentication entry point for GET requests
   *
   * @param provider The id of the provider that needs to handle the call
   */
  def authenticate(provider: String, redirectTo: Option[String] = None) = handleAuth(provider, redirectTo)

  /**
   * The authentication entry point for POST requests
   *
   * @param provider The id of the provider that needs to handle the call
   */
  def authenticateByPost(provider: String, redirectTo: Option[String] = None) = handleAuth(provider, redirectTo)

  /**
   * Overrides the original url if neded
   *
   * @param session the current session
   * @param redirectTo the url that overrides the originalUrl
   * @return a session updated with the url
   */
  private def overrideOriginalUrl(session: Session, redirectTo: Option[String]) = redirectTo match {
    case Some(url) =>
      session + (SecureSocial.OriginalUrlKey -> url)
    case _ =>
      session
  }

  /**
   * Find the AuthenticatorBuilder needed to start the authenticated session
   */
  private def builder() = {
    //todo: this should be configurable maybe
    env.authenticatorService.find(CookieAuthenticator.Id).getOrElse {
      logger.error(s"[securesocial] missing CookieAuthenticatorBuilder")
      throw new AuthenticationException()
    }
  }

  /**
   * Common method to handle GET and POST authentication requests
   *
   * @param provider the provider that needs to handle the flow
   * @param redirectTo the url the user needs to be redirected to after being authenticated
   */
  private def handleAuth(provider: String, redirectTo: Option[String]) = UserAwareAction.async { implicit request =>
    import scala.concurrent.ExecutionContext.Implicits.global
    val authenticationFlow = request.user.isEmpty
    val modifiedSession = overrideOriginalUrl(request.session, redirectTo)

    env.providers.get(provider).map { _.authenticate().flatMap {
      case denied: AuthenticationResult.AccessDenied =>
        Future.successful(Redirect(env.routes.loginPageUrl).flashing("error" -> Messages("securesocial.login.accessDenied")))
      case failed: AuthenticationResult.Failed =>
        logger.error(s"[securesocial] authentication failed, reason: ${failed.error}")
        throw new AuthenticationException()
      case flow: AuthenticationResult.NavigationFlow => Future.successful {
        redirectTo.map { url =>
          flow.result.addToSession(SecureSocial.OriginalUrlKey -> url)
        } getOrElse flow.result
      }
      case authenticated: AuthenticationResult.Authenticated =>
        if ( authenticationFlow ) {
          val profile = authenticated.profile
          env.userService.find(profile.providerId, profile.userId).flatMap { maybeExisting =>
            val mode = if (maybeExisting.isDefined) SaveMode.LoggedIn else SaveMode.SignUp
            env.userService.save(authenticated.profile, mode).flatMap { userForAction =>
              logger.debug(s"[securesocial] user completed authentication: provider = ${profile.providerId}, userId: ${profile.userId}, mode = $mode")
              val evt = if (mode == SaveMode.LoggedIn) new LoginEvent(userForAction) else new SignUpEvent(userForAction)
              val sessionAfterEvents = Events.fire(evt).getOrElse(request.session)
              import scala.concurrent.ExecutionContext.Implicits.global
              builder().fromUser(userForAction).flatMap { authenticator =>
                Ok(Json.toJson(Map("redirect" -> toUrl(sessionAfterEvents)))).withSession(sessionAfterEvents -
                  SecureSocial.OriginalUrlKey -
                  IdentityProvider.SessionId -
                  OAuth1Provider.CacheKey).startingAuthenticator(authenticator)
              }
            }
          }
        } else {
          request.user match {
            case Some(currentUser) =>
              for (
                linked <- env.userService.link(currentUser,  authenticated.profile) ;
                updatedAuthenticator <- request.authenticator.get.updateUser(linked) ;
                result <- Redirect(toUrl(modifiedSession)).withSession(modifiedSession -
                  SecureSocial.OriginalUrlKey -
                  IdentityProvider.SessionId -
                  OAuth1Provider.CacheKey).touchingAuthenticator(updatedAuthenticator)
              ) yield {
                logger.debug(s"[securesocial] linked $currentUser to: providerId = ${authenticated.profile.providerId}")
                result
              }
            case _ =>
              Future.successful(Unauthorized)
          }
        }
    } recover {
      case e =>
        logger.error("Unable to log user in. An exception was thrown", e)
        Redirect(env.routes.loginPageUrl).flashing("error" -> Messages("securesocial.login.errorLoggingIn"))
    }
    } getOrElse {
      Future.successful(NotFound)
    }
  }
}

object ProviderControllerHelper {
  val logger = play.api.Logger("securesocial.controllers.ProviderController")

  /**
   * The property that specifies the page the user is redirected to if there is no original URL saved in
   * the session.
   */
  val onLoginGoTo = "securesocial.onLoginGoTo"

  /**
   * The root path
   */
  val Root = "/"

  /**
   * The application context
   */
  val ApplicationContext = "application.context"

  /**
   * The url where the user needs to be redirected after succesful authentication.
   *
   * @return
   */
  def landingUrl = Play.configuration.getString(onLoginGoTo).getOrElse(
    Play.configuration.getString(ApplicationContext).getOrElse(Root)
  )

  /**
   * Returns the url that the user should be redirected to after login
   *
   * @param session
   * @return
   */
  def toUrl(session: Session) = session.get(SecureSocial.OriginalUrlKey).getOrElse(ProviderControllerHelper.landingUrl)
}

class PasswordReset @Inject() (override implicit val env: RuntimeEnvironment[BasicProfile]) extends BasePasswordReset[BasicProfile]
class PasswordChange @Inject() (override implicit val env: RuntimeEnvironment[BasicProfile]) extends BasePasswordChange[BasicProfile]
