package bravo.apitest.dart

import java.io._
import com.github.tototoshi.csv._
import scala.annotation.tailrec
import org.joda.time._
import bravo.util.DateUtil._

object ReportParser {

  val field = "Report Fields"
  
  def parse(s: String): List[Map[String,String]] = {
    val list = s.split("\\r?\\n").toList        
    val table = findTable(list, "")
    val r = new StringReader(table)
    val rows = CSVReader.open(r).allWithHeaders()
    rows
  }

  def unparse(l: List[Map[String,String]]): String = {
    val header = l.headOption.map(_.keys.mkString(","))
    val body = l.tail.map(m => m.values.mkString(",")).foldLeft("")((a,b) => a + "\\r\\n" + b)
    header.map(h => h + body).getOrElse("") 
  }
  
  @tailrec
  def findTable(rows: List[String], s: String): String = 
     rows match {
      case x :: xs if (x.contains(field)) => 
        (xs.lastOption match {
          case Some(l) if (l.contains("Grand Total")) => xs.dropRight(1)
          case _ => xs
        }).mkString("\n")
      case x :: xs => findTable(xs, s)
      case Nil => s
    }
}
