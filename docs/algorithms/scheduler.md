# Scheduler: the FSRS-6 memory model

`Fsrs` (in `:engine`, package `scheduler`) models how well one item is remembered and when it should come back.
The learner model (M2.4) feeds it a `Rating` and the number of days since the previous review; storage (M2.3)
keeps the resulting `MemoryState`. Only the long-term terms are used: reviews are at least one whole day apart,
there are no learning steps, no same-day terms and no interval fuzzing. The weights w0..w20 are
`FsrsParameters.DEFAULT`, copied from py-fsrs 6.3.3 (MIT, see `NOTICE`); `FsrsVectorsTest` checks the code against
vectors that py-fsrs produced (`scripts/fsrs_vectors.py`, run by hand, never in CI).

## Symbols
- S: stability, the days after which recall has fallen to 90%. D: difficulty, 1 (easy) to 10 (hard).
- R: retrievability, the chance of recall now. t: whole days since the last review.
- G: the rating as a number, AGAIN 1, HARD 2, GOOD 3, EASY 4.

## Forgetting curve
Let d = w20 (the trainable decay) and f = 0.9^(-1/d) - 1. Then R(t, S) = (1 + f * t / S)^(-d). The factor f is
chosen so that R(S, S) = 0.9. Retrievability is 1 at t = 0 and falls towards 0.

## First review
S0 = w[G - 1] (one initial stability per rating). D0(G) = w4 - e^(w5 * (G - 1)) + 1, so a better first rating
gives an easier item.

## Later review
- Difficulty: the step is -w6 * (G - 3), scaled by (10 - D) / 9 so it fades near 10. The result is pulled back
  towards D0(EASY) (unclamped) with weight w7: D' = w7 * D0(4) + (1 - w7) * (D + step). Then clamped to 1..10.
- Stability after recall (HARD, GOOD, EASY): S' = S * (1 + e^w8 * (11 - D) * S^(-w9) * (e^(w10 * (1 - R)) - 1)
  * hard * easy), where hard = w15 for HARD and easy = w16 for EASY, otherwise 1. Recall at a low R, on a
  low-stability, easy item, grows stability the most.
- Stability after a lapse (AGAIN): the smaller of two values. The long-term one is
  w11 * D^(-w12) * ((S + 1)^w13 - 1) * e^(w14 * (1 - R)); the cap is S / e^(w17 * w18), so a lapse never raises
  stability.
- Stability is clamped to at least 0.001 days.

## Interval
Days until R falls to the desired retention r: S / f * (r^(-1/d) - 1), rounded to
the nearest day, then clamped to 1..`MAX_INTERVAL_DAYS`. With r = `DESIRED_RETENTION` (0.9) this is about S.

## Choices and limits
- Bad input throws `IllegalArgumentException`: not 21 weights, `elapsedDays` below 1 in `next` or below 0 in
  `retrievability`, retention outside (0, 1).
- The weight w19 (same-day stability) is read but unused. Parameter fitting from the learner's own history is M7.
