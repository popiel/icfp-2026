package com.wolfskeep.littleman.runtime

import com.wolfskeep.littleman.model._

/** A little man: his position, direction, three integers, and halt state.
  *
  * `blocked` is NOT stored — it is derived per tick by the [[TickExecutor]]
  * (a man is blocked iff the instruction under him is a send/receive that
  * cannot complete this tick). */
final case class LittleMan(
  id: Int,
  pos: Point,
  dir: Direction,
  a: Long = 0L,
  b: Long = 0L,
  bp: Long = 0L,
  halted: Boolean = false
) {
  def withHands(a: Long = this.a, b: Long = this.b): LittleMan = copy(a = a, b = b)
  def withBackpack(bp: Long): LittleMan = copy(bp = bp)
  def withDir(dir: Direction): LittleMan = copy(dir = dir)
  def withPos(pos: Point): LittleMan = copy(pos = pos)
  def halt: LittleMan = copy(halted = true)
}