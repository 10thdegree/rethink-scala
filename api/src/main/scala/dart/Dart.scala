package bravo.api.dart

import scalaz._
import Scalaz._
import org.joda.time.DateTime
import scala.concurrent.ExecutionContext.Implicits.global
import bravo.util.Util._

/*
Instead of polling for specific reports, we should just havea a queue of reports waiting to be fulfilled, get all the file handles, and then check them all at once? or no?
*/


//FOR EXTERNAL USE.  THIS IS OUR API *TO* DART FROM OTHER APPLICATIONS
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

  def createReport(advertiserId: Long): BravoM[DartConfig, Long] = ((c:DartConfig) => { 
    for {
      dfa <- c.api.getDartAuth
      reportId <- c.api.createDartReport(dfa, advertiserId)
    } yield reportId
  }).toBravoM.flatMap(x => x)
    
  def getAdvertisers: BravoM[DartConfig, List[(String,Int)]] = ((c:DartConfig) => {
    for {
      dfa <- c.api.getDartAuth
      advertisers <- c.api.getDimensions(dfa, "dfa:advertiser", new DateTime().plusDays(-365), new DateTime, None)
    } yield advertisers 
  }).toBravoM.flatMap(x => x)

  def getReports(advertiserId: Long): BravoM[DartConfig, List[AvailableReport]] = ((c:DartConfig) => {
    for {
      dfa <- c.api.getDartAuth
      reports <- c.api.getAvailableReports(dfa, advertiserId)
    } yield reports 
  }).toBravoM.flatMap(x => x)

  def getReport(reportId: Long, startDate: DateTime, endDate: DateTime): BravoM[DartConfig, DownloadedReport] = ((c: DartConfig) => {
    val currentReportDays  = c.reportCache.get(reportId).getOrElse(List[ReportDay]())
    val reportIdCache     = DateUtil.toSortedSet(currentReportDays)
    val cachedDays        = DateUtil.findLargestRanges[ReportDay](reportIdCache, startDate, endDate, rd => rd.rowDate)
    val missingDays       = DateUtil.findMissingDates(cachedDays.map(_.rowDate).toList, startDate.toLocalDate(), endDate.toLocalDate())
    missingDays match {
      case Some((newStart, newEnd)) =>
        println("we are missing " + newStart + " and " + newEnd + "!")
        val res: BravoM[DartConfig, DownloadedReport] = for {
          dfa       <- c.api.getDartAuth 
          files     <- c.api.getFilesForReport(dfa, reportId)
          reportStr <- checkFulfilledReports(startDate, endDate, files) match {
                        case Some(fileid) => 
                          println("we found a file!")
                          fulfillReport(dfa, reportId, fileid, 1) 
                        case None => getReportUncached(dfa, reportId, newStart.toDateTimeAtStartOfDay, newEnd.toDateTimeAtStartOfDay)
                      }
          parsed    = ReportParser.parse(reportStr)
          data      = groupDates(parsed)
          merged    = c.reportCache.get(reportId).fold(data)(old => old |+| data) 
          newstate  = c.copy(reportCache = c.reportCache + (reportId -> merged)) 
          _         <- IndexedStateT.stateTMonadState[DartConfig, Future].put(newstate).liftM[BravoHoist]  //need liftM or a way to go to the right type
        } yield {
           DownloadedReport(reportId, startDate, endDate, data) 
        }
        res
      case None =>
        dartMonad.point(DownloadedReport(reportId, startDate, endDate, cachedDays.toList))
    }
   }).toBravoM
    .flatMap(x => x)

  private def checkFulfilledReports(startDate: DateTime, endDate: DateTime, files: List[AvailableFile]): Option[Long] = 
    files.find(f => isBetween((startDate, endDate), (f.startDate, f.endDate))).map(f => f.id) 

  private def getReportUncached(dfa: Dfareporting, reportId: Long, startDate: DateTime, endDate: DateTime): BravoM[DartConfig, String] = ((c: DartConfig) => 
    for {
      _   <- c.api.updateDartReport(dfa, c.clientId, reportId, startDate, endDate)
      id  <- c.api.runDartReport(dfa, c.clientId, reportId)
      rs  <- fulfillReport(dfa, reportId, id, 1) //TODO: take Delay Multiplier from config
    } yield {
        rs
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


/* NOT FOR EXTERNAL USE*/
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
 
  override def getDimensions(reportApi: Dfareporting, name: String, startDate: DateTime, endDate: DateTime, advertId: Option[Long]): BravoM[DartConfig, List[(String,Int)]] = {
    val dreq = new DimensionValueRequest()
    val req =  advertId.cata(id => {
                dreq.setFilters(List(new DimensionFilter().setDimensionName("dfa:advertiser").setValue(id.toString)))
              }, dreq)
                .setStartDate(toGoogleDate(startDate))
                .setEndDate(toGoogleDate(endDate))
                .setDimensionName(name)
    for {
      res <- fctry((c: DartConfig) => reportApi.dimensionValues().query(c.clientId, req).execute())
      items = (res.getItems(): java.util.List[DimensionValue])
    } yield 
      items.toList.map(dv => (dv.getValue(), dv.getId().toInt))  
  }

  override def createDartReport(reportApi: Dfareporting, advertiserId: Long): BravoM[DartConfig, Long] = 
    for {
      floodlights <- getActivityFields(reportApi, advertiserId)
      reportId    <- createReport(reportApi, advertiserId, floodlights.map(_._2))
    } yield 
      reportId

  //we will want to add various report types here
  def createReport(reportApi: Dfareporting, advertiserId: Long, activityIds: List[Int]): BravoM[DartConfig, Long] = {
      val daterange = new DateRange().setRelativeDateRange("MONTH_TO_DATE")
      val dimensions = new SortedDimension().setName("dfa:campaign")
      val mappedActivities = activityIds.map(dv => new DimensionValue().setDimensionName("dfa:activity").setId(dv.toString))
      val dimensionValue = new DimensionValue().setDimensionName("dfa:advertiser").setId(advertiserId.toString) 
      val metricsList = List("dfa:paidSearchAveragePosition", "dfa:paidSearchClickRate", "dfa:paidSearchClicks", "dfa:paidSearchImpressions", "dfa:paidSearchCost", "dfa:paidSearchVisits", "dfa:paidSearchActions")
      
      val criteria = new Criteria()
        .setDateRange(daterange)
        .setActivities( new Activities().setMetricNames(List("dfa:paidSearchActions")).setFilters(mappedActivities))
        .setDimensions(List(dimensions))
        .setMetricNames(metricsList)

      //Schedule should be run month to date 
      val schedule = new Schedule()
        .setRepeats("DAILY")
        .setEvery(1)
        .setExpirationDate(toGoogleDate(new DateTime().plusYears(2))) //???
        .setStartDate(toGoogleDate(new DateTime().plusDays(-1)))
        .setActive(true)
      
      val report = new Report()
        .setCriteria(criteria)
        .setName("Bravo_Search_"+advertiserId.toString)
        .setType("STANDARD")
        .setSchedule(schedule)

      for {
        result <- fctry((c:DartConfig) => reportApi.reports().insert(c.clientId, report).execute())
      } yield result.getId()
    }

   //Await.result(DartAuth.getCredentialService.flatMap(dfa => LiveDart.getDartReport(dfa, rid)).run(LiveTest.prodConfig), Duration(30, SECONDS))._2.toOption.get
   //Await.result(DartAuth.getCredentialService.flatMap(dfa => LiveDart.getActivityFields(dfa, advertId)).run(LiveTest.prodConfig), Duration(30, SECONDS))._2.toOption.get

   def getActivityFields(reportApi: Dfareporting, advertId: Long): BravoM[DartConfig, List[(String, Int)]] = 
    for {
      fields <- fctry((c: DartConfig) => reportApi.floodlightActivities().list(c.clientId).setAdvertiserId(advertId).execute())
      activities  = fields.getFloodlightActivities(): java.util.List[FloodlightActivity]
    } yield activities.map(act => (act.getName(), act.getIdDimensionValue().getValue().toInt)).toList
  
  def viewUserProfiles(reportApi: Dfareporting): BravoM[DartConfig, List[DartProfile]] = 
    for {
      profileResp <- fctry((c:DartConfig) => reportApi.userProfiles().list().execute())
      profiles    = (profileResp.getItems(): java.util.List[UserProfile])
    } yield
      profiles.toList.map(p => DartProfile(p.getAccountName(), p.getUserName(), p.getAccountId(), p.getProfileId()))
      
  override def getAvailableReports(reportApi: Dfareporting, advertiserId: Long): BravoM[DartConfig, List[AvailableReport]] = 
    for {
      reports <- fctry((c:DartConfig) => reportApi.reports().list(c.clientId).execute() )  
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
  
  override def getFilesForReport(reportApi: Dfareporting, reportid: Long): BravoM[DartConfig, List[AvailableFile]] = 
    for {
      files   <- fctry((c:DartConfig) => reportApi.reports().files().list(c.clientId, reportid).execute())
    } yield files.getItems().map(f => AvailableFile(f.getId().toLong, f.getFileName(), new DateTime(f.getDateRange().getStartDate().toString), new DateTime(f.getDateRange().getEndDate().toString))).toList
 
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

