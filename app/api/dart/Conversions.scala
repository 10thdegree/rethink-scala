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

  val agencyId = 20500000000000100L

  case class ConversionRow(
    date: DateTime,
    engineId: Long,
    adGroupId: String, 
    campaignId: Long,
    keywordId: String, 
    adId: Long)
  
  val frmt = DateTimeFormat.forPattern("yyyymmdd")
  
  //todo: log lefts that failed parsing
  def getConversions(filename: String): List[ConversionRow] = 
   CSVReader.open(filename).allWithHeaders.map(toConversionRow(_).toOption).flatten

  def btry[A](f: => A) =
    \/.fromTryCatchNonFatal( f ).leftMap(ex => ex.toString)
  
  def toConversionRow(m: Map[String,String]): \/[String,ConversionRow] = 
    for {
      date <- m.get("Date").toRightDisjunction("no date").map(new DateTime(_))
      egId <- m.get("Paid Search Engine Account ID").toRightDisjunction("no engineId").flatMap(l => btry(l.toLong))
      cId  <- m.get("Campaign ID").toRightDisjunction("no compaignId").flatMap(l => btry(l.toLong))
      adId <- m.get("Ad ID").toRightDisjunction("no ad id").flatMap(l => btry(l.toLong))
    } yield {
      ConversionRow(date, egId, m.get("Paid Search Ad Group ID").getOrElse(""), cId, "",adId)
    }
  
  def getLDConversions(i: Int) = 
    Await.result(getConversions(700000001007061L, new DateTime("2014-08-01"), new DateTime("2014-08-15")).run(Dart.prodConfig), Duration(i, SECONDS))

  def getConversions(engineAccountId: Long, start: DateTime, end: DateTime): BravoM[ConversionList] = {
    val startInt = start.toString(frmt).substring(0,8).toInt
    val endInt = end.toString(frmt).substring(0,8).toInt
    val advertiserId = 4371744L
    for {
      dcsearch <- DartAuth.installedAppSearch
      clist    <- ftry(dcsearch.conversion().get(agencyId, advertiserId, engineAccountId, end.getMillis().asInstanceOf[Int], 10000, start.getMillis().asInstanceOf[Int], 0).execute())
    } yield {
      clist
    }
  }

  def uploadInBatches(l: List[ConversionRow]): Unit = {
    import scala.collection.JavaConversions._
    val crows = l.map(toDartConversions(_))
    val clist = new ConversionList().setConversion(crows)    

    for {
      dcsearch <- DartAuth.installedAppSearch
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
  }

}
