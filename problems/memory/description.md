Simulate a 100-cell memory.

The input is a stream of operations. A READ is the pair `0 addr`; a WRITE is
the triple `1 addr value`. Every cell starts at 0. For each READ, output the
current value of the cell at `addr` — a WRITE produces no output.

For example, the stream `0 5 1 5 10 1 6 9 0 6 0 5` reads cell 5 (still 0),
writes 10 to cell 5 and 9 to cell 6, then reads cells 6 and 5 — so the output
is `0 9 10`.