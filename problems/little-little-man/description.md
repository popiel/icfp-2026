Interpret an LLM program and show its state on a display.

## The LLM Language

Little little man (LLM) is a simple subset of the littleman language that
you have learned during this course. *All valid LLM programs are valid littleman
programs*. This problem will not recap the basics of littleman programs;
consult the [textbook](/textbook), [language
reference](/language-reference), and [editor](/editor) if you're confused.

An LLM program is a grid containing one or more rooms potentially connected
with pipes. Each room holds a single `@` representing a little man. The `@`
moves with the man: the cell where he started is ordinary empty space, and
walking back over it does nothing. Rooms and pipes are drawn exactly as in
littleman.

The full set of operations in the LLM language is:
- `^` `>` `v` `<` — set heading to N / E / S / W
- `0`–`9` — `A = n`
- `M` — `B = A`
- `+` — `A = A + B`
- `-` — `A = A - B`
- `X` — turn clockwise if `A > 0`, counterclockwise if `A < 0`, don't turn if `A = 0`
- `s` — send `A` into the nearest outgoing pipe
- `r` — receive a value into `A` from the nearest incoming pipe
- `H` — halt: the man stays on the `H` forever while the other men keep running

## Ticks and halting

On each tick every man executes the operation he is standing on (if applicable)
and *then* advances in his current direction. All men act on every tick.

The program halts when every man has halted on an `H` — or the moment any man
hits a wall: the whole program stops and every man freezes where he stands,
including the man on the wall cell. The tick in which a man steps onto a wall
completes in full — every other man still executes and moves on that tick —
and then everything freezes. (This differs from littleman, where hitting a
wall is an error.)

**The programs you receive will be well-formed:** rooms and pipes parse, every
room has a single `@` inside, and `s` and `r` are only ever executed in a room
that has a pipe in the required direction. Other characters will either be
spaces or valid operations.

## Pipes

Each pipe cell holds at most one value. On every tick, before the men act,
every value in a pipe advances one cell toward its destination if the next
cell is free.

**Every value sent into a pipe is between `-9` and `9`.**

- `s` writes `A` into the pipe's first cell (the arrowhead leaving the
  sender's room). If that cell is occupied, the man **blocks**: he stays on
  the `s`, retrying every tick, and only moves on once the send succeeds.
- `r` takes the value in the pipe's last cell (the arrowhead entering the
  receiver's room). If no value has arrived yet, the man blocks on the `r`
  the same way.
- When a room has more than one pipe in the relevant direction, `s` and `r`
  use the **nearest** pipe: the one whose arrowhead at this room is closest
  to the man's current cell by Manhattan distance. On an exact tie, the
  arrowhead earliest in reading order (top-to-bottom, then left-to-right)
  wins.

Because pipes move before the men act, a value that arrives at the pipe's
last cell on some tick can be received by an `r` that same tick — and a man
blocked on `s` against a full pipe sends on the tick *after* the receiver's
pop makes room.

## Drawing

Draw the LLM program with its top-left corner at the top-left corner of your
[display](/grading#displays). Your display will be 16x16; if your LLM program
is smaller than that leave the pixels outside of the program black.

You should use color `9` (bright red) to represent the current position of
every little man.  When a little man is on top of an instruction or a wall,
draw *him*, not the thing he is on top of.

**Values in pipes are animated.** A pipe cell holding a value is drawn `14`
(bright cyan); an empty pipe cell is drawn `6` (cyan). Your frames must show
every value at the exact cell it occupies, step by step, as it moves toward
its destination.

Other cells have fixed colors similar to what you see in the editor:
- room walls — 4 (blue)
- `<` `>` `^` `v` `X` `H` — 3 (yellow)
- `0`–`9` — 8 (gray)
- `M` — 12 (bright blue)
- `+` `-` — 10 (bright green)
- `s` `r` — 13 (bright magenta)
- pipe cells (bodies and arrowheads) — 6 (cyan)
- a pipe cell currently holding a value — 14 (bright cyan)
- space — 0 (black)

## Input and output

The first [round](/grading#round-based) supplies two integers `W H`
and then `W*H` [ASCII](/grading#ascii) values that comprise
a valid LLM program, in row-major order (top row first, left to right).
Commit a single frame showing the starting state.

Subsequent rounds supply one integer `k`. Step the program forward `k` ticks
or until it halts, whichever comes first.  Then commit a single frame showing
the state of the program.

Test cases end after the round where the LLM program halts.