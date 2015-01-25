package reporting.models

import java.util.UUID

import com.rethinkscala.Document
import org.joda.time.{Years, Interval, DateTime}
import reporting.engine.Joda

object Fees {

  trait Fees {
    def accountId: Option[UUID]
    def label: String
    def validFrom: Option[DateTime]
    def validUntil: Option[DateTime]

    lazy val dates = (validFrom.getOrElse(Joda.startOfTime), validUntil.getOrElse(Joda.endOfTime))
    lazy val validity = new Interval(dates._1, dates._2)

    /*
    def validFor(date: DateTime) = (validFrom, validUntil) match {
      case (None, None) => true // never expires
      case (None, Some(end)) => date < end // no start, but has expiration
      case (Some(start), None) => date > start // never expires, but has start
      case (Some(start), Some(end)) => date > start && date < end // Within validity
    }
    */

    def overlap(span: Interval) = Option(validity.overlap(span))
  }

  // Many of these
  // If accountId == None, then it is global
  // If validFrom/validUntil == None, then it never expires
  case class ServingFees(accountId: Option[UUID],
                         label: String, // e.g. "video", "banner"
                         cpm: Double,
                         cpc: Double,
                         validFrom: Option[DateTime],
                         validUntil: Option[DateTime]) extends Document with Fees

  // General use is probably to set [0 - MIN impressions] to a monthlyFee but no spendPercent,
  //   and set [MIN to None impressions] to a spendPercent, but no monthlyFee.
  // The spendPercent is computed by: impressions x [dependent spend field] x spendPercent
  // The monthlyFee will be applied proportionally by: [time interval] / [days in month] * monthlyFee
  // In the case more than one month is being reported on, each will be computed separately.
  case class SpendRange(minImpressions: Long,
                        maxImpressions: Option[Long],
                        monthlyFee: Option[Double],
                        spendPercent: Option[Double])

  // If accountId == None, then it is global
  // If validFrom/validUntil == None, then it never expires
  case class AgencyFees(accountId: Option[UUID],
                        label: String, // e.g. "display", "search"
                        spendRanges: List[SpendRange],
                        validFrom: Option[DateTime],
                        validUntil: Option[DateTime]) extends Document with Fees {

    def spend(impressions: Long) = {
      // compute spend
      0
    }
  }

  // XXX(dk): This implementation does not handle cases in which fees's valid ranges are nested;
  //   it expects them to be contiguous only.
  class FeesByLabelLookup[F <: Fees](label: String, fees: List[F]) {

    import Joda._
    val sortedFees = fees.sortBy(x => x.dates)

    def apply(searchSpan: Interval): List[F] = {

      case class State(span: Option[Interval], fees: List[F] = List())

      def fold(state: State, fee: F): State = state.span
        // Some interval remains to process, so let's process it
        .map(ss => Joda.overlapMatch(fee.validity, ss) match {

          // Fees should always subsume, but we have a portion with no valid fees
          case Joda.OverlapMatch(Some(invalid), Some(_), None) => state.copy(span = None, fees = fees :+ fee)

          // Partial fee match
          case Joda.OverlapMatch(None, Some(_), todo @ Some(_)) => state.copy(span = todo, fees = fees :+ fee)

          // No overlap, so just skip the current fee
          case Joda.OverlapMatch(Some(_), None, Some(_)) => state
        })
        // No more interval to process, so this is a NOOP
        .getOrElse(state)

      // TODO(dk): Fees are inherently month based, so we should probably instead
      //   compute the number of months in the date range, and return a list of
      //   FeesComputation[FeeType] for each month; within each FeesComputation may be multiple underlying fees
      //   objects if for some reason things got split midway through the month.
      //   To compute the actual values, a date range is still needed, since we may only be going part way through
      //   the month, e.g. feesComputation.fee(span, impressions(span))
      sortedFees.foldLeft(State(Some(searchSpan)))(fold).fees
    }
  }

  // Usage:
  // val servingFees = new FeesLookup(servingFeesList)("FooLabel")(dateSpan)
  // servingFees.foldLeft(0)((sum, fee) => sum + fee.cpm * impressions(fee.validity)/1000)
  // servingFees.foldLeft(0)((sum, fee) => sum + fee.cpc * clicks(fee.validity))
  //
  // val agencyFees = new FeesLookup(agencyFeesList)("FooLabel")(dateSpan)
  // agencyFees.foldLeft(0)((sum, fee) => sum + fee.spend(impressions(fee.validity)))
  //
  // NOTE that for each Fees object, we need a means to get the dependant field's
  //   value ONLY for the range that that fees object is valid for,
  //   e.g. impressions(range)
  class FeesLookup[F <: Fees](feesList: List[F]) {

    var global = feesList.find(_.accountId.isEmpty).map({ case (f) => new FeesByLabelLookup(f.label, List(f)) })
    var labels = feesList.groupBy(_.label).map({ case (k, fs) => k -> new FeesByLabelLookup(k, fs) }).toMap

    def apply(label: String) = labels.get(label).orElse(global)
  }
}
