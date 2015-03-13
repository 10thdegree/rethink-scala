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

  def getActivities(startDate: DateTime, endDate: DateTime, advertiserId: Option[Long]): BravoM[DartConfig, List[String]] = ((c: DartConfig) => 
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

  def getDartAuth: BravoM[DartConfig, Dfareporting] = DartAuth.getCredentialService

  //FIXME: we need a fctry without a c =>, too confusing otherwise
  def runDartReport(reportApi: Dfareporting, userid: Int, rid: Long): BravoM[DartConfig, Long] = 
    for {
      file <- fctry((c:DartConfig) => reportApi.reports().run(userid, rid).setSynchronous(false).execute())
    } yield { 
      file.getId()
    }
  
  def getDimensions(reportApi: Dfareporting, name: String, startDate: DateTime, endDate: DateTime, advertId: Option[Long]): BravoM[DartConfig, List[String]] = {
    val dreq = new DimensionValueRequest()
    /*
    val req =  advertId.cata(id => {
                val filter = new DimensionFilter().set("dfa:advertiserId", advertId)
                dreq.setFilters(List(filter))
                dreq
              }, dreq)
                .setStartDate(toGoogleDate(startDate))
                .setEndDate(toGoogleDate(endDate))
                .setDimensionName(name)
    */
    val req = new DimensionValueRequest()
      .setStartDate(toGoogleDate(startDate))
      .setEndDate(toGoogleDate(endDate))
      .setDimensionName("dfa:activity")
      //.setFilters(List( new DimensionFilter().set("dfa:advertiserId", 3843776)) )

    for {
     res <- fctry((c: DartConfig) => reportApi.dimensionValues().query(c.clientId, req).execute())
      items = (res.getItems(): java.util.List[DimensionValue])
      _     = items.toList.foreach(i => println(i.toString()))
    } yield 
      items.toList.map(_.getValue())  
  }
/*
teRange dateRange = new DateRange();
    dateRange.setRelativeDateRange("YESTERDAY");

    // Create a dimension to report on.
    SortedDimension dimension = new SortedDimension();
    dimension.setName("dfa:campaign");

    // Create the criteria for the report.
    Criteria criteria = new Criteria();
    criteria.setDateRange(dateRange);
    criteria.setDimensions(ImmutableList.of(dimension));
    criteria.setMetricNames(ImmutableList.of("dfa:clicks"));

    // Create the report.
    Report report = new Report();
    report.setCriteria(criteria);
    report.setName(reportName);
    report.setType("STANDARD");

    // Insert the report.
    Report result = reporting.reports().insert(profileId, report).execute();
*/

  import com.google.api.services.dfareporting.model._
  import com.google.api.services.dfareporting.model.Report._

  def createReport(reportApi: Dfareporting, advertiserId: Long, startDate: DateTime, endDate: DateTime): BravoM[DartConfig, Long] = {
    val dr = new DateRange()
    dr.setRelativeDateRange("YESTERDAY")
    val dimensions = new SortedDimension()
    dimensions.setName("dfa:campaign")

    val dimensionValue = new DimensionValue().set("dfa:advertiserId", advertiserId.toString) 
    println("dimensionValue = " + dimensionValue)
    val metricsList = List("dfa:paidSearchClicks", "dfa:clicks", "dfa:paidSearchImpressions", "dfa:clickRate")
    val criteria = new Criteria()
      .setDateRange(dr)
      .setDimensions(List(dimensions))
      .setMetricNames(metricsList)
      //.setDimensionFilters( List(dimensionValue) ) //THIS BREAKS
 
    val report = new Report()
      .setCriteria(criteria)
      .setName("test_API_latest")
      .setType("STANDARD")

    
    for {
      result <- fctry((c:DartConfig) => reportApi.reports().insert(c.clientId, report).execute())
    } yield result.getId()
  }

/*
  def getActivities[A <: DartConfig](reportApi: Dfareporting, rid: Long): BravoM[DartConfig, List[String]] =
    for {
      report <- fctry((c: A) => reportApi.reports().get(c.clientId, rid).execute())
      activities = report.getCriteria().getActivities()
      names <-  if (activities.containsKey("metricNames")) {
                  ftry[DartConfig,List[String]](activities.get("metricNames").asInstanceOf[ArrayList[Object]].toList.map(_.toString()))
                  //(sFutureMonad.point(activities.get("metricNames").asInstanceOf[ArrayList[Object]].toList.map(_.toString())))
                } else 
                  JazelError(None, "Cannot find metriNames in activities for report " + rid).left[List[String]].toBravoM
    } yield {
      names
    }
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

