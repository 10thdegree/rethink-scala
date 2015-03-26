package bravo.api.dart

/* NOT FOR EXTERNAL USE*/
object InternalLiveDart extends DartInternalAPI {
  import scalaz._
  import Scalaz._
  import com.google.api.services.dfareporting.{Dfareporting, DfareportingScopes} 
  import scala.collection.JavaConversions._
  import com.google.api.services.dfareporting.model._
  import java.io.InputStream
  import org.joda.time._
  import org.joda.time.format.DateTimeFormat
  import bravo.api.dart.Data._
  import bravo.api.dart._
  import java.util.ArrayList
  import bravo.util.Util._
  import com.google.api.services.dfareporting.model._
  import com.google.api.services.dfareporting.model.Report._
  import DartData._

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
      floodlights     <- getActivityFields(reportApi, advertiserId)
      reportTemplate  = getReportTemplate(PaidSearch()).copy(activityIds = floodlights.map(_._2)) //just need the Ids of the floodlights, put into the template 
      reportId        <- createReportFromTemplate(reportApi, advertiserId, reportTemplate)
    } yield 
      reportId

  //we will want to add various report types here
  private def createReportFromTemplate(reportApi: Dfareporting, advertiserId: Long, template: ReportTemplate): BravoM[DartConfig, Long] = {
      val daterange = new DateRange().setRelativeDateRange("MONTH_TO_DATE")
      val dimensions = template.dimensions.map(dm => new SortedDimension().setName(dm))
      val mappedActivities = template.activityIds.map(dv => new DimensionValue().setDimensionName("dfa:activity").setId(dv.toString))
      val dimensionValue = new DimensionValue().setDimensionName("dfa:advertiser").setId(advertiserId.toString) 
      val metricsList = template.metrics 
      
      val criteria = new Criteria()
        .setDateRange(daterange)
        .setActivities( new Activities().setMetricNames(List("dfa:paidSearchActions")).setFilters(mappedActivities))
        .setDimensions(dimensions)
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
        .setName(BRAVO_PREFIX + "Search_"+advertiserId.toString)
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
  
  override def cloneReport(reportApi: Dfareporting, rid: Long, advertiserId: Option[Long], startDate: DateTime, endDate: DateTime): BravoM[DartConfig, Long]= 
    for {
      report    <- fctry((c: DartConfig) => reportApi.reports().get(c.clientId, rid).execute())
      //set criteria
                //report.getCriteria().filter(_.getName() != "advertiser:'d
      criteria  =  report.getCriteria().setDateRange(new DateRange().setStartDate(toGoogleDate(startDate)).setEndDate(toGoogleDate(endDate)))
      newr         =   report.setCriteria(criteria)
      newr      <- fctry((c:DartConfig) => reportApi.reports().insert(c.clientId, report).execute())
    } yield
      newr.getId
  
  override def deleteReport(reportApi: Dfareporting, rid: Long): BravoM[DartConfig, Unit] =
    for {
      _   <- fctry((c: DartConfig) => reportApi.reports().delete(c.clientId, rid).execute())
    } yield ()
  
  /*
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
    */
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
  
  def toAvailableReport(r: Report):  AvailableReport = {
    val d = for {
      criteria <- Option(r.getCriteria())
      range   <- Option(criteria.getDateRange())
      start   <- Option(range.getStartDate())
      end     <- Option(range.getEndDate())
    } yield (new DateTime(start.toString), new DateTime(end.toString))
    
    AvailableReport(r.getId(), 
      r.getName(), 
      r.getFormat(),
      r.getFileName(),
      d.map(_._1).getOrElse(new DateTime()),
      d.map(_._2).getOrElse(new DateTime())
    ) 
  }
}

