import java.lang.reflect.Constructor

import com.google.inject.{AbstractModule, Guice}
import core.dataBrokers.Setup
import core.models.User
import core.services.{RethinkAuthenticatorStore, RethinkUserService}
import net.codingwell.scalaguice.ScalaModule
import play.api.GlobalSettings
import securesocial.core.RuntimeEnvironment
import securesocial.core.authenticator.{AuthenticatorStore, CookieAuthenticatorBuilder, HttpHeaderAuthenticatorBuilder}
import securesocial.core.services.AuthenticatorService

/**
 * Set up the Guice injector and provide the mechanism for return objects from the dependency graph.
 */
object Global extends GlobalSettings {

  val injector = Guice.createInjector(new AbstractModule with ScalaModule {
    protected def configure() {
      //bind[core.controllers.AccountController].in[javax.inject.Singleton]
      bind[RuntimeEnvironment[User]].toInstance(MyRuntimeEnvironment)
    }
  })

  override def onStart(app: play.api.Application) {
    Setup.initial
  }

  /**
   * An implementation that checks if the controller expects a RuntimeEnvironment and
   * passes the instance to it if required.
   *
   * @param controllerClass
   * @tparam A
   * @return
   */
  override def getControllerInstance[A](controllerClass: Class[A]): A = {
    injector.getInstance(controllerClass)
  }

  object MyRuntimeEnvironment extends RuntimeEnvironment.Default[User] {

    override lazy val userService: RethinkUserService = new RethinkUserService()

    override lazy val authenticatorService = new AuthenticatorService(
      new CookieAuthenticatorBuilder[User](new RethinkAuthenticatorStore.Default, idGenerator),
      new HttpHeaderAuthenticatorBuilder[User](new AuthenticatorStore.Default(cacheService), idGenerator)
    )

  }
}
