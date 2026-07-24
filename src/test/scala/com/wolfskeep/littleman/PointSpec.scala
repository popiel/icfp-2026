package com.wolfskeep.littleman

import com.wolfskeep.littleman.model.Point
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class PointSpec extends AnyWordSpec with Matchers {
  "Point" should {
    "construct from x and y" in {
      Point(3, 4).x shouldBe 3
      Point(3, 4).y shouldBe 4
    }

    "support equality and hashCode as a value type" in {
      Point(1, 2) shouldBe Point(1, 2)
      Point(1, 2) should not be Point(1, 3)
      Point(1, 2).hashCode shouldBe Point(1, 2).hashCode
    }

    "add another point as a delta" in {
      Point(1, 1).plus(Point(2, 3)) shouldBe Point(3, 4)
    }

    "compute Manhattan distance to another point" in {
      Point(0, 0).manhattan(Point(3, 4)) shouldBe 7
      Point(5, 2).manhattan(Point(1, 5)) shouldBe 7
      Point(2, 2).manhattan(Point(2, 2)) shouldBe 0
    }

    "compare reading order (top to bottom, left to right)" in {
      Point(0, 0).readingOrder(Point(0, 1)) < 0 shouldBe true
      Point(1, 0).readingOrder(Point(0, 1)) < 0 shouldBe true
      Point(0, 1).readingOrder(Point(1, 0)) > 0 shouldBe true
      Point(2, 3).readingOrder(Point(2, 3)) shouldBe 0
    }
  }
}