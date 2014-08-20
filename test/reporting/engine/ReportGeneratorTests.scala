package reporting.engine

import org.joda.time.DateTime
import reporting.models.DataSources.DataSource

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
  val fooBarMax2 =  "fooBarMax2" -> AST.Max(AST.Variable("fooAndBar"), AST.Variable("fooAndBar3")).some

  val fields = List(foo, bar, fooAndBar, fooAndBar2, fooAndBar3, fooBarMax2)
  val fieldLookup = fields.toMap.withDefaultValue(None)
  val fieldsSorted1 = fields.reverse.sorted(FormulaCompiler.TermOrdering(fieldLookup))
  val fieldsSorted2 = fields.sorted(FormulaCompiler.TermOrdering(fieldLookup))
  val fieldsSorted3 = Random.shuffle(fields).sorted(FormulaCompiler.TermOrdering(fieldLookup))

  val fieldsSegmented = List(
    Set(foo, bar),
    Set(fooAndBar),
    Set(fooAndBar2, fooAndBar3),
    Set(fooBarMax2)
  )
}


import ReportGeneratorTests._
@RunWith(classOf[JUnitRunner])
class ReportGeneratorTests extends Specification with org.specs2.matcher.ThrownExpectations {

  "ReportGenerator" should {

    "order dependencies for labeled terms" >> {

      // Verify sorted is in dependency order!
      fieldsSorted1(0)._2 === None
      fieldsSorted1(1)._2 === None
      fieldsSorted2(0)._2 === None
      fieldsSorted2(0)._2 === None
      fieldsSorted3(1)._2 === None
      fieldsSorted3(1)._2 === None
      fieldsSorted1.last._1 === "fooBarMax2"
      // TODO: Add single tests for checking ordering

      success
    }

    "segment labeled terms by dependencies" >> {

      val segmented = FormulaCompiler.segment(fieldsSorted1: _*)

      segmented.size === fieldsSegmented.size

      for ((grp, idx) <- segmented.zipWithIndex) {
        grp.toSet === fieldsSegmented(idx)
      }

      success
    }

    "compile formulae to ASTs" >> {

      val formulae = Map(
        "foo" -> Some("4"),
        "bar" -> None,
        "fooBar" -> Some("foo + bar"),
        "fooBarSum" -> Some("sum(fooBar)"),
        "fooBarInt" -> Some("wholeNumber(fooBar)"),
        "fooBarMonthSum" -> Some("month.sum(fooBar)"),
        "fooBarMax" -> Some("max(foo, bar)"),
        "fooBarMax2" -> Some("max(fooBarMax, fooBarMonthSum)")
      )
      val formulaeAst = Map(
        "foo" -> Some(AST.Constant(4)),
        "bar" -> None,
        "fooBar" -> Some(AST.Add(AST.Variable("foo"), AST.Variable("bar"))),
        "fooBarSum" -> Some(AST.Sum(AST.Variable("fooBar"))),
        "fooBarInt" -> Some(AST.WholeNumber(AST.Variable("fooBar"))),
        "fooBarMonthSum" -> Some(AST.Month.Sum(AST.Variable("fooBar"))),
        "fooBarMax" -> Some(AST.Max(AST.Variable("foo"), AST.Variable("bar"))),
        "fooBarMax2" -> Some(AST.Max(AST.Variable("fooBarMax"), AST.Variable("fooBarMonthSum")))
      )

      val fc = new FormulaCompiler(formulae.keys.toSeq: _*)

      val asts = for ((key, valueOpt) <- formulae) yield key -> valueOpt.map(fc.apply)

      for ((key, ast) <- asts) {
        ast === formulaeAst(key)
      }

      success
    }

    "evaluate compiled ASTs to produce values" >> {

      val formulae = Map(
        "foo" -> Some("4"),
        "bar" -> None,
        "fooBar" -> Some("foo + bar"))
      val formulaeAst = Map(
        "foo" -> Some(AST.Constant(4)),
        "bar" -> None,
        "fooBar" -> Some(AST.Add(AST.Variable("foo"), AST.Variable("bar"))))

      val fc = new FormulaCompiler(formulae.keys.toSeq: _*)

      val asts = for ((key, valueOpt) <- formulae) yield key -> valueOpt.map(fc.apply)

      for ((key, ast) <- asts) {
        ast === formulaeAst(key)
      }

      case class Row(date: DateTime, values: Map[String, Int])

      val rows = List(
        Row(new DateTime().minusDays(1), Map("bar" -> 5)),
        Row(new DateTime(), Map("bar" -> 10))
      )

      val rowResults = List(
        Map("foo" -> 4.0, "bar" -> 5.0, "fooBar" -> 9.0),
        Map("foo" -> 4.0, "bar" -> 10.0, "fooBar" -> 14.0)
      )

      implicit val cxt = new FormulaEvaluator.EvaluationCxt[Row](FormulaEvaluator.Report(rows.head.date, rows.last.date))

      for ((r, idx) <- rows.zipWithIndex) {
        val terms = for ((key, astOpt) <- asts) yield key -> astOpt.orElse(Some(AST.Constant(r.values(key))))
        val res = FormulaEvaluator.eval(r, r.date, terms.toList)
        res === rowResults(idx)
      }

      for (((k, r), idx) <- cxt.allRows.zipWithIndex) {
        r.values === rowResults(rows.indexOf(k))
      }

      success
    }

    "evaluate aggregation operations" >> {
      val formulae = Map(
        "foo" -> Some("4"),
        "bar" -> None,
        "fooBar" -> Some("foo + bar"),
        "fooBarMonthSum" -> Some("month.sum(fooBar)"),
        "fooBarSum" -> Some("sum(fooBar)"),
        "fooBarMax" -> Some("max(foo, bar)"),
        "fooBarMax2" -> Some("max(fooBarMonthSum, fooBarSum)")
      )

      val formulaeAst = Map(
        "foo" -> Some(AST.Constant(4)),
        "bar" -> None,
        "fooBar" -> Some(AST.Add(AST.Variable("foo"), AST.Variable("bar"))),
        "fooBarMonthSum" -> Some(AST.Month.Sum(AST.Variable("fooBar"))),
        "fooBarSum" -> Some(AST.Sum(AST.Variable("fooBar"))),
        "fooBarMax" -> Some(AST.Max(AST.Variable("foo"), AST.Variable("bar"))),
        "fooBarMax2" -> Some(AST.Max(AST.Variable("fooBarMonthSum"), AST.Variable("fooBarSum")))
      )

      val fc = new FormulaCompiler(formulae.keys.toSeq: _*)

      case class Row(date: DateTime, values: Map[String, Int])

      val rows = List(
        Row(DateTime.parse("2005-02-25"), Map("bar" -> 5)),
        Row(DateTime.parse("2005-02-26"), Map("bar" -> 2)),
        Row(DateTime.parse("2005-03-25"), Map("bar" -> 10))
      )

      val rowResults = List(
        Map("foo" -> 4.0, "bar" -> 5.0, "fooBar" -> 9.0, "fooBarSum" -> 29.0, "fooBarMonthSum" -> 15.0, "fooBarMax" -> 5.0, "fooBarMax2" -> 29.0),
        Map("foo" -> 4.0, "bar" -> 2.0, "fooBar" -> 6.0, "fooBarSum" -> 29.0, "fooBarMonthSum" -> 15.0, "fooBarMax" -> 4.0, "fooBarMax2" -> 29.0),
        Map("foo" -> 4.0, "bar" -> 10.0, "fooBar" -> 14.0, "fooBarSum" -> 29.0, "fooBarMonthSum" -> 14.0, "fooBarMax" -> 10.0, "fooBarMax2" -> 29.0)
      )

      val labeledTerms = for ((key, formulaOpt) <- formulae) yield key -> formulaOpt.map(fc.apply)
      val orderedTerms = labeledTerms.toList.sorted(FormulaCompiler.TermOrdering(labeledTerms))
      println("ordered terms: " + orderedTerms)
      val groupedTerms = FormulaCompiler.segment(orderedTerms: _*)

      implicit val cxt = new FormulaEvaluator.EvaluationCxt[Row](FormulaEvaluator.Report(rows.head.date, rows.last.date))

      groupedTerms.foreach(println)
      for {
        grpTerms <- groupedTerms
        row <- rows
        date = row.date
      } {
        val terms = for ((key, astOpt) <- grpTerms) yield key -> astOpt.orElse(Some(AST.Constant(row.values(key))))
        FormulaEvaluator.eval(row, date, terms.toList)
      }

      for (((k, r), idx) <- cxt.allRows.zipWithIndex) {
        r.values === rowResults(rows.indexOf(k))
      }

      success
    }
  }
}