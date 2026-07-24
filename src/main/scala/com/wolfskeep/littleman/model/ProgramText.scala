package com.wolfskeep.littleman.model

/** An immutable, padded view of a littleman program's raw ASCII grid.
  *
  * Short lines are padded with spaces to the width of the longest line, so
  * that every cell of the rectangular grid is well-defined. Out-of-range or
  * negative coordinates read as a space. */
final case class ProgramText(rows: Vector[String]) {
  val lineCount: Int = rows.length
  val width: Int = if (rows.isEmpty) 0 else rows.map(_.length).max

  private val padded: Vector[String] =
    rows.map(line => line + " " * math.max(0, width - line.length))

  /** The y-th line, padded to `width`. */
  def line(y: Int): String =
    if (y >= 0 && y < padded.length) padded(y) else " " * width

  /** The character at (x, y), or ' ' if the coordinate is out of range. */
  def charAt(x: Int, y: Int): Char = {
    if (x < 0 || y < 0 || y >= padded.length) ' '
    else {
      val l = padded(y)
      if (x >= l.length) ' ' else l.charAt(x)
    }
  }

  def charAt(p: Point): Char = charAt(p.x, p.y)

  /** All grid coordinates in reading order (top-to-bottom, left-to-right). */
  def cells: IndexedSeq[Point] =
    for {
      y <- 0 until lineCount
      x <- 0 until width
    } yield Point(x, y)

  /** All coordinates whose character satisfies `pred`, in reading order. */
  def findAll(pred: Char => Boolean): Vector[Point] =
    cells.filter(p => pred(charAt(p))).toVector
}

object ProgramText {
  /** Build from a newline-delimited string. A trailing newline does not
    * produce an extra empty line. */
  def apply(text: String): ProgramText = {
    val lines = text.split("\n", -1)
    val trimmed =
      if (lines.nonEmpty && lines.last.isEmpty) lines.init
      else lines
    new ProgramText(trimmed.toVector)
  }
}