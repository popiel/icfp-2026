# Littleman Interpreter — Design Plan

A design for a Scala interpreter of the Littleman (`.man`) 2D ASCII language
(see `docs/littleman.md` for the language spec). The interpreter loads a
program, validates its syntax (rooms, pipes, numeric literals), resolves each
pipe instruction to the pipe(s) it reads/writes, then runs it: stdin is a
whitespace-separated series of integers (negatives allowed); stdout is
space-separated numbers, or an error code.

## 0. Locked decisions

- **Tooling:** sbt + Scala 2.13.17 + ScalaTest, mirroring the sibling
  `icfp-2006-ai/` repo (flat package, `fork := true`, no plugins, no
  scalafmt, `project/build.properties` pinned to sbt 1.10.11).
- **Package:** `com.wolfskeep.littleman` (flat).
- **Step cap:** 10,000,000 default; overridable via the 2nd CLI arg.
- **Errors:** print emitted numbers (space-separated) on one line, then the
  error code on a new line; exit non-zero.
- **Display:** maintain `current`/`next` buffers + cursor in memory only;
  never emit.

## 1. Package & layout

Flat package `com.wolfskeep.littleman`, mirroring the sibling's flat
`com.wolfskeep`. Source/test layouts:

```
build.sbt
project/build.properties            # sbt.version=1.10.11
run.sh                             # classpath-export launcher (like sibling)
src/main/scala/com/wolfskeep/littleman/
  Main.scala                       # CLI entry: object Main { def main(args) }
  model/
    Point.scala                    # (x:Int,y:Int) value type + delta ops
    Direction.scala                # sealed: East/West/North/South; delta, turn{Left,Right,Around}
    Glyph.scala                    # classification of a char (Wall, IO, DisplayWall, PipeArrow, PipeBody, Instruction, Digit, Backtick, Space, Other)
    ProgramText.scala              # immutable: Vector[String] padded to max width; charAt
  parse/
    LoadedProgram.scala            # the validated, resolved, immutable result
    Loader.scala                   # ProgramText => Either[ParseError, LoadedProgram] (orchestrates below)
    RoomScanner.scala              # find rooms & displays; mark owned cells
    PipeParser.scala               # trace pipes from arrowheads; validate
    LiteralParser.scala            # pair backticks per axis; validate digit-only interiors; precompute per-axis digit sequences
    Validator.scala                # cross checks (overlap/nest, single @ per room, ≤1 input/output, I/O pipe direction, display-pipe sides, literal 64-bit fit)
  resolve/
    Resolver.scala                 # PipeTargets map: cell -> ResolvedTargets
    PipeTargeting.scala            # Manhattan distance + reading-order; nearest / all-outgoing / all-incoming
  runtime/
    World.scala                    # immutable snapshot: men, pipe cell values, display state, input queue, output buffer, tick count
    LittleMan.scala                # id, pos, dir, A, B, BP, halted
    PipeState.scala                # values: Vector[Option[Long]] indexed along the pipe
    DisplayState.scala             # current: Vector[Long], next: Vector[Long], cursor: Point, width, height
    Instruction.scala              # execute(char, man, world, ctx): Effect — pure-ish; returns effect or block
    TickExecutor.scala             # step(world): Either[Error, World] — the 4-phase tick (pure; output retained in world.output)
    Simulator.scala                # drives ticks until !progressible / error / step cap; drains world.output each tick
    Error.scala                     # sealed trait: Wall | BadOp | NoPipe | DisplayAddr | DisplayData | DisplaySwap
  io/
    InputSource.scala              # reads stdin tokens lazily; returns Option[Long]
    OutputSink.scala               # writes to PrintStream
src/test/scala/com/wolfskeep/littleman/
  ... one Spec per module, written FIRST (TDD per AGENTS.md)
```

DI: `Simulator` is constructed with a `TickExecutor`, `InputSource`,
`OutputSink`, and a `StepCap`; `Main` wires concrete instances. No singletons;
`Loader`/`Resolver`/`TickExecutor` are instantiable classes. Static model
types are immutable case classes.

## 2. Pipeline

`Main` → read file → `Loader.load(text): Either[ParseError, LoadedProgram]`
→ `Resolver.resolve(loaded): LoadedProgram` (annotated with `PipeTargets`) →
build initial `World` (spawns, empty pipes, zeroed hands, input queue from
stdin, empty output) → `Simulator.run(world)` → drain output, handle
termination/error.

Parse errors (malformed rooms/pipes/literals, overlapping rooms, >1 `@`, >1
input/output, bad I/O pipe direction, mismatched backtick, literal exceeds
64 bits in either direction) are caught at load and reported as `load-error`
(detail to stderr) with non-zero exit.

## 3. The static model (immutable)

- **`Point`**, **`Direction`** with `delta`, `turnLeft/Right/around`, `axis`
  (Horizontal/Vertical), `opposite`.
- **`Room`**: id, topLeft, size, the set of border cells and interior cells,
  kind (`Normal | Input | Output | Display`). Interior cells of I/O rooms
  are `I`/`O`; display interior is empty (drawable area).
- **`Pipe`**: id, ordered `Vector[Point]` (source→dest), length, sourceRoomId,
  destEndpoint (roomId or `Output` or `Display`), `destSide` of dest room
  (for `U`), `isOutputPipe`, `isDisplayPipe`, `displaySide` (Top/Left/Bottom →
  ADDR/DATA/SWAP).
- **`Spawn`**: roomId, position, ordered by reading order for deterministic
  execution.
- **`LiteralSegment`**: per backtick cell, for each axis it pairs on: the
  paired backtick cell, the ordered digit cells, and a flag for empty.
  Encoded so `Instruction` can decide open vs close at runtime from the man's
  direction.

## 4. Parsing & validation (well-formedness)

**Rooms:** scan for `+` corners; for each, verify a closed rectangle (`+`
corners, `-` top/bottom, `|` sides for normal/IO; display uses `:` sides, `=`
top/bottom). Mark every border+interior cell as owned. Reject
overlapping/nesting rooms. Detect IO rooms by interior `I`/`O` (must be
exactly 3×3 counting walls). Detect displays by `:`/`=` walls. Each room may
contain at most one `@`; spawns collected.

**Pipes:** pipe-glyph cells (`> < ^ v - |`) NOT owned by any room/display.
Trace each pipe from a start arrowhead (backward cell on a source room border,
arrow pointing away) through body glyphs (direction must match the glyph) and
bends (arrowheads pointing in the new direction) until a terminal arrowhead
whose forward cell is a destination border. Validate: length ≥ 2, both ends
are arrowheads, no backward arrowheads, body glyphs match direction, every
pipe cell used by exactly one pipe. Attach to source room and dest endpoint;
record the side for IO/display.

**Literals:** for each backtick, find the matching partner on its row
(left→right pairing, 1st↔2nd, 3rd↔4th…) and independently on its column
(top→bottom). A backtick that pairs on neither axis is a load error. Between
partners, every cell must be a digit or space (else load error); collect
ordered digit cells for each axis. The numeric value must fit in 64 bits when
read in **both** directions along every axis it pairs on, or load error.
Record `LiteralSegment`s for runtime.

**Validation cross-checks:** ≤1 input room and ≤1 output room; input room has
exactly one pipe flowing **out**, output room exactly one flowing **in**; a
pipe in the wrong direction or a second I/O pipe is a load error (a pipeless
I/O room is legal). A display may have at most one pipe per side, none on the
right side, none at a corner.

## 5. Resolution (pipe instruction → pipes)

Per pipe-instruction cell, precompute `ResolvedTargets` (stored in a
`Map[Point, ResolvedTargets]`):

- `s` → nearest **outgoing** pipe (Manhattan distance from the cell to the
  source segment cell; ties by reading order). `None` ⇒ `no-pipe` at runtime.
- `r`, `q` → nearest **incoming** pipe (dest segment). `None` ⇒ `no-pipe`.
- `S` → all outgoing pipes in reading order. Empty ⇒ `no-pipe`.
- `R`, `U` → all incoming pipes in reading order (selection deferred to
  runtime among ready ones). Empty ⇒ `no-pipe`.

Distance is to the *attached segment* (source segment for outgoing, dest
segment for incoming). "Nearest" = nearest, **not** nearest-that-can-proceed.

## 6. Runtime model

`World` is an immutable case class snapshot. Each
`TickExecutor.step(world)` returns `Either[Error, World]` (a new snapshot);
`Simulator` loops, draining `world.output` to the `OutputSink` each tick.
(Performance note: copying is O(size) per tick; for contest-sized programs
and ≤10M ticks this is acceptable. If profiling later shows a bottleneck, the
hot loop can be migrated to a private mutable `Runtime` without changing the
public immutable interfaces — flagged as a follow-up, not now.)

`World` holds: `men: Vector[LittleMan]`, `pipes: Vector[PipeState]`,
`display: DisplayState`, `input: Queue[Long]` (all stdin tokens read up front
— see §10 limitation), `output: Vector[Long]` (drained each tick),
`tick: Long`, plus a reference to the static `LoadedProgram` (instructions,
targets, literals).

`LittleMan`: `id, pos, dir, a: Long, b: Long, bp: Long, halted: Boolean`.
`blocked` is **derived** per tick (a man is blocked iff his instruction is a
send/receive that cannot complete this tick); it is not stored.

Execution order (deterministic): men are processed in **spawn reading order**
(top-to-bottom, left-to-right by `@` position). Displays consume before men
execute (see §7).

## 7. Tick execution (the 4 phases, per spec §9)

1. **Pipes shift.** For each pipe, for `i` from last-1 down to 0: if
   `cell(i)` holds a value and `cell(i+1)` is empty, move it. (Process
   dest-ward so no value moves twice.)
2. **I/O.** If the output pipe's dest cell holds a value, emit it (append to
   `world.output`) and clear it. **Then**, if the input pipe's source cell is
   empty and `input` is non-empty **and at least one man is active** (not
   halted, not blocked-at-this-instant — see §10), dequeue the next input
   value into the source cell.
3. **Execution.** First the display consumes: for each of its three pipes in
   order ADDR→DATA→SWAP, if the dest cell holds a value, consume and apply
   (ADDR sets cursor `row*width+col`, bounds-checked; DATA sets pixel at
   cursor to value∈0..15 then advances cursor; SWAP copies next→current; `0`
   clears next + resets cursor, `1` preserves). Display violations are
   `DisplayAddr/DisplayData/DisplaySwap` errors. Then each little man (spawn
   order) executes the instruction under him (see §8). Sends place a value
   into the **source end** of the target pipe if empty (else block); receives
   take the **dest end** if occupied (else block).
4. **Movement.** Compute each non-blocked, non-halted man's intended next
   cell. **Simultaneous** resolution: if two men target the same cell, or
   swap cells, both **halt** (stop) and do not move. If a man's next cell is
   a wall (room/display border) ⇒ `Wall` error (ends program). If the next
   cell's char is not a valid instruction (and not an interior
   digit/backtick/space/`.` etc.) ⇒ `BadOp` error. Otherwise the man occupies
   it (execution of that char happens next tick).

Per spec, the spawn cell `@` executes as a no-op. `H` halts; touching another
man halts both.

## 8. Instruction execution (§8)

Pure function `execute(char, man, world): Effect` where `Effect` is one of:
`UpdateA/B/BP`, `SetDir`, `Halt`, `Send(pipeId)`, `Receive(pipeId)`,
`ReceiveAny`, `SendAll`, `Turn{..., awayFrom: Side}`, `Block`, `NoOp`,
`Error(code)`. The `TickExecutor` applies effects to build the next `World`.
Key cases:

- Digits `0–9`: `A = digit` (always, including digits inside literals — the
  closing backtick later overwrites).
- Backtick: using the man's current direction's axis and the precomputed
  `LiteralSegment`: if this backtick pairs on that axis **and** the cell
  immediately behind (opposite direction) lies within the literal range (i.e.
  the man is exiting the literal, having traversed it) ⇒ it is the **closing**
  delimiter; compute the value from the digit cells in traversal order and set
  `A` (empty literal ⇒ NoOp). Otherwise (opening delimiter, or pairs only on
  the perpendicular axis) ⇒ NoOp.
- Hands `M`/`W`; arithmetic `+ - * % / N`; bitwise `& | ~ { }`; direction
  `> < ^ v V X` (X turns by sign(A)); control `.`/space/`H`; backpack
  `b m d a q ] x` (per §4 of the spec). `q` sets `BP` = count of non-empty
  cells in the nearest incoming pipe (never blocks; `no-pipe` if none). `R`/
  `U` pick the first ready incoming pipe (reading order); `U` also turns the
  man to face away from the side the pipe attached to. `s`/`r` use the
  precomputed single nearest pipe. `S` writes to all outgoing pipes, blocking
  unless **all** source cells are free (never partial). Pipe ops with no
  resolved pipe ⇒ `NoPipe` error.

## 9. Termination

`progressible(world) = men.exists(!halted && !blocked) ||
pipes.exists(_.hasMovableValue)` where `hasMovableValue` = a value can shift
next tick (interior value with free cell ahead) **or** sits at an
auto-consuming dest (output pipe ⇒ emit; display pipe ⇒ consumed). A value
parked at a normal room's dest end is **not** movable on its own (needs a
receiver), so an all-inactive world with such stuck pipes is quiescent.

`Simulator` loop: while `tick < stepCap && progressible(world)` ⇒ `step`.
Stops on: error (print code + exit non-zero), step cap (treat as normal end —
flush output, exit 0), or `!progressible` (normal end). Output already
drained each tick is already on stdout; on error, print the code on a new
line and exit non-zero.

This realizes the exit condition (all men stopped-or-blocked **and** all
pipes empty-or-blocked) and the spec's "keep draining the output pipe after
everyone halts" rule.

## 10. Confirmed interpretations

1. **Movement collisions are simultaneous.** Two men targeting the same
   cell, or swapping cells, both **halt** (stop), they don't move.
2. **Intra-tick execution order = spawn reading order**, and the **display
   consumes before men execute** in phase 3. (Order is only observable in
   rare races; the editor would be authoritative.)
3. **Input is read from stdin up front** into an immutable `Queue[Long]`.
   This means local interactively-staged/withheld input isn't supported —
   **input is only fed while ≥1 man is active**, which mirrors the judge's
   "withheld until produced" semantics well enough for local testing. If
   streaming stdin is needed, swap `InputSource` to a lazy token iterator
   (keeps everything else pure).
4. **Parse/load failures** report the code `load-error` (with a detail line
   on **stderr**), distinct from runtime codes (`wall`, `bad-op`, `no-pipe`,
   `display-addr`/`-data`/`-swap`).
5. **Step cap reached** = normal termination (flush output, exit 0), not an
   error.
6. **`v` and `V`** both mean head south (per spec); **`>` inside a room is a
   direction instruction, outside a room it's a pipe arrowhead** — location
   disambiguates. Body glyphs `-`/`|` are pipe bodies only when outside any
   room/display (inside, they're walls).
7. **Display errors** are program-fatal with codes `display-addr`/
   `display-data`/`display-swap` (spec says these "are an error" without
   naming them).
8. **Package name `com.wolfskeep.littleman`** (flat).

## 11. TDD plan (tests-first, per AGENTS.md)

Order of Spec files, each defining behavior *before* the implementation it
exercises:

1. `PointSpec` / `DirectionSpec` — deltas, turns, axis, opposite.
2. `ProgramTextSpec` — padding, charAt, bounds.
3. `RoomScannerSpec` — well-formed room OK; overlapping/nested rooms
   rejected; IO rooms detected; display detected; `@`-outside-room rejected;
   multiple `@` in a room rejected.
4. `PipeParserSpec` — minimal `>>` OK; length-1 pipe rejected; `>----|`
   (body into wall) rejected; `>--<` rejected; bent pipe `>----^` into a room
   above OK; wrong body glyph rejected; both ends need arrowheads.
5. `LiteralParserSpec` — `` `123` `` horizontal pairs; vertical pairs;
   independent-axis pairing; non-digit between partners rejected; empty ``
   `` is a nop; value must fit 64 bits both directions.
6. `ValidatorSpec` — second input/output room rejected; I/O pipe wrong
   direction rejected; pipeless I/O room legal; display pipe on right side
   rejected; display pipe on corner rejected.
7. `PipeTargetingSpec` / `ResolverSpec` — nearest by Manhattan; reading-order
   tie-break; `s`/`r`/`q` single target; `S` all outgoing; `R`/`U` all
   incoming; `no-pipe` when none.
8. `InstructionSpec` — per-instruction unit tests (hands, arithmetic incl.
   division remainder→B and the `B=0` rules, bitwise incl. negative operands,
   direction/backpack/`x`/`]`/`q`, the backtick open/close rule driven by
   direction, send/receive blocking, `S` partial-write prohibition, `U`
   turn-away).
9. `TickExecutorSpec` — the 4-phase order: shift-before-execute (sent value
   moves next tick; a value can be shifted-and-read same tick), output-then-
   input order in phase 2, display ADDR→DATA→SWAP order, movement collision
   halts both, `wall`/`bad-op` errors.
10. `SimulatorSpec` — full halting, deadlock/quiescence, step cap,
    output-flush-after-halt, end-to-end on
    `problems/1-1-triangle/solution.man` with sample inputs.
11. `MainSpec` / `CliSpec` — arg parsing, step-cap override, error-output
    format (numbers line then code line, non-zero exit), stdin parsing of
    negatives.

Each Spec uses `AnyWordSpec with Matchers` (matching sibling style), BDD
phrasing `"... should { "... in" } }` and minimal JDK I/O
(`ByteArrayInputStream`/`ByteArrayOutputStream`) for the I/O specs.