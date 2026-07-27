Simulate a game of ["Snake"](https://en.wikipedia.org/wiki/Snake_%28video_game_genre%29)
and draw it on a display.

In Snake, a player steers a line (the snake) as it grows and turns. The game
runs on a 16x16 grid: the top-left corner is `0,0`, x grows right, y grows
down.

Data is provided in [rounds](/grading#round-based). The first round is `sx sy`,
the snake's starting position: a single cell, moving *right*. Commit a frame
showing it. Every later round is one of:

* **Fruit spawn:** `1 fx fy`. A fruit spawns at `fx fy`; the game does not
  tick. Commit a new frame.
* **Direction change:** `2/3/4/5`. The snake's direction is set to
  `up/right/down/left` respectively from the next tick on. The game does not tick.
  Do not commit a new frame.
* **Tick:** `0`. Advance the game one tick (explained below). Commit a new frame.

On each tick the head advances one cell in the snake's current direction:
* Landing on a fruit **grows** the snake — the tail stays put, the fruit
  disappears.
* Otherwise the tail moves **before** the head (moving to where the tail just
  was is legal).
* If the head would land off the grid or on a cell the snake still occupies,
  the player loses and the test case ends. The snake does not move (draw it
  where it was before the tick).

At most one fruit is on the board at a time; fruit always appears in an empty
cell. You will receive at most one direction change between consecutive ticks,
and a direction change never reverses the snake (you will not receive `down`
while the snake moves `up`).

**To draw this game to the [display](/grading#displays):**
* If the game is ongoing, draw the snake in **green (color 10)**
* If the game has ended, draw the snake in **red (color 9)**
* Draw fruit in **red (color 9)**
* Other cells should be left **black (color 0)**.