package com.wolfskeep.littleman

import com.wolfskeep.littleman.io.CollectingSink
import com.wolfskeep.littleman.parse.{Loader, WorldFactory}
import com.wolfskeep.littleman.runtime.TickExecutor
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class SimulatorSpec extends AnyWordSpec with Matchers {
  import RoomScannerSpec.grid

  private def simulate(s: String, input: String, stepCap: Long = 10000L):
      (Vector[Long], Option[com.wolfskeep.littleman.runtime.RunError]) = {
    val text = grid(s)
    val loaded = Loader.load(text) match {
      case Right(p)  => p
      case Left(err) => throw new RuntimeException(s"load failed: ${err.message}")
    }
    val resolved = com.wolfskeep.littleman.resolve.Resolver.resolve(loaded)
    val inputs = input.trim.split("\\s+").filter(_.nonEmpty).map(_.toLong).toVector
    val world = new WorldFactory(stepCap).build(resolved, inputs)
    val sink = new CollectingSink
    val sim = new com.wolfskeep.littleman.Simulator(stepCap, new TickExecutor, sink)
    val err = sim.run(world)
    (sink.toVector, err.left.toOption)
  }

  "Simulator" should {
    "drain emitted values to the sink in order" in {
      val (out, err) = simulate(
        """
          #+-+>>+-----+>>+-+
          #|I|  |@rsH |  |O|
          #+-+  +-----+  +-+""",
        input = "42")
      if (out != Vector(42L) || err.isDefined)
        println(s"DEBUG drain out=$out err=$err")
      out shouldBe Vector(42L)
    }

    "halt cleanly when all men stop and pipes drain" in {
      val (out, err) = simulate(
        """
          #+------+
          #|@ 3 H |
          #+------+""",
        input = "")
      out shouldBe empty
      err shouldBe None
    }

    "stop at the step cap without error (normal exit)" in {
      // An infinite loop: the man enters a 4-cell ring of direction-setters
      // and never halts. `@` is only visited once (it is off the loop path).
      //   @ v
      //   > v
      //   ^ <
      val (out, err) = simulate(
        """
          #+--+
          #|@v|
          #|>v|
          #|^<|
          #+--+""",
        input = "",
        stepCap = 100L)
      err shouldBe None // step cap is normal termination, not an error
    }

    "report a fatal error and flush partial output" in {
      val (out, err) = simulate(
        """
          #+---+
          #|@Z |
          #+---+""",
        input = "")
      err shouldBe defined
      err.get.code shouldBe "bad-op"
    }
  }
}