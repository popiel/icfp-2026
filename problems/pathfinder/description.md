Guide a robot through a maze to find a flag and draw the robot's path on a display.

This problem takes place on a 16x16 board. The board's top-left corner is `0,0`.
Every cell on the board is either a `wall` or a `path`. `path` cells are traversable,
`wall` cells are not.

### Rounds
The first [round](/grading#round-based) is a *setup round*. It supplies the
board's state and the robot's starting position. The board is supplied as 256
values in row-major order (row 0, then row 1, etc). A `0` represents a path and
a `1` represents a wall. Every cell on the board's border is always a wall. The
robot's position `rx ry` is an x,y coordinate pair that is always on a path.

Each subsequent round is a *pathfinding round*. It supplies a flag `fx fy`.
The flag is on a path and is reachable from (and different to) the robot's
current position. The robot's starting position at pathfinding round `N` is
equivalent to its ending position at round `N-1`.

### Output
To complete the setup round, commit **one** frame to your
[display](/grading#displays) showing the
walls, paths, and robot.

To complete a pathfinding round, commit **one frame after each move** — `k`
frames in total, where `k` is the length of the shortest path from the robot
to the flag. The robot may not move through walls.

**If multiple paths are tied for shortest** the robot should prefer moving
up (`y-1`), then right (`x+1`), then down (`y+1`), then left (`x-1`).

**To draw the state of the board** draw paths in color 0, walls in color 7, the
flag in color 9, and the robot in color 10. The flag is not drawn on the last
frame of each round because the robot is on top of it.