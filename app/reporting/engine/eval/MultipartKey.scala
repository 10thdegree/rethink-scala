package reporting.engine.eval

// TODO: Should use Options instead of nulls
case class MultipartKey(parts: List[String]) extends Ordered[MultipartKey] {
  def length = parts.length

  def shortest(that: MultipartKey) =
    if (this.length < that.length) this
    else that

  def partial(len: Int) = {
    MultipartKey(parts.take(len))
  }

  def apply(idx: Int) = parts(idx)

  def map(p: (String, Int) => Boolean) = {
    MultipartKey(for {
      (pk, idx) <- parts.zipWithIndex
    } yield if (p(pk, idx)) pk else null)
  }

  // e.g.:
  // this      A,B,C
  // pattern   A,-,C
  // TODO: Return a match result that indicates whether
  // child keys of this key can possibly match or not
  // e.g. B,X,Y can never match the pattern above.
  def matchesPattern(pattern: MultipartKey): Boolean = {
    this.length == pattern.length &&
      (this.parts zip pattern.parts)
        .forall({ case (a, b) => a == b || b == null })
  }

  def matchPattern(pattern: MultipartKey): MultipartKey.KeyMatch = {
      val c = (this.parts zip pattern.parts)
        .map({ case (a, b) => a == b || b == null })
    if (c.exists(false.==)) {
      MultipartKey.KeyMatch.NoMatch
    } else {
      if (this.length == pattern.length)
        MultipartKey.KeyMatch.ExactMatch
      else
        MultipartKey.KeyMatch.MayMatch
    }
  }

  override def compare(that: MultipartKey): Int = {
    val diffs = (this.parts zip that.parts)
      .map({ case (x, y) => x compare y})
    val same = diffs.takeWhile(_ == 0).length
    if (parts.length > same) diffs(same) else 0
  }
}
object MultipartKey {
  def empty = MultipartKey(List.empty)

  sealed trait KeyMatch
  object KeyMatch {
    case object ExactMatch extends KeyMatch
    case object NoMatch extends KeyMatch
    case object MayMatch extends KeyMatch
  }
}
