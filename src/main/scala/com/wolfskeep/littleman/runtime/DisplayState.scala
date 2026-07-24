package com.wolfskeep.littleman.runtime

import com.wolfskeep.littleman.model.{DisplaySide, Point}

/** Mutable runtime state for an LM-75 display.
  *
  * - `current` is shown; `next` is being composed; `cursor` is the next pixel
  *   to draw, advancing left-to-right, top-to-bottom after each DATA write.
  * - Both buffers initially hold color 0 (black); cursor begins at (0,0). */
final class DisplayState(val width: Int, val height: Int) {
  require(width >= 1 && height >= 1 && width <= 64 && height <= 64)

  private var current: Array[Long] = Array.fill(width * height)(0L)
  private var next: Array[Long] = Array.fill(width * height)(0L)
  private var cx: Int = 0
  private var cy: Int = 0

  def cursor: Point = Point(cx, cy)

  /** ADDR: set cursor to (col, row). */
  def setCursor(col: Int, row: Int): Unit = {
    cx = col
    cy = row
  }

  /** DATA: set pixel at the cursor to `color` (must be 0..15), then advance. */
  def draw(color: Long): Unit = {
    if (cx >= 0 && cx < width && cy >= 0 && cy < height)
      next(cy * width + cx) = color
    advanceCursor()
  }

  /** SWAP: copy next->current. mode=0 clears next and resets cursor; mode=1
    * preserves next and cursor. */
  def swap(mode: Int): Unit = {
    Array.copy(next, 0, current, 0, current.length)
    if (mode == 0) {
      java.util.Arrays.fill(next, 0L)
      cx = 0
      cy = 0
    }
  }

  private def advanceCursor(): Unit = {
    if (cx + 1 < width) cx += 1
    else if (cy + 1 < height) { cx = 0; cy += 1 }
    else { cx = 0; cy = 0 }
  }

  def currentBuffer: Vector[Long] = current.toVector
  def nextBuffer: Vector[Long] = next.toVector

  /** Copy buffer contents and cursor from `other` (same dims). */
  def copyFrom(other: DisplayState): Unit = {
    Array.copy(other.current, 0, current, 0, current.length)
    Array.copy(other.next, 0, next, 0, next.length)
    cx = other.cx
    cy = other.cy
  }
}