package bravo.util.matching

import scala.util.matching.Regex

object RegexOps {
  // Given a previous match m, applies a replacement pattern
  // e.g., "Found $1 in $0" using the match groups.
  // Will through an exception if the replacement pattern uses
  // a non-existent group.
  private val groupsCapture = """(?<!\$)\$(\d)""".r
  def runReplacement(replacement: String)(m: Regex.Match): String = {
    groupsCapture.replaceAllIn(replacement, rm => m.group(rm.group(1).toInt))
  }

  object implicits {
    // This allows us to use a regex replacement pattern (e.g. "$1")
    // but still get back an option if it matched or not.
    implicit class MaybeMatch(regex: Regex) {
      def maybeReplaceFirstIn(input: String, replacement: String): Option[String] = {
        regex.findFirstMatchIn(input).map(runReplacement(replacement))
      }
    }

    implicit class MaybeReplaceAllIn(input: String) {
      def maybeReplaceAll(regex: String, replacement: String): Option[String] = {
        var matched = false
        val res = regex.r.replaceAllIn(input, m => {
          matched = true
          runReplacement(replacement)(m)
        })
        if (matched) Some(res) else None
      }
    }
  }
}
