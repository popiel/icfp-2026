Validate a Sudoku solution.

A Sudoku grid is a 9x9 square of numbers. In a correctly solved grid, each row,
each column, and each of the nine 3x3 boxes (rows `0-2/3-5/6-8` crossed with
columns `0-2/3-5/6-8`) in the grid contains exactly the digits 1-9 without
repetition.

Each [round](/grading#round-based) delivers three integers `r c v`
describing the contents of one cell. `r` and `c` are the 0-indexed row and
column of the cell, and `v` is the value (between 1 and 9) placed at that cell.

After reading each cell, output `1` if the grid is still valid (that is, if no
row, column, or box in the grid contains a duplicate number) and `0` if the
grid is no longer valid.

No cell is delivered more than once. Your program only needs to output `0`
once: the test case ends as soon as an invalid value is delivered.