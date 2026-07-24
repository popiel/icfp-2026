package com.wolfskeep.littleman

import com.wolfskeep.littleman.model._
import com.wolfskeep.littleman.parse.{LiteralParser, Loader, PipeParser, RoomScanner}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class ValidatorSpec extends AnyWordSpec with Matchers {
  import RoomScannerSpec.grid

  private def load(s: String): Either[String, LoadedProgram] =
    Loader.load(grid(s)) match {
      case Right(p)  => Right(p)
      case Left(err) => Left(err.message)
    }

  "Validator (via Loader)" should {
    "accept a single-room program with no pipes" in {
      val p = load(
        """
          #+--+
          #|@ |
          #+--+""").toOption.get
      p.rooms.rooms should have size 1
      p.pipes.pipes shouldBe empty
      p.inputRoom shouldBe None
      p.outputRoom shouldBe None
    }

    "accept a program with one input room and one output room, each pipeless" in {
      val p = load(
        """
          #+-+ +-+
          #|I| |O|
          #+-+ +-+""").toOption.get
      p.inputRoom shouldBe defined
      p.outputRoom shouldBe defined
      p.inputPipe shouldBe None
      p.outputPipe shouldBe None
    }

    "accept an input room with one pipe flowing out and an output room with one pipe flowing in" in {
      val p = load(
        """
          #+-+>>+-+
          #|I|  |O|
          #+-+  +-+""").toOption.get
      p.inputRoom shouldBe defined
      p.outputRoom shouldBe defined
      p.inputPipe shouldBe defined
      p.outputPipe shouldBe defined
    }

    "reject a second input room" in {
      val err = load(
        """
          #+-+  +-+
          #|I|  |I|
          #+-+  +-+""").left.get
      err.toLowerCase should include("input")
    }

    "reject a second output room" in {
      val err = load(
        """
          #+-+  +-+
          #|O|  |O|
          #+-+  +-+""").left.get
      err.toLowerCase should include("output")
    }

    "reject a pipe flowing INTO an input room (wrong direction)" in {
      // right room's `<<` pipe flows west into the input room on the left
      val err = load(
        """
          #+-+<<+-+
          #|I|  |@|
          #+-+  +-+""").left.get
      err.toLowerCase should include("input")
    }

    "reject a pipe flowing OUT OF an output room (wrong direction)" in {
      // output room's `>>` pipe flows east out of it
      val err = load(
        """
          #+-+>>+-+
          #|O|  |H|
          #+-+  +-+""").left.get
      err.toLowerCase should include("output")
    }

    "reject a second pipe attached to the input room" in {
      // input room has one pipe flowing east AND a second flowing south
      val err = load(
        """
          #+-+>>+-+
          #|I|  |@|
          #+-+  +-+
          # v
          # |
          # v
          # +-+
          # |@|
          # +-+""").left.get
      err.toLowerCase should include("input")
    }

    "reject a display with a pipe on its right side" in {
      // display's right wall receives a west-pointing `<<` pipe from the east
      val err = load(
        """
          #+===+  +-+
          #:   :<<|@|
          #+===+  +-+""").left.get
      err.toLowerCase should include("display")
    }

    "reject two display pipes on the same side" in {
      // two source rooms each send a `>>` pipe into the display's left wall
      val err = load(
        """
          #+-+  +====+
          #|@|>>:    :
          #+-+  :    :
          #     :    :
          #+-+  :    :
          #|@|>>:    :
          #+-+  :    :
          #     +====+""").left.get
      err.toLowerCase should include("display")
    }

    "accept a display with one pipe each on top, left, and bottom" in {
      val p = load(
        """
          #      +-+
          #      |@|
          #      +-+
          #      v
          #      v
          #+-+  +===+
          #|@|>>:   :
          #+-+  :   :
          #     +===+
          #      ^
          #      ^
          #      +-+
          #      |@|
          #      +-+""").toOption.get
      p.displayRoom shouldBe defined
      p.pipes.pipes should have size 3
    }
  }
}