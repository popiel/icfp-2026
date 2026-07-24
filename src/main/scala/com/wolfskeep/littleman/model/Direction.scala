package com.wolfskeep.littleman.model

/** A movement axis on the grid. */
sealed trait Axis { def perpendicular: Axis }
object Axis {
  case object Horizontal extends Axis { val perpendicular = Vertical }
  case object Vertical   extends Axis { val perpendicular = Horizontal }

  val values: Vector[Axis] = Vector(Horizontal, Vertical)
}

/** One of the four cardinal directions a little man may face. */
sealed trait Direction {
  def delta: Point
  def opposite: Direction
  def axis: Axis
  def turnLeft: Direction
  def turnRight: Direction
  def turnAround: Direction = opposite
}

object Direction {
  case object East  extends Direction {
    val delta = Point(1, 0)
    val opposite = West
    val axis = Axis.Horizontal
    val turnLeft = North
    val turnRight = South
  }
  case object West  extends Direction {
    val delta = Point(-1, 0)
    val opposite = East
    val axis = Axis.Horizontal
    val turnLeft = South
    val turnRight = North
  }
  case object North extends Direction {
    val delta = Point(0, -1)
    val opposite = South
    val axis = Axis.Vertical
    val turnLeft = West
    val turnRight = East
  }
  case object South extends Direction {
    val delta = Point(0, 1)
    val opposite = North
    val axis = Axis.Vertical
    val turnLeft = East
    val turnRight = West
  }

  val values: Vector[Direction] = Vector(East, West, North, South)

  /** Parse an arrowhead character into a direction. Returns None for non-
    * arrowheads. Both 'v' and 'V' mean South. */
  def fromArrow(c: Char): Option[Direction] = c match {
    case '>' => Some(East)
    case '<' => Some(West)
    case '^' => Some(North)
    case 'v' | 'V' => Some(South)
    case _ => None
  }
}