package com.wolfskeep.littleman

import com.wolfskeep.littleman.model.{Direction, Pipe, Point, ProgramText}
import com.wolfskeep.littleman.parse.{PipeParser, RoomScanner}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class PipeParserSpec extends AnyWordSpec with Matchers {
  import RoomScannerSpec.grid

  // helper: scan rooms + pipes together; return Right(pipes) or Left(err)
  private def pipes(s: String): Either[String, Vector[Pipe]] = {
    val text = grid(s)
    RoomScanner.scan(text).flatMap { rooms =>
      PipeParser.parse(text, rooms).map(_.pipes)
    } match {
      case Right(p)  => Right(p)
      case Left(err) =>
        Left(err.message)
    }
  }

  "PipeParser" should {
    "parse a length-2 pipe '>>' between two rooms" in {
      val p = pipes(
        """
          #+-+>>+-+
          #|@|  |H|
          #+-+  +-+
          #""").toOption.get
      p should have size 1
      val pipe = p.head
      pipe.cells shouldBe Vector(Point(3, 0), Point(4, 0))
      pipe.destSide shouldBe Direction.West
    }

    "reject a length-1 pipe (single arrowhead between rooms)" in {
      val err = pipes(
        """
          #+-+>+-+
          #|@| |H|
          #+-+ +-+
          #""").left.get
      err.toLowerCase should include("pipe")
    }

    "reject a body glyph running into a wall without a terminal arrowhead" in {
      val err = pipes(
        """
          #+-+>----+-+
          #|@|     |H|
          #+-+     +-+
          #""").left.get
      err.toLowerCase should include("pipe")
    }

    "reject an arrowhead pointing back along the flow ('>--<')" in {
      val err = pipes(
        """
          #+--+>--<+-+
          #|@ |     |H|
          #+--+     +-+
          #""").left.get
      err.toLowerCase should include("pipe")
    }

    "parse a bent pipe (east then south) into a room below" in {
      val p = pipes(
        """
          #+-+>v
          #|@| |
          #+-+ |
          #    v
          #   +-+
          #   |H|
          #   +-+
          #""").toOption.get
      p should have size 1
      val pipe = p.head
      pipe.cells shouldBe Vector(
        Point(3, 0), Point(4, 0), Point(4, 1), Point(4, 2), Point(4, 3))
      pipe.destSide shouldBe Direction.North
    }

    "reject a free body glyph that belongs to no pipe" in {
      val err = pipes(
        """
          #+--+
          #|@ |
          #+--+
          #   -
          #""").left.get
      err.toLowerCase should include("pipe")
    }

    "reject an arrowhead in open free space (not attached to any room)" in {
      val err = pipes(
        """
          #+--+
          #|@ |
          #+--+
          #   >
          #""").left.get
      err.toLowerCase should include("pipe")
    }

    "reject a body glyph of the wrong axis in the flow" in {
      val err = pipes(
        """
          #+-+>|
          #|@|  
          #+-+  
          #""").left.get
      err.toLowerCase should include("pipe")
    }

    "treat '>' inside a room as an instruction, not a pipe arrowhead" in {
      // the '>' is interior (owned); only the exterior '>>' is a pipe
      val p = pipes(
        """
          #+---+>>+-+
          #|>@ |  |H|
          #+---+  +-+
          #""").toOption.get
      p should have size 1
      val pipe = p.head
      pipe.cells shouldBe Vector(Point(5, 0), Point(6, 0))
    }
  }
}