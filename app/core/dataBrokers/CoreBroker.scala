package core.dataBrokers

import com.rethinkscala.net._
import core.models._
import com.rethinkscala.ast.Table
import com.rethinkscala._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.Play.current

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
  val hostWithKey = (current.configuration.getString("rethinkdb.host").get,current.configuration.getString("rethinkdb.key").get)

  val port = current.configuration.getInt("rethinkdb.port").get

  val version3 = new Version3(hostWithKey._1, port, authKey = hostWithKey._2)

  type TableType = Document

  def useVersion = version3

  val connection: BlockingConnection = BlockingConnection(useVersion)
}

object Setup {
  implicit val c = Connection.connection
  val coreBroker = new CoreBroker

  def setupDB = current.configuration.getBoolean("rethinkdb.setupDb").get

  def initial = {
    if (setupDB) {
      Future {
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
