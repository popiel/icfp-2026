package com.wolfskeep.littleman.runtime

import com.wolfskeep.littleman.model._

/** Executes a single instruction character for a little man, producing an
  * [[Effect]] describing state changes. Pure for everything except the pipe
  * ops, which read the live pipe state via [[ExecContext]].
  *
  * The TickExecutor calls `execute` then applies the effect: writes to pipes,
  * updates hands/backpack/direction, halts, or reports a fatal error / block.
  *
  * Per-instruction semantics follow `docs/littleman.md`. Division/modulo are
  * floored (Python-style); bitwise ops are two's-complement on all 64 bits;
  * arithmetic wraps silently on 64-bit overflow. */
object Instruction {

  def execute(char: Char, man: LittleMan, ctx: ExecContext): Effect = {
    char match {
      // --- constants ---
      case c if c >= '0' && c <= '9' => Effect.setA((c - '0').toLong)

      // --- hands ---
      case 'M' => Effect.hands(man.a, man.a)               // B = A
      case 'W' => Effect.hands(man.b, man.a)               // swap A,B

      // --- arithmetic (wrap on overflow is implicit in Long) ---
      case '+' => Effect.setA(man.a + man.b)
      case '-' => Effect.setA(man.a - man.b)
      case '*' => Effect.setA(man.a * man.b)
      case '%' => flooredMod(man.a, man.b)
      case '/' => flooredDiv(man.a, man.b)
      case 'N' => Effect.setA(-man.a)

      // --- bitwise (two's complement on 64 bits; Long ops already do this) ---
      case '&' => Effect.setA(man.a & man.b)
      case '|' => Effect.setA(man.a | man.b)
      case '~' => Effect.setA(man.a ^ man.b)
      case '{' => shiftLeft(man.a, man.b)
      case '}' => shiftRightArith(man.a, man.b)

      // --- direction ---
      case '>' => Effect.setDir(Direction.East)
      case '<' => Effect.setDir(Direction.West)
      case '^' => Effect.setDir(Direction.North)
      case 'v' | 'V' => Effect.setDir(Direction.South)
      case 'X' => Effect.setDir(turnBySign(man.a, man.dir))

      // --- control flow ---
      case '.' | ' ' | '@' => Effect.NoOp
      case 'H' => Effect.Halt

      // --- backpack ---
      case 'b' => Effect.setBP(man.a)                       // BP = A
      case 'm' => Effect.setBP(man.bp - 1L)                // BP -= 1
      case 'd' => if (man.bp > 0) Effect.setDir(man.dir.turnRight) else Effect.NoOp
      case 'a' => if (man.bp > 0) Effect.setDir(man.dir.turnLeft) else Effect.NoOp
      case 'q' => doQueryCount(man, ctx)
      case ']' => Effect.setBP(man.bp >> 1)                // arithmetic shift right
      case 'x' => Effect.setDir(if ((man.bp & 1L) == 1L) man.dir.turnRight else man.dir.turnLeft)

      // --- backtick literal delimiter ---
      case '`' => doBacktick(man, ctx)

      // --- pipes ---
      case 's' => doSendNearest(man, ctx)
      case 'S' => doSendAll(man, ctx)
      case 'r' => doReceiveNearest(man, ctx, turnAway = false)
      case 'R' => doReceiveAny(man, ctx, turnAway = false)
      case 'U' => doReceiveAny(man, ctx, turnAway = true)

      // --- anything else is a bad-op error ---
      case _ => Effect.error("bad-op")
    }
  }

  // ----- arithmetic helpers -----

  /** Floored modulo: A mod B with B's sign; 0 if B = 0. */
  private def flooredMod(a: Long, b: Long): Effect = {
    if (b == 0L) Effect.setA(0L)
    else {
      val r = a % b
      val fixed = if ((r != 0) && ((r < 0) != (b < 0))) r + b else r
      Effect.setA(fixed)
    }
  }

  /** Floored division: A = floor(A/B); remainder goes to B. If B = 0: A = 0,
    * B keeps the dividend. */
  private def flooredDiv(a: Long, b: Long): Effect = {
    if (b == 0L) Effect.hands(0L, a)
    else {
      val q = Math.floorDiv(a, b)
      val r = a - q * b
      Effect.hands(q, r)
    }
  }

  private def shiftLeft(a: Long, b: Long): Effect =
    if (b < 0 || b > 63) Effect.setA(0L)
    else Effect.setA(a << b)

  private def shiftRightArith(a: Long, b: Long): Effect =
    if (b < 0) Effect.setA(0L)
    else if (b > 63) Effect.setA(a >> 63) // sign-fill: a >> 63 is all-sign-bits
    else Effect.setA(a >> b)

  // ----- direction -----
  private def turnBySign(a: Long, dir: Direction): Direction =
    if (a > 0L) dir.turnRight
    else if (a < 0L) dir.turnLeft
    else dir

  // ----- backpack -----
  private def doQueryCount(man: LittleMan, ctx: ExecContext): Effect =
    ctx.resolved.targets.get(man.pos) match {
      case Some(ResolvedTargets.Nearest(Some(pipe))) =>
        Effect.setBP(ctx.pipeValueCount(pipe.id).toLong)
      case Some(ResolvedTargets.Nearest(None)) => Effect.error("no-pipe")
      case _ => Effect.error("no-pipe")
    }

  // ----- backtick -----
  private def doBacktick(man: LittleMan, ctx: ExecContext): Effect = {
    val segs = ctx.literalsAt(man.pos).filter(_.axis == man.dir.axis)
    segs.headOption match {
      case None => Effect.NoOp // pairs only on the perpendicular axis; no-op
      case Some(seg) =>
        val naturalDir = seg.axis match {
          case Axis.Horizontal =>
            if (seg.openCell.x < seg.closeCell.x) Direction.East else Direction.West
          case Axis.Vertical =>
            if (seg.openCell.y < seg.closeCell.y) Direction.South else Direction.North
        }
        // the man closes at the delimiter he reaches AFTER walking the digits
        val closingCell =
          if (man.dir == naturalDir) seg.closeCell else seg.openCell
        if (man.pos == closingCell) {
          // load value: traversal order is from the delimiter he entered to the
          // closing one. seg.digits are stored open->close; reverse if walked
          // against the natural direction.
          val ordered =
            if (man.dir == naturalDir) seg.digits
            else seg.digits.reverse
          val digitsStr = ordered.map(p => ctx.program.text.charAt(p)).mkString
          if (digitsStr.isEmpty) Effect.NoOp // empty literal is a no-op
          else {
            try {
              Effect.setA(java.lang.Long.parseLong(digitsStr))
            } catch {
              case _: NumberFormatException => Effect.error("bad-literal")
            }
          }
        } else {
          // opening delimiter: man keeps walking into the literal
          Effect.NoOp
        }
    }
  }

  // ----- pipes: send -----
  private def doSendNearest(man: LittleMan, ctx: ExecContext): Effect =
    ctx.resolved.targets.get(man.pos) match {
      case Some(ResolvedTargets.Nearest(Some(pipe))) =>
        if (ctx.sourceCellFree(pipe.id)) Effect.send(pipe.id, man.a)
        else Effect.Block
      case Some(ResolvedTargets.Nearest(None)) => Effect.error("no-pipe")
      case _ => Effect.error("no-pipe")
    }

  private def doSendAll(man: LittleMan, ctx: ExecContext): Effect =
    ctx.resolved.targets.get(man.pos) match {
      case Some(ResolvedTargets.All(pipes)) if pipes.nonEmpty =>
        // block unless ALL source cells are free; never write to just some
        if (pipes.forall(p => ctx.sourceCellFree(p.id)))
          Effect(pipeWrites = pipes.map(p => (p.id, man.a)))
        else Effect.Block
      case _ => Effect.error("no-pipe")
    }

  // ----- pipes: receive -----
  private def doReceiveNearest(man: LittleMan, ctx: ExecContext, turnAway: Boolean): Effect =
    ctx.resolved.targets.get(man.pos) match {
      case Some(ResolvedTargets.Nearest(Some(pipe))) =>
        ctx.destCellValue(pipe.id) match {
          case Some(v) => finishReceive(pipe.id, v, pipe.destSide, turnAway, man.dir)
          case None => Effect.Block
        }
      case Some(ResolvedTargets.Nearest(None)) => Effect.error("no-pipe")
      case _ => Effect.error("no-pipe")
    }

  private def doReceiveAny(man: LittleMan, ctx: ExecContext, turnAway: Boolean): Effect =
    ctx.resolved.targets.get(man.pos) match {
      case Some(ResolvedTargets.All(pipes)) if pipes.nonEmpty =>
        // pick the first ready pipe in reading order
        pipes.find(p => ctx.destCellValue(p.id).isDefined) match {
          case Some(pipe) =>
            finishReceive(pipe.id, ctx.destCellValue(pipe.id).get, pipe.destSide, turnAway, man.dir)
          case None => Effect.Block
        }
      case _ => Effect.error("no-pipe")
    }

  private def finishReceive(pipeId: Int, value: Long, side: Direction, turnAway: Boolean, curDir: Direction): Effect = {
    val dirEffect = if (turnAway) Some(side.opposite) else None
    Effect.receive(pipeId, value, dirEffect)
  }

  /** Turn the man to face away from the room `side` he read from. The side
    * is the direction the pipe ENTERS the room (from the room's perspective);
    * "turn away" means face the opposite of that side. */
  private def turnAwayFromSide(curDir: Direction, side: Direction): Direction =
    side.opposite
}