package reporting.engine

class ReportGenerator {

  val foo = AST.Variable("foo")
  val bar = AST.Variable("bar")
  val fooAndBar = AST.Add(AST.Variable("foo"), AST.Variable("bar"))
  val fooAndBar2 = AST.WholeNumber(AST.Max(AST.Variable("foo"), AST.Variable("fooAndBar")))
  val fooAndBar3 = AST.Sum(AST.Variable("fooAndBar"))

  val fields = List(foo, bar, fooAndBar, fooAndBar2, fooAndBar3)
  val fieldsSorted = fields.reverse.sorted(FormulaEvaluator.TermOrdering)

  // TODO: Verify sorted is in dependency order!
  assert(fields == fieldsSorted)
}
