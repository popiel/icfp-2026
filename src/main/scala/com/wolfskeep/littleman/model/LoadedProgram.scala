package com.wolfskeep.littleman.model

/** The fully-parsed, validated, immutable littleman program.
  *
  * @param text       the source grid
  * @param rooms      rooms & displays with spawns
  * @param pipes      the pipe network
  * @param literals   the backtick literal table
  */
final case class LoadedProgram(
  text: ProgramText,
  rooms: RoomScan,
  pipes: PipeNetwork,
  literals: LiteralTable
) {
  /** All spawn positions in reading order. */
  def spawns: Vector[Point] = rooms.spawns

  /** The input room, if any. */
  def inputRoom: Option[Room] = rooms.rooms.find(_.kind == RoomKind.Input)

  /** The output room, if any. */
  def outputRoom: Option[Room] = rooms.rooms.find(_.kind == RoomKind.Output)

  /** The display, if any. */
  def displayRoom: Option[Room] = rooms.rooms.find(_.kind == RoomKind.Display)

  /** Pipe flowing out of the input room (its source is the input room), if any. */
  def inputPipe: Option[Pipe] =
    pipes.pipes.find(p => inputRoom.exists(_.id == p.sourceRoomId))

  /** Pipe flowing into the output room, if any. */
  def outputPipe: Option[Pipe] =
    pipes.pipes.find(p => p.dest == PipeDest.Output)
}