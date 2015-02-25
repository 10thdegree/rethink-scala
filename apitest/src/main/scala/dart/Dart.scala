package bravo.apitest.dart

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
  import bravo.apitest.dart.Data._
  import scala.concurrent.{Future,Await}
  import org.joda.time.format._
  import org.joda.time._
  import bravo.util._
  //import bravo.util.Data._
  import bravo.util.DateUtil._

  /*
  def getReport[A <: DartConfig](reportId: Int, startDate: DateTime, endDate: DateTime): BravoM[A, DownloadedReport] = fctr((c: Config) => {
    val reportIdCache = DateUtil.toSortedSet(c.cache(reportId))
    val cachedDays = DateUtil.findLargestRanges[ReportDay](reportIdCache, startDate, endDate, rd => rd.rowDate)
    val missingDays = DateUtil.findMissingDates(cachedDays.map(_.rowDate).toList, startDate.toLocalDate(), endDate.toLocalDate())
    missingDays match {
      case Some((newStart, newEnd)) =>
        println("we are missing " + newStart + " and " + newEnd + "!")
        for {
          report <- getReportUncached(reportId, newStart.toDateTimeAtStartOfDay, newEnd.toDateTimeAtStartOfDay)
          _     <- put(c.updateCache(reportId, report.data))
        } yield {
           DownloadedReport(reportId, startDate, endDate, report.data) 
          }
      case None =>
        DownloadedReport(reportId, startDate, endDate, cachedDays.toList).point[BravoM] 
    }
    
   } )
 
  
  def getReportUncached(reportId: Int, startDate: DateTime, endDate: DateTime): BravoM[DownloadedReport] = ((c:Config) => 
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

    ).toBravoM
  */

  private def fulfillReport[A <: DartConfig](dfa: Dfareporting, reportId: Long, fileId: Long, delayMultiplier: Int): BravoM[A,String] = fctry((c: A) => {
    //not tailrec but we're not going that deep
    def rec(attempts: Int): BravoM[A, String] =  
      c.api.downloadReport(dfa, reportId, fileId).run.flatMap(e => e match {
        case -\/(err) if (attempts < 8) =>
          val sleeptime = 1000 + (Math.pow(2,attempts)*100) //exponential backoff
          Thread.sleep(sleeptime.toLong)
          rec(attempts+1).run
        case _ => 
          e.toBravoM.run
      }) //.liftM[({ type l[a[_],b] = EitherT[SFuture, JazelError, b]})#l] //.run._2.toBravoM
    rec(1)
    })
}

object LiveDart extends DartInternalAPI {
  import com.google.api.services.dfareporting.{Dfareporting, DfareportingScopes} 
  import scala.collection.JavaConversions._
  import com.google.api.services.dfareporting.model._
  import java.io.InputStream
  import org.joda.time.format.DateTimeFormat
  import bravo.apitest.dart.Data._
  import bravo.apitest.dart._
  import java.util.ArrayList
  import bravo.util.Util._

  def getDartAuth[A <: DartConfig]: BravoM[A, Dfareporting] = DartAuth.getCredentialService

  //FIXME: we need a fctry without a c =>, too confusing otherwise
  def runDartReport[A <: DartConfig](reportApi: Dfareporting, userid: Int, rid: Long): BravoM[A, Long] = 
    for {
      file <- fctry((c:A) => reportApi.reports().run(userid, rid).setSynchronous(false).execute())
    } yield { 
      file.getId()
    }
  
   /* 
  def getActivities[A <: DartConfig](reportApi: Dfareporting, rid: Long): BravoM[A, List[String]] =
    for {
      report <- fctry((c: A) => reportApi.reports().get(c.clientId, rid).execute())
      activities = report.getCriteria().getActivities()
      names <-  if (activities.containsKey("metricNames")) {
                  ftry[A,List[String]](activities.get("metricNames").asInstanceOf[ArrayList[Object]].toList.map(_.toString()))
                  //(sFutureMonad.point(activities.get("metricNames").asInstanceOf[ArrayList[Object]].toList.map(_.toString())))
                } else 
                  JazelError(None, "Cannot find metriNames in activities for report " + rid).left[List[String]].toBravoM
    } yield {
      names
    }
    */
  
  override def viewDartReports[A <: DartConfig](reportApi: Dfareporting, userid: Int): BravoM[A, List[AvailableReport]] = 
    for {
      reports <- fctry((c:A) => reportApi.reports().list(userid).execute() )  
      items   = (reports.getItems(): java.util.List[Report])
    } yield 
      items.toList.map(toAvailableReport(_))
 
  override def updateDartReport[A <: DartConfig](reportApi: Dfareporting, userid: Int, rid: Long, startDate: DateTime, endDate: DateTime): BravoM[A, Unit]= 
    for {
      report    <- fctry((c:A) => reportApi.reports().get(userid, rid).execute())
      criteria  <- fctry((c:A) =>  
                    report.getCriteria().setDateRange(new DateRange().setStartDate(toGoogleDate(startDate)).setEndDate(toGoogleDate(endDate)))
                  )
      _         = report.setCriteria(criteria)
      _         <- ftry(reportApi.reports().update(userid, rid, report).execute())
    } yield
      ()
  
   
  
  override def downloadReport[A <: DartConfig](reportApi: Dfareporting, reportid: Long, fid: Long): BravoM[A, String] = 
    for {
      filehandle  <- fctry((c:A) => reportApi.files().get(reportid, fid))
      file        <- fctry((c: A) => filehandle.execute())
      is          <- if (file.getStatus != "REPORT_AVAILABLE") 
                      JazelError(none, "Report " + reportid + "is not available").left[InputStream].toBravoM[A]
                    else
                      fctry((c: A) => filehandle.executeMediaAsInputStream()) 
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

