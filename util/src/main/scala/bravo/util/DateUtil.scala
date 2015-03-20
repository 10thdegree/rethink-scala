package bravo.util

import org.joda.time.format._
import org.joda.time._
import scalaz._
import Scalaz._
//import bravo.api.dart.Data._
import scala.collection.immutable.{TreeSet, SortedSet}


object DateUtil {

  case class ReportDay(retrievedDate: DateTime = new DateTime(), rowDate: LocalDate, rows: List[Map[String,String]])
  
  implicit val localDateOrd = new scala.math.Ordering[LocalDate] {
    def compare(a: LocalDate, b: LocalDate): Int = 
      a compareTo b
  }

  def mkDateTime(str: String, fmt: String = "yyyy-MM-dd") =
    DateTime.parse(str, DateTimeFormat.forPattern(fmt))

  //Takes a List of Maps that have a "Date" key inside them then groups by the "Date" value 
  def groupDates(li: List[Map[String,String]]): List[ReportDay] = {
    li.map(m => (mkDateTime(m("Date")).toLocalDate, m)).groupBy(_._1).toList.map(t => ReportDay(new DateTime(), t._1, t._2.map(_._2))).sortBy(rd => rd.rowDate)
  }

  def ungroupDates[A](li: List[ReportDay]): List[Map[String,String]] = 
    li.map(_.rows).join  

  private def generateDatesFromDateRange(startDate: DateTime, endDate: DateTime): List[LocalDate] = {
    val r = (0 to Math.abs(Days.daysBetween(startDate.toLocalDate(), endDate.toLocalDate()).getDays())).toList
    r.map(i => startDate.plusDays(i).toLocalDate())
  }

  //this finds the largest cached chunk that starts at the boundary of the range
  /*
  def findLargestRange(cache: List[ReportDay], startDate: DateTime, endDate: DateTime): List[ReportDay] = {
    val sd = startDate.toLocalDate
    val ed = endDate.toLocalDate
    val trimmedCache = cache.filter(l => (l.rowDate compareTo sd) != -1 && (l.rowDate compareTo ed) != 1)   
    val groups = slidingGroup[ReportDay](trimmedCache, (a,b) => Math.abs(Days.daysBetween(a.rowDate, b.rowDate).getDays()) == 1)
    val groupsAtBoundaries = groups.filter(l => l.headOption.fold(false)((rd: ReportDay) => (rd.rowDate equals sd)) || l.reverse.headOption.fold(false)((rd: ReportDay) => rd.rowDate equals ed))
    //val bestCache = groupsAtBoundaries.sortWith((a,b) => a.size == b.size).headOption.toList.flatten
    bestCache
  }*/

  def findLargestRanges[A](cache: SortedSet[A], startDate: DateTime, endDate: DateTime, f: A => LocalDate)(implicit O: scala.math.Ordering[A]): SortedSet[A] = {
    val sd = startDate.toLocalDate
    val ed = endDate.toLocalDate
    val trimmedCache = cache.filter(l => (f(l) compareTo sd) != -1 && (f(l) compareTo ed) != 1)   
    val groups = slidingGroup[A](trimmedCache.toList, (a,b) => Math.abs(Days.daysBetween(f(a), f(b)).getDays()) == 1)
    val groupsAtBoundaries = groups.filter(l => l.headOption.fold(false)((a: A) => (f(a) equals sd)) || l.reverse.headOption.fold(false)((a:A) => f(a) equals ed))
    val bestCache = groupsAtBoundaries.sortWith((a,b) => a.size == b.size).headOption.toList.flatten
    toSortedSet(bestCache)
  }
 
  def isBetween(t: (DateTime, DateTime), b: (DateTime, DateTime)): Boolean =
    isBetweenLD( (t._1.toLocalDate(), t._2.toLocalDate()), (b._1.toLocalDate(), b._2.toLocalDate()))

  def isBetweenLD(t: (LocalDate, LocalDate), b: (LocalDate, LocalDate)): Boolean = 
    ((t._1 compareTo b._1) > -1) && ((t._2 compareTo b._2) < 1) 
      
  def toSortedSet[A](l: List[A])(implicit O: scala.math.Ordering[A]): SortedSet[A] =
    l.foldLeft( TreeSet[A]() )( (a,b) => a + b )

  def findMissingDates(l: List[LocalDate], sd: LocalDate, ed: LocalDate): Option[(LocalDate, LocalDate)] = 
    l match {
      case Nil => (sd, ed).some
      case _ => 
        val (s, e) = (l.head, l.reverse.head) 
        ((s equals sd),(e equals ed)) match {
          case (true, false) => (e, ed).some
          case (false, true) => (sd, s).some
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

