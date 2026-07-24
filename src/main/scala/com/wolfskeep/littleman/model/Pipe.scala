package com.wolfskeep.littleman.model

/** A parsed pipe: a unidirectional, ordered list of cells carrying values
  * from a source room to a destination endpoint.
  *
  * @param cells     the pipe cells in flow order, source segment first.
  *                  `cells.head` is the source end (where sends write);
  *                  `cells.last` is the destination end (where receives read).
  * @param sourceRoomId the room the pipe flows out of.
  * @param dest        the pipe's destination.
  * @param destSide    the direction (from the dest room's perspective) that
  *                    the pipe enters the destination room; used by `U`.
  */
final case class Pipe(
  id: Int,
  cells: Vector[Point],
  sourceRoomId: Int,
  dest: PipeDest,
  destSide: Direction
) {
  require(cells.length >= 2, "pipe must be at least 2 cells long")

  /** The source segment cell (where sends put values). */
  def sourceCell: Point = cells.head

  /** The destination segment cell (where receives take values from). */
  def destCell: Point = cells.last

  /** Length in cells; also the max number of values the pipe can hold. */
  def length: Int = cells.length

  /** Interior cells between the source and dest ends (exclusive of both). */
  def interiorCells: Vector[Point] = cells.drop(1).dropRight(1)
}

/** What a pipe leads to. */
sealed trait PipeDest
object PipeDest {
  /** The pipe flows into a normal little-man room. */
  final case class Room(roomId: Int) extends PipeDest
  /** The pipe flows into the output room (its values become program output). */
  case object Output extends PipeDest
  /** The pipe flows into a display's ADDR/DATA/SWAP side. */
  final case class Display(roomId: Int, side: DisplaySide) extends PipeDest
}

/** Which side of an LM-75 display a pipe attaches to. */
sealed trait DisplaySide
object DisplaySide {
  case object Top    extends DisplaySide // ADDR
  case object Left   extends DisplaySide // DATA
  case object Bottom extends DisplaySide // SWAP
}

/** The result of parsing pipes: the pipes plus a map from every pipe cell
  * to the pipe id that owns it. */
final case class PipeNetwork(
  pipes: Vector[Pipe],
  cellOwner: Map[Point, Int]
)