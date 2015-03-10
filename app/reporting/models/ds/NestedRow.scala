package reporting.models.ds

import DataSource._

// NOTE(dk): Will probably just delete this, but it is immutable
// and may be useful in the eval package for EvalCxt

/*
case class NestedRow(keys: List[String],
                     dateRange: (DateTime, DateTime), // TODO: Handle nested structures with various ranges
                     attributes: Attributes = Attributes(),
                     children: List[NestedRow] = Nil) extends Row {

  type R = NestedRow

  def +(other: Row) = ???

  // Takes values at higher nodes and distributes them down to leaf nodes
  def distributeDown(attr: String, dependantAttr: Option[String] = None, value: Option[BigDecimal] = None): NestedRow =
    (this.children, attributes.get(attr), value, dependantAttr) match {
      // End of the line, do we have a value?
      case (Nil, _, None, _) => this
      case (Nil, _, Some(nv), _) => this.copy(attributes = attributes + Attributes.fromList(attr -> nv))

      // Continue: distribute by dependant attribute
      case (_, None, Some(nv), Some(d)) =>
        this.copy(
          attributes = attributes + Attributes.fromList(attr -> nv),
          children = children.map(c => c.distributeDown(attr, dependantAttr, Some(nv / c(d)))))

      // Continue: even distribution
      case (_, None, Some(nv), None) =>
        this.copy(
          attributes = attributes + Attributes.fromList(attr -> nv),
          children = children.map(_.distributeDown(attr, dependantAttr, Some(nv / children.size))))

      // Start: distribute by dependant attribute
      case (_, Some(v), None, Some(d)) =>
        this.copy(children = children.map(c => c.distributeDown(attr, dependantAttr, Some(v / c(d)))))

      // Start: even distribution
      case (_, Some(v), None, None) =>
        this.copy(children = children.map(_.distributeDown(attr, dependantAttr, Some(v / children.size))))

      // Haven't found the node with the value yet
      case (_, _, _, _) =>
        this.copy(children = children.map(_.distributeDown(attr, dependantAttr)))
    }

  // Takes values at leaf nodes and pushes them up to the top
  def distributeUp(attr: String): NestedRow = {
    (this.children, this.get(attr)) match {
      case (Nil, None) => throw new RuntimeException(s"Value for ttribute $attr expected.")
      case (Nil, Some(v)) => this // We already have the value, so just return
      case (_, _) =>
        val nchildren = children.map(_.distributeUp(attr))
        val sum = nchildren.map(_(attr)).sum
        this.copy(
          attributes = attributes + Attributes.fromList(attr -> sum),
          children = nchildren)
    }
  }

  def terminals: List[NestedRow] =
    if (children.isEmpty) List(this)
    else children.flatMap(_.terminals)

  val date = dateRange._1
}


implicit object DataSourceAggregatorNestedRows extends DataSourceAggregator[NestedRow] {
  def nestAndCoalesce(rows: NestedRow*): List[NestedRow] = {
    val nested = DataSource.NestedRow.nest(rows.toList)
    val coalesced = DataSource.NestedRow.coalesce(nested)
    coalesced
  }

  // Merge all values for unique keys (so dates get merged together)
  def flattenByKeys(rows: NestedRow*) = {
    rows
      .groupBy(r => r.keys)
      .map({ case (key, subrows) => subrows.reduce(_ + _)})
      .toList
  }

  // Merge values for any rows that have matching keys/dates
  def flattenByKeysAndDate(rows: NestedRow*) = {
    rows
      .groupBy(r => r.keys -> r.date)
      .map({ case (key, subrows) => subrows.reduce(_ + _)})
      .toList
  }

}

object NestedRow {

  import reporting.models.ds.DataSource.RowOrderings._

  // XXX: Currently ignores date; expects all BasicRows to have same date!
  def nest(cs: List[Row]): List[NestedRow] = {
    def go(c: Row, beg: Vector[String], end: List[String]): NestedRow = end match {
      case Nil => new NestedRow(
        keys = c.keys,
        dateRange = (c.date, c.date),
        attributes = c.attributes)
      case h :: tail => new NestedRow(
        keys = beg.toList,
        dateRange = (c.date, c.date),
        children = go(c, beg :+ h, tail) :: Nil)
    }
    cs.map(c => go(c, Vector(c.keys.head), c.keys.tail))
  }

  // TODO: Should handle dates in some capacity
  // TODO: This can be a semigroup, define it.
  def coalesce(cs: List[NestedRow]): List[NestedRow] = {
    cs
      .foldLeft(List.empty[NestedRow])(
        (accum, r) => accum.headOption match {
          case Some(h) if h.date != r.date => throw new RuntimeException("Coalescing of rows with different dates not supported.")
          case Some(h) if h.keys == r.keys => h.copy(
            dateRange = r.dateRange,
            attributes = r.attributes + h.attributes,
            children = coalesce((r.children ::: h.children).sorted)) :: accum.tail
          case _ => r :: accum
        })
      .reverse
  }

  /*
  // TODO: Parameter specifying for what date range to do the collapsing, by day, week, whole range, etc.
  def flattenByKeysAndDate(rows: List[Row]) = {
    def go(accum: List[Row], next: Row) = accum.headOption match {
      case Some(prev) if next.keys == prev.keys && next.date == prev.date => (next + prev) :: accum.tail
      case _ => next :: accum
    }
    rows
      .sortBy(r => r.date)
      .foldLeft(List.empty[Row])(go)
      .reverse
  }

  def flattenByKeys(rows: List[Row]) = {
    def go(accum: List[Row], next: Row) = accum.headOption match {
      case Some(prev) if next.keys == prev.keys => (next + prev) :: accum.tail
      case _ => next :: accum
    }
    rows
      .sortBy(r => r.keys)
      .foldLeft(List.empty[Row])(go)
      .reverse
  }*/
}

}
*/