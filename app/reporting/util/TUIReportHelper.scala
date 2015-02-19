package reporting.util

import java.util.UUID

import org.joda.time.DateTime
import reporting.models.Fees.ServingFees
import reporting.models.Fees.AgencyFees
import reporting.models._
import reporting.models.ds.{DateSelector, dart}

import scala.collection.GenIterableLike
import scala.util.matching.Regex

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
                          views: List[View],
                          ds: DartDS,
                          fieldBindings: List[FieldBinding],
                          report: Report,
                          servingFees: List[ServingFees] = List(),
                          agencyFees: List[AgencyFees] = List()) {

    val fieldsLookup = fields.map(f => f.varName -> f).toMap
    val fieldsLookupById = fields.map(f => f.id.get -> f).toMap
  }

  def TUISearchPerformanceRO() = searchPerformanceRO

  lazy val searchPerformanceRO = {

    val account = Account(randUUID, "TUI")

    val servingFeesList = List(Fees.ServingFees(
      accountId = None,
      label = "banner",
      cpm = 1.0,
      cpc = 0.25,
      validFrom = None,
      validUntil = None
    ))

    val agencyFeesList = List(Fees.AgencyFees(
      accountId = None,
      label = "display",
      minMonthlyFee = (0d).some,
      spendRanges = List(
        Fees.SpendRange(    0, 10000L.some, None, (0.15).some),
        Fees.SpendRange(10000, 25000L.some, None, (0.14).some),
        Fees.SpendRange(20000, 40000L.some, None, (0.13).some),
        Fees.SpendRange(40000, 75000L.some, None, (0.12).some),
        Fees.SpendRange(75000,        None, None, (0.11).some)
      ),
      validFrom = None,
      validUntil = None
    ))


    val fields = {
      import Field._

      val currency = FormatTypes.Currency
      val numberWhole = FormatTypes.WholeNumber
      val numberFrac = FormatTypes.FractionalNumber
      val percent = FormatTypes.Percentage

      val summation = FooterTypes.Summation.some
      val average = FooterTypes.Average.some

      List(
        // Bound fields
        Field(randUUID, "spend", None, Display(None, currency, summation).some),
        Field(randUUID, "contact", None, Display(None, numberWhole, summation).some),
        Field(randUUID, "inquiries", None, Display(None, numberWhole, summation).some),
        Field(randUUID, "apps", None, Display(None, numberWhole, summation).some),
        Field(randUUID, "calls", Formula("0").some, Display(None, numberWhole, summation).some),
        Field(randUUID, "impressions", None, Display(None, numberWhole, summation).some),
        Field(randUUID, "clicks", None, Display(None, numberWhole, summation).some),
        // Derived fields (raw transform)
        //Field(randUUID, "avgPos_"),
        //Field(randUUID, "avgPosSUMPROD", Formula("avgPos_ * impressions", isRawTransform = true).some),
        // Derived fields (fees)
        Field(randUUID, "cpcFees",
          Formula("""fees.serving("banner").cpc(clicks)""").some,
          Display(None, currency, summation).some),
        Field(randUUID, "cpmFees",
          Formula("""fees.serving("banner").cpm(impressions)""").some,
          Display(None, currency, summation).some),
        Field(randUUID, "dispFees",
          Formula("""fees.agency("display").monthly(spend, impressions)""").some,
          Display(None, currency, summation).some),
        //Field(randUUID, "avgPos",
        //  Formula("avgPosSUMPROD / impressions").some,
        //  Display("Avg. Pos.".some, numberFrac, average).some),

        // Derived fields (with fees)
        Field(randUUID, "allFees",
          Formula("cpcFees + cpmFees + dispFees").some,
          Display("Fees".some, currency, summation).some),
        Field(randUUID, "totalSpend",
          Formula("spend + allFees").some,
          Display("Total Spend".some, currency, summation).some),
        Field(randUUID, "cpc",
          Formula("totalSpend / clicks").some,
          Display(None, currency, average).some),
        //Field(randUUID, "AvgPosition", None),
        Field(randUUID, "cpl",
          Formula("totalSpend / totalLeads").some,
          Display(None, currency, average).some),

        // Derived fields (without fees)
        Field(randUUID, "totalSpendNoFees",
          Formula("spend").some,
          Display("Total Spend (NoFees)".some, currency, summation).some),
        Field(randUUID, "cpcNoFees",
          Formula("totalSpendNoFees / clicks").some,
          Display("CPC (NoFees)".some, currency, average).some),
        Field(randUUID, "cpcDelta",
          Formula("cpc - cpcNoFees").some,
          Display("CPC (Markup)".some, currency, average).some),
        //Field(randUUID, "AvgPosition", None),
        Field(randUUID, "cplNoFees",
          Formula("totalSpendNoFees / totalLeads").some,
          Display("CPL (NoFees)".some, currency, average).some),

        // Dervied fields (non-fee related)
        Field(randUUID, "ctr",
          Formula("clicks / impressions").some,
          Display(None, percent, average).some),
        Field(randUUID, "totalLeads",
          Formula("contact + inquiries + apps + calls").some,
          Display("Total Leads".some, numberWhole, summation).some),
        Field(randUUID, "ssc",
          Formula("totalLeads / clicks").some,
          Display(None, percent, average).some)
      )
    }
    val fieldsLookup = fields.map(f => f.varName -> f).toMap

    val template = Template(randUUID, "Search Performance", fields.map(_.id).flatten)

    val viewWithoutFees = View(
      UUID.fromString("087f5861-cc16-4bc5-b00e-c43ac2f83203").some,
      "Admin view (with and without fees)",
      template.id.get,
      List(
        "totalSpendNoFees", "totalSpend", "allFees",
        "impressions", "clicks", "ctr", "cpcNoFees", "cpc", "cpcDelta",
        "contact", "inquiries", "apps", "totalLeads",
        "cplNoFees", "cpl",
        "ssc")
        .map(fieldsLookup).map(_.id).flatten,
      fields.find(_.varName == "totalSpendNoFees").map(f => FieldSort(f.id.get, false)),
      List(),
      List(
        Chart.Pie("Visitors by Category", fieldsLookup("impressions").id.get),
        Chart.Bar("Cost per Visitor", fieldsLookup("cpcNoFees").id.get)))

    val viewWithFees = View(
      UUID.fromString("1eca53d7-0867-4683-bfa1-20729a729345").some,
      "Client view (with fees)",
      template.id.get,
      List(
        "totalSpend", "impressions", "clicks", "ctr", "cpc",
        "contact", "inquiries", "apps", "totalLeads", "cpl", "ssc")
        .map(fieldsLookup).map(_.id).flatten,
      fields.find(_.varName == "totalSpend").map(f => FieldSort(f.id.get, false)),
      List(),
      List(
        Chart.Pie("Visitors by Category", fieldsLookup("impressions").id.get),
        Chart.Bar("Cost per Visitor", fieldsLookup("cpc").id.get)))

    val viewBoth = View(
      UUID.fromString("57eb513a-a6fe-46f1-b929-b91ebfe6f08c").some,
      "Client view (with fees + 4 charts)",
      template.id.get,
      List(
        "totalSpend", "impressions", "clicks", "ctr", "cpc",
        "contact", "inquiries", "apps", "totalLeads", "cpl", "ssc")
        .map(fieldsLookup).map(_.id).flatten,
      fields.find(_.varName == "totalSpend").map(f => FieldSort(f.id.get, false)),
      List(),
      List(
        Chart.Pie("Visitors by Category", fieldsLookup("impressions").id.get),
        Chart.Pie("Leads by Category", fieldsLookup("totalLeads").id.get),
        Chart.Bar("Cost per Visitor", fieldsLookup("cpc").id.get),
        Chart.Bar("Cost by Category", fieldsLookup("totalSpend").id.get)))

    val views = List(viewWithoutFees, viewWithFees, viewBoth)

    // XXX(dk): this can't be serialised as is!!
    val keySelector = new ds.KeySelector {

      import bravo.util.matching.RegexOps.implicits._

      def select(attrs: Map[String, String]): Option[String] = {
        val patterns = List[(String, String)](
          "^(Brand).*$" -> "$1",
          "^(Partners)hips.*$" -> "$1",
          "^(Accredited)$" -> "$1",
          "^(Auto Suggest).*$" -> "$1",
          "^(Bachelors).*" -> "$1",
          "^Online (Bachelors).*$" -> "$1",
          "^(Certificate).*$" -> "$1",
          "^(Anthem College).*$" -> "$1",
          "^(Competitors).*$" -> "Anthem College",
          "^(Content).*$" -> "$1",
          "^Remarketing (Content).*$" -> "$1",
          "^(Distance Learning).*$" -> "$1",
          "^(Doctorate).*" -> "$1",
          "^(Masters).*$" -> "$1",
          "^(Military).*$" -> "$1",
          "^(Degree).*$" -> "$1",
          "^(Veterans).*$" -> "$1",
          "^(PhD).*$" -> "$1",
          "^(Undergraduate).*$" -> "$1",
          "^(Veterans).*$" -> "$1",
          "^(Online).*$" -> "$1")
          .map(p => p._1.r -> p._2)

        val campaign = attrs.getOrElse("Paid Search Campaign", "")

        patterns
          .findFirst({case (regex,replacement) =>
            regex.maybeReplaceFirstIn(campaign, replacement)
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
      //new FieldBinding(fieldsLookup("avgPos_").id.get, dartDs.dsId.get,
      //  "Paid Search Average Position"),
      new FieldBinding(fieldsLookup("spend").id.get, dartDs.dsId.get,
        "Paid Search Cost"),
      new FieldBinding(fieldsLookup("impressions").id.get, dartDs.dsId.get,
        "Paid Search Impressions"),
      new FieldBinding(fieldsLookup("clicks").id.get, dartDs.dsId.get,
        "Paid Search Clicks"),
      new FieldBinding(fieldsLookup("contact").id.get, dartDs.dsId.get,
        "TUI  Home Page : Arrival: Paid Search Actions"),

      // SHOULD BE:
      // Marketing LP Inquiry Confirmation
      // Trident.edu Inquiry Confirmation
      // Ph.D. Inquiry Confirmation
      // Partnership Inquiry Confirmation
      // Net Price Calculator Inquiry Confirmation
      new FieldBinding(fieldsLookup("inquiries").id.get, dartDs.dsId.get,
        "TUI Confirmation : PHD Request Info: Paid Search Actions"),

      // SHOULD BE: Step 3 (was step 4)
      new FieldBinding(fieldsLookup("apps").id.get, dartDs.dsId.get,
        "TUI Counter : Step 3 (was step 4): Paid Search Actions")
      //new FieldBinding(fieldsLookup("Calls").id.get, dartDs.dsId.get, "")
    )

    val report = for {
      aid <- account.id
      tid <- template.id
      vid <- views(0).id
      dsbid <- dartDs.dsId.map(id => DataSourceBinding(id))
    } yield Report(aid, tid, vid, List(dsbid), fieldBindings)

    ReportObjects(
      account,
      fields,
      template,
      views,
      dartDs,
      fieldBindings,
      report.get,
      servingFeesList,
      agencyFeesList)
  }
}
