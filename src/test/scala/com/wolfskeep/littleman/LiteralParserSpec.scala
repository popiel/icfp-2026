package com.wolfskeep.littleman

import com.wolfskeep.littleman.model.{Axis, Point, ProgramText, LiteralSegment, LiteralTable}
import com.wolfskeep.littleman.parse.LiteralParser
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class LiteralParserSpec extends AnyWordSpec with Matchers {
  import RoomScannerSpec.grid

  private def lit(s: String): Either[String, LiteralTable] =
    LiteralParser.parse(grid(s)) match {
      case Right(t)  => Right(t)
      case Left(err) => Left(err.message)
    }

  "LiteralParser" should {
    "parse a horizontal `123` literal into one segment with 3 digits" in {
      val t = lit("""`123`""").toOption.get
      t.segments should have size 1
      val seg = t.segments.head
      seg.axis shouldBe Axis.Horizontal
      seg.openCell shouldBe Point(0, 0)
      seg.closeCell shouldBe Point(4, 0)
      seg.digits shouldBe Vector(Point(1, 0), Point(2, 0), Point(3, 0))
    }

    "parse an empty backtick pair `` as one segment with no digits" in {
      val t = lit("""``""").toOption.get
      t.segments should have size 1
      t.segments.head.digits shouldBe empty
    }

    "ignore spaces between the backticks when collecting digits" in {
      val t = lit("""` 1 23 `""").toOption.get
      t.segments.head.digits shouldBe Vector(
        Point(2, 0), Point(4, 0), Point(5, 0))
    }

    "parse a vertical literal in a column" in {
      val t = lit(
        """#`
          #1
          #2
          #`""").toOption.get
      t.segments should have size 1
      val seg = t.segments.head
      seg.axis shouldBe Axis.Vertical
      seg.openCell shouldBe Point(0, 0)
      seg.closeCell shouldBe Point(0, 3)
      seg.digits shouldBe Vector(Point(0, 1), Point(0, 2))
    }

    "pair backticks independently per axis when stacked row and column" in {
      // backticks at (0,0),(3,0),(0,1): the corner (0,0) opens a horizontal
      // literal to (3,0) and a vertical literal to (0,1)
      val t = lit(
        """#`12`
           #`""").toOption.get
      t.segments should have size 2
      t.segments.map(_.axis).toSet shouldBe Set(Axis.Horizontal, Axis.Vertical)
    }

    "pair backticks on a row left-to-right (1st with 2nd, 3rd with 4th)" in {
      val t = lit("""`1``2`""").toOption.get
      t.segments should have size 2
      // first pair: backticks at x=0 and x=2 -> "1"
      // second pair: backticks at x=3 and x=5 -> "2"
      val segs = t.segments
      segs.find(_.openCell == Point(0, 0)).get.digits shouldBe Vector(Point(1, 0))
      segs.find(_.openCell == Point(3, 0)).get.digits shouldBe Vector(Point(4, 0))
    }

    "reject a non-digit character between a matched backtick pair" in {
      val err = lit("""`1x3`""").left.get
      err.toLowerCase should include("literal")
    }

    "reject a backtick that pairs on neither axis" in {
      val err = lit("""`""").left.get
      err.toLowerCase should include("backtick")
    }

    "reject a value that does not fit in 64 bits read in either direction" in {
      val big = "9" * 20 // 999999999999999999999 > Long.MaxValue
      val err = lit(s"`$big`").left.get
      err.toLowerCase should include("64")
    }

    "accept a value at the 64-bit boundary (Long.MaxValue)" in {
      val t = lit("""`9223372036854775807`""").toOption.get
      t.segments should have size 1
    }

    "leave a backtick that a man walks perpendicular to as a no-op (it pairs only on one axis)" in {
      // A horizontal literal `123`; the left backtick also sits in a column
      // with no other backtick, so it must NOT error.
      val t = lit(
        """`123`
          #H
          #""").toOption.get
      t.segments should have size 1
      t.segments.head.axis shouldBe Axis.Horizontal
    }
  }
}