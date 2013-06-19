import com.rethinkscala.{Version2, Version1, Connection}
import org.scalatest.FunSuite

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 6/18/13
 * Time: 3:45 PM 
 */
import ql2._
import com.rethinkscala.Implicits.Quick._
trait BaseTest {
  self: FunSuite =>
  val host = "172.16.2.45"
  val port = 28015
  val authKey = ""
  val version1 = new Version1(host, port)
  val version2 = new Version2(host, port, authKey = authKey)

  def useVersion = version1

  implicit val connection: Connection = new Connection(useVersion)

  def assert(t:Term,tt:Term.TermType.EnumVal){
    assert(t.`type`.get == tt)
  }
  def assert(d:Option[Datum],value:String){
    assert(d.get.str == value)
  }
  def assert(d:Datum,value:String){
    assert(d.str == value)
  }

}