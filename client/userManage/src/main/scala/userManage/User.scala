package userManage

import scala.scalajs.js

trait UsersResponse extends js.Object {
  var status: String = js.native
  var users: js.Array[User] = js.native
}

trait UsersInviteResponse extends js.Object {
  var status: String = js.native
  var invited: js.Array[UserInvite] = js.native
}

trait User extends js.Object {
  var id: String = js.native
  var fullName: String = js.native
  var email: String = js.native
  var permissions: js.Array[String] = js.native
}

object User {
  def apply(): User =
    js.Dynamic.literal(id = "", fullName = "", email = "", permissions = js.Array[String]()).asInstanceOf[User]

  def apply(id: String, fullName: String, email: String, permissions: js.Array[String]): User =
    js.Dynamic.literal(id = id, fullName = fullName, email = email, permissions = permissions).asInstanceOf[User]
}

trait UserInvite extends js.Object {
  var id: String = js.native
  var email: String = js.native
  var creationTime: String = js.native
  var expirationTime: String = js.native
  var isSignUp: Boolean = js.native
}

object UserInvite {
  def apply(): UserInvite =
    js.Dynamic.literal(id = "", creationTime = "", expirationTime = "",email = "", isSignUp = true).asInstanceOf[UserInvite]
}

trait UsersFullResponse extends js.Object {
  var status: String = js.native
  var users: js.Array[UserFull] = js.native
}

trait AuthMethod extends js.Object {
  var method: String = js.native
}

object AuthMethod {
  def apply(): AuthMethod =
    js.Dynamic.literal(method = "").asInstanceOf[AuthMethod]
}

trait PasswordInfo extends js.Object {
  var hasher: String = js.native
  var password: String = js.native
}

object PasswordInfo {
  def apply(): PasswordInfo =
    js.Dynamic.literal(hasher = "", password = "").asInstanceOf[PasswordInfo]
}

trait UserMain extends js.Object {
  var providerId: String = js.native
  var userId: String = js.native
  var firstName: String = js.native
  var lastName: String = js.native
  var fullName: String = js.native
  var email: String = js.native
  var authMethod: AuthMethod = js.native
  var passwordInfo: PasswordInfo = js.native
}

object UserMain {
  def apply(): UserMain =
    js.Dynamic.literal(providerId = "", userId = "", firstName = "", userId = "",
      lastName = "", fullName = "", email = "", auhtMethod = AuthMethod(), passwordInfo = PasswordInfo()).asInstanceOf[UserMain]
}

trait UserFull extends js.Object {
  var main: UserMain = js.native
  var permissions: js.Array[AccountPermissionIds] = js.native
  var systemPermissions: js.Array[Permission] = js.native
  var lastSelectedAccount: String = js.native
  var id: String = js.native
}

object UserFull {
  def apply(): UserFull =
    js.Dynamic.literal(id = "", main = null, permissions = null, systemPermissions = null, lastSelectedAccount = "").asInstanceOf[UserFull]
}


trait Email extends js.Object {
  var email: String = js.native
}

object Email {
  def apply(email: String): Email =
    js.Dynamic.literal(email = email).asInstanceOf[Email]
}

trait FormUser extends js.Object {
  var user: User = js.native
}

object FormUser {
  def apply(): FormUser =
    js.Dynamic.literal(user = null).asInstanceOf[FormUser]
}

trait UserIds extends js.Object {
  var userIds: js.Array[String] = js.native
}

object UserIds {
  def apply(userId: String): UserIds =
    js.Dynamic.literal(userIds = js.Array[String](userId)).asInstanceOf[UserIds]
}

trait UserWatch extends js.Object {
  var email: String = js.native
}

object UserWatch {
  def apply(): UserWatch =
    js.Dynamic.literal(email = "").asInstanceOf[UserWatch]
}
