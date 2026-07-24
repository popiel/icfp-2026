package com.wolfskeep.littleman

import com.wolfskeep.littleman.model.{Direction, LoadedProgram, Point, ProgramText}
import com.wolfskeep.littleman.parse.Loader
import com.wolfskeep.littleman.runtime.{Effect, Instruction, LittleMan, NoOpContext}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class InstructionSpec extends AnyWordSpec with Matchers {
  import InstructionSpec._

  // a default little man at the origin facing east, A=B=BP=0
  private def man: LittleMan = LittleMan(0, Point(0, 0), Direction.East)
  private def ctx = NoOpContext(emptyProgram)

  private def ex(c: Char, m: LittleMan = man): Effect = Instruction.execute(c, m, ctx)

  "Instruction" should {
    "load a digit 0-9 into A" in {
      ex('0').a shouldBe Some(0L)
      ex('9').a shouldBe Some(9L)
      ex('5', man.copy(a = 100L)).a shouldBe Some(5L)
    }

    "M copies A into B (A unchanged)" in {
      val e = ex('M', man.copy(a = 7L, b = 3L))
      e.a shouldBe Some(7L)
      e.b shouldBe Some(7L)
    }

    "W swaps A and B" in {
      val e = ex('W', man.copy(a = 7L, b = 3L))
      e.a shouldBe Some(3L)
      e.b shouldBe Some(7L)
    }

    "perform arithmetic: +, -, *, N" in {
      ex('+', man.copy(a = 3L, b = 4L)).a shouldBe Some(7L)
      ex('-', man.copy(a = 3L, b = 4L)).a shouldBe Some(-1L)
      ex('*', man.copy(a = 3L, b = 4L)).a shouldBe Some(12L)
      ex('N', man.copy(a = 5L)).a shouldBe Some(-5L)
      ex('N', man.copy(a = -5L)).a shouldBe Some(5L)
    }

    "wrap on 64-bit overflow (Long semantics)" in {
      ex('+', man.copy(a = Long.MaxValue, b = 1L)).a shouldBe Some(Long.MinValue)
    }

    "/ floors and puts remainder in B" in {
      val e = ex('/', man.copy(a = 7L, b = 2L))
      e.a shouldBe Some(3L)
      e.b shouldBe Some(1L)
    }

    "/ floors toward negative infinity for negative operands" in {
      val e = ex('/', man.copy(a = -7L, b = 2L))
      e.a shouldBe Some(-4L) // floor(-3.5) = -4
      e.b shouldBe Some(1L)   // (-4)*2 + 1 = -7
    }

    "/ with B=0 sets A=0 and B keeps the dividend" in {
      val e = ex('/', man.copy(a = 7L, b = 0L))
      e.a shouldBe Some(0L)
      e.b shouldBe Some(7L)
    }

    "% is floored modulo with B's sign; 0 if B=0" in {
      ex('%', man.copy(a = 7L, b = 3L)).a shouldBe Some(1L)
      ex('%', man.copy(a = -7L, b = 3L)).a shouldBe Some(2L)  // floored: -7 mod 3 = 2
      ex('%', man.copy(a = 7L, b = -3L)).a shouldBe Some(-2L) // B's sign
      ex('%', man.copy(a = 7L, b = 0L)).a shouldBe Some(0L)
    }

    "satisfy (A/B)*B + remainder = A" in {
      val cases = Vector((17L, 5L), (-17L, 5L), (17L, -5L), (-17L, -5L))
      for ((a, b) <- cases) {
        val e = ex('%', man.copy(a = a, b = b))
        val m = e.a.get
        val q = Math.floorDiv(a, b)
        q * b + m shouldBe a
      }
    }

    "do bitwise AND, OR, XOR on two's-complement 64-bit" in {
      ex('&', man.copy(a = 0xC, b = 0xA)).a shouldBe Some(0x8L)
      ex('|', man.copy(a = 0xC, b = 0xA)).a shouldBe Some(0xEL)
      ex('~', man.copy(a = 0xC, b = 0xA)).a shouldBe Some(0x6L)
      // negative operands: -1 is all ones
      ex('&', man.copy(a = -1L, b = 0xF0L)).a shouldBe Some(0xF0L)
    }

    "{ shift left by B; 0 if B outside 0-63" in {
      ex('{', man.copy(a = 1L, b = 4L)).a shouldBe Some(16L)
      ex('{', man.copy(a = 1L, b = 64L)).a shouldBe Some(0L)
      ex('{', man.copy(a = 1L, b = -1L)).a shouldBe Some(0L)
    }

    "} arithmetic shift right (sign-filling)" in {
      ex('}', man.copy(a = 16L, b = 2L)).a shouldBe Some(4L)
      ex('}', man.copy(a = -16L, b = 2L)).a shouldBe Some(-4L)
      ex('}', man.copy(a = -1L, b = 64L)).a shouldBe Some(-1L) // sign-fill for b>63
      ex('}', man.copy(a = 1L, b = -1L)).a shouldBe Some(0L)
    }

    "set direction with > < ^ v V" in {
      ex('>').dir shouldBe Some(Direction.East)
      ex('<').dir shouldBe Some(Direction.West)
      ex('^').dir shouldBe Some(Direction.North)
      ex('v').dir shouldBe Some(Direction.South)
      ex('V').dir shouldBe Some(Direction.South)
    }

    "X turns by sign(A): right if >0, left if <0, straight if 0" in {
      ex('X', man.copy(a = 5L)).dir shouldBe Some(Direction.South)  // east -> right -> south
      ex('X', man.copy(a = -5L)).dir shouldBe Some(Direction.North) // east -> left -> north
      ex('X', man.copy(a = 0L)).dir shouldBe Some(Direction.East)
      ex('X', man.copy(a = 1L, dir = Direction.North)).dir shouldBe Some(Direction.East)
    }

    "treat . space @ as no-op" in {
      ex('.').isProceed shouldBe true
      ex(' ').isProceed shouldBe true
      ex('@').isProceed shouldBe true
      ex('.').a shouldBe None
    }

    "H halts" in {
      ex('H').halt shouldBe true
    }

    "b copies A into BP" in {
      ex('b', man.copy(a = 42L)).bp shouldBe Some(42L)
    }

    "m decrements BP by 1 with no clamp" in {
      ex('m', man.copy(bp = 5L)).bp shouldBe Some(4L)
      ex('m', man.copy(bp = 0L)).bp shouldBe Some(-1L)
    }

    "d turns right if BP > 0, else straight" in {
      ex('d', man.copy(bp = 5L)).dir shouldBe Some(Direction.South)
      ex('d', man.copy(bp = 0L)).dir shouldBe None
      ex('d', man.copy(bp = -1L)).dir shouldBe None
    }

    "a turns left if BP > 0, else straight" in {
      ex('a', man.copy(bp = 5L)).dir shouldBe Some(Direction.North)
      ex('a', man.copy(bp = 0L)).dir shouldBe None
    }

    "] arithmetic-shifts BP right by 1 (sign-preserving)" in {
      ex(']', man.copy(bp = 8L)).bp shouldBe Some(4L)
      ex(']', man.copy(bp = -8L)).bp shouldBe Some(-4L)
      ex(']', man.copy(bp = -1L)).bp shouldBe Some(-1L)
    }

    "x turns right if BP low bit is 1, else left (always turns; reads raw bit)" in {
      ex('x', man.copy(bp = 5L)).dir shouldBe Some(Direction.South)  // 5 odd -> right
      ex('x', man.copy(bp = 4L)).dir shouldBe Some(Direction.North)  // 4 even -> left
      ex('x', man.copy(bp = -3L)).dir shouldBe Some(Direction.South) // -3 is ...11101, low bit 1 -> right
      ex('x', man.copy(bp = -2L)).dir shouldBe Some(Direction.North) // -2 is ...11110, low bit 0 -> left
    }

    "an unrecognized char is a bad-op error" in {
      ex('z').error shouldBe Some("bad-op")
      ex('?').error shouldBe Some("bad-op")
    }
  }
}

object InstructionSpec {
  lazy val emptyProgram: LoadedProgram =
    Loader.load(ProgramText("+\n")).toOption
      .getOrElse(throw new RuntimeException("empty program failed to load"))
}