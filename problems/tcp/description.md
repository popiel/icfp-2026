Reassemble a stream of packets.

A test case carries one stream of `n` packets, numbered `seq = 0 .. n-1`,
each with a value `val`. [Round](/grading#round-based) 1 delivers `n`,
then the first packet; every later round delivers one more packet — `seq
val` — and the packets arrive in some scrambled order.

Output the packets in the correct order, as early as possible. For example:
* Your program begins. You are waiting for packet `0`
* Packet `(2, 30)` arrives. No output - you're waiting for `0`
* Packet `(0, 10)` arrives. Output `10` because `0` arrived. You are waiting for `1`
* Packet `(1, 20)` arrives. Output `20 30` because `1` arrived and you already know the value for `2`
* Packet `(3, 40)` arrives. Output `40` because you are now waiting for `3`.

At any moment, you are waiting for the lowest-numbered packet you haven't seen
yet. When it arrives, you should output its value (and so on, until you hit a
gap). As you can see, a single arrival can produce no output, one value of
output, or several values of output at once. You won't see the next packet
until you've output everything the current one unlocks.

**Maximum delay:** If a packet arrives with a `seq` that is 16 or more above
the `seq` you are waiting for, output `-1` and stop. For example, if the first
packet you receive is `(16, 1)` you should output `-1` and stop.