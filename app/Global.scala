import com.google.inject.{AbstractModule, Guice}
import core.controllers.CustomRoutesService
import core.dataBrokers.Setup
import core.models.User
import core.services.{RethinkAuthenticatorStore, RethinkUserService}
import net.codingwell.scalaguice.ScalaModule
import play.api.GlobalSettings
import securesocial.core.authenticator.{AuthenticatorStore, CookieAuthenticatorBuilder, HttpHeaderAuthenticatorBuilder}
import securesocial.core.services.AuthenticatorService
import securesocial.core.{BasicProfile, RuntimeEnvironment}
import reporting.core.ReportingRuntime

/**
 * Set up the Guice injector and provide the mechanism for return objects from the dependency graph.
 */
object Global extends GlobalSettings {

  lazy val injector = Guice.createInjector(new AbstractModule with ScalaModule {
    protected def configure() {
      //bind[core.controllers.AccountController].in[javax.inject.Singleton]
      bind[RuntimeEnvironment[User]].toInstance(MyRuntimeEnvironment)
      bind[RuntimeEnvironment[BasicProfile]].toInstance(MyRuntimeEnvironment.asInstanceOf[RuntimeEnvironment[BasicProfile]])
      bind[ReportingRuntime].toInstance(MyReportingRuntime)

      try {
        /*import scala.reflect.runtime.universe
        for (
          t <- universe.runtimeMirror(getClass.getClassLoader)
            .staticPackage("securesocial.controllers")
            .typeSignature.decls
          if t.isClass
        ) {
          val cls = t.asClass.getClass
          bind(cls).toConstructor(cls.getConstructor(classOf[RuntimeEnvironment[User]]))
        }*/

        bind[securesocial.controllers.LoginPage]
          .toConstructor(classOf[securesocial.controllers.LoginPage].getConstructor(classOf[RuntimeEnvironment[BasicProfile]]))
      } catch {
        case e: NoSuchMethodException => addError(e)
      }
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

    override lazy val routes = new CustomRoutesService()

    override lazy val userService: RethinkUserService = new RethinkUserService()

    override lazy val authenticatorService = new AuthenticatorService(
      new CookieAuthenticatorBuilder[User](new RethinkAuthenticatorStore.Default, idGenerator),
      new HttpHeaderAuthenticatorBuilder[User](new AuthenticatorStore.Default(cacheService), idGenerator)
    )

  }

  object MyReportingRuntime extends ReportingRuntime.Default

}
