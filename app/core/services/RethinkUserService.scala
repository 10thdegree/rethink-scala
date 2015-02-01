package core.services

import com.rethinkscala.reflect.Reflector
import core.dataBrokers.{Connection, CoreBroker}
import core.models.{MailTokens, User}
import securesocial.core.providers.MailToken
import securesocial.core.services.{SaveMode, UserService}
import securesocial.core.{BasicProfile, PasswordInfo}

import scala.concurrent.Future

class RethinkUserService extends UserService[User] {

  implicit val c = Connection.connection
  lazy val coreBroker: CoreBroker = new CoreBroker

  def find(providerId: String, userId: String): Future[Option[BasicProfile]] = {
    import com.rethinkscala.Blocking._

    val result = coreBroker.usersTable.filter(Map("main" -> Map("userId" -> userId, "providerId" -> providerId))).run match {
      case Right(x) => x match {
        case Nil => None
        case f: Seq[User] => Some(f(0).main)
        case _ => None;
      }
      case Left(x) => None
    }
    Future.successful(result)
  }

  def findByEmailAndProvider(email: String, providerId: String): Future[Option[BasicProfile]] = {
    import com.rethinkscala.Blocking._

    val result = coreBroker.usersTable.filter(Map("main" -> Map("email" -> email, "providerId" -> providerId))).run match {
      case Right(x) => x match {
        case f: Seq[User] => if(f.size == 0) None else Some(f(0).main)
        case _ => None;
      }
      case Left(x) => None
    }
    Future.successful(result)
  }

  def save(user: BasicProfile, mode: SaveMode): Future[User] = {
    import com.rethinkscala.Blocking._

    mode match {
      case SaveMode.SignUp | SaveMode.LoggedIn => {

        val maybeUser = coreBroker.usersTable
          .filter(Map("main" -> Map("userId" -> user.userId, "providerId" -> user.providerId)))
          .run match {
          case Right(tx) => tx match {
            case f: Seq[User] => if(f.size == 0) None else Some(f(0))
            case _ => None;
          }
          case Left(er) => None
        }

        maybeUser match {
          case Some(existingUser) =>
            Future.successful(existingUser)
          case None =>
            val newUser = User(user, Nil, Nil, None)
//            coreBroker.usersTable.insert(newUser).run
            coreBroker.usersTable.insertMap(Seq(Reflector.toMap(newUser))).run
            coreBroker.tokensTable.filter(Map("email"->newUser.main.email)).delete().run
            Future.successful(newUser)
        }
      }
      case SaveMode.PasswordChange => {
        val updateUser = coreBroker.usersTable
          .filter(Map("main" -> Map("userId" -> user.userId, "providerId" -> user.providerId)))
          .run match {
          case Right(tx) => tx match {
            case f: Seq[User] => if (f.size == 0) None else {
              val updated = f(0).main.copy(passwordInfo = user.passwordInfo)
              coreBroker.usersTable.get(f(0).id.get.toString).update(Map("main" -> Reflector.toMap(updated))).run
              Some(f(0).copy(main = updated))
            }
            case _ => None;
          }
          case Left(er) => None
        }
        updateUser match {
          case Some(existingUser) =>
            Future.successful(existingUser)
          case None =>
            val newUser = User(user, Nil, Nil, None)
//            coreBroker.usersTable.insert(newUser).run
            coreBroker.usersTable.insertMap(Seq(Reflector.toMap(newUser))).run
            Future.successful(newUser)
        }
      }
    }
  }

  def saveToken(token: MailToken): Future[MailToken] =
    Future.successful {
      import com.rethinkscala.Blocking._
      coreBroker.tokensTable.insert(MailTokens(token.email, token.creationTime.toString, token.expirationTime.toString(), token.isSignUp, Some(token.uuid))).run
      token
    }

  def findToken(token: String): Future[Option[MailToken]] =
    Future.successful {
      import com.rethinkscala.Blocking._
      coreBroker.tokensTable.get(token).run match {
        case Right(x) => Some(x.toMailToken)
        case Left(x) => None
      }
    }

  def deleteToken(uuid: String): Future[Option[MailToken]] =
    Future.successful {
      import com.rethinkscala.Blocking._
      coreBroker.tokensTable.get(uuid).delete().run match {
        case _ => None
      }
    }

  def deleteExpiredTokens() = {
    import com.rethinkscala.Blocking._
    coreBroker.tokensTable.filter((e: Var) => e \ "expirationTime" < r.now).delete().run
  }

  override def updatePasswordInfo(user: User, info: PasswordInfo): Future[Option[BasicProfile]] =
    Future.successful {
      import com.rethinkscala.Blocking._
      coreBroker.usersTable.get(user.id.get.toString).run match {
        case Right(user) => {
          val updated = user.main.copy(passwordInfo = Some(info))
          coreBroker.usersTable.get(user.id.get.toString).update(Map("main" -> updated)).run
          Some(updated)
        }
        case Left(x) => None
      }
    }

  override def passwordInfoFor(user: User): Future[Option[PasswordInfo]] =
    Future.successful {
      import com.rethinkscala.Blocking._
      coreBroker.usersTable.get(user.id.get.toString).run match {
        case Right(user) => user.main.passwordInfo
        case Left(x) => None
      }
    }

  override def link(current: User, to: BasicProfile): Future[User] = {
    //No need to implement with main only
    Future.successful(current)
  }
}