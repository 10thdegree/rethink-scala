package shared

sealed trait Response {
  def status: String
}

case class LastAccount(status: String, lastSelectedAccount: String) extends Response

case class Account(id: String,label: String)

object Account {
  def apply() : Account = new Account(id="", label="")
}

case class AccountsResponse(status: String, accounts: Seq[Account]) extends Response

case class Credential(username: String, password: String)

case class Authenticated(redirect: String)