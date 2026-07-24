package com.wolfskeep.littleman

import com.wolfskeep.littleman.io.OutputSink
import com.wolfskeep.littleman.runtime.{RunError, TickExecutor, World}

/** Drives the tick loop of a [[World]] until termination: the world is no
  * longer progressible, an error occurs, or the step cap is reached. Drains
  * the world's output buffer to the supplied [[OutputSink]] after each tick.
  *
  * @param stepCap  the maximum number of ticks; hitting it ends the run
  *                 normally (output flushed, exit 0)
  * @param executor the [[TickExecutor]] performing each tick
  */
final class Simulator(stepCap: Long, executor: TickExecutor, sink: OutputSink) {

  /** Run to completion. Returns `Right(())` on a clean stop (halt or step
    * cap) or `Left(RunError)` on a fatal error. The output produced so far
    * has already been flushed to the sink by the time this returns. */
  def run(world: World): Either[RunError, Unit] = {
    var tick: Long = 0L
    drain(world)
    while (tick < stepCap && executor.progressible(world)) {
      executor.step(world) match {
        case Left(e) =>
          drain(world)
          return Left(e)
        case Right(_) =>
          drain(world)
          tick += 1
      }
    }
    Right(())
  }

  /** Flush any values accumulated in `world.output` this tick to the sink. */
  private def drain(world: World): Unit = {
    while (world.output.nonEmpty) sink.emit(world.output.remove(0))
  }
}