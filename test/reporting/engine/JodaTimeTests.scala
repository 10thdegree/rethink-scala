package reporting.engine

import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class JodaTimeTests extends Specification with org.specs2.matcher.ThrownExpectations {

  import JodaTime.mkSpan
  def dt(dateStr: String) = JodaTime.mkDateTime(dateStr)

  "JodaTime" should {

    "determine a whole month is a single month" >> {
      val span = mkSpan(dt("2001-01-01"), dt("2001-01-31"))
      JodaTime.breakIntoMonths(span).size === 1
    }

    "determine a range across 2 months is 2 months" >> {
      val span = mkSpan(dt("2001-01-01"), dt("2001-02-01"))
      JodaTime.breakIntoMonths(span).size === 2
    }

    "determine a range across 2 whole months is 2 months" >> {
      val span = mkSpan(dt("2001-01-01"), dt("2001-02-28"))
      JodaTime.breakIntoMonths(span).size === 2
    }

    "determine a range across 2 partial months is 2 months" >> {
      val span = mkSpan(dt("2001-01-15"), dt("2001-02-01"))
      JodaTime.breakIntoMonths(span).size === 2
    }

    "determine same month different years is 13 months" >> {
      val span = mkSpan(dt("2001-01-01"), dt("2002-01-01"))
      JodaTime.breakIntoMonths(span).size === 13
    }
  }
}
