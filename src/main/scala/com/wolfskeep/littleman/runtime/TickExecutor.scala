package com.wolfskeep.littleman.runtime

import com.wolfskeep.littleman.model._

/** Drives one tick of the four-phase execution model (see docs/littleman.md
  * §9):
  *
  * 1. **Pipes shift** — every value moves one cell toward its destination if
  *    the next cell is free.
  * 2. **I/O** — a value at the end of the output pipe is emitted, then the
  *    next input value enters the input pipe (if able and some man is active).
  * 3. **Execution** — the display consumes its pipe inputs (ADDR, DATA, SWAP),
  *    then each little man executes the instruction under him (spawn order).
  * 4. **Movement** — each non-blocked, non-halted man advances one cell;
  *    simultaneous collisions (same cell or swap) halt both, and a wall or
  *    invalid cell produces a fatal error.
  *
  * Returns `Left(RunError)` for a fatal error or `Right(())` on success.
  * Mutates the [[World]] in place. */
final class TickExecutor {

  /** True if any man is active or any pipe has a movable value. A movable
    * value is one that can shift next tick (interior value with a free cell
    * ahead) or one sitting at an auto-consuming dest (output or display). */
  def progressible(world: World): Boolean = {
    val activeMan = world.men.exists(m => !m.halted && {
      val ch = world.program.text.charAt(m.pos)
      !isBlockedNow(m, ch, world)
    })
    if (activeMan) return true
    // any pipe with a value that can move or be auto-consumed
    world.pipes.exists { ps =>
      val snap = ps.snapshot
      snap.exists(_.isDefined) && (
        // interior value with free cell ahead
        snap.indices.exists { i =>
          i < snap.length - 1 && snap(i).isDefined && snap(i + 1).isEmpty
        } ||
        // value at dest end of an output pipe (will be emitted)
        (ps.pipe.dest == PipeDest.Output && snap.last.isDefined) ||
        // value at dest end of a display pipe (will be consumed)
        (ps.pipe.dest.isInstanceOf[PipeDest.Display] && snap.last.isDefined)
      )
    }
  }

  /** Execute one tick; returns fatal error or success. */
  def step(world: World): Either[RunError, Unit] = {
    // phase 1: pipes shift
    world.pipes.foreach(_.shift())

    // phase 2: I/O
    doIO(world)

    // phase 3: execution (display first, then men in spawn order)
    doDisplay(world) match {
      case Some(e) => return Left(e)
      case None =>
    }
    val menOrder = world.men.toVector.sortBy(m => (m.pos.y, m.pos.x))
    val effects = scala.collection.mutable.ArrayBuffer.empty[(Int, Effect)]
    val blockedMen = scala.collection.mutable.Set.empty[Int]
    for (man <- menOrder if !man.halted) {
      val ch = world.program.text.charAt(man.pos)
      val ctx = newContext(world)
      // determine block status BEFORE executing, using current pipe state
      if (isBlockedNow(man, ch, world)) {
        blockedMen += man.id
        effects += ((man.id, Effect.Block))
      } else {
        val e = Instruction.execute(ch, man, ctx)
        if (e.isFatal) return Left(RunError.fromCode(e.error.get))
        effects += ((man.id, e))
      }
    }
    // apply non-pipe effects first (hands/backpack/direction), then pipe writes
    for ((id, e) <- effects) {
      val man = world.men(id)
      var nm = man
      e.a.foreach(v => nm = nm.copy(a = v))
      e.b.foreach(v => nm = nm.copy(b = v))
      e.bp.foreach(v => nm = nm.copy(bp = v))
      e.dir.foreach(v => nm = nm.withDir(v))
      if (e.halt) nm = nm.halt
      world.men(id) = nm
    }
    for ((id, e) <- effects if e.pipeWrites.nonEmpty) {
      for ((pipeId, value) <- e.pipeWrites) {
        world.pipes(pipeId).putSource(value)
      }
    }
    // perform the takes for receives (remove the value from the dest cell)
    for ((id, e) <- effects if e.receives.nonEmpty) {
      for (pipeId <- e.receives) {
        world.pipes(pipeId).takeDest()
      }
    }

    // phase 4: movement (skip men that blocked or halted this tick)
    doMovement(world, blockedMen.toSet) match {
      case Some(e) => return Left(e)
      case None =>
    }

    world.tick += 1
    Right(())
  }

  /** Is a little man blocked NOW (before executing), based on the instruction
    * under him and the current pipe state? A blocked man does not execute
    * and does not move this tick. */
  private def isBlockedNow(man: LittleMan, ch: Char, world: World): Boolean = ch match {
    case 's' =>
      nearestPipe(man, world, outgoing = true) match {
        case Some(pipe) => !world.pipes(pipe.id).isSourceFree
        case None      => false // no-pipe error will fire at execution
      }
    case 'S' =>
      allPipes(man, world, outgoing = true) match {
        case pipes if pipes.nonEmpty => pipes.exists(p => !world.pipes(p.id).isSourceFree)
        case _ => false
      }
    case 'r' =>
      nearestPipe(man, world, outgoing = false) match {
        case Some(pipe) => !world.pipes(pipe.id).isDestReady
        case None      => false
      }
    case 'R' | 'U' =>
      allPipes(man, world, outgoing = false) match {
        case pipes if pipes.nonEmpty => !pipes.exists(p => world.pipes(p.id).isDestReady)
        case _ => false
      }
    case _ => false
  }

  // ---- execution context ----
  private def newContext(world: World): ExecContext = new ExecContext {
    val program = world.program
    val resolved = world.resolved
    def sourceCellFree(pipeId: Int): Boolean = world.pipes(pipeId).isSourceFree
    def destCellValue(pipeId: Int): Option[Long] = world.pipes(pipeId).destValue
    def pipeDestSide(pipeId: Int): Direction = world.program.pipes.pipes.find(_.id == pipeId).map(_.destSide).getOrElse(Direction.East)
    def pipeValueCount(pipeId: Int): Int = world.pipes(pipeId).count
    def literalsAt(point: Point): Vector[LiteralSegment] =
      world.program.literals.backtickAt.getOrElse(point, Vector.empty)
  }

  // ---- phase 2: I/O ----
  private def doIO(world: World): Unit = {
    // emit any value at the output pipe's dest end
    world.program.outputPipe.foreach { pipe =>
      val ps = world.pipes(pipe.id)
      if (ps.isDestReady) {
        val v = ps.takeDest()
        world.output += v
      }
    }
    // feed the next input value into the input pipe's source if free and a
    // man is active
    world.program.inputPipe.foreach { pipe =>
      val ps = world.pipes(pipe.id)
      val activeMan = world.men.exists(m => !m.halted && {
        val ch = world.program.text.charAt(m.pos)
        !isBlockedNow(m, ch, world)
      })
      if (ps.isSourceFree && world.input.nonEmpty && activeMan) {
        ps.putSource(world.input.removeHead())
      }
    }
  }

  // ---- phase 3: display consumption ----
  private def doDisplay(world: World): Option[RunError] = {
    world.display.flatMap { disp =>
      val dispRoom = world.program.displayRoom.get
      // process ADDR, then DATA, then SWAP
      val bySide: Map[DisplaySide, Int] = Map(
        DisplaySide.Top    -> 0,
        DisplaySide.Left   -> 1,
        DisplaySide.Bottom -> 2
      )
      val dispPipes = world.program.pipes.pipes.flatMap { p =>
        p.dest match {
          case PipeDest.Display(_, side) => Some((bySide(side), p))
          case _ => None
        }
      }.sortBy(_._1).map(_._2)
      for (pipe <- dispPipes) {
        val ps = world.pipes(pipe.id)
        if (ps.isDestReady) {
          val v = ps.takeDest()
          pipe.dest match {
            case PipeDest.Display(_, DisplaySide.Top) =>
              if (v < 0 || v >= disp.width.toLong * disp.height.toLong)
                return Some(RunError.DispAddr)
              disp.setCursor((v % disp.width).toInt, (v / disp.width).toInt)
            case PipeDest.Display(_, DisplaySide.Left) =>
              if (v < 0 || v > 15) return Some(RunError.DispData)
              disp.draw(v)
            case PipeDest.Display(_, DisplaySide.Bottom) =>
              if (v != 0 && v != 1) return Some(RunError.DispSwap)
              disp.swap(v.toInt)
            case _ =>
          }
        }
      }
      None
    }
  }

  // ---- phase 4: movement ----
  private def doMovement(world: World, blockedMen: Set[Int]): Option[RunError] = {
    // compute intended next cell per non-blocked, non-halted man
    val active = world.men.toVector.zipWithIndex.filter { case (m, i) =>
      !m.halted && !blockedMen.contains(i)
    }
    val intents: Vector[(Int, Point)] = active.map { case (m, i) =>
      (i, m.pos.plus(m.dir.delta))
    }
    // detect collisions: two men targeting the same cell, or swapping
    val targetCounts = intents.groupBy(_._2).view.mapValues(_.map(_._1).toVector).toMap
    val swaps = scala.collection.mutable.Set.empty[Int]
    for ((i, pi) <- intents; (j, pj) <- intents if i < j) {
      if (pi == world.men(j).pos && pj == world.men(i).pos) {
        swaps += i; swaps += j
      }
    }
    // apply each intent
    for ((i, target) <- intents) {
      val man = world.men(i)
      // collision: same target cell
      if (targetCounts(target).size > 1 || swaps.contains(i)) {
        world.men(i) = man.halt
      } else {
        // validate target
        val roomOpt = world.program.rooms.roomAt(target)
        roomOpt match {
          case None =>
            return Some(RunError.Wall)  // outside any room
          case Some(room) if room.borderCells.contains(target) =>
            return Some(RunError.Wall)   // a wall cell
          case Some(room) =>
            // the char must be a valid instruction (not, e.g., an interior wall)
            val ch = world.program.text.charAt(target)
            if (!isValidInstruction(ch))
              return Some(RunError.BadOp)
            world.men(i) = man.withPos(target)
        }
      }
    }
    None
  }

  private def isValidInstruction(c: Char): Boolean =
    (c >= '0' && c <= '9') || "+-*/%N&|~{}><^vVX.Hbmdaq]x` sSrRUWM@".contains(c)

  // ---- blocked detection ----
  // (isBlockedNow is defined above, near the step() method)

  private def nearestPipe(man: LittleMan, world: World, outgoing: Boolean): Option[Pipe] =
    world.resolved.targets.get(man.pos).flatMap {
      case ResolvedTargets.Nearest(p) => p
      case _ => None
    }

  private def allPipes(man: LittleMan, world: World, outgoing: Boolean): Vector[Pipe] =
    world.resolved.targets.get(man.pos).map {
      case ResolvedTargets.All(pipes) => pipes
      case _ => Vector.empty
    }.getOrElse(Vector.empty)
}