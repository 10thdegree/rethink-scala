package reporting.engine.eval

import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class MultipartKeyTests extends Specification with org.specs2.matcher.ThrownExpectations {

  import MultipartKey.KeyMatch

  val keys = List(
    MultipartKey(List("A1", "B1", "C1")),
    MultipartKey(List("A1", "B1", "C2")),
    MultipartKey(List("A1", "B2", "C1")),
    MultipartKey(List("A1", "B2", "C2")),
    MultipartKey(List("A2", "B1", "C1")),
    MultipartKey(List("A2", "B1", "C2")),
    MultipartKey(List("A2", "B2", "C1")),
    MultipartKey(List("A2", "B2", "C2"))
  )

  val pattern = MultipartKey(List("A1", null, "C1"))

  "MultipartKey" should {
    "match pattern exactly" >> {
      keys(0).matchPattern(pattern) === KeyMatch.ExactMatch
    }

    "match pattern exactly" >> {
      MultipartKey(List("A1")).matchPattern(pattern) === KeyMatch.MayMatch
    }

    "match and not match pattern" >> {
      for (key <- keys) key match {
        case MultipartKey(List(a, b, c)) if a == "A1" && c == "C1" =>
          key.partial(1).matchPattern(pattern) === KeyMatch.MayMatch
          key.partial(2).matchPattern(pattern) === KeyMatch.MayMatch
          key.matchPattern(pattern) === KeyMatch.ExactMatch
        case _ =>
          key.matchPattern(pattern) === KeyMatch.NoMatch
      }
      success
    }
  }
}
