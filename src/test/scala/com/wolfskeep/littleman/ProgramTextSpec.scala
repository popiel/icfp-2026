package com.wolfskeep.littleman

import com.wolfskeep.littleman.model.{Point, ProgramText}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class ProgramTextSpec extends AnyWordSpec with Matchers {
  "ProgramText" should {
    "construct from an iterable of lines, preserving content" in {
      val pt = ProgramText(Vector("abc", "def"))
      pt.line(0) shouldBe "abc"
      pt.line(1) shouldBe "def"
    }

    "report its number of lines" in {
      ProgramText(Vector("abc", "def")).lineCount shouldBe 2
      ProgramText(Vector.empty).lineCount shouldBe 0
    }

    "pad short lines with spaces to the width of the longest line" in {
      val pt = ProgramText(Vector("abc", "x"))
      pt.line(0) shouldBe "abc"
      pt.line(1) shouldBe "x  "
      pt.width shouldBe 3
    }

    "report width as the max line length" in {
      ProgramText(Vector("hello", "hi")).width shouldBe 5
      ProgramText(Vector.empty).width shouldBe 0
      ProgramText(Vector("", "")).width shouldBe 0
    }

    "charAt returns the character at (x, y)" in {
      val pt = ProgramText(Vector("abc", "def"))
      pt.charAt(0, 0) shouldBe 'a'
      pt.charAt(2, 1) shouldBe 'f'
    }

    "charAt treats out-of-range x as a space (padding)" in {
      val pt = ProgramText(Vector("ab", "cd"))
      pt.charAt(5, 0) shouldBe ' '
      pt.charAt(5, 1) shouldBe ' '
    }

    "charAt treats out-of-range y as a space" in {
      val pt = ProgramText(Vector("ab", "cd"))
      pt.charAt(0, 9) shouldBe ' '
      pt.charAt(3, 9) shouldBe ' '
    }

    "charAt on empty program returns a space" in {
      ProgramText(Vector.empty).charAt(0, 0) shouldBe ' '
    }

    "charAt on an undefined (negative) coordinate returns a space" in {
      ProgramText(Vector("ab")).charAt(-1, 0) shouldBe ' '
      ProgramText(Vector("ab")).charAt(0, -1) shouldBe ' '
    }

    "find all coordinates matching a predicate" in {
      val pt = ProgramText(Vector("a.b", ".c."))
      val dots = pt.findAll { c => c == '.' }
      dots shouldBe Vector(Point(1, 0), Point(0, 1), Point(2, 1))
    }

    "iterate cells in reading order" in {
      val pt = ProgramText(Vector("ab", "cd"))
      pt.cells.toVector shouldBe Vector(Point(0, 0), Point(1, 0), Point(0, 1), Point(1, 1))
    }

    "construct from a newline-delimited string via apply(String)" in {
      val pt = ProgramText("ab\ncd\n")
      pt.lineCount shouldBe 2
      pt.line(0) shouldBe "ab"
      pt.line(1) shouldBe "cd"
      pt.width shouldBe 2
    }
  }
}