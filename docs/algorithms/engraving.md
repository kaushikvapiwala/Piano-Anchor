# Engraving

`Engraving.engrave(passage)` (`engine/src/commonMain/kotlin/pianoanchor/engine/notation/Engraving.kt`) turns a
passage's steps into the symbols a staff draws: chords with a note value, stem, flag, beam group and accidentals, plus
rests and the key. It is pure music-theory code; `StaffLayout` places the symbols in dp and `StaffView` draws them.
Glyphs are Bravura (SMuFL), see `docs/ui/living-studio.md`.

## Staves and values

- A note below middle C (C4) goes to the bass staff, any other to the treble staff. A step with notes on both staves
  gives one chord per staff. The view draws only the staves that carry notes, so a one-hand piece stays clean and
  gets no rests on the empty staff.
- A note's length picks its value exactly: whole, dotted half, half, dotted quarter, quarter, eighth. A length with no
  value uses the longest value that fits (the eighth when shorter); no built-in passage needs this, and a content test
  checks every passage and one exercise per sight level. Notes of one chord on a staff share the shortest length's value.

## Rests

Per staff, from the end of its last sounding note (or the bar start) to its next onset, or to the passage end (the
last note's end rounded up to a bar). Gaps are cut at bar lines. A whole empty bar is a whole rest, whatever the metre.
Any other gap is filled left to right with the longest of half, quarter and eighth that starts on a multiple of its own
length inside the bar and still fits, so a quarter then a half fills three beats (never a dotted half rest).

## Stems, flags and beams

- No stem on whole notes. Otherwise the stem points up when the notehead farthest from the staff's middle line is
  below it, and down when it is on or above it (a chord's farthest note decides; B4 on the treble middle line points
  down). It is 3.5 staff spaces long from the outer notehead.
- Consecutive eighths on one staff with no rest between them and inside one beat (a quarter's span from the bar start)
  form a beam group: one horizontal beam at the stem ends, every stem pointing the way its farthest note says and
  ending at the same height. A lone eighth gets a flag.

## Dots and accidentals

A dotted value draws one augmentation dot right of each notehead, in the space above when the head sits on a line.
Accidentals use sharps-only spelling. A raised note on a letter the key raises needs nothing; a natural on such a
letter gets a natural sign; a sharp on any other letter gets a sharp. An accidental holds until the end of the bar for
that letter and octave on that staff; the next bar starts from the key again.

## Key signature

Drawn after the clef on each staff, before the time signature, in the standard sharp positions (treble F5 C5 G5 D5 A4 E5
B4; bass two octaves lower). The staff reserves 14 dp per sharp; C reserves none. Flat keys exist in the model and are
not drawn yet.

## Spacing and colour

Spacing is by ticks with the usual minimum gap; rests occupy tick positions like notes. Stems, flags and dots take their
notehead's colour; a beam takes the colour of its first unplayed note, or "done" once all are played; rests, key
signature and clefs use the staff line colour.
