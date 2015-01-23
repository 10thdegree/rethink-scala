package bravo.test.api

import bravo.api

import org.joda.time._
import com.google.api.services.dfareporting.Dfareporting
import bravo.api.dart.Data._
import bravo.core.Util._
import scalaz._
import Scalaz._
import scala.concurrent.{Future,Await}
import scala.concurrent.duration._
import bravo.api.dart._
import scala.concurrent.ExecutionContext.Implicits.global 
import org.scalacheck.Properties
import org.scalacheck.Prop.forAll
import org.scalacheck.Prop._
import org.scalacheck._    
import Gen._               
import Arbitrary.arbitrary 
import org.joda.time._

object DartAPITest extends Properties("Dart API test") {
  
  property("nonblocking test") = forAll { (i:Int) =>
   val reportCall = Dart.getReport(444, new DateTime(), new DateTime())
   val future = reportCall.run.run(config)
   val result = Await.result(future, scala.concurrent.duration.Duration(1, SECONDS) )
   true
  }

  property("test Cache") = forAll { (i: Int) =>
    val reportCall = Dart.getReport(1, new DateTime(), new DateTime())
    val future = reportCall.run.run(config)
    val result = Await.result(future, scala.concurrent.duration.Duration(1, SECONDS) )
    result._2.fold(l => false, r => {
      true
    })
  }

  case class TestConfig(
    val api: DartInternalAPI  = internal(),
    val filePath: String = "",
    val accountId: String = "",
    val userAccount: String = "",
    val clientId: Int = 0,
    val marchexuser: String = "", 
    val marchexpass: String = "",
    val marchexurl: String = "",
    val m: Map[String, List[Map[String,String]]] = Map[String, List[Map[String,String]]]("1" -> List[Map[String,String]]( Map[String,String]("asdf" -> "blah"))) 
    ) extends Config {
      def cache(id: String, s: DateTime, e: DateTime) = m.get(id).getOrElse( List[Map[String,String]]() )
      def updateCache(id: String, s: DateTime, e: DateTime, d: List[Map[String,String]]): Config = {
        this.copy(m = (m + ((id) -> d)))
      }
  }

  val config = TestConfig()

  def internal():DartInternalAPI = new DartInternalAPI {
   
    def getDartAuth: BravoM[Dfareporting] = Monad[BravoM].point(null)

    def viewDartReports(r: Dfareporting, userid: Int): BravoM[List[AvailableReport]] = ???

    def updateDartReport(r: Dfareporting, userid: Int, rid: Long, s: DateTime, e: DateTime): BravoM[Unit] = Monad[BravoM].point(())

    def runDartReport(r: Dfareporting, userid: Int, rid: Long): BravoM[Long] = Monad[BravoM].point(1L)

    def downloadReport(r: Dfareporting, rid: Long, fid: Long): BravoM[String] = Monad[BravoM].point("asdf,blah\\r\\na,b")
  
  }

  def dartCacheTest() {
      //Data/

  }

}

