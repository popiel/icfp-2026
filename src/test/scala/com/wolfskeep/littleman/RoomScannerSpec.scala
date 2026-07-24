package com.wolfskeep.littleman

import com.wolfskeep.littleman.model.{Point, ProgramText, RoomKind}
import com.wolfskeep.littleman.parse.RoomScanner
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class RoomScannerSpec extends AnyWordSpec with Matchers {
  import RoomScannerSpec.grid

  "RoomScanner" should {
    "scan a single 3x3 room with one spawn" in {
      val scan = RoomScanner.scan(grid(
        """
          |+-+
          ||@|
          |+-+
          |""")).toOption.get
      scan.rooms should have size 1
      val r = scan.rooms.head
      r.topLeft shouldBe Point(0, 0)
      r.width shouldBe 3
      r.height shouldBe 3
      r.kind shouldBe RoomKind.Normal
      scan.spawns shouldBe Vector(Point(1, 1))
      scan.isOwned(Point(0, 0)) shouldBe true
      scan.isOwned(Point(1, 1)) shouldBe true
      scan.isFree(Point(3, 0)) shouldBe true
    }

    "scan a larger room and place the spawn at the @" in {
      val scan = RoomScanner.scan(grid(
        """
          |+----+
          ||@ H |
          ||    |
          |+----+
          |""")).toOption.get
      scan.rooms should have size 1
      scan.spawns shouldBe Vector(Point(1, 1))
    }

    "scan two adjacent rooms sharing a wall as two rooms" in {
      val scan = RoomScanner.scan(grid(
        """
          |+--+--+
          ||@.|. |
          |+--+--+
          |""")).toOption.get
      scan.rooms should have size 2
      scan.spawns shouldBe Vector(Point(1, 1))
    }

    "reject nested rooms (overlapping interiors)" in {
      val err = RoomScanner.scan(grid(
        """
          |+----+
          ||+--+|
          |||@ ||
          ||+--+|
          |+----+
          |"""))
      err.isLeft shouldBe true
      err.left.get.message.toLowerCase should include("overlap")
    }

    "reject a spawn outside any room" in {
      val err = RoomScanner.scan(grid(
        """
          | @+-+
          |  |@|
          |  +-+
          |"""))
      err.isLeft shouldBe true
      err.left.get.message.toLowerCase should include("spawn")
    }

    "reject multiple @ in a single room" in {
      val err = RoomScanner.scan(grid(
        """
          |+------+
          ||@  @  |
          |+------+
          |"""))
      err.isLeft shouldBe true
      err.left.get.message.toLowerCase should include("spawn")
    }

    "detect a 3x3 input room by interior 'I'" in {
      val scan = RoomScanner.scan(grid(
        """
          |+-+
          ||I|
          |+-+
          |""")).toOption.get
      scan.rooms.head.kind shouldBe RoomKind.Input
    }

    "detect a 3x3 output room by interior 'O'" in {
      val scan = RoomScanner.scan(grid(
        """
          |+-+
          ||O|
          |+-+
          |""")).toOption.get
      scan.rooms.head.kind shouldBe RoomKind.Output
    }

    "detect a display by '=' horizontal and ':' vertical walls" in {
      val scan = RoomScanner.scan(grid(
        """
          |+====+
          |:    :
          |:    :
          |+====+
          |""")).toOption.get
      scan.rooms should have size 1
      scan.rooms.head.kind shouldBe RoomKind.Display
      scan.rooms.head.interiorWidth shouldBe 4
      scan.rooms.head.interiorHeight shouldBe 2
      scan.spawns shouldBe empty
    }

    "reject a spawn ('@') inside a display" in {
      val err = RoomScanner.scan(grid(
        """
          |+===+
          |:@  :
          |+===+
          |"""))
      err.isLeft shouldBe true
      err.left.get.message.toLowerCase should include("display")
    }

    "reject a room with mixed wall styles" in {
      val err = RoomScanner.scan(grid(
        """
          |+===+
          ||   |
          |+===+
          |"""))
      err.isLeft shouldBe true
      err.left.get.message.toLowerCase should include("wall")
    }

    "order spawns by reading order across rooms" in {
      val scan = RoomScanner.scan(grid(
        """
          |+--+ +--+
          ||@ | |@ |
          |+--+ +--+
          |""")).toOption.get
      scan.spawns shouldBe Vector(Point(1, 1), Point(6, 1))
    }

    "return an empty scan for a blank program" in {
      val scan = RoomScanner.scan(grid(
        """
          |   
          |   """)).toOption.get
      scan.rooms shouldBe empty
      scan.spawns shouldBe empty
    }
  }
}

object RoomScannerSpec {
  /** Build a ProgramText from a `stripMargin`-style heredoc. Each content
    * line begins with a '|' margin marker (which is stripped); the left wall
    * '|' of a room is written as the second '|' on that line, so it survives
    * stripping. Common leading indentation is then removed with stripIndent. */
  def grid(s: String): ProgramText = {
    val raw = s.linesIterator.toVector
      .dropWhile(_.trim.isEmpty).reverse.dropWhile(_.trim.isEmpty).reverse
    val marged = raw.map { line =>
      val i = line.indexWhere(c => c != ' ')
      if (i >= 0 && line(i) == '|') line.substring(0, i) + line.substring(i + 1)
      else line
    }
    ProgramText(marged.mkString("\n").stripIndent)
  }
}