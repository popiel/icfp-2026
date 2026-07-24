package com.wolfskeep.littleman.model

/** The pipes that a single pipe-instruction cell resolves to.
  *
  * - `s` / `r` / `q` use [[Nearest]]: a single pipe, chosen by Manhattan
  *   distance to the cell, ties broken by reading order. `None` if there is
  *   no such pipe (a runtime `no-pipe` error).
  * - `S` uses [[All]]: every outgoing pipe in reading order; writes to all,
  *   blocks unless all are free.
  * - `R` / `U` use [[All]]: every incoming pipe in reading order; the runtime
  *   picks the first with a value ready.
  */
sealed trait ResolvedTargets
object ResolvedTargets {
  /** A single resolved pipe (nearest), or None if no pipe on the needed side. */
  final case class Nearest(pipe: Option[Pipe]) extends ResolvedTargets
  /** All pipes on the needed side, in reading order. Empty => no-pipe. */
  final case class All(pipes: Vector[Pipe]) extends ResolvedTargets
  /** This cell is not a pipe instruction. */
  case object NotPipe extends ResolvedTargets
}

/** A loaded program annotated with per-cell pipe targets. */
final case class ResolvedProgram(
  program: LoadedProgram,
  targets: Map[Point, ResolvedTargets]
)