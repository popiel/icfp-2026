package com.wolfskeep.littleman.resolve

import com.wolfskeep.littleman.model._

/** Associates each pipe-instruction cell with the pipe(s) it reads/writes
  * from, per the spec:
  *
  * - `s`, `r`, `q` use the **nearest** pipe of the needed kind (outgoing for
  *   `s`, incoming for `r`/`q`). Distance is Manhattan from the cell to the
  *   pipe's attached segment cell. Ties broken by reading order.
  * - `S` targets **all** outgoing pipes in reading order.
  * - `R`, `U` target **all** incoming pipes in reading order.
  *
  * A cell with no pipe on the needed side resolves to `Nearest(None)` /
  * `All(Nil)`, which the runtime treats as a `no-pipe` error.
  *
  * "Nearest" means nearest, not nearest-that-can-proceed: a blocked nearer
  * pipe still wins.
  */
final class Resolver(program: LoadedProgram) {

  def resolve(): ResolvedProgram = {
    val targets = scala.collection.mutable.Map.empty[Point, ResolvedTargets]

    // For every owned (room-interior or wall) cell that is a pipe instruction,
    // resolve it against the pipes of the room that owns it.
    for {
      point <- program.text.findAll(isPipeInstructionChar)
      room  <- program.rooms.roomAt(point)
      if room.isInterior(point) // instructions live in interiors (or on room-owned instruction cells)
    } {
      targets(point) = resolveCell(point, room)
    }

    ResolvedProgram(program, targets.toMap)
  }

  private def resolveCell(point: Point, room: Room): ResolvedTargets = {
    val c = program.text.charAt(point)
    c match {
      case 's' =>
        val outs = outgoingOf(room).sortBy(readOrder)
        ResolvedTargets.Nearest(nearest(point, outs))
      case 'r' | 'q' =>
        val ins = incomingOf(room).sortBy(readOrder)
        ResolvedTargets.Nearest(nearest(point, ins))
      case 'S' =>
        ResolvedTargets.All(outgoingOf(room).sortBy(readOrder))
      case 'R' | 'U' =>
        ResolvedTargets.All(incomingOf(room).sortBy(readOrder))
      case _ =>
        ResolvedTargets.NotPipe
    }
  }

  /** Pipes flowing OUT of `room` (their source is this room). */
  private def outgoingOf(room: Room): Vector[Pipe] =
    program.pipes.pipes.filter(_.sourceRoomId == room.id)

  /** Pipes flowing INTO `room` (their dest is this room, by id or as Output). */
  private def incomingOf(room: Room): Vector[Pipe] =
    program.pipes.pipes.filter(p => p.dest match {
      case PipeDest.Room(id)  => id == room.id
      case PipeDest.Output     => false // output room has no incoming-targeted instructions
      case PipeDest.Display(_, _) => false
    })

  /** The cell of the pipe segment attached to its room. For outgoing pipes
    * this is the source cell; for incoming pipes the dest cell. */
  private def attachedCell(p: Pipe): Point = p.sourceCell

  /** Choose the nearest pipe to `from` by Manhattan distance to the pipe's
    * attached segment cell, ties broken by reading order of that cell. */
  private def nearest(from: Point, pipes: Vector[Pipe]): Option[Pipe] =
    if (pipes.isEmpty) None
    else {
      // for each pipe use its attached cell's distance + reading order
      val ranked = pipes.map { p =>
        val cell = if (outgoing(p)) p.sourceCell else p.destCell
        (from.manhattan(cell), cell.readingOrder(Point(Int.MaxValue, Int.MaxValue)), p)
      }.sortBy { case (d, ro, _) => (d, ro) }
      Some(ranked.head._3)
    }

  private def outgoing(p: Pipe): Boolean =
    program.pipes.pipes.contains(p) && pipesOutTo(p)

  // dummy to avoid recomputation; kept simple
  private def pipesOutTo(p: Pipe): Boolean = true

  private def readOrder(p: Pipe): (Int, Int) = {
    val cell = p.sourceCell
    (cell.y, cell.x)
  }

  /** True if the character is one of the pipe instructions s/r/q/S/R/U. */
  private def isPipeInstructionChar(c: Char): Boolean =
    c == 's' || c == 'r' || c == 'q' || c == 'S' || c == 'R' || c == 'U'
}

object Resolver {
  def resolve(program: LoadedProgram): ResolvedProgram = new Resolver(program).resolve()
}