Find a set of integers in a list that sum to a target number.

Each test case provides a count `n`, then `n` values `v_0 .. v_(n-1)`,
then a target `t`.

You should output a count `k` and then `k` values that sum to the target
`t`, ordered by their original index.

**If no subset of the list sums to `t`** you should output `0` and nothing else.

**If more than one subset sums to `t`**, output the subset whose chosen indices
are lexicographically smallest. E.g. the set `0, 4` beats the set `1, 3` and the
set `1, 2, 4` beats the sets `1, 3` and `2, 3, 4`.

**Example.** `values = [3, 5, 2, 6]`, `target = 8`. Two subsets sum to 8:
indices `{0, 1}` (values `3 + 5`) and `{2, 3}` (values `2 + 6`). The indices
`[0, 1]` beat `[2, 3]` at the first position, and the chosen set has 2 elements,
so the output is `2 3 5`.