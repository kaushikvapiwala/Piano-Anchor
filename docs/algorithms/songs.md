# Songs and piece status

The Songs screen lists every piece and says where the learner stands on it. The rules live in `Pieces.list`
(`engine/src/commonMain/kotlin/pianoanchor/engine/content/Pieces.kt`), a pure function, so the screen holds no domain
rules.

## What a piece is

A passage with `piece = true`: the traditional tunes of units 1 and 2 and the four songs of the chord unit
(`SongsUnit.kt`). Exercises are never pieces. Pieces are listed by unit in curriculum order, in content order inside a
unit.

## Status

- **Locked** when any skill of the piece has a prerequisite that is not learned. `waitingFor` is the first such skill
  in graph order, and the row says "Needs: <that skill's title>".
- Otherwise **new** when the piece was never played, **mastered** when its top tempo rung has a CLEAN run, else **in
  progress**.

## Rungs

`cleanRungs` counts the rungs of the tempo ladder (wait mode, then 60, 70, 85 and 100 per cent) with a CLEAN run.
`nextRung` is the first rung above the highest clean one, wait mode when none: the same rule as Today's Song card. It
is null once mastered; opening a mastered piece uses the top rung.

## Chord content

The chord unit teaches six skills in C position (triads, changes in time, melody over held chords, first inversions,
A minor and the I-V-vi-IV progression). The left hand plays C3-E3-G3 (C), F2-A2-C3 or A2-C3-F3 (F), G2-B2-D3 or
B2-D3-G3 (G) and A2-C3-E3 (Am) while the right hand stays in C4-G4. A left-hand chord and the right-hand note that
starts with it are one step, so wait mode asks for them together; the 90 ms chord window then covers up to four notes,
which may prove tight on real hardware.
