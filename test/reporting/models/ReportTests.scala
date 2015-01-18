package reporting.models

import java.util.UUID
import javax.xml.crypto.KeySelectorException

import org.joda.time.DateTime
import reporting.engine.SimpleReportGenerator
import reporting.models.ds.{DateSelector, dart, DataSources}
import DataSources.DataSource

import scala.util.Random

import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._

import scala.util.matching.Regex

object TUIReportHelper {

  import scalaz._, Scalaz._

  def randUUID = UUID.randomUUID().some

  def sampleRawRow() = Map(
    "Search Campaign Name" -> "Brand",
    "Date" -> "2014-01-01",
    "Paid Search Cost" -> BigDecimal(201.03),
    "Paid Search Impressions" -> BigDecimal(6000),
    "Paid Search Clicks" -> BigDecimal(20),
    "TUI  Home Page : Arrival: Paid Search Actions" -> BigDecimal(10),
    "TUI Confirmation : PHD Request Info: Paid Search Actions" -> BigDecimal(5),
    "TUI Apply Online : Step 0 - New Application: Paid Search Actions" -> BigDecimal(1)
  )

  def sampleAttributes = ds.DataSource.Attributes()

  def sampleData = List[ds.DataSource.Row](
    ds.DataSource.BasicRow(List("Brand"), DateTime.parse("2014-01-01"), sampleAttributes)
  )

  case class ReportObjects(
                          account: Account,
                          fields: List[Field],
                          template: Template,
                          view: View,
                          ds: ds.DataSource,
                          fieldBindings: List[FieldBinding],
                          report: Report)

  def TUISearchPerformanceRO() = {

    val account = Account(randUUID, "TUI")

    val fields = List(
      Field(randUUID, "Spend", None),
      // Spend = [Cost] + ([Clicks]f * [PpcTrackingRate])
      Field(randUUID, "TotalSpend", "Spend".some), // Should include fees!
      Field(randUUID, "Impressions", None),
      Field(randUUID, "Clicks", None, None),
      Field(randUUID, "CTR", "Clicks / Impressions".some),
      Field(randUUID, "CPC", "Clicks / Spend".some),
      //Field(randUUID, "AvgPosition", None),
      Field(randUUID, "Contact", None),
      Field(randUUID, "Inquiries", None),
      Field(randUUID, "Apps", None),
      Field(randUUID, "Calls", "0".some),
      Field(randUUID, "TotalLeads", "Contact + Inquiries + Apps + Calls".some),
      Field(randUUID, "CPL", "TotalLeads / Spend".some),
      // What is SSC??
      Field(randUUID, "SSC", "TotalLeads / Clicks".some)
    )
    val fieldsLookup = fields.map(f => f.label -> f).toMap

    val template = Template(randUUID, "Search Performance", fields.map(_.id).flatten)

    val view = View(
      randUUID,
      "General User",
      template.id.get,
      fields.filterNot(_.label == "TotalSpend").map(_.id).flatten,
      List(),
      List())

    // XXX(dk): this can't be serialised as is!!
    val keySelector = new ds.KeySelector {
      def select(attrs: Map[String, String]): Option[String] = {
        val patterns = List[(String, Regex.Match=>String)](
          "^(Brand).*$" -> (m => m.group(0)),
          "^(Partners)hips.*$" -> (m => m.group(0)),
          "^(Accredited)$" -> (m => m.group(0)),
          "^(Auto Suggest).*$" -> (m => m.group(0)),
          "^(Bachelors).*" -> (m => m.group(0)),
          "^Online (Bachelors).*$" -> (m => m.group(0)),
          "^(Certificate).*$" -> (m => m.group(0)),
          "^(Anthem College).*$" -> (m => m.group(0)),
          "^(Competitors).*$" -> (_ => "Anthem College"), // => Anthem College
          "^(Content).*$" -> (m => m.group(0)),
          "^Remarketing (Content).*$" -> (m => m.group(0)),
          "^(Distance Learning).*$" -> (m => m.group(0)),
          "^(Doctorate).*" -> (m => m.group(0)),
          "^(Masters).*$" -> (m => m.group(0)),
          "^(Military).*$" -> (m => m.group(0)),
          "^(Degree).*$" -> (m => m.group(0)),
          "^(Veterans).*$" -> (m => m.group(0)),
          "^(PhD).*$" -> (m => m.group(0)),
          "^(Undergraduate).*$" -> (m => m.group(0)),
          "^(Veterans).*$" -> (m => m.group(0)),
          "^(Online).*$" -> (m => m.group(0)))
          .map(p => p._1.r -> p._2)

        val campaign = attrs("Paid Search Campaign")

        patterns
          .collectFirst({case (r,t) => r
            .findFirstMatchIn(campaign)
            .map(t)
          })
          .getOrElse("Other".some)
      }
    }

    val dateSelector = DateSelector("Date", "yyyy-MM-dd")

    val dartDs = dart.DartDS(
      randUUID,
      "Dart DS for Search Performance",
      "", // dart query-id
      account.id.get,
      List(keySelector),
      dateSelector
    )

    val fieldBindings = List(
      new FieldBinding(fieldsLookup("Spend").id.get, dartDs.dsId.get,
        "Paid Search Cost"),
      new FieldBinding(fieldsLookup("Impressions").id.get, dartDs.dsId.get,
        "Paid Search Impressions"),
      new FieldBinding(fieldsLookup("Clicks").id.get, dartDs.dsId.get,
        "Paid Search Clicks"),
      new FieldBinding(fieldsLookup("Contact").id.get, dartDs.dsId.get,
        "TUI  Home Page : Arrival: Paid Search Actions"),

      // SHOULD BE:
      // Marketing LP Inquiry Confirmation
      // Trident.edu Inquiry Confirmation
      // Ph.D. Inquiry Confirmation
      // Partnership Inquiry Confirmation
      // Net Price Calculator Inquiry Confirmation
      new FieldBinding(fieldsLookup("Inquiries").id.get, dartDs.dsId.get,
        "TUI Confirmation : PHD Request Info: Paid Search Actions"),

      // SHOULD BE: Step 3 (was step 4)
      new FieldBinding(fieldsLookup("Apps").id.get, dartDs.dsId.get,
        "TUI Apply Online : Step 0 - New Application: Paid Search Actions")
      //new FieldBinding(fieldsLookup("Calls").id.get, dartDs.dsId.get, "")
    )

    val report = for {
      aid <- account.id
      tid <- template.id
      vid <- view.id
      dsbid <- dartDs.dsId.map(id => DataSourceBinding(id))
    } yield Report(aid, tid, vid, List(dsbid), fieldBindings)

    ReportObjects(
      account,
      fields,
      template,
      view,
      dartDs,
      fieldBindings,
      report.get)
  }
}

@RunWith(classOf[JUnitRunner])
class ReportTests extends Specification with org.specs2.matcher.ThrownExpectations {

  "ReportGenerator" should {

    "generate a report with expected values" >> {
      val ro = TUIReportHelper.TUISearchPerformanceRO()
      val gen = new SimpleReportGenerator(ro.report, ro.fields)

      val start = DateTime.parse("2014-01-01")
      val end = DateTime.parse("2014-01-31")

      // TODO: Transform row data into DataSource.Rows (apply date/key selectors)
      //val dsf = new ds.DataSource.DataSourceRowFactory(ro.ds)
      val data = TUIReportHelper.sampleData//dsf.process(TUIReportHelper.sampleRawRow())
      val res = gen.getReport(ro.ds, data)(start, end)

      // TODO: confirm output matches expected
    }
  }
}
