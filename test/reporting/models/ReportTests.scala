package reporting.models

import java.util.UUID

import org.joda.time.DateTime
import reporting.engine.SimpleReportGenerator
import reporting.models.ds.{DateSelector, dart}
import reporting.util.TUIReportHelper

import scala.util.Random

import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._

import scala.util.matching.Regex

@RunWith(classOf[JUnitRunner])
class ReportTests extends Specification with org.specs2.matcher.ThrownExpectations {

  "ReportGenerator" should {

    "convert maps to properly aggregated data source rows" >> {

      val ro = TUIReportHelper.TUISearchPerformanceRO()
      val rows = for (i <- 0 to 10) yield TUIReportHelper.sampleRawRow()

      import ds.DataSource.DataSourceAggregators.implicits._
      val dsf = new ds.DataSource.DataSourceRowFactory(ro.ds)
      val dsa = ds.DataSource.DataSourceAggregators.get[ds.DataSource.BasicRow]

      val brows = dsf.process(rows:_*)
      val cost = (rows(0)("Paid Search Cost").asInstanceOf[BigDecimal] * rows.length)

      brows.length === 1
      brows(0).attributes("Paid Search Cost") === cost

      success
    }

    "generate a report with unique (keys and date) having expected values" >> {
      val ro = TUIReportHelper.TUISearchPerformanceRO()
      val gen = new SimpleReportGenerator(ro.report, ro.fields)

      val start = DateTime.parse("2014-01-01")
      val end = DateTime.parse("2014-01-31")

      import ds.DataSource.DataSourceAggregators.implicits._
      val dsf = new ds.DataSource.DataSourceRowFactory(ro.ds)
      val dsa = ds.DataSource.DataSourceAggregators.get[ds.DataSource.BasicRow]
      val data = dsa.flattenByKeysAndDate(TUIReportHelper.sampleData:_*)//dsf(TUIReportHelper.sampleRawRow())
      val res = gen.getReport(ro.ds, data)(start, end)

      val bydate = res
        .groupBy(r => r.date)
        .map({case (k, vs) => k -> vs.groupBy(r => r.keys).toMap })
        .toMap

      res.length === 4

      bydate(start)(List("Brand"))(0).fields(ro.fieldsLookup("TotalSpend")).value === 402.06
      bydate(end)(List("Brand"))(0).fields(ro.fieldsLookup("TotalSpend")).value === 201.03

      /*for { r <- res } {
        val key = r.keys.mkString("(", ",", ")")
        val date = r.date
        val fields = r.fields.map(f => f._1.label -> f._2)
        println(s"$key, $date, $fields")
      }*/

      success
    }

    "generate a report with unique keys having expected values" >> {
      val ro = TUIReportHelper.TUISearchPerformanceRO()
      val gen = new SimpleReportGenerator(ro.report, ro.fields)

      val start = DateTime.parse("2014-01-01")
      val end = DateTime.parse("2014-01-31")

      import ds.DataSource.DataSourceAggregators.implicits._
      val dsf = new ds.DataSource.DataSourceRowFactory(ro.ds)
      val dsa = ds.DataSource.DataSourceAggregators.get[ds.DataSource.BasicRow]
      val data = dsa.flattenByKeys(TUIReportHelper.sampleData:_*)//dsf(TUIReportHelper.sampleRawRow())
      val res = gen.getReport(ro.ds, data)(start, end)

      val bykeys = res
        .groupBy(r => r.keys)
        .toMap

      res.length === 2

      bykeys(List("Brand"))(0).fields(ro.fieldsLookup("TotalSpend")).value === 603.09
      bykeys(List("Content"))(0).fields(ro.fieldsLookup("TotalSpend")).value === 402.06

      /*for { r <- res } {
        val key = r.keys.mkString("(", ",", ")")
        val date = r.date
        val fields = r.fields.map(f => f._1.label -> f._2)
        println(s"$key, $date, $fields")
      }*/

      success
    }
  }
}
