package com.wolfskeep.littleman.runtime

import com.wolfskeep.littleman.model._
import scala.collection.mutable

/** The mutable runtime state of a loaded littleman program.
  *
  * Holds the little men, the per-pipe [[PipeState]]s, the display state, the
  * input queue, the tick counter, and the output buffer emitted this tick (to
  * be drained by the [[Simulator]]).
  *
  * `World` is intentionally mutable: snapshot-copying on every tick scales
  * poorly up to the 10M-tick step cap. Methods like `shift`/`execute` mutate
  * the world in place; the [[TickExecutor]] drives them in the spec's 4-phase
  * order. The simulator owns exactly one World per run. */
final class World(
  val program: LoadedProgram,
  val resolved: ResolvedProgram,
  val men: mutable.ArrayBuffer[LittleMan],
  val pipes: mutable.ArrayBuffer[PipeState],
  val display: Option[DisplayState],
  var input: mutable.ArrayDeque[Long],
  var output: mutable.ArrayBuffer[Long],
  var tick: Long = 0L
) {
  def copy: World = {
    val menCopy = men.map(identity)
    val pipesCopy = pipes.map { ps =>
      val nps = new PipeState(ps.pipe)
      val snap = ps.snapshot
      for (i <- snap.indices) nps.setSlot(i, snap(i))
      nps
    }
    val dispCopy = display.map { d =>
      val nd = new DisplayState(d.width, d.height)
      nd.copyFrom(d)
      nd
    }
    new World(
      program, resolved,
      menCopy, pipesCopy, dispCopy,
      input.clone(), output.clone(),
      tick
    )
  }
}