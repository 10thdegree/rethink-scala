package bravo.api.dart

import org.joda.time.format._
import org.joda.time._
import scalaz._
import Scalaz._
import bravo.api.dart.Data._

object DateUtil {

  def mkDateTime(str: String, fmt: String = "yyyy-MM-dd") =
    DateTime.parse(str, DateTimeFormat.forPattern(fmt))

  def groupDates(li: List[Map[String,String]]): List[ReportDay] = {
    val t = li.map(m => {
    try {
        val dates = mkDateTime(m("Date"))
    } catch {
      case ex: Exception => 
        println("ex = " + m.get("Date") )
        println(" Ex = " + ex) //println("Error parsing date = " + m("Date"))     
        ex.printStackTrace
    }
    })
    li.map(m => (mkDateTime(m("Date")).toLocalDate, m)).groupBy(_._1).toList.map(t => ReportDay(new DateTime(), t._1, t._2.map(_._2)))
  
  }

  def ungroupDates(li: List[ReportDay]): List[Map[String,String]] = 
    li.map(_.rows).join  

  private def generateDatesFromDateRange(startDate: DateTime, endDate: DateTime): List[LocalDate] = {
    val r = (0 to Math.abs(Days.daysBetween(startDate.toLocalDate(), endDate.toLocalDate()).getDays())).toList
    r.map(i => startDate.plusDays(i).toLocalDate())
  }

  //this finds the largest cached chunk that starts at the boundary of the range
  def findLargestRange(cache: List[ReportDay], startDate: DateTime, endDate: DateTime): List[ReportDay] = {
    val sd = startDate.toLocalDate
    val ed = endDate.toLocalDate
    val trimmedCache = cache.filter(l => (l.rowDate compareTo sd) != -1 && (l.rowDate compareTo ed) != 1)   
    val groups = slidingGroup[ReportDay](trimmedCache, (a,b) => Math.abs(Days.daysBetween(a.rowDate, b.rowDate).getDays()) == 1)
    val groupsAtBoundaries = groups.filter(l => l.headOption.fold(false)((rd: ReportDay) => (rd.rowDate equals sd)) || l.reverse.headOption.fold(false)((rd: ReportDay) => rd.rowDate equals ed))
    val bestCache = groupsAtBoundaries.sortWith((a,b) => a.size == b.size).headOption.toList.flatten
    bestCache
  }

  def findMissingDates(l: List[ReportDay], sd: LocalDate, ed: LocalDate): Option[(LocalDate, LocalDate)] = 
    l match {
      case Nil => (sd, ed).some
      case _ => 
        val (s, e) = (l.head, l.reverse.head) 
        ((s.rowDate equals sd),(e.rowDate equals ed)) match {
          case (true, false) => (e.rowDate, ed).some
          case (false, true) => (sd, s.rowDate).some
          case _ => None
        }
    }

  def slidingGroup[A](l: List[A], f: (A,A) => Boolean ): List[List[A]] = {
    l match {
      case h :: t =>
        t.foldLeft( List[List[A]](List(h)) )( (a,b) => {
          if (f(b,a.head.head))
            (b :: a.head) :: a.tail
          else
        List(b) :: a
        }).map(_.reverse).reverse
      case Nil => List[List[A]]()
    }
  }
}

