package core.services

import core.dataBrokers.{Connection, CoreBroker}
import core.models.{Authenticators, User}
import org.joda.time.DateTime
import securesocial.core.authenticator.{AuthenticatorStore, CookieAuthenticator}

import scala.concurrent.Future
import scala.reflect.ClassTag

object RethinkAuthenticatorStore {

  class Default extends AuthenticatorStore[CookieAuthenticator[User]] {

    implicit val c = Connection.connection
    lazy val coreBroker: CoreBroker = new CoreBroker

    override def find(id: String)(implicit ct: ClassTag[CookieAuthenticator[User]]): Future[Option[CookieAuthenticator[User]]] = {
      import com.rethinkscala.Blocking._
      val auth = coreBroker.authenticatorsTable.filter(Map("authId" -> id)).run match {
        case Right(x) => if(x.size == 0) None else Some(x(0))
        case Left(x) => None
      }
      val authGive = auth match {
        case Some(x) => Some(new CookieAuthenticator[User](x.authId,x.user,
          new DateTime(x.expirationDate),new DateTime(x.lastUsed),new DateTime(x.creationDate),this))
        case None => None
      }
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
              coreBroker.authenticatorsTable.insert(auth).run
            else {
              coreBroker.authenticatorsTable.filter(Map("authId"->x(0).authId)).update(
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