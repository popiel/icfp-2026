Interpret an LLLM program and show its state on a display.

## The LLLM Language

Little little littleman (LLLM) is a simple subset of the
[little littleman (LLM)](/problems/little-little-man) language, which is a
simple subset of the language that you have learned during the course. *All
valid LLLM programs are valid LLM programs, which are valid littleman
programs*. This problem will not recap the basics of littleman programs;
consult the [textbook](/textbook), [language
reference](/language-reference), and [editor](/editor) if you're confused.

LLLM programs run in a single room and have a single `@` designating the little
man's starting position. The `@` moves with the man: the cell where he started
is ordinary empty space, and walking back over it does nothing.

The full set of operations in the LLLM language is:
- `^` `>` `v` `<` — set heading to N / E / S / W
- `0`–`9` — `A = n`
- `M` — `B = A`
- `+` — `A = A + B`
- `-` — `A = A - B`
- `X` — turn clockwise if `A > 0`, counterclockwise if `A < 0`, don't turn if `A = 0`
- `H` — halt: the man stays on the `H` forever

On each tick the little man executes the operation he is standing on (if applicable)
and *then* advances in his current direction. The little man halts if he hits a wall:
he stays put on the wall cell forever, and frames committed after that show him drawn
on the wall cell. (This differs from littleman, where hitting a wall is an error.)

**The programs you receive will be well-formed:** every program will have a single
room with a single `@` inside. Other characters will either be spaces or valid operations.

## Drawing

Draw the LLLM program with its top-left corner at the top-left corner of your
[display](/grading#displays). Your display will be 16x16; if your LLLM program is smaller than that
leave the pixels outside of the program black.

You should use color `9` (bright red) to represent the current position of the
little man.  When the little man is on top of an instruction or a wall, draw
*him*, not the thing he is on top of.

Other cells have fixed colors similar to what you see in the editor:
- room walls — 4 (blue)
- `<` `>` `^` `v` `X` `H` — 3 (yellow)
- `0`–`9` — 8 (gray)
- `M` — 12 (bright blue)
- `+` `-` — 10 (bright green)
- space — 0 (black)

## Input and output

The first [round](/grading#round-based) supplies two integers `W H`
and then `W*H` [ASCII](/grading#ascii) values that comprise
a valid LLLM program, in row-major order (top row first, left to right).
Commit a single frame showing the starting state.

Subsequent rounds supply one integer `k`. Step the program forward `k` ticks
or until it halts, whichever comes first.  Then commit a single frame showing
the state of the program.

Test cases end after the round where the LLLM program halts.