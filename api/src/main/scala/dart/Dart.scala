package bravo.api.dart

import scalaz._
import Scalaz._
import org.joda.time.DateTime
import scala.concurrent.ExecutionContext.Implicits.global
import bravo.util.Util._

/*
Instead of polling for specific reports, we should just havea a queue of reports waiting to be fulfilled, get all the file handles, and then check them all at once? or no?
*/

object Dart {
  import com.google.api.services.dfareporting.Dfareporting
  import scala.annotation.tailrec
  import bravo.api.dart.Data._
  import scala.concurrent.{Future,Await}
  import org.joda.time.format._
  import org.joda.time._
  import bravo.util._
  import bravo.util.DateUtil._
  import com.google.api.services.dfareporting.model._
    
  implicit def dartMonad: Monad[({type l[a] = BravoM[DartConfig,a]})#l] = EitherT.eitherTMonad[({ type l[a] = SFuture[DartConfig,a]})#l, JazelError]

  def getReport(reportId: Long, startDate: DateTime, endDate: DateTime): BravoM[DartConfig, DownloadedReport] = ((c: DartConfig) => {
    val currentReportDays  = c.reportCache.get(reportId).getOrElse(List[ReportDay]())
    val reportIdCache     = DateUtil.toSortedSet(currentReportDays)
    val cachedDays        = DateUtil.findLargestRanges[ReportDay](reportIdCache, startDate, endDate, rd => rd.rowDate)
    val missingDays       = DateUtil.findMissingDates(cachedDays.map(_.rowDate).toList, startDate.toLocalDate(), endDate.toLocalDate())
    missingDays match {
      case Some((newStart, newEnd)) =>
        println("we are missing " + newStart + " and " + newEnd + "!")
        val res: BravoM[DartConfig, DownloadedReport] = for {
          report    <- getReportUncached(reportId, newStart.toDateTimeAtStartOfDay, newEnd.toDateTimeAtStartOfDay)
          merged    = c.reportCache.get(reportId).fold(report.data)(old => old |+| report.data) 
          newstate  = c.copy(reportCache = c.reportCache + (reportId -> merged)) 
          _         <- IndexedStateT.stateTMonadState[DartConfig, Future].put(newstate).liftM[BravoHoist]  //need liftM or a way to go to the right type
        } yield {
           DownloadedReport(reportId, startDate, endDate, report.data) 
        }
        res
      case None =>
        dartMonad.point(DownloadedReport(reportId, startDate, endDate, cachedDays.toList))
    }
   }).toBravoM
    .flatMap(x => x)

  def getActivities(startDate: DateTime, endDate: DateTime, advertiserId: Option[Long]): BravoM[DartConfig, List[DimensionValue]] = ((c: DartConfig) => 
    for {
      dfa <- c.api.getDartAuth
      activities <- c.api.getDimensions(dfa, "dfa:activities", startDate, endDate, advertiserId)
    } yield
      activities
    ).toBravoM.flatMap(x => x)

  def getReportUncached(reportId: Long, startDate: DateTime, endDate: DateTime): BravoM[DartConfig, DownloadedReport] = ((c: DartConfig) => 
    for {
      dfa <- c.api.getDartAuth
      _   <- c.api.updateDartReport(dfa, c.clientId, reportId, startDate, endDate)
      id  <- c.api.runDartReport(dfa, c.clientId, reportId)
      rs  <- fulfillReport(dfa, reportId, id, 1) //TODO: take Delay Multiplier from config
      parsed = ReportParser.parse(rs)
      rep    = groupDates(parsed)
    } yield {
      DownloadedReport(reportId, startDate, endDate, rep)
    }       
  ).toBravoM.join


  private def fulfillReport(dfa: Dfareporting, reportId: Long, fileId: Long, delayMultiplier: Int): BravoM[DartConfig,String] = {
    //not tailrec but we're not going that deep
    def rec(c: DartConfig, attempts: Int): BravoM[DartConfig, String] = 
      c.api.downloadReport(dfa, reportId, fileId).run.flatMap(e => 
        e match {
          case -\/(err) if (attempts < 8) =>
            val sleeptime = 1000 + (Math.pow(2,attempts)*100) //exponential backoff
            Thread.sleep(sleeptime.toLong)
            rec(c, attempts+1).run
          case _ =>
            e.toBravoM[DartConfig].run
        }
       )
    fctry((c: DartConfig) => rec(c, +1)).join
  }
}

object LiveDart extends DartInternalAPI {
  import com.google.api.services.dfareporting.{Dfareporting, DfareportingScopes} 
  import scala.collection.JavaConversions._
  import com.google.api.services.dfareporting.model._
  import java.io.InputStream
  import org.joda.time.format.DateTimeFormat
  import bravo.api.dart.Data._
  import bravo.api.dart._
  import java.util.ArrayList
  import bravo.util.Util._
  import com.google.api.services.dfareporting.model._
  import com.google.api.services.dfareporting.model.Report._

  def getDartAuth: BravoM[DartConfig, Dfareporting] = DartAuth.getCredentialService

  //FIXME: we need a fctry without a c =>, too confusing otherwise
  def runDartReport(reportApi: Dfareporting, userid: Int, rid: Long): BravoM[DartConfig, Long] = 
    for {
      file <- fctry((c:DartConfig) => reportApi.reports().run(userid, rid).setSynchronous(false).execute())
    } yield { 
      file.getId()
    }
 

  //def getAdvertisers(reportId: 

  def getDimensions(reportApi: Dfareporting, name: String, startDate: DateTime, endDate: DateTime, advertId: Option[Long]): BravoM[DartConfig, List[DimensionValue]] = {
    val dreq = new DimensionValueRequest()
    
    //val dimensionValue = new DimensionValue().setDimensionName("dfa:advertiser").setId(advertiserId.toString) 
    val req =  advertId.cata(id => {
                dreq.setFilters(List(new DimensionFilter().setDimensionName("dfa:advertiser").setValue(id.toString)))
              }, dreq)
                .setStartDate(toGoogleDate(startDate))
                .setEndDate(toGoogleDate(endDate))
                .setDimensionName(name)
    for {
      res <- fctry((c: DartConfig) => reportApi.dimensionValues().query(c.clientId, req).execute())
      items = (res.getItems(): java.util.List[DimensionValue])
      _     = items.toList.foreach(i => println(i.toString()))
    } yield 
      items.toList  
  }

  def createReport(reportApi: Dfareporting, advertiserId: Long): BravoM[DartConfig, Long] = 
    for {
      floodlights <- getActivityFields(reportApi, advertiserId)
      reportId    <- createReport(reportApi, advertiserId, floodlights.map(_._2))
    } yield 
      reportId
/*
  def createReport(reportApi: Dfareporting, advertiserId: Long, activities: List[String]): BravoM[DartConfig, Long] = {
    val dr = new DateRange()
    dr.setRelativeDateRange("YESTERDAY")
    val dimensions = new SortedDimension()
    dimensions.setName("dfa:campaign")
    val dimensionValue = new DimensionValue().setDimensionName("dfa:advertiser").setId(advertiserId.toString) 
    val metricsList = List("dfa:paidSearchClicks", "dfa:clicks", "dfa:paidSearchImpressions", "dfa:clickRate")
    val criteria = new Criteria()
      .setDateRange(dr)
      .setDimensions(List(dimensions))
      .setMetricNames(metricsList)
      .setDimensionFilters( List(dimensionValue) ) 
      .setActivities(new Activities().setMetricNames(List("TUIU International Students")))

    val report = new Report()
      .setCriteria(criteria)
      .setName("test_API_latest+"+advertiserId.toString)
      .setType("STANDARD")
    
    for {
      result <- fctry((c:DartConfig) => reportApi.reports().insert(c.clientId, report).execute())
    } yield result.getId()
  }
*/
  def createReport(reportApi: Dfareporting, advertiserId: Long, activities: List[DimensionValue]): BravoM[DartConfig, Long] = {
      val dr = new DateRange()
      dr.setRelativeDateRange("YESTERDAY")
      val dimensions = new SortedDimension()
      dimensions.setName("dfa:campaign")
      val mappedActivities = activities.map(dv => new DimensionValue().setDimensionName("dfa:activity").setId(dv.getValue()))
      val dimensionValue = new DimensionValue().setDimensionName("dfa:advertiser").setId(advertiserId.toString) 
      val metricsList = List("dfa:paidSearchAveragePosition", "dfa:paidSearchClickRate", "dfa:paidSearchClicks", "dfa:paidSearchImpressions", "dfa:paidSearchCost", "dfa:paidSearchVisits", "dfa:paidSearchActions")
      val criteria = new Criteria()
        .setDateRange(dr)
        .setActivities( new Activities().setMetricNames(List("dfa:paidSearchActions")).setFilters(mappedActivities))
        .setDimensions(List(dimensions))
        .setMetricNames(metricsList)
        //.setActivities(new Activities().setMetricNames(List("TUIU International Students")))

      val report = new Report()
        .setCriteria(criteria)
        .setName("test_API_latest+"+advertiserId.toString)
        .setType("STANDARD")
      
      for {
        result <- fctry((c:DartConfig) => reportApi.reports().insert(c.clientId, report).execute())
      } yield result.getId()
    }

   //Await.result(DartAuth.getCredentialService.flatMap(dfa => LiveDart.getDartReport(dfa, rid)).run(LiveTest.prodConfig), Duration(30, SECONDS))._2.toOption.get
   // Await.result(DartAuth.getCredentialService.flatMap(dfa => LiveDart.getActivityFields(dfa, advertId)).run(LiveTest.prodConfig), Duration(30, SECONDS))._2.toOption.get

   def getActivityFields(reportApi: Dfareporting, advertId: Long): BravoM[DartConfig, List[(String, DimensionValue)]] = {
    val fields = "nextPageToken,floodlightActivities(id,name)"
    for {
      fields <- fctry((c: DartConfig) => reportApi.floodlightActivities().list(c.clientId).setAdvertiserId(advertId).execute())
        //.setFields(fields).execute())
      //_     = println("fields = " + fields )
      activities  = fields.getFloodlightActivities(): java.util.List[FloodlightActivity]
      _      = activities.foreach(act => println(act.getName() + "|" + act.getFloodlightActivityGroupName() + " | " + act.getFloodlightActivityGroupType()  + "|" + act.getIdDimensionValue()))
    } yield activities.map(act => (act.getName(), act.getIdDimensionValue())).toList
  }
 

/* Path to Conversion report with required dimensions.
Report pathToConversionReport = new Report()
  .setType("PATH_TO_CONVERSION")
  .setPathToConversionCriteria(new Report.PathToConversionCriteria()
  .setConversionDimensions(ImmutableList.of(new SortedDimension().setName("dfa:conversionId")))
  .setPerInteractionDimensions(ImmutableList.of(
    new SortedDimension().setName("dfa:interactionNumber"),
    new SortedDimension().setName("dfa:interactionTime"))));
CompatibleFields pathToConversionFields = reporting.reports().compatibleFields()
*/

  def viewUserProfiles(reportApi: Dfareporting): BravoM[DartConfig, List[DartProfile]] = 
    for {
      profileResp <- fctry((c:DartConfig) => reportApi.userProfiles().list().execute())
      profiles    = (profileResp.getItems(): java.util.List[UserProfile])
    } yield
      profiles.toList.map(p => DartProfile(p.getAccountName(), p.getUserName(), p.getAccountId(), p.getProfileId()))
      
  
  override def viewDartReports(reportApi: Dfareporting, userid: Int): BravoM[DartConfig, List[AvailableReport]] = 
    for {
      reports <- fctry((c:DartConfig) => reportApi.reports().list(userid).execute() )  
      items   = (reports.getItems(): java.util.List[Report])
    } yield 
      items.toList.map(toAvailableReport(_))

  def getDartReport(reportApi: Dfareporting, rid: Long): BravoM[DartConfig, Report] = 
    for {
      r <- fctry((c:DartConfig) => reportApi.reports().get(c.clientId, rid).execute())
    } yield r

  override def updateDartReport(reportApi: Dfareporting, userid: Int, rid: Long, startDate: DateTime, endDate: DateTime): BravoM[DartConfig, Unit]= 
    for {
      report    <- fctry((c: DartConfig) => reportApi.reports().get(userid, rid).execute())
      criteria  <- fctry((c: DartConfig) =>  
                    report.getCriteria().setDateRange(new DateRange().setStartDate(toGoogleDate(startDate)).setEndDate(toGoogleDate(endDate)))
                  )
      _         = report.setCriteria(criteria)
      _         <- ftry(reportApi.reports().update(userid, rid, report).execute())
    } yield
      ()
  
  
  override def downloadReport(reportApi: Dfareporting, reportid: Long, fid: Long): BravoM[DartConfig, String] = 
    for {
      filehandle  <- fctry((c:DartConfig) => reportApi.files().get(reportid, fid))
      file        <- fctry((c: DartConfig) => filehandle.execute())
      is          <- if (file.getStatus != "REPORT_AVAILABLE") 
                      JazelError(none, "Report " + reportid + "is not available").left[InputStream].toBravoM[DartConfig]
                    else
                      fctry((c: DartConfig) => filehandle.executeMediaAsInputStream()) 
    } yield {
      val reportData = scala.io.Source.fromInputStream(is).mkString  
      reportData
    }
  

  def toAvailableReport(r: Report):  AvailableReport = AvailableReport(r.getId(), 
    r.getName(), 
    r.getFormat(),
    r.getFileName(),
    new DateTime(r.getCriteria().getDateRange().getStartDate().toString),
    new DateTime(r.getCriteria().getDateRange().getEndDate()))
 
}

