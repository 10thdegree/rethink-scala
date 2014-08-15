package reporting.engine

import scala.util.Random

import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._

object ReportGeneratorTests {
  import scalaz._, Scalaz._
  val foo = "foo" -> None //AST.Variable("foo")
  val bar = "bar" -> None //AST.Variable("bar")
  val fooAndBar = "fooAndBar" -> AST.Add(AST.Variable("foo"), AST.Variable("bar")).some
  val fooAndBar2 = "fooAndBar2" -> AST.WholeNumber(AST.Max(AST.Variable("foo"), AST.Variable("fooAndBar"))).some
  val fooAndBar3 = "fooAndBar3" -> AST.Sum(AST.Variable("fooAndBar")).some

  val fields = List(foo, bar, fooAndBar, fooAndBar2, fooAndBar3)
  val fieldLookup = fields.toMap.withDefaultValue(None)
  val fieldsSorted1 = fields.reverse.sorted(FormulaEvaluator.TermOrdering(fieldLookup))
  val fieldsSorted2 = fields.sorted(FormulaEvaluator.TermOrdering(fieldLookup))
  val fieldsSorted3 = Random.shuffle(fields).sorted(FormulaEvaluator.TermOrdering(fieldLookup))

  val fieldsSegmented = List(
    Set(foo, bar),
    Set(fooAndBar),
    Set(fooAndBar2, fooAndBar3)
  )
}


import ReportGeneratorTests._
@RunWith(classOf[JUnitRunner])
class ReportGeneratorTests extends Specification with org.specs2.matcher.ThrownExpectations {

  "ReportGenerator" should {

    "order dependencies for labeled terms" in {

      // Verify sorted is in dependency order!
      fieldsSorted1(0)._2 mustEqual None
      fieldsSorted1(1)._2 mustEqual None
      fieldsSorted2(0)._2 mustEqual None
      fieldsSorted2(0)._2 mustEqual None
      fieldsSorted3(1)._2 mustEqual None
      fieldsSorted3(1)._2 mustEqual None
    }

    "segment labeled terms by dependencies" in {

      val segmented = FormulaEvaluator.segment(fieldsSorted1: _*)

      segmented must size(3)

      for ((grp, idx) <- segmented.zipWithIndex) {
        grp.toSet mustEqual fieldsSegmented(idx)
      }
    }
  }
}