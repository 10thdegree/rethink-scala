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
import scala.io.Source

case class ConversionRow(
    conversionTimestamp: DateTime,
    advertiserId: Long,
    agencyId: Long,
    engineId: Long,
    adGroupId: String, 
    campaignId: Long,
    keywordId: Long, 
    adId: Long,
    identifier: String)

object ConversionRow {
  def toConversionRow(m: Map[String,String]): \/[String,ConversionRow] = 
    for {
      date <- m.get("Date").toRightDisjunction("no date").flatMap(d => btry(new DateTime(d)))
      egId <- m.get("Paid Search Engine Account ID").toRightDisjunction("no engineId").flatMap(l => btry(l.toLong))
      cId  <- m.get("Campaign ID").toRightDisjunction("no compaignId").flatMap(l => btry(l.toLong))
      advertId <- m.get("Paid Search Advertiser ID").toRightDisjunction("no advertiserID").flatMap(l => btry(l.toLong))
      adId <- m.get("Paid Search Ad ID").toRightDisjunction("no ad id").flatMap(l => btry(l.toLong))
      keyId <- m.get("Paid Search Keyword ID").toRightDisjunction("can't find paid keyword searchId").flatMap(l => btry(l.toLong))
      id   <- m.get("leadaggid (string)").toRightDisjunction("no conversion id")
      adgrp <- m.get("Paid Search Ad Group ID").toRightDisjunction("no PaidSearchAdGroup ID")
      agId  <- m.get("Paid Search Agency ID").toRightDisjunction("no paid agency id").flatMap(l => btry(l.toLong)) 
    } yield {
      ConversionRow(conversionTimestamp = date,
        advertiserId = advertId,
        agencyId = agId,
        engineId = egId,
        adGroupId = adgrp,
        campaignId = cId,
        keywordId = keyId,
        adId = adId,
        identifier = id)
    }
  
  def btry[A](f: => A) =
    \/.fromTryCatchNonFatal( f ).leftMap(ex => ex.toString)
}

object Conversions {
  val keywordIDColumn = "leadaggid (string)"

  val keywordSearchColum = "Paid Search Keyword ID"
  /*
    case class ConversionConfig() extends Config {
    val api = LiveDart 
    val filePath = "/users/vmarquez/API Project-d15a2d4cf8d4.p12"
    val accountId = "399851814004-fgpilom3s4tgudlmu0epc5lo4c7g5h1n.apps.googleusercontent.com"
    val userAccount = "rashton@10thdegree.com"
    val clientId =  1297324
    val marchexpass = ""
    val marchexurl = ""
    val marchexuser = ""
    val m: Map[String, List[Map[String,String]]] = Map()
  }*/
  
  
  val conversionConfig = LiveTest.prodConfig.copy(accountId = "399851814004-fgpilom3s4tgudlmu0epc5lo4c7g5h1n.apps.googleusercontent.com", userAccount = "rashton@10thdegree.com") 

  def readReport(filename: String) =
    ReportParser.parse(scala.io.Source.fromFile(filename).mkString)

  val frmt = DateTimeFormat.forPattern("yyyyMdd")
  
  def getConversionIdentifiers(filename: String): ISet[String] = 
    ISet.fromList(CSVReader.open(filename).allWithHeaders.map(_(keywordIDColumn)))
  
   def intersect(set: ISet[String], rows: List[ConversionRow]): List[ConversionRow] =
    rows.filter(r => set.contains(r.identifier.toString))
     
  

  def addConversions(reportPath: String, originationPath: String): \/[JazelError,(List[JazelError], List[ConversionRow])] = {
    val (failures, report) = readReport(reportPath).map(ConversionRow.toConversionRow(_)).separate
    val conkeys = getConversionIdentifiers(originationPath)
    val toAdd = intersect(conkeys, report)
    
    val f = uploadInBatches(toAdd).run(conversionConfig)
   
    Await.result(f, Duration(5, MINUTES))._2
  }

  def getLDConversions(i: Int) = 
    Await.result(getConversions(700000001007058L, new DateTime("2014-11-01"), new DateTime("2014-12-01")).run(conversionConfig), Duration(i, SECONDS))

  def getConversions(engineAccountId: Long, start: DateTime, end: DateTime): BravoM[ConversionList] = {
    val startInt = start.toString(frmt).substring(0,8).toInt
    val endInt = end.toString(frmt).substring(0,8).toInt
    val advertiserId = 21700000001003599L
    val agencyId = 20500000000000142L
    //val advertiserId = 4371744L
    for {
      dcsearch <- DartAuth.refreshSearch.toBravoM
      conversionCmd = dcsearch.conversion().get(agencyId, advertiserId, engineAccountId, endInt, 1000, startInt, 0)
      clist    <- ftry(conversionCmd.execute())
    } yield {
      clist
    }
  }

  def uploadInBatches(l: List[ConversionRow]): BravoM[(List[JazelError],List[ConversionRow])] = {

    val newlist = l.map(cr => ftry(cr) )
    newlist.separateSequence
  }
  
  def toDartConversions(cr: ConversionRow): Conversion = {
     val c = new Conversion()
      .setEngineAccountId(cr.engineId)
      .setAgencyId(cr.agencyId)
      .setCampaignId(cr.campaignId)
      .setAdId(cr.adId)
      .setConversionTimestamp(BigInteger.valueOf(cr.conversionTimestamp.getMillis()))
      .setConversionId(cr.identifier)
      .setCriterionId(cr.keywordId)
      .setSegmentationType("FLOODLIGHT")
      .setSegmentationName("Origination") //fixme: we need to pull this from the report
      .setQuantityMillis(1000)
      .setType("ACTION")
      .setConversionId(cr.identifier)       
      c
  }
}
