package com.wolfskeep.littleman

import com.wolfskeep.littleman.io.PrintStreamSink
import com.wolfskeep.littleman.parse.{Loader, WorldFactory}
import com.wolfskeep.littleman.runtime.{RunError, TickExecutor}

/** CLI entry point for the littleman interpreter.
  *
  * Usage: `Main <program.man> [step-cap]`
  *
  * Reads the program file, validates it, resolves pipe targets, then runs
  * it. Input is read from stdin as whitespace-separated integers (negatives
  * allowed); output is written to stdout as space-separated integers. On a
  * load error, prints `load-error` (with a detail line on stderr) and exits
  * non-zero. On a runtime error, prints the emitted values so far on one
  * line, then the error code on a new line, and exits non-zero. On a clean
  * stop (halt or step cap), prints the emitted values and exits 0. */
object Main {

  val DefaultStepCap: Long = 10000000L

  def main(args: Array[String]): Unit = {
    if (args.length < 1) {
      System.err.println("Usage: Main <program.man> [step-cap]")
      sys.exit(1)
    }
    val filename = args(0)
    val stepCap = if (args.length >= 2) args(1).toLongOption.getOrElse(DefaultStepCap) else DefaultStepCap

    val exitCode = run(filename, stepCap, System.in, System.out)
    sys.exit(exitCode)
  }

  /** Run the program; return the process exit code (0 = clean, 1 = error). */
  def run(filename: String, stepCap: Long, in: java.io.InputStream, out: java.io.PrintStream): Int = {
    val source = try {
      val s = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(filename)), "UTF-8")
      s
    } catch {
      case _: java.io.IOException =>
        System.err.println(s"could not read file: $filename")
        out.println("load-error")
        return 1
    }

    val programText = com.wolfskeep.littleman.model.ProgramText(source)
    Loader.load(programText) match {
      case Left(err) =>
        System.err.println(s"load-error: ${err.message}")
        out.println("load-error")
        1
      case Right(loaded) =>
        val resolved = com.wolfskeep.littleman.resolve.Resolver.resolve(loaded)
        // read all stdin tokens up front
        val inputTokens = readInput(in)
        val world = new WorldFactory(stepCap).build(resolved, inputTokens)
        val sink = new PrintStreamSink(out)
        val sim = new Simulator(stepCap, new TickExecutor, sink)
        sim.run(world) match {
          case Left(err) =>
            // print a newline then the error code on its own line
            out.println()
            out.println(errorCode(err))
            out.flush()
            1
          case Right(()) =>
            out.println()
            out.flush()
            0
        }
    }
  }

  /** Read whitespace-separated integers from stdin (negatives allowed). */
  private def readInput(in: java.io.InputStream): Vector[Long] = {
    val sb = new StringBuilder
    val tokens = scala.collection.mutable.ArrayBuffer.empty[Long]
    var c: Int = in.read()
    while (c != -1) {
      val ch = c.toChar
      if (ch.isWhitespace) {
        if (sb.nonEmpty) {
          parseToken(sb.toString).foreach(tokens += _)
          sb.clear()
        }
      } else {
        sb.append(ch)
      }
      c = in.read()
    }
    if (sb.nonEmpty) parseToken(sb.toString).foreach(tokens += _)
    tokens.toVector
  }

  private def parseToken(s: String): Option[Long] =
    try Some(java.lang.Long.parseLong(s.trim)) catch { case _: NumberFormatException => None }

  private def errorCode(e: RunError): String = e.code
}