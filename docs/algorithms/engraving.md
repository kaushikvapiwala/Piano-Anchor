# Engraving

`Engraving.engrave(passage)` (`engine/src/commonMain/kotlin/pianoanchor/engine/notation/Engraving.kt`) turns a
passage's steps into the symbols a staff would draw. It is pure music theory with no drawing types. The practice
staff does not draw its output yet; the drawing waits on the grand-staff decision.

## Staves and values

- A note below middle C (C4) goes on the bass staff, every other note on the treble staff. A step with notes on both
  staves gives one chord per staff.
- A chord's value is picked from the written length exactly (whole, dotted half, half, dotted quarter, quarter,
  eighth). Notes of one chord on one staff share the shortest length's value. A length with no exact value uses the
  longest value that fits (an eighth when nothing does), so odd content never crashes; a content test checks that all
  built-in passages and sight-reading exercises have exact lengths.

## Rests

Per staff, a gap runs from the end of the staff's last sounding note (or the bar start) to its next onset. The last
gap runs to the passage end, which is the end of the last note rounded up to a whole bar. A gap is cut at bar lines.
A whole empty bar is a whole rest, in any metre. Any other stretch is filled left to right with the longest of half,
quarter or eighth that starts on a multiple of its own length within the bar and fits, so `C4q` then three beats of
silence gives a quarter rest at beat 2 and a half rest at beat 3, never a dotted rest. A staff with no notes at all
gets no rests, so a one-hand piece stays clean.

## Stems, beams and flags

- Whole notes have no stem. Otherwise the stem points up when the notehead farthest from the staff's middle line (B4
  treble, D3 bass) is below it, and down when it is on or above it. In a chord the farthest note decides.
- Consecutive eighths on one staff in the same beat form a beam group; the beat is a quarter's span from the bar start,
  whatever the metre. All stems of a group follow the group's farthest note. A lone eighth gets a flag instead.

## Accidentals

Spelling is sharps only, as in `MidiNote` names. A raised note whose letter the key raises needs no sign; a natural
note on a raised letter gets a natural sign; a sharp on any other letter gets a sharp. A sign lasts to the bar's end for
that letter and octave on that staff; the next bar starts from the key again. Flat keys are accepted by the model
but, for now, are treated like C here.
