package com.wolfskeep.littleman.parse

import com.wolfskeep.littleman.model._

/** The wall style of a candidate room. */
private[parse] sealed trait WallStyle
private[parse] object WallStyle {
  case object Normal  extends WallStyle // '-', '|'
  case object Display extends WallStyle // '=', ':'
  case object Mixed   extends WallStyle // inconsistent
}

/** Scans a [[ProgramText]] grid for rooms and displays.
  *
  * A room is a rectangle of `+` corners with either `-` horizontal and `|`
  * vertical walls (normal/IO rooms) or `=` horizontal and `:` vertical walls
  * (displays). A 3x3 normal room whose single interior cell is `I` or `O` is
  * an input or output room. Each normal/IO room may contain at most one `@`
  * (a spawn); displays may not contain `@`. Rooms may not overlap (interior
  * of one may not meet interior of another, which also catches nesting).
  *
  * Detection is by top-left corner: for each `+` in reading order, look for
  * the nearest top-right `+` along a consistent horizontal wall and the
  * nearest bottom-left `+` along a consistent vertical wall, verify the
  * rectangle closes and that the wall styles agree.
  */
final class RoomScanner {

  def scan(text: ProgramText): Either[ParseError, RoomScan] = {
    val corners = text.findAll(_ == '+').sortBy(p => (p.y, p.x))

    var rooms: Vector[Room] = Vector.empty
    var ownerAt: Map[Point, Int] = Map.empty
    var interiorClaim: Map[Point, Int] = Map.empty
    var nextId: Int = 0

    for (tl <- corners) {
      detectRoom(text, tl, nextId) match {
        case Right(Some(room)) =>
          for (c <- room.interiorCells) {
            interiorClaim.get(c) match {
              case Some(other) =>
                return Left(ParseError(s"rooms overlap at ${c.x},${c.y} (room ${room.id} overlaps room $other)"))
              case None =>
                interiorClaim = interiorClaim.updated(c, room.id)
            }
          }
          rooms = rooms :+ room
          for (c <- room.borderCells ++ room.interiorCells if !ownerAt.contains(c)) {
            ownerAt = ownerAt.updated(c, room.id)
          }
          nextId += 1
        case Right(None) => // not a corner of a room
        case Left(err)  => return Left(err)
      }
    }

    // enforce per-room '@' counts and the display rule; collect spawns
    val spawns = scala.collection.mutable.ArrayBuffer.empty[Point]
    for (room <- rooms) {
      val roomAts = room.interiorCells.filter(p => text.charAt(p) == '@')
      room.kind match {
        case RoomKind.Display =>
          if (roomAts.nonEmpty)
            return Left(ParseError(s"spawn at ${roomAts.head.x},${roomAts.head.y} is inside a display (not allowed)"))
        case _ =>
          if (roomAts.lengthIs > 1)
            return Left(ParseError(s"room ${room.id} at ${room.topLeft.x},${room.topLeft.y} contains ${roomAts.size} spawns; at most one is allowed"))
      }
      spawns ++= roomAts
    }

    // any '@' not inside any room interior is an error
    val insideAts = spawns.toSet
    for (at <- text.findAll(_ == '@') if !insideAts.contains(at))
      return Left(ParseError(s"spawn at ${at.x},${at.y} is not inside any room"))

    Right(RoomScan(rooms, ownerAt, spawns.toVector.sortBy(p => (p.y, p.x))))
  }

  private type DetectResult = Either[ParseError, Option[Room]]

  private def detectRoom(text: ProgramText, tl: Point, id: Int): DetectResult =
    (findTopEdge(text, tl), findLeftEdge(text, tl)) match {
      case (Some(top), Some(left)) => finishRoom(text, tl, top, left, id)
      case _ => Right(None)
    }

  private case class Edge(toCorner: Point, style: WallStyle, consistent: Boolean)

  private def findTopEdge(text: ProgramText, tl: Point): Option[Edge] = {
    var x = tl.x + 1
    val styles = scala.collection.mutable.Set.empty[Char]
    while (text.charAt(x, tl.y) == '-' || text.charAt(x, tl.y) == '=') {
      styles += text.charAt(x, tl.y)
      x += 1
    }
    if (text.charAt(x, tl.y) == '+' && x > tl.x + 1)
      Some(Edge(Point(x, tl.y), styleOf(styles, horiz = true), consistent = styles.size == 1))
    else None
  }

  private def findLeftEdge(text: ProgramText, tl: Point): Option[Edge] = {
    var y = tl.y + 1
    val styles = scala.collection.mutable.Set.empty[Char]
    while (text.charAt(tl.x, y) == '|' || text.charAt(tl.x, y) == ':') {
      styles += text.charAt(tl.x, y)
      y += 1
    }
    if (text.charAt(tl.x, y) == '+' && y > tl.y + 1)
      Some(Edge(Point(tl.x, y), styleOf(styles, horiz = false), consistent = styles.size == 1))
    else None
  }

  private def styleOf(styles: scala.collection.mutable.Set[Char], horiz: Boolean): WallStyle =
    if (styles.size != 1) WallStyle.Mixed
    else if (styles.head == (if (horiz) '-' else '|')) WallStyle.Normal
    else WallStyle.Display

  private def finishRoom(
    text: ProgramText, tl: Point, top: Edge, left: Edge, id: Int
  ): DetectResult = {
    if (!top.consistent || !left.consistent)
      return Left(ParseError(s"malformed room at ${tl.x},${tl.y}: mixed wall styles"))
    if (top.style != left.style)
      return Left(ParseError(s"malformed room at ${tl.x},${tl.y}: inconsistent wall styles"))

    val br = Point(top.toCorner.x, left.toCorner.y)
    if (text.charAt(br) != '+')
      return Left(ParseError(s"malformed room at ${tl.x},${tl.y}: missing bottom-right corner"))

    val width = top.toCorner.x - tl.x + 1
    val height = left.toCorner.y - tl.y + 1
    val style = top.style

    for (x <- tl.x + 1 until br.x)
      if (!isHorizWall(text.charAt(x, br.y), style))
        return Left(ParseError(s"malformed room at ${tl.x},${tl.y}: bad bottom wall at ${x},${br.y}"))
    for (y <- tl.y + 1 until br.y)
      if (!isVertWall(text.charAt(br.x, y), style))
        return Left(ParseError(s"malformed room at ${tl.x},${tl.y}: bad right wall at ${br.x},${y}"))

    val kind = style match {
      case WallStyle.Display => RoomKind.Display
      case WallStyle.Normal =>
        if (width == 3 && height == 3) text.charAt(tl.x + 1, tl.y + 1) match {
          case 'I' => RoomKind.Input
          case 'O' => RoomKind.Output
          case _   => RoomKind.Normal
        }
        else RoomKind.Normal
      case WallStyle.Mixed =>
        return Left(ParseError(s"malformed room at ${tl.x},${tl.y}: mixed wall styles"))
    }

    Right(Some(Room(id, tl, width, height, kind)))
  }

  private def isHorizWall(c: Char, style: WallStyle): Boolean = style match {
    case WallStyle.Normal  => c == '-'
    case WallStyle.Display => c == '='
    case WallStyle.Mixed   => c == '-' || c == '='
  }
  private def isVertWall(c: Char, style: WallStyle): Boolean = style match {
    case WallStyle.Normal  => c == '|'
    case WallStyle.Display => c == ':'
    case WallStyle.Mixed   => c == '|' || c == ':'
  }
}

object RoomScanner {
  /** Convenience entry point using a fresh scanner. */
  def scan(text: ProgramText): Either[ParseError, RoomScan] = new RoomScanner().scan(text)
}