package bravo.api.dart

import java.io._
import com.github.tototoshi.csv._
import scala.annotation.tailrec

object ReportParser {
  val field = "Report Fields"
  
  def parse(s: String): List[Map[String,String]] = {
    val list = s.split("\\r?\\n").toList        
    val table = findTable(list, "")
    //println("table = " + table)
    val r = new StringReader(table)
    val rows = CSVReader.open(r).allWithHeaders()
    rows
  }
  
  @tailrec
  def findTable(rows: List[String], s: String): String = 
     rows match {
      case x :: xs if (x.contains(field)) => 
        xs.mkString("\n")
      case x :: xs => findTable(xs, s)
      case Nil => s
    }
}
