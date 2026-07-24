package com.wolfskeep.littleman.model

/** Axis-paired numeric literal delimited by backticks.
  *
  * @param axis         the axis this literal lies along (Horizontal = a row,
  *                     Vertical = a column)
  * @param openCell     the backtick cell the little man enters first when
  *                     walking through the literal in the open->close order
  * @param closeCell    the backtick cell that loads the value into A when
  *                     the man steps onto it
  * @param digits       digit cells in traversal order from open to close
  *                     (spaces skipped); may be empty for an empty literal
  */
final case class LiteralSegment(
  axis: Axis,
  openCell: Point,
  closeCell: Point,
  digits: Vector[Point]
)

/** The result of parsing all backtick literals: the segments plus a map from
  * every backtick cell to the segment(s) it participates in (a corner
  * backtick may open both a horizontal and a vertical literal). */
final case class LiteralTable(
  segments: Vector[LiteralSegment],
  backtickAt: Map[Point, Vector[LiteralSegment]]
)