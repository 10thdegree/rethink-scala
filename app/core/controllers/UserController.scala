package core.controllers

import com.google.inject.Inject
import com.rethinkscala.reflect.Reflector
import core.dataBrokers.{Connection, CoreBroker}
import core.models._
import play.api.libs.{json => pjson}
import play.api.mvc.Action
import securesocial.core.RuntimeEnvironment

class UserController @Inject() (override implicit val env: RuntimeEnvironment[User]) extends securesocial.core.SecureSocial[User]  {


  implicit val c = Connection.connection
  lazy val coreBroker: CoreBroker = new CoreBroker

  def getUsers =  Action {
    import com.rethinkscala.Blocking._
    coreBroker.usersTable.run match {
      case Right(tx) => Ok(pjson.Json.obj("status" -> "OK", "users"->pjson.Json.parse(Reflector.toJson(tx))))
      case Left(er) => BadRequest(pjson.Json.toJson(Map("error"->er.getMessage)))
    }
  }

  def getInvitedUsers =  Action {
    import com.rethinkscala.Blocking._
    coreBroker.tokensTable.filter(Map("isSignUp"->true)).run match {
      case Right(tx) => Ok(pjson.Json.obj("status" -> "OK", "invited"->pjson.Json.parse(Reflector.toJson(tx))))
      case Left(er) => BadRequest(pjson.Json.toJson(Map("error"->er.getMessage)))
    }
  }

  def deleteUser(userId: String) = Action {
    import com.rethinkscala.Blocking._
    coreBroker.usersTable.get(userId).delete().run match {
      case Right(_) => Ok(pjson.Json.obj("status" -> "OK", "message" -> s"$userId deleted."))
      case Left(x) => Ok(pjson.Json.obj("status" -> "error", "permissions" -> x.getMessage))
    }
  }

  def deleteInvite(inviteId: String) = Action {
    import com.rethinkscala.Blocking._
    coreBroker.tokensTable.get(inviteId).delete().run match {
      case Right(_) => Ok(pjson.Json.obj("status" -> "OK", "message" -> s"$inviteId deleted."))
      case Left(x) => Ok(pjson.Json.obj("status" -> "error", "permissions" -> x.getMessage))
    }
  }
}
