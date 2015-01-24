package reporting.util

import java.util.UUID

import org.joda.time.DateTime
import reporting.models._
import reporting.models.ds.{DateSelector, dart}

import scala.collection.GenIterableLike
import scala.util.matching.Regex
import scala.util.matching.Regex.Match

/**
 * Created by dain on 19/01/2015.
 */
object TUIReportHelper {

  import reporting.models.ds.DataSource.BasicRow

  import scalaz.Scalaz._
  import scalaz._

  def randUUID = UUID.randomUUID().some

  def sampleRawRow() = Map(
    "Paid Search Campaign" -> "Brand",
    "Date" -> "2014-01-01") ++ sampleNumericRow()

  def sampleNumericRow() = Map(
    "Paid Search Cost" -> BigDecimal(201.03),
    "Paid Search Impressions" -> BigDecimal(6000),
    "Paid Search Clicks" -> BigDecimal(20),
    "TUI  Home Page : Arrival: Paid Search Actions" -> BigDecimal(10),
    "TUI Confirmation : PHD Request Info: Paid Search Actions" -> BigDecimal(5),
    "TUI Counter : Step 3 (was step 4): Paid Search Actions" -> BigDecimal(1)
  )

  def sampleAttributes = ds.DataSource.Attributes.fromMap(sampleNumericRow())

  def sampleData = List(
    BasicRow(List("Brand"), DateTime.parse("2014-01-01"), sampleAttributes),
    BasicRow(List("Brand"), DateTime.parse("2014-01-01"), sampleAttributes),
    BasicRow(List("Content"), DateTime.parse("2014-01-01"), sampleAttributes),
    BasicRow(List("Brand"), DateTime.parse("2014-01-31"), sampleAttributes),
    BasicRow(List("Content"), DateTime.parse("2014-01-31"), sampleAttributes)
  )

  import reporting.models.ds.dart.DartDS

  implicit class CollectionOps[+A, B <: Iterable[A]](col: GenIterableLike[A, B]) {
    def findFirst[B](f: A => Option[B]): Option[B] = {
      col.repr.view.map(f).collectFirst({
        case Some(x) => x
      })
    }
  }

  //Can we move this out of the TUI specific report helper?
  case class ReportObjects(
                          account: Account,
                          fields: List[Field],
                          template: Template,
                          view: View,
                          ds: DartDS,
                          fieldBindings: List[FieldBinding],
                          report: Report) {

    val fieldsLookup = fields.map(f => f.label -> f).toMap
    val fieldsLookupById = fields.map(f => f.id.get -> f).toMap
  }

  def TUISearchPerformanceRO() = {

    val account = Account(randUUID, "TUI")

    val fields = List(
      Field(randUUID, "Spend", None),
      // Spend = [Cost] + ([Clicks]f * [PpcTrackingRate])
      Field(randUUID, "TotalSpend", "currency(Spend)".some), // Should include fees!
      Field(randUUID, "Impressions", None),
      Field(randUUID, "Clicks", None, None),
      Field(randUUID, "CTR", "percentage(Clicks / Impressions)".some),
      Field(randUUID, "CPC", "currency(TotalSpend / Clicks)".some),
      //Field(randUUID, "AvgPosition", None),
      Field(randUUID, "Contact", None),
      Field(randUUID, "Inquiries", None),
      Field(randUUID, "Apps", None),
      Field(randUUID, "Calls", "0".some),
      Field(randUUID, "TotalLeads", "Contact + Inquiries + Apps + Calls".some),
      Field(randUUID, "CPL", "currency(TotalSpend / TotalLeads)".some),
      // What is SSC??
      Field(randUUID, "SSC", "percentage(TotalLeads / Clicks)".some)
    )
    val fieldsLookup = fields.map(f => f.label -> f).toMap

    val template = Template(randUUID, "Search Performance", fields.map(_.id).flatten)

    val view = View(
      randUUID,
      "General User",
      template.id.get,
      fields.filterNot(_.label == "Spend").map(_.id).flatten,
      List(),
      List())

    // XXX(dk): this can't be serialised as is!!
    val keySelector = new ds.KeySelector {
      def select(attrs: Map[String, String]): Option[String] = {
        val patterns = List[(String, Regex.Match=>String)](
          "^(Brand).*$" -> (m => m.group(1)),
          "^(Partners)hips.*$" -> (m => m.group(1)),
          "^(Accredited)$" -> (m => m.group(1)),
          "^(Auto Suggest).*$" -> (m => m.group(1)),
          "^(Bachelors).*" -> (m => m.group(1)),
          "^Online (Bachelors).*$" -> (m => m.group(1)),
          "^(Certificate).*$" -> (m => m.group(1)),
          "^(Anthem College).*$" -> (m => m.group(1)),
          "^(Competitors).*$" -> (_ => "Anthem College"), // => Anthem College
          "^(Content).*$" -> (m => m.group(1)),
          "^Remarketing (Content).*$" -> (m => m.group(1)),
          "^(Distance Learning).*$" -> (m => m.group(1)),
          "^(Doctorate).*" -> (m => m.group(1)),
          "^(Masters).*$" -> (m => m.group(1)),
          "^(Military).*$" -> (m => m.group(1)),
          "^(Degree).*$" -> (m => m.group(1)),
          "^(Veterans).*$" -> (m => m.group(1)),
          "^(PhD).*$" -> (m => m.group(1)),
          "^(Undergraduate).*$" -> (m => m.group(1)),
          "^(Veterans).*$" -> (m => m.group(1)),
          "^(Online).*$" -> (m => m.group(1)))
          .map(p => p._1.r -> p._2)

        val campaign = attrs.getOrElse("Paid Search Campaign", "")

        patterns
          .findFirst({case (regex,getLabel) => regex
            .findFirstMatchIn(campaign)
            .map(getLabel)
          })
          .orElse("Other".some)
      }
    }

    val dateSelector = DateSelector("Date", "yyyy-MM-dd")

    val dartDs = dart.DartDS(
      randUUID,
      "Dart DS for Search Performance",
      "18158200", // dart query-id
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
        "TUI Counter : Step 3 (was step 4): Paid Search Actions")
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
