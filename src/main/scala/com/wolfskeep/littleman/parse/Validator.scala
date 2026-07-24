package com.wolfskeep.littleman.parse

import com.wolfskeep.littleman.model._

/** Cross-checks across the parsed rooms, pipes, and literals of a
  * [[LoadedProgram]]: at most one input/output room; I/O pipe direction &
  * count; display pipe attachment rules (at most one per side, none on the
  * right, none at a corner). Fails the load if any rule is violated. */
object Validator {

  def validate(p: LoadedProgram): Either[ParseError, Unit] = {
    // --- input / output rooms ---
    val inputs = p.rooms.rooms.filter(_.kind == RoomKind.Input)
    val outputs = p.rooms.rooms.filter(_.kind == RoomKind.Output)

    if (inputs.size > 1)
      return Left(ParseError(s"multiple input rooms (${inputs.size}); at most one is allowed"))
    if (outputs.size > 1)
      return Left(ParseError(s"multiple output rooms (${outputs.size}); at most one is allowed"))

    inputs.headOption match {
      case Some(room) =>
        val outPipes = p.pipes.pipes.filter(_.sourceRoomId == room.id)
        if (outPipes.size > 1)
          return Left(ParseError(s"input room at ${room.topLeft} has ${outPipes.size} outgoing pipes; at most one is allowed"))
        val inPipes = p.pipes.pipes.filter(pp => pp.dest == PipeDest.Room(room.id))
        if (inPipes.nonEmpty)
          return Left(ParseError(s"a pipe flows into the input room at ${room.topLeft} (must flow out)"))
      case None =>
    }

    outputs.headOption match {
      case Some(room) =>
        val inPipes = p.pipes.pipes.filter(_.dest == PipeDest.Output)
        if (inPipes.size > 1)
          return Left(ParseError(s"output room at ${room.topLeft} has ${inPipes.size} incoming pipes; at most one is allowed"))
        val outPipes = p.pipes.pipes.filter(_.sourceRoomId == room.id)
        if (outPipes.nonEmpty)
          return Left(ParseError(s"a pipe flows out of the output room at ${room.topLeft} (must flow in)"))
      case None =>
    }

    // --- display pipe attachments ---
    p.displayRoom.foreach { display =>
      val dispPipes = p.pipes.pipes.collect {
        case pp @ Pipe(_, _, _, PipeDest.Display(_, side), _) => pp -> side
      }
      val bySide = dispPipes.groupBy(_._2)
      for ((side, ps) <- bySide if ps.size > 1)
        return Left(ParseError(s"display at ${display.topLeft} has ${ps.size} pipes on its $side side; at most one per side is allowed"))
      // right-side and corner attachments are already rejected by PipeParser
      // during pipe construction; nothing to re-check here.
    }

    Right(())
  }
}