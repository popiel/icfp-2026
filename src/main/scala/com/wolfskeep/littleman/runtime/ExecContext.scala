package com.wolfskeep.littleman.runtime

import com.wolfskeep.littleman.model._

/** Read-only execution context passed to [[Instruction.execute]].
  *
  * Provides the static program/text/literals, the resolved pipe targets, and
  * the live pipe state queries needed by send/receive/q to decide whether
  * they can proceed this tick. */
trait ExecContext {
  def program: LoadedProgram
  def resolved: ResolvedProgram

  /** Is the source cell of pipe `pipeId` currently free (a send can write)? */
  def sourceCellFree(pipeId: Int): Boolean

  /** The value currently held at the dest cell of pipe `pipeId`, if any. */
  def destCellValue(pipeId: Int): Option[Long]

  /** The side of the room that pipe `pipeId` enters (its destSide); for `U`. */
  def pipeDestSide(pipeId: Int): Direction

  /** Number of values currently held in pipe `pipeId`. For `q`. */
  def pipeValueCount(pipeId: Int): Int

  /** Literal segments whose delimiter includes `point`, if any. */
  def literalsAt(point: Point): Vector[LiteralSegment]
}

/** A no-op context for testing pure (non-pipe) instructions: no pipes, no
  * literals. Pipe ops in this context return a `no-pipe` error. */
object NoOpContext {
  def apply(prog: LoadedProgram): ExecContext = new ExecContext {
    val program = prog
    val resolved = ResolvedProgram(prog, Map.empty)
    def sourceCellFree(pipeId: Int): Boolean = false
    def destCellValue(pipeId: Int): Option[Long] = None
    def pipeDestSide(pipeId: Int): Direction = Direction.East
    def pipeValueCount(pipeId: Int): Int = 0
    def literalsAt(point: Point): Vector[LiteralSegment] = Vector.empty
  }
}