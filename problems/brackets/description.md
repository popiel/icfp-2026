Read a string of bracket characters and report whether it is balanced.

Each input is one length-prefixed string: a count `n`, then `n` bytes, each
the decimal [ASCII](/grading#ascii) code of one character drawn from
`( ) [ ] { }`.

A string is **balanced** if every opener `(` `[` `{` is matched by a closer
`)` `]` `}` of the same type. Bracket pairs may only be nested or
concatenated, never interleaved — e.g., `[()]` and `[]()` are valid but `[{]}`
is not. The empty string is balanced.

Formal definition of balanced strings (in [BNF](https://en.wikipedia.org/wiki/Backus%E2%80%93Naur_form)):

```
s := ε
   | ( s )
   | [ s ]
   | { s }
   | s s
```

Output one integer:

- `0` if the string is balanced.
- Otherwise, the **1-based position** of the first offending character: the
  first closer that doesn't match the most recently opened, still-unclosed
  opener (or that appears with nothing open), or `n + 1` if the string ends
  with openers still unclosed.

In `([)]` the `)` at position 3 is the first offending character — the most recently opened
bracket there is `[`, not `(`. In `([` nothing offends inside the string,
but both openers are left unclosed, so the answer is `n + 1 = 3`.