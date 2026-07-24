package com.wolfskeep.littleman.parse

import com.wolfskeep.littleman.model._

/** Parses pipes from a [[ProgramText]] grid given a room scan.
  *
  * Pipe-glyph cells (`> < ^ v - |`) that are NOT owned by any room/display
  * form the raw material of pipes. Each pipe is traced from a start
  * arrowhead (whose backward cell lies on a source room border, arrow
  * pointing away) through body glyphs (whose axis must match the glyph) and
  * bends (arrowheads pointing in the new direction) until a terminal
  * arrowhead whose forward cell lies on a destination room border.
  *
  * Validation: length >= 2, both ends are arrowheads, no backward
  * arrowheads, body glyphs match the current flow direction, and every pipe
  * cell is owned by exactly one pipe.
  */
final class PipeParser(text: ProgramText, rooms: RoomScan) {

  def parse(): Either[ParseError, PipeNetwork] = {
    var pipes: Vector[Pipe] = Vector.empty
    var cellOwner: Map[Point, Int] = Map.empty
    var nextId: Int = 0

    // Candidate start arrowheads: free cells that are arrowheads. We attempt each
    // in reading order, but only those whose backward cell is on a room border
    // (the arrow pointing away from it) actually start a pipe. A candidate that
    // fails this check is not errored here — it may be a mid-pipe cell claimed
    // by another start (or flagged as dangling afterward).
    val candidates = text.findAll(c => Direction.fromArrow(c).isDefined)
      .filter(p => rooms.isFree(p))
      .sortBy(p => (p.y, p.x))

    for (start <- candidates) {
      // skip if already consumed by a prior pipe
      if (!cellOwner.contains(start)) {
        // only attempt a trace if this looks like a real start
        val dir0 = Direction.fromArrow(text.charAt(start)).get
        val back = start.plus(dir0.opposite.delta)
        rooms.roomAt(back) match {
          case Some(room) if room.borderCells.contains(back) && rooms.isFree(start.plus(dir0.delta)) =>
            traceFrom(start, nextId) match {
              case Right(pipe) =>
                for (c <- pipe.cells) {
                  if (cellOwner.contains(c))
                    return Left(ParseError(s"pipe cell at ${c.x},${c.y} belongs to two pipes"))
                  cellOwner = cellOwner.updated(c, pipe.id)
                }
                pipes = pipes :+ pipe
                nextId += 1
              case Left(err) => return Left(err)
            }
          case _ => // not a start; will be claimed or flagged later
        }
      }
    }

    // any free pipe-glyph cell not claimed by a pipe is a dangling pipe error
    for (c <- text.findAll(isPipeGlyph) if rooms.isFree(c) && !cellOwner.contains(c))
      return Left(ParseError(s"pipe glyph at ${c.x},${c.y} is not part of any valid pipe"))

    Right(PipeNetwork(pipes, cellOwner))
  }

  private def isPipeGlyph(c: Char): Boolean =
    Direction.fromArrow(c).isDefined || c == '-' || c == '|'

  /** Trace a pipe starting from `start` (an arrowhead), flowing in the
    * arrow's direction. Returns the parsed pipe or an error. */
  private def traceFrom(start: Point, id: Int): Either[ParseError, Pipe] = {
    val dir0 = Direction.fromArrow(text.charAt(start)).get
    // the source room: backward cell must be on a room border
    val back = start.plus(dir0.opposite.delta)
    rooms.roomAt(back) match {
      case Some(room) if room.borderCells.contains(back) =>
        // arrow must point away from the room (into free space)
        if (!rooms.isFree(start.plus(dir0.delta)))
          return Left(ParseError(s"pipe start at ${start.x},${start.y} does not point into free space"))
        traceBody(start, dir0, room.id, id)
      case _ =>
        Left(ParseError(s"pipe arrowhead at ${start.x},${start.y} is not attached to a room border"))
    }
  }

  /** Walk the pipe from `start` in direction `dir`, collecting cells until a
    * terminal arrowhead whose forward cell is on a (non-source) room border. */
  private def traceBody(
    start: Point, dir: Direction, sourceRoomId: Int, id: Int
  ): Either[ParseError, Pipe] = {
    val cells = scala.collection.mutable.ArrayBuffer.empty[Point]
    cells += start
    var cur = start
    var curDir = dir

    val maxCells = text.width * math.max(1, text.lineCount) + 10

    var guard = 0
    while (guard <= maxCells) {
      guard += 1
      val fwd = cur.plus(curDir.delta)
      val fc = text.charAt(fwd)

      // a body glyph or wall running into a room border is an error; pipes
      // must end with a terminal arrowhead pointing into the room.
      if (rooms.isOwned(fwd))
        return Left(ParseError(
          s"pipe at ${fwd.x},${fwd.y} runs into a wall; end with an arrowhead pointing into the room"))

      // fwd is free space. Is it an arrowhead?
      Direction.fromArrow(fc) match {
        case Some(arrowDir) =>
          if (arrowDir == curDir.opposite)
            return Left(ParseError(s"pipe at ${fwd.x},${fwd.y} has a backward arrowhead"))
          // is fwd terminal? check the cell ahead of the arrowhead
          val ffwd = fwd.plus(arrowDir.delta)
          rooms.roomAt(ffwd) match {
            case Some(destRoom) if destRoom.borderCells.contains(ffwd) =>
              if (destRoom.id == sourceRoomId)
                return Left(ParseError(s"pipe at ${fwd.x},${fwd.y} returns to its source room"))
              cells += fwd
              return finishPipe(id, cells.toVector, sourceRoomId, destRoom, termDir = arrowDir)
            case _ =>
              // bend or redundant straight-through arrowhead in free space
              cells += fwd
              cur = fwd
              curDir = arrowDir
          }
        case None =>
          fc match {
            case '-' if curDir.axis == Axis.Horizontal =>
              cells += fwd; cur = fwd
            case '|' if curDir.axis == Axis.Vertical =>
              cells += fwd; cur = fwd
            case ' ' =>
              return Left(ParseError(s"pipe at ${cur.x},${cur.y} runs into empty space"))
            case _ =>
              return Left(ParseError(s"pipe at ${fwd.x},${fwd.y} has wrong body glyph '$fc'"))
          }
      }
    }
    Left(ParseError(s"pipe starting at ${start.x},${start.y} is too long (unterminated?)"))
  }

  /** Build the final Pipe, determining dest + destSide from the terminal
    * room and the terminal arrow's direction. */
  private def finishPipe(
    id: Int, cells: Vector[Point], sourceRoomId: Int,
    destRoom: Room, termDir: Direction
  ): Either[ParseError, Pipe] = {
    if (cells.length < 2)
      return Left(ParseError(s"pipe at ${cells.head} is too short (need >= 2 cells)"))

    // the arrow points INTO the room, so the room side the pipe attaches to
    // is the side the arrow enters from = the opposite of the arrow direction
    val destSide = termDir.opposite

    destRoom.kind match {
      case RoomKind.Input =>
        Left(ParseError(s"pipe flows INTO an input room at ${cells.last} (must flow out)"))
      case RoomKind.Output =>
        Right(Pipe(id, cells, sourceRoomId, PipeDest.Output, destSide))
      case RoomKind.Display =>
        val side = termDir match {
          case Direction.South => DisplaySide.Top    // arrow points down into top
          case Direction.East  => DisplaySide.Left   // arrow points right into left
          case Direction.North => DisplaySide.Bottom  // arrow points up into bottom
          case Direction.West  =>
            return Left(ParseError(s"pipe at ${cells.last} attaches to the right side of a display (not allowed)"))
        }
        Right(Pipe(id, cells, sourceRoomId, PipeDest.Display(destRoom.id, side), destSide))
      case RoomKind.Normal =>
        Right(Pipe(id, cells, sourceRoomId, PipeDest.Room(destRoom.id), destSide))
    }
  }
}

object PipeParser {
  def parse(text: ProgramText, rooms: RoomScan): Either[ParseError, PipeNetwork] =
    new PipeParser(text, rooms).parse()
}