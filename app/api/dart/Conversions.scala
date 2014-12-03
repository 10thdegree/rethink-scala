package bravo.api.dart

import scalaz._
import Scalaz._
import org.joda.time.DateTime
import scala.concurrent.ExecutionContext.Implicits.global
import bravo.core.Util._
import org.joda.time.format.DateTimeFormat
import com.google.api.services.doubleclicksearch.model._
import com.github.tototoshi.csv._
import java.math._
import scala.concurrent.duration._
import scala.concurrent.Await

object Conversions {

  //762360694414-rfjpd5rkopfjnme19ppscnd7i1652jg5@developer.gserviceaccount.com email
  val conversionConfig = new Config {
    val api = LiveDart 
    val filePath = "/users/vmarquez/API Project-d15a2d4cf8d4.p12"
    val accountId = "399851814004-fgpilom3s4tgudlmu0epc5lo4c7g5h1n.apps.googleusercontent.com"
    val userAccount = "rashton@10thdegree.com"
    val clientId =  1297324
    val marchexpass = ""
    val marchexurl = ""
    val marchexuser = ""
  }

  val agencyId = 20500000000000142L
  
  case class ConversionRow(
    date: DateTime,
    engineId: Long,
    adGroupId: String, 
    campaignId: Long,
    keywordId: String, 
    adId: Long,
    identifier: String)

  
  val frmt = DateTimeFormat.forPattern("yyyyMdd")
  
  //todo: log lefts that failed parsing
  def getConversionRows(filename: String): List[ConversionRow] = 
   CSVReader.open(filename).allWithHeaders.map(toConversionRow(_).toOption).flatten

  def btry[A](f: => A) =
    \/.fromTryCatchNonFatal( f ).leftMap(ex => ex.toString)
  
  def toConversionRow(m: Map[String,String]): \/[String,ConversionRow] = 
    for {
      date <- m.get("Date").toRightDisjunction("no date").map(new DateTime(_))
      egId <- m.get("Paid Search Engine Account ID").toRightDisjunction("no engineId").flatMap(l => btry(l.toLong))
      cId  <- m.get("Campaign ID").toRightDisjunction("no compaignId").flatMap(l => btry(l.toLong))
      adId <- m.get("Ad ID").toRightDisjunction("no ad id").flatMap(l => btry(l.toLong))
      id   <- m.get("leadaggid (string)").toRightDisjunction("no conversion id")
    } yield {
      ConversionRow(date, egId, m.get("Paid Search Ad Group ID").getOrElse(""), cId, "",adId, id)
    }
  
  def getLDConversions(i: Int) = 
    Await.result(getConversions(700000001007061L, new DateTime("2014-11-01"), new DateTime("2014-12-01")).run(conversionConfig), Duration(i, SECONDS))

  def getConversions(engineAccountId: Long, start: DateTime, end: DateTime): BravoM[ConversionList] = {
    val startInt = start.toString(frmt).substring(0,8).toInt
    val endInt = end.toString(frmt).substring(0,8).toInt
    val advertiserId = 21700000001003599L
    //val advertiserId = 4371744L
    println("startInt = " + startInt)
    println("endInt = " + endInt)
    for {
      dcsearch <- DartAuth.refreshSearch
      _         = println(" \n \n \n \n ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ \n \n \n ")
      conversionCmd = dcsearch.conversion().get(agencyId, advertiserId, engineAccountId, endInt, 1000, startInt, 0)
      clist    <- ftry(conversionCmd.execute())
    } yield {
      clist
    }
  }

  def uploadInBatches(l: List[ConversionRow]): BravoM[ConversionList] = {
    import scala.collection.JavaConversions._
    val crows = l.map(toDartConversions(_))
    val clist = new ConversionList().setConversion(crows)    

    for {
      dcsearch <- DartAuth.refreshSearch
      results  <-ftry(dcsearch.conversion().insert(clist).execute())
    } yield
      results
  }

  def toDartConversions(cr: ConversionRow): Conversion = {
     new Conversion()
      .setEngineAccountId(cr.engineId)
      .setAgencyId(agencyId)
      .setCampaignId(cr.campaignId)
      .setAdId(cr.adId)
      .setConversionTimestamp(BigInteger.valueOf(cr.date.getMillis()))
      .setConversionId(cr.identifier)
  }

}
