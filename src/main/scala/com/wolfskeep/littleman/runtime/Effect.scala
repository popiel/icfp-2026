package com.wolfskeep.littleman.runtime

import com.wolfskeep.littleman.model.Direction

/** The result of executing one instruction: a set of optional state changes
  * to apply to the little man and/or pipes.
  *
  * - `error` (fatal) ends the whole program immediately.
  * - `block` (retry) leaves the man where he is; he retries next tick. A
  *   blocked man does not move.
  * - Otherwise the TickExecutor applies the non-empty fields: hands, backpack,
  *   direction, the halt flag, and any pipe writes (value into a pipe's source
  *   cell).
  *
  * `pipeWrites` holds `(pipeId, value)` pairs to write into each target pipe's
  * source cell this tick. For `S` it carries one entry per outgoing pipe. The
  * TickExecutor performs these writes (checking the cell is free, which
  * `execute` already verified). */
final case class Effect(
  a: Option[Long] = None,
  b: Option[Long] = None,
  bp: Option[Long] = None,
  dir: Option[Direction] = None,
  halt: Boolean = false,
  pipeWrites: Vector[(Int, Long)] = Vector.empty,
  receives: Vector[Int] = Vector.empty,
  block: Boolean = false,
  error: Option[String] = None
) {
  def isFatal: Boolean = error.isDefined
  def isBlock: Boolean = block && error.isEmpty
  def isProceed: Boolean = !error.isDefined && !block
}

object Effect {
  val NoOp: Effect = Effect()
  val Block: Effect = Effect(block = true)
  val Halt: Effect = Effect(halt = true)
  def error(code: String): Effect = Effect(error = Some(code))
  def hands(a: Long, b: Long): Effect = Effect(a = Some(a), b = Some(b))
  def setA(v: Long): Effect = Effect(a = Some(v))
  def setBP(v: Long): Effect = Effect(bp = Some(v))
  def setDir(d: Direction): Effect = Effect(dir = Some(d))
  def send(pipeId: Int, value: Long): Effect = Effect(pipeWrites = Vector((pipeId, value)))
  def receive(pipeId: Int, value: Long, dir: Option[Direction]): Effect =
    Effect(a = Some(value), dir = dir, receives = Vector(pipeId))
}