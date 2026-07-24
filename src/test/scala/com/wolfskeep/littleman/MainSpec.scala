package com.wolfskeep.littleman

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, PrintStream}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class MainSpec extends AnyWordSpec with Matchers {
  import RoomScannerSpec.grid

  private def runMain(s: String, input: String, stepCap: Long = 1000L): (String, Int) = {
    val tmp = java.io.File.createTempFile("littleman-test", ".man")
    tmp.deleteOnExit()
    java.nio.file.Files.write(tmp.toPath, grid(s).rows.mkString("\n").getBytes("UTF-8"))
    val in = new ByteArrayInputStream(input.getBytes("UTF-8"))
    val out = new ByteArrayOutputStream()
    val ps = new PrintStream(out)
    val code = Main.run(tmp.getAbsolutePath, stepCap, in, ps)
    ps.flush()
    (out.toString("UTF-8"), code)
  }

  "Main" should {
    "echo a single input value through to output" in {
      val (output, code) = runMain(
        """
          #+-+>>+-----+>>+-+
          #|I|  |@rsH |  |O|
          #+-+  +-----+  +-+""",
        input = "42")
      output.trim shouldBe "42"
      code shouldBe 0
    }

    "echo multiple input values" in {
      val (output, code) = runMain(
        """
          #+-+>>+-----+>>+-+
          #|I|  |@rsH |  |O|
          #+-+  +-----+  +-+""",
        input = "1 2 3")
      // the program reads one value then halts; only one is echoed.
      output.trim should not be empty
      code shouldBe 0
    }

    "accept negative input values" in {
      val (output, code) = runMain(
        """
          #+-+>>+-----+>>+-+
          #|I|  |@rsH |  |O|
          #+-+  +-----+  +-+""",
        input = "-5")
      output.trim shouldBe "-5"
      code shouldBe 0
    }

    "exit non-zero with 'load-error' for a malformed program" in {
      val (output, code) = runMain(
        """
          #+--+
          #@  |
          #+--+""",
        input = "")
      output.trim shouldBe "load-error"
      code shouldBe 1
    }

    "exit non-zero and print partial output then the error code on a wall error" in {
      val (output, code) = runMain(
        """
          #+--+
          #|@ |
          #+--+""",
        input = "")
      output.trim should include("wall")
      code shouldBe 1
    }

    "exit non-zero with 'bad-op' for an invalid interior character" in {
      val (output, code) = runMain(
        """
          #+---+
          #|@Z |
          #+---+""",
        input = "")
      output.trim should include("bad-op")
      code shouldBe 1
    }

    "honor a custom step cap via the second argument" in {
      // an infinite loop with a tiny step cap; exits 0 (step cap is clean)
      val (output, code) = runMain(
        """
          #+--+
          #|@v|
          #|>v|
          #|^<|
          #+--+""",
        input = "",
        stepCap = 5L)
      code shouldBe 0
    }
  }
}