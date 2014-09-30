package core.dataBrokers

import com.rethinkscala.net._
import core.models._
import com.rethinkscala.ast.Table
import com.rethinkscala._

class CoreBroker(implicit connection: BlockingConnection) {

  import net.Blocking._

  def db = r.db("bravo")

  def usersTable: Table[User] = db.table[User]("users")

  def accountsTable: Table[Account] = db.table[Account]("accounts")

  def permissionsTable: Table[Permission] = db.table[Permission]("permissions")

  def tokensTable: Table[MailTokens] = db.table[MailTokens]("tokens")

  def authenticatorsTable: Table[Authenticators] = db.table[Authenticators]("authenticators")

  def users: Either[RethinkError, Seq[User]] = usersTable.getAll().run

  def accounts: Either[RethinkError, Seq[Account]] = accountsTable.getAll().run

}

object Connection {
  val hostWithKey = java.net.InetAddress.getLocalHost().getHostName() match {
    case "MO-MBA" => ("127.0.0.1","scalaDrive")
    case _ => ("127.0.0.1","")
  }
  val port = 28015
  //val version1 = new Version1(hostWithKey._1, port)
  val version2 = new Version2(hostWithKey._1, port, authKey = hostWithKey._2)

  type TableType = Document

  def useVersion = version2

  val connection: BlockingConnection = BlockingConnection(useVersion)
}

object Setup {
  implicit val c = Connection.connection
  val coreBroker = new CoreBroker

  def setupDB = true

  def initial = {
    if (setupDB) {
      import com.rethinkscala.Blocking._
      coreBroker.db.create.run
      coreBroker.usersTable.create.run
      coreBroker.accountsTable.create.run
      coreBroker.permissionsTable.create.run
      coreBroker.tokensTable.create.run
      coreBroker.authenticatorsTable.create.run
      coreBroker.authenticatorsTable.indexCreate("authId").run
      getCorePermissions(BasicPermissions).map(x => {
        coreBroker.permissionsTable.insert(x._2).run
      })
    }
  }

  def getCorePermissions(o: Any): Map[String, Permission] = {
    val fieldsAsPairs = (for (field <- o.getClass.getDeclaredFields) yield {
      field.setAccessible(true)
      (field.getName, field.get(o))
    })
    val updated = fieldsAsPairs collect { case (x: String, y: Permission) ⇒ (x,y)}
    Map(updated :_*)
  }
}
