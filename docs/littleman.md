# Littleman Language Specification

A concise specification for the **Littleman** (`.man`) language — a 2D, grid-based, ASCII programming language in which one or more "little men" walk over a grid of characters, executing the instruction under them each tick.

Sources: the [textbook](https://icfpcontest2026.com/textbook) and the [language reference](https://icfpcontest2026.com/language-reference).

## 1. The machine

- A **program** is a grid of ASCII characters walked by one or more **little men**.
- Time advances in discrete **ticks**. On each tick, every little man (in order):
  1. **Executes** the instruction under him; then
  2. **Advances** one cell in his current **direction** — unless the instruction **blocks** or **halts** him.
- A little man carries three signed 64-bit integers, all starting at 0:
  - **`A`** — main hand
  - **`B`** — off hand
  - **`BP`** — backpack (cannot be read directly; only turned on)
- Arithmetic is 64-bit signed and **wraps silently** on overflow.

## 2. Rooms & spawning

- Little men live in **rooms**: rectangles drawn with `+` at the corners, `-` along the top/bottom walls, and `|` along the left/right walls.
- A little man may never be placed outside a room and may never leave his room. Stepping on a wall is an **error** (ends the whole program).
- A little man **spawns** at every `@` inside a room. He always begins facing **right** (east).
- A program may contain many rooms, each with at most one `@`. Rooms may not overlap or nest. All little men move in **lockstep** (one tick applies to all simultaneously).

## 3. Program termination

A **run** ends in exactly one of three ways:

| Ending | Trigger |
| --- | --- |
| **Halting** | Every little man has stopped (via `H` or by touching another little man). |
| **Error** | Any little man hits a wall, an invalid instruction, or a pipe op with no pipe. Ends the whole program immediately. |
| **Step cap** | The maximum number of ticks is reached. Ends the program immediately. |

- A little man **stops** on `H`, or when he touches another little man (both stop). Other little men keep running.
- **Judging**: you pass a test the moment you emit the correct output (halting is not required). You fail the moment you emit wrong output, or the run ends before emitting the correct output.

## 4. Instruction set

Stepping on any character **not** listed below is an error (`bad-op`).

### Little men
| Char | Effect |
| --- | --- |
| `@` | Where a little man begins. Only one per room. |

### Constants
| Char | Effect |
| --- | --- |
| `0`–`9` | `A` = the digit's value. |
| `` ` `` `` ` `` | Numeric literal: digits between a matched pair of backticks load into `A` when the little man steps onto the **closing** backtick. Spaces are allowed and ignored. `` `123` `` walked left→right is 123; right→left is 321. Vertical literals work the same. Anything but a digit or space between a matched pair is a load error. |

### Hands
| Char | Effect |
| --- | --- |
| `M` | `B = A` (A unchanged). |
| `W` | Swap `A` and `B`. |

### Arithmetic
| Char | Effect |
| --- | --- |
| `+` | `A = A + B`. |
| `-` | `A = A − B`. |
| `*` | `A = A × B`. |
| `%` | `A = A mod B`, result takes B's sign; `0` if `B = 0`. |
| `/` | `A = ⌊A / B⌋` (floored); **remainder goes to `B`**. Floored to match `%` so `(A/B)·B + remainder = A`. If `B = 0`: `A = 0`, `B` keeps the dividend. |
| `N` | `A = −A`. |

### Bitwise (two's-complement on all 64 bits)
| Char | Effect |
| --- | --- |
| `&` | `A = A AND B`. |
| `\|` | `A = A OR B`. |
| `~` | `A = A XOR B`. |
| `{` | `A = A << B`; `0` if `B` outside `0–63`. |
| `}` | `A = A >> B` (arithmetic, sign-filling); `0` if `B < 0`; sign-fill if `B > 63`. |

### Direction
| Char | Effect |
| --- | --- |
| `>` | Head east (right). |
| `<` | Head west (left). |
| `^` | Head north (up). |
| `v` / `V` | Head south (down). |
| `X` | Turn by sign(`A`): clockwise if `A > 0`, counter-clockwise if `A < 0`, straight if `A = 0`. `A` unchanged. |

### Control flow
| Char | Effect |
| --- | --- |
| `.` | No-op. |
| ` ` (space) | No-op; little men walk straight over spaces. |
| `H` | Halt this little man. The program ends when every little man has stopped. |

### Backpack
| Char | Effect |
| --- | --- |
| `b` | `BP = A` (A unchanged). |
| `m` | `BP −= 1` (no clamp; may go negative). |
| `d` | Turn clockwise if `BP > 0`, else go straight. |
| `a` | Turn counter-clockwise if `BP > 0`, else go straight. |
| `q` | `BP` = number of values currently in the nearest incoming pipe. |
| `]` | `BP >>= 1` (arithmetic shift right; sign-preserving). |
| `x` | Turn clockwise if `BP`'s low bit is 1, else counter-clockwise. **Always** turns, and reads the raw bit — a negative backpack is not treated as zero. |

## 5. Pipes

A **pipe** is a unidirectional connection carrying values between two rooms, drawn **outside** of rooms using arrowheads (`>`, `<`, `^`, `v`) and body glyphs (`-` horizontal, `|` vertical).

### Mechanics
- Pipes must be **at least 2 cells** long. A single cell is not a pipe.
- Each cell holds at most one value. Every value shifts one cell toward the destination each tick **if the next cell is free**.
- A pipe with `n` cells can hold up to `n` values.
- **Sends** put a value into the **source end** (the segment attached to the sending room); **receives** take from the **destination end** (the segment attached to the receiving room).
- Sending to a full pipe **blocks**; receiving from an empty pipe **blocks**. Blocked little men do not move and retry next tick.
- Running a pipe instruction in a room with no pipe is an **error** (`no-pipe`).

### Parsing rules (a pipe is valid when all hold)
1. It **starts** with an arrowhead whose *backward* cell (opposite the arrow) is on the **source** room's border. The arrow points **away** from the room.
2. Body glyphs match their direction: `-` on horizontal runs, `|` on vertical. A wrong body glyph is a **load error** (not a bend).
3. Every bend is an arrowhead pointing in the **new** direction. Straight-through arrowheads are legal but redundant.
4. It **ends** at the first arrowhead whose *forward* cell is on a room border (any room other than the source). The terminal arrowhead may itself be a bend.

### Common mistakes
- `>----^` into a room above needs **no** bend arrow before `^` — the terminal arrowhead doubles as the final bend.
- A body glyph running into a wall (`>----|`) is a load error; end with an arrowhead pointing into the room.
- An arrowhead pointing back along the flow (`>--<`) is a load error.
- Both ends need arrowheads even for a length-2 pipe (`>>`).

### Pipe operations
| Char | Effect |
| --- | --- |
| `s` | Send `A` into the **nearest** outgoing pipe. Blocks if full. |
| `S` | Send `A` into **every** outgoing pipe at once. Blocks unless **all** have a free source cell — never writes to just some. |
| `r` | Receive into `A` from the **nearest** incoming pipe. Blocks if nothing ready. |
| `R` | Receive into `A` from **any** incoming pipe with a value ready. Blocks if none ready. |
| `U` | Like `R`, but on success the little man **turns away** from the side of the room he read from. |

## 6. Which pipe is "nearest"?

- `s`, `r`, and `q` use the **nearest** pipe to the operation. Distance is the **Manhattan distance** (`|Δx| + |Δy|`) from the operation to the pipe segment attached to the current room (source segment for outgoing, destination segment for incoming).
- **Ties** are broken by **reading order** (top-to-bottom, left-to-right).
- "Nearest" means nearest, **not** nearest-that-can-proceed. A blocked nearer pipe still wins.
- `R` / `U` take a single value from **any** ready incoming pipe; ties by reading order; block when none ready.
- `S` writes to **all** outgoing pipes; blocks if any cannot be written.

## 7. Input & output (I/O rooms)

- The **input room** is a 3×3 room (counting walls) whose single interior cell is `I`, with exactly one pipe flowing **out** of it.
- The **output room** is the same with `O` and one pipe flowing **in**.
- A program may have **at most one** of each.
- It is a load error to: attach a pipe in the wrong direction; attach a second pipe to an I/O room; or have a second I/O room. A pipeless I/O room is legal.
- **Input**: a whitespace-separated sequence of integers. Each tick, if the input pipe's source cell is free, the next value is placed into it.
- **Output**: a value reaching the end of the output pipe is consumed and appended to the program output.

## 8. The LM-75 display

A special room drawn with `+` at corners, `:` on vertical walls, `=` on horizontal walls. Max interior dimensions of 64×64 (so max 66×66 counting borders).

### State
- **Current buffer** — the image currently displayed.
- **Next buffer** — the image being composed.
- **Cursor** — position of the next pixel to draw; starts at `(0, 0)` (upper-left). Buffers start filled with color `0` (black).

### Control via pipes (the attached side determines function)
| Side | Pipe name | Function |
| --- | --- | --- |
| **Top** | `ADDR` | Write `row * width + column` to set the cursor to `(col, row)`. Negative or out-of-bounds value is an error. |
| **Left** | `DATA` | Write a color value `0–15` to set the pixel at the cursor, then advance the cursor (next column, else next row, else upper-left). Values outside `0–15` are an error. |
| **Bottom** | `SWAP` | Copy next buffer into current (shows the new image). `0` = clear next buffer and reset cursor to upper-left; `1` = preserve next buffer and cursor. Any other value is an error. |

- The display can read from all three pipes in the **same tick**, processed in order: `ADDR`, then `DATA`, then `SWAP`.
- Attaching multiple pipes to the same side, attaching a pipe to the **right** side, or attaching a pipe to a **corner** is a load error.

## 9. Tick order (fine print)

Within one tick, in this order:

1. **Pipes shift**: every value moves one cell toward its destination if the next cell is free.
2. **I/O**: a value at the end of the output pipe is emitted; then the next input value enters the input pipe if able.
3. **Execution**: every little man executes the instruction under him. Displays consume and process their pipe input.
4. **Movement**: every non-blocked little man advances one cell.

Because pipes shift **before** instructions execute, a value sent this tick starts moving next tick, and a value can be moved and read on the same tick.

### Output flush when everyone halts
If values are still in flight in the output pipe when the last little man halts, pipes and I/O rooms keep ticking until the output pipe drains (unless the step cap is hit).

### Withheld input
On some problems the judge releases input in stages. Withheld input looks identical to input still in flight — the pipe runs dry until it is released.

## 10. Numeric literals (fine print)

- Backticks pair on rows and columns **independently**. Within a row they pair left-to-right (1st with 2nd, 3rd with 4th, …); within a column top-to-bottom. A backtick that pairs on neither axis is a load error.
- A backtick cannot opt out of an axis: one meant as a horizontal delimiter still pairs **vertically** if its column holds other backticks. Literals stacked across rows can therefore form unintended vertical pairs, and a non-digit between such a pair is a load error.
- The value must fit in 64 bits read in **both** directions, or the program is rejected at load.
- A backtick delimits along whichever axis it pairs on; a corner backtick can open a horizontal **and** a vertical literal at once — literals may overlap and cross, sharing digits. A digit walked in a direction where it belongs to no literal is an ordinary single-digit load.
- Walked along an axis it does not delimit, a backtick is a **no-op**; an empty literal (`` `` `` or spaces only) is also a no-op.

## 11. Glossary

| Term | Definition |
| --- | --- |
| **tick** | The unit of time. Each tick, every little man executes the instruction he is standing on and then, if possible, takes one step in his current direction. |
| **blocked** | The instruction he is standing on cannot complete yet (e.g. receiving from an empty pipe). A blocked man stays where he is and tries again next tick. |
| **instruction** | A single ASCII character specifying an operation. |
| **error** | A fatal mistake — hitting a wall, stepping on an invalid instruction. Immediately ends the whole program. |
| **halt** | A halted little man stops moving and executing. A program stops ticking when all little men have stopped. |
| **direction** | The way a little man is currently facing — up, down, left, or right. Each tick he tries to step one cell that way, unless blocked. |
| **room** | A little man's home: a rectangle drawn with `+` at corners, `-` top/bottom, `\|` left/right. |
| **pipe** | A unidirectional connection between two rooms. Values travel one pipe cell per tick; a pipe holds as many values as it has cells. |
| **io room** | A special 3×3 room containing only `I` or `O`. Values read from the input room's pipe are program input; values sent to the output room's pipe are program output. |
| **main hand (A)** | One of the little man's hands. Many operations (running over a number, arithmetic) change `A`. |
| **off hand (B)** | The other hand. Operations often read `B` but rarely write to it. |
| **backpack (BP)** | A container holding one integer. Cannot be read directly, but the little man can turn based on it. |
| **program** | A grid of ASCII characters that little men walk and execute. |
| **pixel** | A solid block of color, approximately as large as a little man. |
| **display** | A rectangular device that can show images on command. |
| **display cursor** | The position where the next pixel will be drawn. Advances left-to-right, top-to-bottom. |
| **display screen buffer** | One of a display's two stored images: *current* (shown) and *next* (being composed). A swap copies next to current. |