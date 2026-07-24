Process operations over student grades across several subjects.

A grade book tracks `N` students across `K` subjects. Subjects are numbered
`1` through `K`. A student record is a unique `id` followed by one grade per
subject, in subject order: `id g1 g2 ... gK`. [Round
1](/grading#round-based) provides `N K`, then the `N` student records.

An operation is an integer `op` naming an action to perform, followed by that
action's arguments. Rounds 2 and beyond provide a
count `O` and then `O` operations. Your program should process each operation
in order, outputting data as it is requested.

There are 4 operations, so `op` is between `1` and `4`. The operations are:
* **`GET (op=1)`** - `1 id s` - output student `id`'s grade in subject `s`
* **`SET (op=2)`** - `2 id s v` - set student `id`'s grade in subject `s` to `v`
* **`AVG (op=3)`** - `3 s` - output the average grade in subject `s` rounded down
* **`TOP (op=4)`** - `4 s` - output the `id` of the student with the highest grade in subject `s`

Your program does not need to output anything for `SET` operations. If multiple
students are tied for the highest grade in a subject, `TOP` should return the
smallest such student id.