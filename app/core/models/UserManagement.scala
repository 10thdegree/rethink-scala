package core.models

import java.util.UUID

import com.rethinkscala.Document
import org.joda.time.DateTime
import securesocial.core.BasicProfile
import securesocial.core.providers.MailToken
import reporting.models.ds.DSAccountCfg

case class Permission(label: String, readonly: Boolean = false, id: Option[UUID] = None) extends Document

object BasicPermissions {
  final val CanLogin = Permission("Can Login", true, Some(UUID.fromString("5764ed02-87ac-43d1-b9e3-5952756adfe7")))
  final val Viewer = Permission("Viewer", true, Some(UUID.fromString("9230dfdf-3b03-4290-9f60-12227156d278")))
  final val Administrator = Permission("Administrator", true, Some(UUID.fromString("2830dfdf-3b03-4290-9f60-12227156d273")))
}

case class User(main: BasicProfile,
                permissions: List[AccountPermissions],
                systemPermissions: List[UUID],
                lastSelectedAccount: Option[UUID],
                id: Option[UUID] = None) extends Document

object User {
  object fieledName {

  }
}
case class Account(val label: String,
                   permissions: List[UUID],
                   dsCfg: List[DSAccountCfg] = Nil,
                   id: Option[UUID] = None) extends Document

case class AccountPermissions(accountId: UUID,
                              permissionIds: List[UUID])

case class PermissionIds(permissionIds: List[UUID])

case class MailTokens(email: String,
                      creationTime: String,
                      expirationTime: String,
                      isSignUp: Boolean,
                      id: Option[String] = None) extends Document {
  def toMailToken: MailToken = new MailToken(id.toString, email, new DateTime(creationTime), new DateTime(expirationTime), isSignUp)
}

object MailTokens {
  val expirationTime: String = "expirationTime"
}
case class Authenticators(creationDate: String,
                          expirationDate: String,
                          lastUsed: String,
                          user: User,
                          valid: Boolean,
                          timeoutInSeconds: Int,
                          authId: String) extends Document
