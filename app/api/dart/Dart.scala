package bravo.api.dart

import scalaz._
import scalaz.Free._
import Scalaz._
import org.joda.time.DateTime
import scala.concurrent.{Future,Promise}
import scala.concurrent.ExecutionContext.Implicits.global
import bravo.core.Util._
/*
Instead of polling for specific reports, we hsould just havea a queue of reports waiting to be fulfilled, get all the file handles, and then check them all at once? or no?
//THOUGHTS:
*/

object Dart {
  import com.google.api.services.dfareporting.Dfareporting
  import scala.annotation.tailrec
  import bravo.api.dart.Data._
  
  def testDartReport(dartApi: DartInternalAPI) = 
    getReport(1297324, 15641682, new DateTime().minusWeeks(1), new DateTime())

  def getReport(clientId: Int, reportId: Int, startDate: DateTime, endDate: DateTime): BravoM[DownloadedReport] = ((c:Config) => 
    for {
      dfa <- c.api.getDartAuth
      _   =  println("got a dfa, " + dfa )
      _   <- c.api.updateDartReport(dfa, clientId, reportId, startDate, endDate)
      _    = println("UPDATED the report")
      id  <- c.api.runDartReport(dfa, clientId, reportId)
      _   = println("updated the report!")
      rep <- fulfillReport(dfa, reportId, id, 5)
    } yield {
      rep
    }).toBravoM
 
  private def fulfillReport(dfa: Dfareporting, reportId: Long, fileId: Long, delayMultiplier: Int): BravoM[DownloadedReport] = ((c:Config) => {
    //not tailrec but we're not going that deep
    def rec(attempts: Int): BravoM[DownloadedReport] =  
      c.api.downloadReport(dfa, reportId, fileId).run.flatMap(e => e match {
        case -\/(err) if (attempts < 5) =>
          val sleeptime = attempts*delayMultiplier*1000
          println("Ok we are sleeping for " + sleeptime + " err =" + err)
          Thread.sleep(sleeptime)
          println("done sleeping")
          rec(attempts+1).run
        case _ => 
          println("OK we have an result " + e)
          e.toBravoM.run
      }).run.toBravoM
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
 
  def getDartAuth: BravoM[Dfareporting] = DartAuth.getCredentialService

  def runDartReport(reportApi: Dfareporting, userid: Int, rid: Long): BravoM[Long] = 
    for {
      file <- fctry(c => reportApi.reports().run(userid, rid).setSynchronous(false).execute())
    } yield { 
      file.getId()
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
  
  override def downloadReport(reportApi: Dfareporting, reportid: Long, fid: Long): BravoM[DownloadedReport] = ((c:Config) => 
    for {
      filehandle  <- ftry(reportApi.files().get(reportid, fid))
      file        <- ftry(filehandle.execute())
      is          <- if (file.getStatus != "REPORT_AVAILABLE") 
                      ("Report " + reportid + "is not available".toJazelError).toJazelError.left[InputStream].toBravoM
                    else
                      ftry(filehandle.executeMediaAsInputStream()) 
    } yield {
      val reportData = scala.io.Source.fromInputStream(is).mkString  
      DownloadedReport(reportid,reportData)
    }
  ).toBravoM

  def toAvailableReport(r: Report):  AvailableReport = AvailableReport(r.getId(), 
    r.getName(), 
    r.getFormat(),
    r.getFileName(),
    new DateTime(r.getCriteria().getDateRange().getStartDate().toString),
    new DateTime(r.getCriteria().getDateRange().getEndDate()))
 /*
  def test() =
    for {
      dfa <- DartAuth.unsafeGetReporting()
      _   <- updateDartReport(dfa,1297324,15641682, new DateTime().minusWeeks(1),new DateTime())
     id  <- runDartReport(dfa,1297324, 15641682)
     _    = Thread.sleep(2000)
     downloadedReport <- downloadReport(dfa, 15641682, id)
    } yield {
      println("id = " + id)
      print("process the report here?")
    }
  */
}

