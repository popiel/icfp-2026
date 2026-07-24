package com.wolfskeep.littleman.parse

import com.wolfskeep.littleman.model._

/** Builds the initial runtime [[com.wolfskeep.littleman.runtime.World]] from
  * a loaded, resolved program: spawns little men (in reading order, each
  * facing east with A=B=BP=0), creates per-pipe [[PipeState]]s, an
  * [[com.wolfskeep.littleman.runtime.DisplayState]] if a display is present,
  * and an empty input queue / output buffer. */
final class WorldFactory(stepCap: Long) {

  def build(rp: ResolvedProgram, input: Vector[Long]): com.wolfskeep.littleman.runtime.World = {
    val program = rp.program
    val men = scala.collection.mutable.ArrayBuffer.from(
      program.spawns.zipWithIndex.map { case (pt, i) =>
        com.wolfskeep.littleman.runtime.LittleMan(id = i, pos = pt, dir = Direction.East)
      }
    )
    val pipes = scala.collection.mutable.ArrayBuffer.from(
      program.pipes.pipes.map(new com.wolfskeep.littleman.runtime.PipeState(_))
    )
    // index pipes by id for quick lookup
    val display = program.displayRoom.map { r =>
      new com.wolfskeep.littleman.runtime.DisplayState(
        r.interiorWidth, r.interiorHeight
      )
    }
    new com.wolfskeep.littleman.runtime.World(
      program, rp,
      men, pipes, display,
      scala.collection.mutable.ArrayDeque.from(input),
      scala.collection.mutable.ArrayBuffer.empty[Long],
      tick = 0L
    )
  }
}