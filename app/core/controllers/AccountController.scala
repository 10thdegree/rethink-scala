package core.controllers

import java.util.UUID
import com.google.inject.Inject
import com.rethinkscala.reflect.Reflector
import core.dataBrokers.{Connection, CoreBroker}
import core.models._
import play.api.libs.{json => pjson}
import play.api.mvc.{Action, BodyParsers}
import securesocial.core.RuntimeEnvironment
import shared.{LastAccount,AccountsResponse}
import prickle._

class AccountController @Inject()(override implicit val env: RuntimeEnvironment[User]) extends securesocial.core.SecureSocial[User] {
  implicit val accountFormat = pjson.Json.format[Account]
  implicit val labelFormat = pjson.Json.format[Label]
  implicit val userIdFormat = pjson.Json.format[UserIds]

  implicit val lastAccountPickler: Pickler[LastAccount] = Pickler.materializePickler[LastAccount]
  implicit val accountsResponsePickler: Pickler[AccountsResponse] = Pickler.materializePickler[AccountsResponse]

  case class Label(label: String)

  case class UserIds(userIds: List[UUID])

  implicit val c = Connection.connection
  lazy val coreBroker: CoreBroker = new CoreBroker

  def getLastSelectedAccount = UserAwareAction { implicit request =>
    request.user match {
      case Some(user) => Ok(Pickle.intoString(LastAccount("Ok", user.lastSelectedAccount.get.toString)))
      case _ => Ok(pjson.Json.obj("status" -> "OK", "lastSelectedAccount" -> ""))
    }
  }

  def setLastSelectedAccount(accountId: String) = UserAwareAction { implicit request =>
    request.user match {
      case Some(user) => {
        import com.rethinkscala.Blocking._
        coreBroker.usersTable.get(user.id.get.toString).update(
          Map("lastSelectedAccount" -> accountId)
        ).run
      }
      case _ => None
    }
    Ok(pjson.Json.obj("status" -> "OK", "lastSelectedAccount" -> "complete"))
  }


  def getAvailableAccounts = UserAwareAction { implicit request =>
    val userPermissions = request.user match {
      case Some(user) => user.permissions.filterNot(x => x.permissionIds.isEmpty).map(x => x.accountId.toString)
      case _ => Nil
    }
    import com.rethinkscala.Blocking._
    if (userPermissions.isEmpty) {
      Ok(Pickle.intoString(AccountsResponse("Ok", Nil)))
    } else {
      coreBroker.accountsTable.getAll(userPermissions: _*).run match {
        case Right(tx) => {
          Ok(Pickle.intoString(AccountsResponse("Ok", tx.map((x: Account) => shared.Account(x.id.get.toString, x.label)))))
        }
        case Left(er) => BadRequest(pjson.Json.toJson(Map("error" -> er.getMessage)))
      }
    }
  }

  def getAccount(accountId: String) = Action {
    import com.rethinkscala.Blocking._
    coreBroker.accountsTable.get(accountId).run match {
      case Right(tx) => Ok(pjson.Json.obj("status" -> "OK", "account" -> pjson.Json.parse(Reflector.toJson(tx))))
      case Left(er) => BadRequest(pjson.Json.toJson(Map("error" -> er.getMessage)))
    }
  }

  def getAccounts = Action {
    import com.rethinkscala.Blocking._
    coreBroker.accountsTable.run match {
      case Right(tx) => Ok(pjson.Json.obj("status" -> "OK", "accounts" -> pjson.Json.parse(Reflector.toJson(tx))))
      case Left(er) => BadRequest(pjson.Json.toJson(Map("error" -> er.getMessage)))
    }
  }

  def getAccountUsers(accountId: String) = Action {
    import com.rethinkscala.Blocking._
    coreBroker.usersTable.filter(f => (f \ "permissions").contains(i => (i \ "accountId" === accountId))).run match {
      case Right(tx) => {
        val userPermissions = tx.map(x =>
          Map("id" -> x.id, "fullName" -> x.main.fullName, "email" -> x.main.email,
            "permissions" -> (x.permissions collect { case (i: AccountPermissions) if i.accountId.toString == accountId => i.permissionIds}).flatten)
        )
        Ok(pjson.Json.obj("status" -> "OK", "users" -> pjson.Json.parse(Reflector.toJson(userPermissions))))
      }
      case Left(er) => BadRequest(pjson.Json.toJson(Map("error" -> er.getMessage)))
    }
  }

  def addAccount = Action(BodyParsers.parse.json) {
    request =>
      request.body.validate[Account].fold(
        errors => {
          BadRequest(pjson.Json.obj("status" -> "OK", "message" -> pjson.JsError.toFlatJson(errors)))
        },
        account => {
          import com.rethinkscala.Blocking._
          coreBroker.accountsTable.insert(account).run match {
            case Right(x) => Ok(pjson.Json.obj("status" -> "OK", "message" -> "Account created."))
            case Left(x) => BadRequest(pjson.Json.obj("status" -> "OK", "message" -> x.getMessage))
          }
        }
      )
  }

  def renameAccount(accountId: String) = Action(BodyParsers.parse.json) {
    request =>
      request.body.validate[Label].fold(
        errors => {
          BadRequest(pjson.Json.obj("status" -> "OK", "message" -> pjson.JsError.toFlatJson(errors)))
        },
        account => {
          import com.rethinkscala.Blocking._
          coreBroker.accountsTable.get(accountId).update(
            Map("label" -> account.label)
          ).run
          Ok(pjson.Json.obj("status" -> "OK", "message" -> s"$accountId renamed."))
        }
      )
  }

  def deleteAccount(accountId: String) = Action {
    import com.rethinkscala.Blocking._
    coreBroker.accountsTable.get(accountId).delete().run match {
      case Right(_) => Ok(pjson.Json.obj("status" -> "OK", "message" -> s"$accountId deleted."))
      case Left(x) => Ok(pjson.Json.obj("status" -> "error", "permissions" -> x.getMessage))
    }
  }

  def deleteAccountUsers(accountId: String) = Action(BodyParsers.parse.json) {
    request =>
      request.body.validate[UserIds].fold(
        errors => {
          BadRequest(pjson.Json.obj("status" -> "OK", "message" -> pjson.JsError.toFlatJson(errors)))
        },
        deleteUsers => {
          import com.rethinkscala.Blocking._
          deleteUsers.userIds.foreach(userId =>
            coreBroker.usersTable.get(userId.toString).run match {
              case Right(x) => {
                val existingAccountPermissions = x.permissions
                val accountIndex = existingAccountPermissions.indexWhere(i => i.accountId.toString == accountId)
                accountIndex match {
                  case -1 => Ok(pjson.Json.obj("status" -> "OK", "message" -> "User was not in the account"))
                  case y => {
                    val updatedList = existingAccountPermissions.zipWithIndex.filter(_._2 != y).map(_._1)
                    coreBroker.usersTable.get(userId.toString).update(
                      Map("permissions" -> updatedList.map(Reflector.toMap(_)))
                    ).run
                  }
                }
              }
              case Left(x) => BadRequest(pjson.Json.obj("status" -> "OK", "message" -> x.getMessage))
            }
          )
          Ok(pjson.Json.obj("status" -> "OK", "message" -> "All users removed from account."))
        }
      )
  }

  def addAccountUsers(accountId: String) = Action(BodyParsers.parse.json) {
    request =>
      request.body.validate[UserIds].fold(
        errors => {
          BadRequest(pjson.Json.obj("status" -> "OK", "message" -> pjson.JsError.toFlatJson(errors)))
        },
        addUsers => {
          import com.rethinkscala.Blocking._
          addUsers.userIds.foreach(userId =>
            coreBroker.usersTable.get(userId.toString).run match {
              case Right(x) => {
                val existingUserPermissions = x.permissions
                val accountIndex = existingUserPermissions.indexWhere(i => i.accountId.toString == accountId)

                accountIndex match {
                  case -1 => {
                    val updatedList = existingUserPermissions ++ List(new AccountPermissions(UUID.fromString(accountId), List()))
                    coreBroker.usersTable.get(userId.toString).update(
                      Map("permissions" -> updatedList.map(Reflector.toMap(_)))
                    ).run
                  }
                  case y => {}
                }
              }
              case Left(x) => BadRequest(pjson.Json.obj("status" -> "OK", "message" -> x.getMessage))
            }
          )
          Ok(pjson.Json.obj("status" -> "OK", "message" -> "All users added to account."))
        }
      )
  }
}
