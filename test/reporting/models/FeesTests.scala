package reporting.models

import java.util.UUID

import org.joda.time.format.DateTimeFormat
import org.joda.time.{Interval, DateTime}
import reporting.engine.{JodaTime, SimpleReportGenerator}
import reporting.models.Fees.{FeesLookup, LabeledFeesLookup, ServingFees}
import reporting.models.ds.{DateSelector, dart}
import reporting.util.TUIReportHelper

import scala.util.Random

import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._

import scala.util.matching.Regex

@RunWith(classOf[JUnitRunner])
class FeesTests extends Specification with org.specs2.matcher.ThrownExpectations {

  import JodaTime.mkSpan
  def dt(str: String) = JodaTime.mkDateTime(str)

  "Fees" should {

    "compute serving fees for part of a month" >> {
      val servingFees = ServingFees(
        accountId = None,
        label = "banner",
        cpm = 1,
        cpc = 0.25,
        validFrom = None,
        validUntil = None)
      val span = mkSpan(dt("2001-01-01"), dt("2001-01-07"))
      val impressions = List(
        dt("2001-01-01") -> 100,
        dt("2001-01-02") -> 100,
        dt("2001-01-03") -> 100,
        dt("2001-01-04") -> 100,
        dt("2001-01-05") -> 100,
        dt("2001-01-06") -> 100,
        dt("2001-01-07") -> 100)

      val lookup = new FeesLookup(List(servingFees))
      for (labelLookup <- lookup("banner")) {
        val fees = labelLookup(span)

      }

      success
    }
  }
}