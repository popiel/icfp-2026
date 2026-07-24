package com.wolfskeep.littleman.parse

import com.wolfskeep.littleman.model._

/** Pairs backtick cells into numeric literals on each axis independently.
  *
  * Within a row, backticks pair left-to-right (1st with 2nd, 3rd with 4th);
  * within a column they pair top-to-bottom. A backtick that pairs on neither
  * axis is a load error. Between a matched pair every cell must be a digit or
  * space (else load error). The numeric value must fit in 64 bits when read
  * in both directions along the axis, or it is a load error.
  *
  * Each pair yields one [[LiteralSegment]] with `openCell` = the backtick
  * the man enters first and `closeCell` = the one that loads the value. The
  * open direction is determined at runtime by the man's walking direction; we
  * store both ends and the ordered digit cells so the runtime can pick the
  * reading order.
  */
final class LiteralParser(text: ProgramText) {

  def parse(): Either[ParseError, LiteralTable] = {
    val backs = text.findAll(_ == '`').sortBy(p => (p.y, p.x))
    // map from each backtick to the partner it pairs with on each axis (if any)
    var rowPairs: Map[Point, Point] = Map.empty
    var colPairs: Map[Point, Point] = Map.empty

    // Pair on rows: group by y, sort by x, greedily pair consecutive backticks
    // (1st with 2nd, 3rd with 4th, ...). A trailing odd backtick in a row does
    // NOT error here — it may still pair on a column. A backtick that pairs on
    // neither axis is rejected after both passes.
    val byRow = backs.groupBy(_.y).view.mapValues(_.sortBy(_.x)).toMap
    for ((_, ps) <- byRow) {
      var i = 0
      while (i + 1 < ps.size) {
        val open = ps(i)
        val close = ps(i + 1)
        rowPairs = rowPairs.updated(open, close)
        i += 2
      }
    }
    // Pair on columns: group by x, sort by y, greedily pair consecutive
    val byCol = backs.groupBy(_.x).view.mapValues(_.sortBy(_.y)).toMap
    for ((_, ps) <- byCol) {
      var i = 0
      while (i + 1 < ps.size) {
        val open = ps(i)
        val close = ps(i + 1)
        colPairs = colPairs.updated(open, close)
        i += 2
      }
    }

    // any backtick that paired on neither axis is an error
    val paired = rowPairs.keySet ++ colPairs.keySet ++ rowPairs.values ++ colPairs.values
    for (b <- backs if !paired.contains(b))
      return Left(ParseError(s"backtick at ${b.x},${b.y} pairs on neither axis"))

    var segments: Vector[LiteralSegment] = Vector.empty
    var backtickAt: Map[Point, Vector[LiteralSegment]] = Map.empty

    // Build segments for row pairs
    for ((open, close) <- rowPairs) {
      buildSegment(Axis.Horizontal, open, close) match {
        case Left(e) => return Left(e)
        case Right(seg) =>
          segments = segments :+ seg
          backtickAt = backtickAt.updatedWith(open)(_.map(_ :+ seg).orElse(Some(Vector(seg))))
          backtickAt = backtickAt.updatedWith(close)(_.map(_ :+ seg).orElse(Some(Vector(seg))))
      }
    }
    // Build segments for column pairs
    for ((open, close) <- colPairs) {
      buildSegment(Axis.Vertical, open, close) match {
        case Left(e) => return Left(e)
        case Right(seg) =>
          segments = segments :+ seg
          backtickAt = backtickAt.updatedWith(open)(_.map(_ :+ seg).orElse(Some(Vector(seg))))
          backtickAt = backtickAt.updatedWith(close)(_.map(_ :+ seg).orElse(Some(Vector(seg))))
      }
    }

    Right(LiteralTable(segments, backtickAt))
  }

  /** Validate the cells between `open` and `close` (along `axis`) and build a
    * segment. The value must fit in 64 bits read both directions. */
  private def buildSegment(axis: Axis, open: Point, close: Point): Either[ParseError, LiteralSegment] = {
    // walk from open toward close (inclusive), collecting digit cells, checking non-digits
    val (dx, dy) = axis match {
      case Axis.Horizontal =>
        if (open.y != close.y) return Left(ParseError(s"horizontal pair not on same row at $open/$close"))
        (math.signum(close.x - open.x).toInt, 0)
      case Axis.Vertical =>
        if (open.x != close.x) return Left(ParseError(s"vertical pair not on same column at $open/$close"))
        (0, math.signum(close.y - open.y).toInt)
    }
    val digits = scala.collection.mutable.ArrayBuffer.empty[Point]
    var p = open.plus(Point(dx, dy))
    var guard = 0
    val limit = math.max(math.abs(close.x - open.x), math.abs(close.y - open.y))
    while (guard <= limit + 1) {
      guard += 1
      if (p == close)
        guard = limit + 5 // done
      else {
        text.charAt(p) match {
          case c if c.isDigit => digits += p
          case ' '            => // ignored
          case c => return Left(ParseError(s"non-digit '$c' inside backtick literal at ${p.x},${p.y}"))
        }
        p = p.plus(Point(dx, dy))
      }
    }

    // value must fit in a signed 64-bit long read both directions
    val digitsStr = digits.map(d => text.charAt(d)).mkString
    if (digitsStr.nonEmpty) {
      try { java.lang.Long.parseLong(digitsStr) }
      catch {
        case _: NumberFormatException =>
          return Left(ParseError(s"literal '$digitsStr' does not fit in 64 bits at $open"))
      }
      val rev = digitsStr.reverse
      try { java.lang.Long.parseLong(rev) }
      catch {
        case _: NumberFormatException =>
          return Left(ParseError(s"literal '$rev' (reversed) does not fit in 64 bits at $open"))
      }
    }

    Right(LiteralSegment(axis, open, close, digits.toVector))
  }
}

object LiteralParser {
  def parse(text: ProgramText): Either[ParseError, LiteralTable] =
    new LiteralParser(text).parse()
}