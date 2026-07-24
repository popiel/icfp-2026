package com.wolfskeep.littleman.parse

import com.wolfskeep.littleman.model._

/** Orchestrates the full parse pipeline: rooms -> pipes -> literals ->
  * validation -> a [[LoadedProgram]]. Either[ParseError, LoadedProgram]. */
object Loader {

  def load(text: ProgramText): Either[ParseError, LoadedProgram] =
    for {
      rooms    <- RoomScanner.scan(text)
      pipes    <- PipeParser.parse(text, rooms)
      literals <- LiteralParser.parse(text)
      program  = LoadedProgram(text, rooms, pipes, literals)
      _        <- Validator.validate(program)
    } yield program
}