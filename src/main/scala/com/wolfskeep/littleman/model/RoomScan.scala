package com.wolfskeep.littleman.model

/** The result of scanning a program grid for rooms/displays.
  *
  * @param rooms   every detected room, ordered by reading order of its
  *                top-left corner
  * @param ownerAt maps every cell that belongs to some room (wall or
  *                interior) to that room's id. Cells shared by adjacent
  *                rooms map to one of their owners arbitrarily; what matters
  *                is that such a cell is "owned" (not free for pipes).
  * @param spawns  the positions of every '@' inside a room, in reading
  *                order (top-to-bottom, left-to-right)
  */
final case class RoomScan(
  rooms: Vector[Room],
  ownerAt: Map[Point, Int],
  spawns: Vector[Point]
) {
  /** The room id owning the cell, if any. */
  def ownerOf(p: Point): Option[Int] = ownerAt.get(p)

  /** True if the cell belongs to some room's walls or interior. */
  def isOwned(p: Point): Boolean = ownerAt.contains(p)

  /** Whether the cell is free space (not owned by any room). */
  def isFree(p: Point): Boolean = !isOwned(p)

  /** Find the room containing a point, if any. */
  def roomAt(p: Point): Option[Room] =
    ownerAt.get(p).flatMap(id => rooms.find(_.id == id))
}