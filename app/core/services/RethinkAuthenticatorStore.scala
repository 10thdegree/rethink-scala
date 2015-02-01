package core.services

import com.rethinkscala.reflect.Reflector
import core.dataBrokers.{Connection, CoreBroker}
import core.models.{Authenticators, User}
import org.joda.time.DateTime
import securesocial.core.authenticator.{AuthenticatorStore, CookieAuthenticator}

import scala.concurrent.Future
import scala.reflect.ClassTag
import scalaz.Scalaz._

object RethinkAuthenticatorStore {

  class Default extends AuthenticatorStore[CookieAuthenticator[User]] {

    implicit val c = Connection.connection
    lazy val coreBroker: CoreBroker = new CoreBroker

    override def find(id: String)(implicit ct: ClassTag[CookieAuthenticator[User]]): Future[Option[CookieAuthenticator[User]]] = {
      import com.rethinkscala.Blocking._
      val auth = coreBroker.authenticatorsTable.filter(Map("authId" -> id))(0).toOpt
      val authGive = for {
        x     <- auth
        user  <- coreBroker.usersTable.get(x.user.id.get.toString).run.fold(err => None, u => u.some)
      } yield
        new CookieAuthenticator[User](x.authId,user,
                new DateTime(x.expirationDate),new DateTime(x.lastUsed),new DateTime(x.creationDate),this)

      Future.successful(authGive)
    }

    override def save(authenticator: CookieAuthenticator[User], timeoutInSeconds: Int): Future[CookieAuthenticator[User]] = {
      Future.successful {
        import com.rethinkscala.Blocking._
        val auth = Authenticators(authenticator.creationDate.toString,
          authenticator.expirationDate.toString,
          authenticator.lastUsed.toString,
          authenticator.user,
          true,
          timeoutInSeconds,
          authenticator.id
        )
        coreBroker.authenticatorsTable.filter(Map("authId" -> authenticator.id)).run match {
          case Right(x) => {
            if (x.size == 0)
              // TODO: Nesting still giving null
//              coreBroker.authenticatorsTable.insert(auth).run
              coreBroker.authenticatorsTable.insertMap(Seq(Reflector.toMap(auth))).run
            else {
              coreBroker.authenticatorsTable.filter(Map(
                "authId"-> x(0).authId
              )).update(
              // TODO: Need to update permissions in authentication token
//                Map("user"-> Reflector.toMap(authenticator.user))).run
                  Map("lastUsed" -> authenticator.lastUsed.toString())).run
            }
          }
          case Left(x) => coreBroker.authenticatorsTable.insert(auth).run
        }
        authenticator
      }
    }

    override def delete(id: String): Future[Unit] = {
      import com.rethinkscala.Blocking._
      coreBroker.authenticatorsTable.filter(Map("authId" -> id)).delete().run
      Future.successful((): Unit)
    }
  }
}
