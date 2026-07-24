package com.wolfskeep.littleman.model

/** An immutable (x, y) cell coordinate. x is the column (increases east);
  * y is the row (increases south). */
final case class Point(x: Int, y: Int) {
  def plus(d: Point): Point = Point(x + d.x, y + d.y)

  /** Manhattan distance |Δx| + |Δy|. */
  def manhattan(other: Point): Int =
    Math.abs(other.x - x) + Math.abs(other.y - y)

  /** Reading-order comparison (top-to-bottom, left-to-right): negative if
    * this comes before other, positive if after, zero if equal. */
  def readingOrder(other: Point): Int = {
    val byY = Integer.compare(y, other.y)
    if (byY != 0) byY else Integer.compare(x, other.x)
  }
}