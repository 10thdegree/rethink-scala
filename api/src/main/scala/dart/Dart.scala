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
    } yield reports.filter(r => r.name.contains(BRAVO_PREFIX) && r.name.split("_")(1) == advertiserId.toString)
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



