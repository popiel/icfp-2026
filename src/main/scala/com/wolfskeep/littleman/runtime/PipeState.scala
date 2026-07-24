package com.wolfskeep.littleman.runtime

import com.wolfskeep.littleman.model.Pipe

/** Mutable per-pipe state at runtime: a slot per cell of the pipe, holding
  * Option[Long].
  *
  * `cells(0)` is the source end; `cells(length-1)` is the destination end. */
final class PipeState(val pipe: Pipe) {
  private val slots: Array[Option[Long]] = Array.fill(pipe.length)(None)

  def length: Int = pipe.length
  def sourceCell: com.wolfskeep.littleman.model.Point = pipe.sourceCell
  def destCell: com.wolfskeep.littleman.model.Point = pipe.destCell

  def isSourceFree: Boolean = slots(0).isEmpty
  def isDestReady: Boolean = slots(length - 1).isDefined
  def destValue: Option[Long] = slots(length - 1)
  def count: Int = slots.count(_.isDefined)

  /** Read and clear the dest cell. Caller must check isDestReady first. */
  def takeDest(): Long = {
    val v = slots(length - 1).get
    slots(length - 1) = None
    v
  }

  /** Write `value` into the source cell. Caller must check isSourceFree. */
  def putSource(value: Long): Unit = {
    slots(0) = Some(value)
  }

  /** Shift every value one cell toward the destination if the next cell is
    * free. Process from the destination end backward so a value cannot
    * advance twice in one tick. */
  def shift(): Unit = {
    var i = length - 2
    while (i >= 0) {
      if (slots(i).isDefined && slots(i + 1).isEmpty) {
        slots(i + 1) = slots(i)
        slots(i) = None
      }
      i -= 1
    }
  }

  /** Snapshot, useful for tests/debugging. */
  def snapshot: Vector[Option[Long]] = slots.toVector

  /** Read slot i (for snapshotting). */
  def slot(i: Int): Option[Long] =
    if (i >= 0 && i < length) slots(i) else None

  /** Set slot i to v (for restoring a snapshot). */
  def setSlot(i: Int, v: Option[Long]): Unit =
    if (i >= 0 && i < length) slots(i) = v
}