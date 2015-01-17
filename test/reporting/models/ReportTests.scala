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

object TUIReportHelper {

  import scalaz._, Scalaz._

  def randUUID = UUID.randomUUID().some

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
      Field(randUUID, "CPL", "TotalLeads / Spend".some)
      // What is SSC??
      //Field(randUUID, "SSC", None)
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
        val patterns = List(
          "^(Bachelors).*$",
          "^(Anthem College).*$",
          "^(Masters).*$",
          "^(Military).*$",
          "^(Veterans).*$")
          .map(_.r)

        val campaign = attrs("Paid Search Campaign")

        patterns
          .collectFirst({case r => r
            .findFirstMatchIn(campaign)
            .map(_.group(0))
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
      new FieldBinding(fieldsLookup("Spend").id.get, dartDs.dsId.get, "Paid Search Cost"),
      new FieldBinding(fieldsLookup("Impressions").id.get, dartDs.dsId.get, "Paid Search Impressions"),
      new FieldBinding(fieldsLookup("Clicks").id.get, dartDs.dsId.get, "Paid Search Clicks"),
      new FieldBinding(fieldsLookup("Contact").id.get, dartDs.dsId.get, "TUI  Home Page : Arrival: Paid Search Actions"),
      new FieldBinding(fieldsLookup("Inquiries").id.get, dartDs.dsId.get, "TUI Confirmation : PHD Request Info: Paid Search Actions"),
      new FieldBinding(fieldsLookup("Apps").id.get, dartDs.dsId.get, "TUI Apply Online : Step 0 - New Application: Paid Search Actions")
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

      val start = DateTime.now()
      val end = DateTime.now().plusMonths(-1)

      // TODO: ro.ds doesn't have a way of getting mock data!
      val res = gen.getReport(ro.ds)(start, end)

      // TODO: confirm output matches expected
    }
  }
}
