package bravo.api.dart

import scalaz._
import Scalaz._
import org.joda.time.DateTime
import scala.concurrent.ExecutionContext.Implicits.global
import bravo.core.Util._

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
  import bravo.core._
  import bravo.api.dart.DateUtil._

  def getReport(reportId: Int, startDate: DateTime, endDate: DateTime): BravoM[DownloadedReport] = ((c: Config) => {
    val reportIdCache = c.cache(reportId)  
    val cachedDays = DateUtil.findLargestRange(reportIdCache, startDate, endDate)
    val missingDays = DateUtil.findMissingDates(cachedDays, startDate.toLocalDate(), endDate.toLocalDate())
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
        Monad[BravoM].point( DownloadedReport(reportId, startDate, endDate, cachedDays) )
    }
    
   } ).toBravoM
  
  
  def getReportUncached(reportId: Int, startDate: DateTime, endDate: DateTime): BravoM[DownloadedReport] = ((c:Config) => 
        for {
          dfa <- c.api.getDartAuth
          _   <- c.api.updateDartReport(dfa, c.clientId, reportId, startDate, endDate)
          id  <- c.api.runDartReport(dfa, c.clientId, reportId)
          rs  <- fulfillReport(dfa, reportId, id, 1) //TODO: take Delay Multiplier from config
          parsed = ReportParser.parse(rs)
          rep    = groupDates(parsed)
          //_   <- put(c.updateCache(reportId, rep))
        } yield {
          DownloadedReport(reportId, startDate, endDate, rep)
        }       

    ).toBravoM


  private def fulfillReport(dfa: Dfareporting, reportId: Long, fileId: Long, delayMultiplier: Int): BravoM[String] = ((c:Config) => {
    //not tailrec but we're not going that deep
    def rec(attempts: Int): BravoM[String] =  
      c.api.downloadReport(dfa, reportId, fileId).run.flatMap(e => e match {
        case -\/(err) if (attempts < 8) =>
          val sleeptime = 1000 + (Math.pow(2,attempts)*100) //exponential backoff
          Thread.sleep(sleeptime.toLong)
          rec(attempts+1).run
        case _ => 
          e.toBravoM.run
      }) //.liftM[({ type l[a[_],b] = EitherT[SFuture, JazelError, b]})#l] //.run._2.toBravoM
    rec(1)
    }).toBravoM
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
 
  def getDartAuth: BravoM[Dfareporting] = DartAuth.getCredentialService

  //FIXME: we need a fctry without a c =>, too confusing otherwise
  def runDartReport(reportApi: Dfareporting, userid: Int, rid: Long): BravoM[Long] = 
    for {
      file <- fctry(c => reportApi.reports().run(userid, rid).setSynchronous(false).execute())
    } yield { 
      file.getId()
    }
  
    
  def getActivities(reportApi: Dfareporting, rid: Long): BravoM[List[String]] =
    for {
      report <- fctry(c => reportApi.reports().get(c.clientId, rid).execute())
      activities = report.getCriteria().getActivities()
      names <-  if (activities.containsKey("metricNames")) {
                  Monad[BravoM].point(activities.get("metricNames").asInstanceOf[ArrayList[Object]].toList.map(_.toString()))
                } else 
                  ("Cannot find metriNames in activities for report " + rid).toJazelError.left[List[String]].toBravoM
    } yield {
      names
    }

  override def viewDartReports(reportApi: Dfareporting, userid: Int): BravoM[List[AvailableReport]] = 
    for {
      reports <- fctry(c => reportApi.reports().list(userid).execute() )  
      items   = (reports.getItems(): java.util.List[Report])
    } yield 
      items.toList.map(toAvailableReport(_))
  
  override def updateDartReport(reportApi: Dfareporting, userid: Int, rid: Long, startDate: DateTime, endDate: DateTime): BravoM[Unit]= ((c:Config) => 
    for {
      report    <- ftry(reportApi.reports().get(userid, rid).execute())
      criteria  <- ftry(  
                    report.getCriteria().setDateRange(new DateRange().setStartDate(toGoogleDate(startDate)).setEndDate(toGoogleDate(endDate)))
                  )
      _         = report.setCriteria(criteria)
      _         <- ftry(reportApi.reports().update(userid, rid, report).execute())
    } yield
      ()
  ).toBravoM
  
  override def downloadReport(reportApi: Dfareporting, reportid: Long, fid: Long): BravoM[String] = ((c:Config) => 
    for {
      filehandle  <- ftry(reportApi.files().get(reportid, fid))
      file        <- ftry(filehandle.execute())
      is          <- if (file.getStatus != "REPORT_AVAILABLE") 
                      ("Report " + reportid + "is not available".toJazelError).toJazelError.left[InputStream].toBravoM
                    else
                      ftry(filehandle.executeMediaAsInputStream()) 
    } yield {
      val reportData = scala.io.Source.fromInputStream(is).mkString  
      reportData
    }
  ).toBravoM

  def toAvailableReport(r: Report):  AvailableReport = AvailableReport(r.getId(), 
    r.getName(), 
    r.getFormat(),
    r.getFileName(),
    new DateTime(r.getCriteria().getDateRange().getStartDate().toString),
    new DateTime(r.getCriteria().getDateRange().getEndDate()))
 
}

