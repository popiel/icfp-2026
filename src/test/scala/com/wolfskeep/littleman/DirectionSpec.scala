package com.wolfskeep.littleman

import com.wolfskeep.littleman.model.{Axis, Direction, Point}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class DirectionSpec extends AnyWordSpec with Matchers {
  "Direction" should {
    "have four values: East, West, North, South" in {
      Direction.values.toSet shouldBe Set(
        Direction.East, Direction.West, Direction.North, Direction.South
      )
    }

    "report a delta point for each direction" in {
      Direction.East.delta shouldBe Point(1, 0)
      Direction.West.delta shouldBe Point(-1, 0)
      Direction.North.delta shouldBe Point(0, -1)
      Direction.South.delta shouldBe Point(0, 1)
    }

    "report an opposite" in {
      Direction.East.opposite shouldBe Direction.West
      Direction.West.opposite shouldBe Direction.East
      Direction.North.opposite shouldBe Direction.South
      Direction.South.opposite shouldBe Direction.North
    }

    "report an axis (Horizontal or Vertical)" in {
      Direction.East.axis shouldBe Axis.Horizontal
      Direction.West.axis shouldBe Axis.Horizontal
      Direction.North.axis shouldBe Axis.Vertical
      Direction.South.axis shouldBe Axis.Vertical
    }

    "turn left (counter-clockwise)" in {
      Direction.East.turnLeft shouldBe Direction.North
      Direction.North.turnLeft shouldBe Direction.West
      Direction.West.turnLeft shouldBe Direction.South
      Direction.South.turnLeft shouldBe Direction.East
    }

    "turn right (clockwise)" in {
      Direction.East.turnRight shouldBe Direction.South
      Direction.South.turnRight shouldBe Direction.West
      Direction.West.turnRight shouldBe Direction.North
      Direction.North.turnRight shouldBe Direction.East
    }

    "turn around" in {
      Direction.East.turnAround shouldBe Direction.West
      Direction.South.turnAround shouldBe Direction.North
    }

    "parse single-char arrowheads > < ^ v V" in {
      Direction.fromArrow('>') shouldBe Some(Direction.East)
      Direction.fromArrow('<') shouldBe Some(Direction.West)
      Direction.fromArrow('^') shouldBe Some(Direction.North)
      Direction.fromArrow('v') shouldBe Some(Direction.South)
      Direction.fromArrow('V') shouldBe Some(Direction.South)
    }

    "return None for non-arrowheads" in {
      Direction.fromArrow('-') shouldBe None
      Direction.fromArrow('|') shouldBe None
      Direction.fromArrow('x') shouldBe None
      Direction.fromArrow(' ') shouldBe None
    }
  }

  "Axis" should {
    "have two values: Horizontal and Vertical" in {
      Axis.values.toSet shouldBe Set(Axis.Horizontal, Axis.Vertical)
    }

    "report the perpendicular axis" in {
      Axis.Horizontal.perpendicular shouldBe Axis.Vertical
      Axis.Vertical.perpendicular shouldBe Axis.Horizontal
    }
  }
}