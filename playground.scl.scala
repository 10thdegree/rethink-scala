case class Attributes(map: Map[String, BigDecimal] = Map()) {
  def +(that: Attributes): Attributes = {
  	import scalaz._, Scalaz._
    Attributes(this.map |+| that.map)
  }
}

trait Row {
  def keys: List[String]
  def attributes: Attributes
}

case class BasicRow(keys: List[String], attributes: Attributes = Attributes()) extends Row

case class RowTree(keys: List[String], attributes: Attributes = Attributes(), children: List[RowTree] = Nil) extends Row

object RowOrderings {

  implicit def keysOrdering[A](implicit o: Ordering[A]): Ordering[List[A]] = new Ordering[List[A]] {
    def compare(o1: List[A], o2: List[A]) = {
      (o1 zip o2)
        .map({case (a, b) => o.compare(a, b)})
        .collectFirst({case c if c != 0 => c})
        .getOrElse(o1.length - o2.length)
      }
    }

  implicit def RowOrdering(implicit o: Ordering[List[String]]) = new Ordering[BasicRow] {
    def compare(o1: BasicRow, o2: BasicRow) = o.compare(o1.keys, o2.keys)
  }

  implicit def RowTreeOrdering(implicit o: Ordering[List[String]]) = new Ordering[RowTree] {
    def compare(o1: RowTree, o2: RowTree) = o.compare(o1.keys, o2.keys)
  }
}

object RowTree {
  
  import RowOrderings._

  def nest(cs: List[BasicRow]): List[RowTree] = {
    def go(c: BasicRow, beg: List[String], end: List[String]): RowTree = end match {
      case Nil => new RowTree(keys = c.keys, attributes = c.attributes)
      case _ => new RowTree(keys = beg, children = go(c, end.headOption.map(beg :+ _).getOrElse(beg), end.tail) :: Nil)
    }
    cs.map(c => go(c, List(c.keys.head), c.keys.tail))
  }

  def coalesce(cs: List[RowTree]): List[RowTree] = {
    (cs.foldLeft(List.empty[RowTree]) { (accum, r) => accum.headOption match {
        case Some(h) if h.keys == r.keys => h.copy(attributes = r.attributes + h.attributes, children = coalesce((r.children ::: h.children).sorted)) :: accum.tail
        case _ => r :: accum
      }
    }).reverse
  }
}

object Test {
  def makeBasicRows = (for {
    i <- (1 to 2).toList; is = i.toString
    j <- (0 to 2).toList; js = j.toString
    k <- (0 to 2).toList; ks = k.toString
  } yield (j, k) match {
    case (jj, kk) if jj > 0 && kk == 0 => BasicRow(keys = List(is, js), attributes = Attributes(Map("cnt" -> 10)))
    case (jj, kk) if jj > 0 && kk > 0 => BasicRow(keys = List(is, js, ks), attributes = Attributes(Map("cnt" -> 100)))
    case _ => BasicRow(keys = List(is), attributes = Attributes(Map("cnt" -> 1)))
  }).toSet.toList


  def makeNestedRows: List[RowTree] = {
    def go(keylen: Int = 3, key: List[String] = List()): List[RowTree] = {
      for {
        i <- (1 until (keylen)).toList
        is = i.toString
        newkey = is :: key
      } yield new RowTree(keys = newkey, children = if (newkey.length == keylen) Nil else go(keylen, newkey))
    }
    go()
  }

  def print(r: RowTree) {
    def go(r: RowTree, ident: Int = 0) {
      println(("  " * ident) + r.keys + ": " + r.attributes)
      r.children.foreach(c => go(c, ident + 1))
    }
    go(r)
  }
}

//val rows = makeBasicRows
//val randomRows = scala.util.Random.shuffle(rows)
//rkeyss.sorted

import RowOrderings._

//assert(RowTree.nest(makeBasicRows.sorted) == Test.makeNestedRows.sorted)

Test.makeBasicRows.sorted.foreach(println)
//RowTree.nest(Test.makeBasicRows.sorted).foreach(RowTree.print _)
RowTree.coalesce(RowTree.nest(Test.makeBasicRows.sorted)).foreach(Test.print _)
//RowTree.coalesce(Test.makeNestedRows.sorted).foreach(Test.print _)

val double = RowTree.nest(Test.makeBasicRows.sorted) ::: RowTree.nest(Test.makeBasicRows.sorted)
RowTree.coalesce(double.sorted).foreach(Test.print _)