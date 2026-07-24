Multiply two matrices.

An `R×C` matrix is a 2D array of numbers with `R` rows and `C` columns.
Matrices in this problem are *row-major* — row 0 in full, then row 1, and so on
until row `R-1`.

Each test case will provide three integers: `N`, `M`, `K`, and then two
matrices `A` and `B`. `A` will be `N×M` and `B` will be `M×K`. You
should multiply `A` and `B` and output `C`, an `N×K` matrix.

Matrix multiplication works as follows: `C[i][j]` should be the sum of
`A[i][t] × B[t][j]` where `t` goes from `0` to `M-1`.