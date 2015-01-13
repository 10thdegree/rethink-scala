package reporting.models

import java.util.UUID

import com.rethinkscala.Document
import org.joda.time.DateTime

object Fees {

  // Many of these
  // If accountId == None, then it is global
  // If validFrom/validUntil == None, then it never expires
  case class ServingFees(accountId: Option[UUID],
                         label: String, // e.g. "video", "banner"
                         cpm: Double,
                         cpc: Double,
                         validFrom: Option[DateTime],
                         validUntil: Option[DateTime]) extends Document

  case class SpendRange(minImpressions: Long,
                        maxImpressions: Long,
                        spendPercent: Double)

  case class MonthlyFee(year: Int, month: Int /* 1-12 */ , fee: Double)

  // If accountId == None, then it is global
  // If validFrom/validUntil == None, then it never expires
  case class AgencyFees(accountId: Option[UUID],
                        label: String, // e.g. "display", "search"
                        spendRanges: List[SpendRange],
                        monthlyFees: List[MonthlyFee],
                        validFrom: Option[DateTime],
                        validUntil: Option[DateTime]) extends Document

}
