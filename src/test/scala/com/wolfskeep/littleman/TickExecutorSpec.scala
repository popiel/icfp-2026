package com.wolfskeep.littleman

import com.wolfskeep.littleman.parse.{Loader, WorldFactory}
import com.wolfskeep.littleman.runtime.{RunError, TickExecutor}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class TickExecutorSpec extends AnyWordSpec with Matchers {
  import RoomScannerSpec.grid
  import TickExecutorSpec.{runProg, Result}

  "TickExecutor" should {
    "execute the spawn '@' as a no-op and move the man east to H which halts" in {
      val r = runProg(
        """
          #+----+
          #|@ H |
          #+----+""",
        input = "")
      r.output shouldBe Vector.empty
      r.men.head.halted shouldBe true
      r.men.head.pos shouldBe PointTest.point(3, 1) // @ moves east to H over 3 ticks
    }

    "load a digit into A then halt" in {
      val r = runProg(
        """
          #+-----+
          #|@3 H |
          #+-----+""",
        input = "")
      r.men.head.a shouldBe 3L
      r.men.head.halted shouldBe true
    }

    "shift pipes before instructions execute (a sent value moves next tick)" in {
      // sender @3sH sends 3; receiver @rsH reads it and forwards to output
      val r = runProg(
        """
          #+----+>>+-----+>>+-+
          #|@3sH|  |@rsH |  |O|
          #+----+  +-----+  +-+""",
        input = "")
      r.output shouldBe Vector(3L)
    }

    "drain output pipe after everyone halts (output flush)" in {
      val r = runProg(
        """
          #+----+>>>>>>>>>>+-+
          #|@3sH|          |O|
          #+----+          +-+""",
        input = "")
      r.output shouldBe Vector(3L)
    }

    "emit a wall error when a man runs into a wall" in {
      val r = runProg(
        """
          #+--+
          #|@ |
          #+--+""",
        input = "")
      r.error shouldBe defined
      r.error.get.code shouldBe "wall"
    }

    "emit a bad-op error for an invalid interior character" in {
      val r = runProg(
        """
          #+---+
          #|@Z |
          #+---+""",
        input = "")
      r.error shouldBe defined
      r.error.get.code shouldBe "bad-op"
    }

    "send to a full pipe blocks the sender" in {
      val r = runProg(
        """
          #+----+>>+----+
          #|@5sH|  |O   |
          #+----+  +----+""",
        input = "",
        stepCap = 4L)
      r.output shouldBe empty
    }

    "receive from an empty pipe blocks the receiver" in {
      val r = runProg(
        """
          #+-----+>>+----+
          #|@H   |  |rH  |
          #+-----+  +----+""",
        input = "",
        stepCap = 3L)
      r.error shouldBe None
      r.men.count(_.halted) shouldBe 1 // the @H man halted
    }

"read input from stdin via the input room's pipe" in {
      val r = runProg(
        """
          #+-+>>+-----+>>+-+
          #|I|  |@rMsH|  |O|
          #+-+  +-----+  +-+""",
        input = "7")
      r.output shouldBe Vector(7L)
    }

    "support multiple rooms moving in lockstep" in {
      val r = runProg(
        """
          #+--+ +--+
          #|@H| |@H|
          #+--+ +--+""",
        input = "")
      r.men.count(_.halted) shouldBe 2
      r.output shouldBe empty
    }
  }
}

object TickExecutorSpec {
  final case class Result(
    output: Vector[Long],
    men: Vector[com.wolfskeep.littleman.runtime.LittleMan],
    ticks: Long,
    error: Option[RunError]
  )

  def runProg(s: String, input: String, stepCap: Long = 10000L): Result = {
    val text = RoomScannerSpec.grid(s)
    val loaded = Loader.load(text) match {
      case Right(p)  => p
      case Left(err) => throw new RuntimeException(s"load failed: ${err.message}\nfor:\n${(0 until text.lineCount).map(y => s"  $y:[${text.line(y)}]").mkString("\n")}")
    }
    val resolved = com.wolfskeep.littleman.resolve.Resolver.resolve(loaded)
    val inputs = input.trim.split("\\s+").filter(_.nonEmpty).map(_.toLong).toVector
    val world = new WorldFactory(stepCap).build(resolved, inputs)
    val exe = new TickExecutor()
    var err: Option[RunError] = None
    var tick = 0L
    while (err.isEmpty && tick < stepCap && exe.progressible(world)) {
      exe.step(world) match {
        case Left(e) => err = Some(e)
        case Right(_) => tick += 1
      }
    }
    Result(world.output.toVector, world.men.toVector, tick, err)
  }
}

object PointTest {
  def point(x: Int, y: Int): com.wolfskeep.littleman.model.Point =
    com.wolfskeep.littleman.model.Point(x, y)
}