package core.controllers

import com.rethinkscala.reflect.Reflector
import core.dataBrokers.{Connection, CoreBroker}
import core.models._
import play.api.libs.{json => pjson}
import play.api.mvc.{Action, BodyParsers}
import securesocial.core.RuntimeEnvironment

class PermissionsController(override implicit val env: RuntimeEnvironment[User]) extends securesocial.core.SecureSocial[User]  {
  implicit val accountPermissionsFormat = pjson.Json.format[AccountPermissions]
  implicit val permissionsFormat = pjson.Json.format[PermissionIds]
  implicit val permissionFormat = pjson.Json.format[Permission]
  implicit val labelFormat = pjson.Json.format[Label]

  case class Label(label: String)

  implicit val c = Connection.connection
  lazy val coreBroker: CoreBroker = new CoreBroker

  def getReadOnlyPermissions =  Action {
    import com.rethinkscala.Blocking._
    coreBroker.permissionsTable.filter(Map("readonly"->true)).run match {
      case Right(tx) => Ok(pjson.Json.obj("status" -> "OK", "permissions"->pjson.Json.parse(Reflector.toJson(tx))))
      case Left(er) => BadRequest(pjson.Json.toJson(Map("error"->er.getMessage)))
    }
  }

  def getWriteablePermissions =  Action {
    import com.rethinkscala.Blocking._
    coreBroker.permissionsTable.filter(Map("readonly"->false)).run match {
      case Right(tx) => Ok(pjson.Json.obj("status" -> "OK", "permissions"->pjson.Json.parse(Reflector.toJson(tx))))
      case Left(er) => BadRequest(pjson.Json.toJson(Map("error"->er.getMessage)))
    }
  }

  def getPermissions =  Action {
    import com.rethinkscala.Blocking._
    coreBroker.permissionsTable.run match {
      case Right(tx) => Ok(pjson.Json.obj("status" -> "OK", "permissions"->pjson.Json.parse(Reflector.toJson(tx))))
      case Left(er) => BadRequest(pjson.Json.toJson(Map("error"->er.getMessage)))
    }
  }

  def addPermission = Action(BodyParsers.parse.json) {
    request =>
      request.body.validate[Permission].fold(
        errors => {
          BadRequest(pjson.Json.obj("status" -> "OK", "message" -> pjson.JsError.toFlatJson(errors)))
        },
        permission => {
          import com.rethinkscala.Blocking._
          coreBroker.permissionsTable.insert(permission).run match {
            case Right(x) => Ok(pjson.Json.obj("status" -> "OK", "message" -> "Permission created."))
            case Left(x) =>  BadRequest(pjson.Json.obj("status" -> "OK", "message" -> x.getMessage))
          }
        }
      )
  }

  def renamePermission(permissionId: String) = Action(BodyParsers.parse.json) {
    request =>
      request.body.validate[Label].fold(
        errors => {
          BadRequest(pjson.Json.obj("status" -> "OK", "message" -> pjson.JsError.toFlatJson(errors)))
        },
        account => {
          import com.rethinkscala.Blocking._
          coreBroker.permissionsTable.get(permissionId).update(
            Map("label" -> account.label)
          ).run
          Ok(pjson.Json.obj("status" -> "OK", "message" -> s"$permissionId renamed."))
        }
      )
  }

  def deletePermission(permissionId: String) = Action {
    import com.rethinkscala.Blocking._
    coreBroker.permissionsTable.get(permissionId).delete().run match {
      case Right(_) => Ok(pjson.Json.obj("status" -> "OK", "message" -> s"$permissionId deleted."))
      case Left(x) => Ok(pjson.Json.obj("status" -> "error", "permissions" -> x.getMessage))
    }
  }

  def addUserPermission(userId: String) = Action(BodyParsers.parse.json) {
    request =>
      request.body.validate[AccountPermissions].fold(
        errors => {
          BadRequest(pjson.Json.obj("status" -> "OK", "message" -> pjson.JsError.toFlatJson(errors)))
        },
        accountPermission => {
          import com.rethinkscala.Blocking._
          coreBroker.usersTable.get(userId).run match {
            case Right(x) => {
              val existingAccountPermissions = x.permissions
              val accountIndex = existingAccountPermissions.indexWhere( i => i.accountId == accountPermission.accountId)
              val updatedList = accountIndex match {
                case -1 => accountPermission :: existingAccountPermissions
                case x => existingAccountPermissions.patch(x, {
                  val existingPermissions = existingAccountPermissions(x)
                  List(AccountPermissions(existingPermissions.accountId, (existingPermissions.permissionIds ::: accountPermission.permissionIds).distinct))
                }, 1)
              }

              coreBroker.usersTable.get(userId).update(
                Map("permissions" -> updatedList.map(Reflector.toMap(_)))
              ).run
              Ok(pjson.Json.obj("status" -> "OK", "permissions" -> pjson.Json.parse(Reflector.toJson(updatedList))))
            }
            case Left(x) =>  BadRequest(pjson.Json.obj("status" -> "OK", "message" -> x.getMessage))
          }
        }
      )
  }

  def deleteUserPermission(userId: String) = Action(BodyParsers.parse.json) {
    request =>
      request.body.validate[AccountPermissions].fold(
        errors => {
          BadRequest(pjson.Json.obj("status" -> "OK", "message" -> pjson.JsError.toFlatJson(errors)))
        },
        accountPermission => {
          import com.rethinkscala.Blocking._
          coreBroker.usersTable.get(userId).run match {
            case Right(x) => {
              val existingAccountPermissions = x.permissions
              val accountIndex = existingAccountPermissions.indexWhere( i => i.accountId == accountPermission.accountId)
              accountIndex match {
                case -1 => Ok(pjson.Json.obj("status" -> "OK", "permissions" -> pjson.Json.parse(Reflector.toJson(existingAccountPermissions))))
                case x => {
                  val updatedList = existingAccountPermissions.patch(x, {
                    val existingPermissions = existingAccountPermissions(x)
                    List(AccountPermissions(existingPermissions.accountId, (existingPermissions.permissionIds.diff(accountPermission.permissionIds))))
                  }, 1)
                  coreBroker.usersTable.get(userId).update(
                    Map("permissions" -> updatedList.map(Reflector.toMap(_)))
                  ).run
                  Ok(pjson.Json.obj("status" -> "OK", "permissions" -> pjson.Json.parse(Reflector.toJson(updatedList))))
                }
              }
            }
            case Left(x) =>  BadRequest(pjson.Json.obj("status" -> "OK", "message" -> x.getMessage))
          }
        }
      )
  }

  def addAccountPermission(accountId: String) = Action(BodyParsers.parse.json) {
    request =>
      request.body.validate[PermissionIds].fold(
        errors => {
          BadRequest(pjson.Json.obj("status" -> "OK", "message" -> pjson.JsError.toFlatJson(errors)))
        },
        addPermissions => {
          import com.rethinkscala.Blocking._
          coreBroker.accountsTable.get(accountId).run match {
            case Right(x) => {
              val existingPermissions = x.permissions
              val updatedPermissions = (existingPermissions ::: addPermissions.permissionIds).distinct
              coreBroker.accountsTable.get(accountId).update(
                Map("permissions" -> updatedPermissions.map(_.toString))
              ).run
              Ok(pjson.Json.obj("status" -> "OK", "permissions" -> pjson.Json.parse(Reflector.toJson(updatedPermissions))))
            }
            case Left(x) => BadRequest(pjson.Json.obj("status" -> "OK", "message" -> x.getMessage))
          }
        }
      )
  }

  def deleteAccountPermission(accountId: String) = Action(BodyParsers.parse.json) {
    request =>
      request.body.validate[PermissionIds].fold(
        errors => {
          println(errors);
          BadRequest(pjson.Json.obj("status" -> "OK", "message" -> pjson.JsError.toFlatJson(errors)))
        },
        deletePermissions => {
          import com.rethinkscala.Blocking._
          coreBroker.accountsTable.get(accountId).run match {
            case Right(x) => {
              val existingPermissions = x.permissions
              val updatedPermissions = existingPermissions.diff(deletePermissions.permissionIds)
              coreBroker.accountsTable.get(accountId).update(
                Map("permissions" -> updatedPermissions.map(_.toString))
              ).run
              Ok(pjson.Json.obj("status" -> "OK", "permissions" -> pjson.Json.parse(Reflector.toJson(updatedPermissions))))
            }
            case Left(x) =>   println(x);BadRequest(pjson.Json.obj("status" -> "OK", "message" -> x.getMessage))
          }
        }
      )
  }
}
