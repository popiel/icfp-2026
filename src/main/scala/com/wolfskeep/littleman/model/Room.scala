package com.wolfskeep.littleman.model

/** The kind of a room detected on the grid. */
sealed trait RoomKind
object RoomKind {
  case object Normal  extends RoomKind // an ordinary room a little man walks
  case object Input   extends RoomKind // a 3x3 room whose interior cell is 'I'
  case object Output  extends RoomKind // a 3x3 room whose interior cell is 'O'
  case object Display extends RoomKind // an LM-75 display, '=' / ':' walls
}

/** A rectangular room (or display) on the grid.
  *
  * Coordinates are inclusive of the walls: `topLeft` is the top-left corner
  * cell (a '+'), `width`/`height` span corner to corner (so >= 3). The
  * interior is the rectangle strictly inside the walls. */
final case class Room(
  id: Int,
  topLeft: Point,
  width: Int,
  height: Int,
  kind: RoomKind
) {
  require(width >= 3 && height >= 3, "room must be at least 3x3")

  /** Top-left interior cell. */
  def interiorOrigin: Point = Point(topLeft.x + 1, topLeft.y + 1)

  /** Interior width (excluding walls). */
  def interiorWidth: Int = width - 2

  /** Interior height (excluding walls). */
  def interiorHeight: Int = height - 2

  /** True if the point is within the bounding box (including walls). */
  def contains(p: Point): Boolean =
    p.x >= topLeft.x && p.x < topLeft.x + width &&
      p.y >= topLeft.y && p.y < topLeft.y + height

  /** True if the point is strictly inside the walls. */
  def isInterior(p: Point): Boolean =
    p.x > topLeft.x && p.x < topLeft.x + width - 1 &&
      p.y > topLeft.y && p.y < topLeft.y + height - 1

  /** All wall (border) cells of this room. */
  def borderCells: IndexedSeq[Point] = {
    val xs = topLeft.x until topLeft.x + width
    val ys = topLeft.y until topLeft.y + height
    (topLeft.y until topLeft.y + height).flatMap { y =>
      xs.map(x => Point(x, y))
    }.filter(p => !isInterior(p))
  }

  /** All strictly-interior cells of this room. */
  def interiorCells: IndexedSeq[Point] =
    for {
      y <- interiorOrigin.y until interiorOrigin.y + interiorHeight
      x <- interiorOrigin.x until interiorOrigin.x + interiorWidth
    } yield Point(x, y)
}