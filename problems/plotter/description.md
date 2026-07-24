Graph line segments on a display.

A line segment is composed of two coordinate pairs `x0 y0` and `x1 y1` that define
its start and end points.

Each [round](/grading#round-based) supplies one line segment. Draw the segment
on the [display](/grading#displays) in **bright white (color 15)**; every other pixel stays black.
Commit the segment
only when it is finished. Lines do not persist between rounds.

Your line must consist of *exactly* the pixels produced by Bresenham's
line drawing algorithm (in its symmetric error form). In pseudocode:

```
dx = abs(x1 - x0);  sx = (x0 < x1) ? 1 : -1
dy = -abs(y1 - y0); sy = (y0 < y1) ? 1 : -1
err = dx + dy
loop forever:
    plot(x0, y0)
    if x0 == x1 and y0 == y1: stop
    e2 = 2 * err
    if e2 >= dy: err = err + dy; x0 = x0 + sx
    if e2 <= dx: err = err + dx; y0 = y0 + sy
```

The algorithm is direction-sensitive: A→B may select different pixels than B→A,
so draw from `(x0, y0)` to `(x1, y1)` as given.