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
In simple metres any other gap is filled left to right with the longest of half, quarter and eighth that starts on a
multiple of its own length inside the bar and still fits, so a quarter then a half fills three beats (never a dotted
half rest). In compound metres (below) a rest never crosses a beat: a dotted quarter where the gap starts on a beat and
fits, otherwise a quarter or eighth that starts on a multiple of its own length within the beat. The dotted rest is the
quarter rest glyph with an augmentation dot right of it in the space above the middle line.

## Stems, flags and beams

- No stem on whole notes. Otherwise the stem points up when the notehead farthest from the staff's middle line is
  below it, and down when it is on or above it (a chord's farthest note decides; B4 on the treble middle line points
  down). It is 3.5 staff spaces long from the outer notehead.
- Compound metre: `beatUnit == 8` with 6, 9 or 12 beats (6/8, 9/8, 12/8; 3/8 stays simple). Its beat is a dotted
  quarter, three eighths.
- Consecutive eighths on one staff with no rest between them and inside one beat (a quarter's span from the bar start,
  a dotted quarter's in compound metres, so 6/8 beams in threes) form a beam group: one horizontal beam at the stem ends, every stem pointing the way its farthest note says and
  ending at the same height. A lone eighth gets a flag.

## Dots and accidentals

A dotted value draws one augmentation dot right of each notehead, in the space above when the head sits on a line.
A black key is spelled by the key's direction: a sharp in C and sharp keys, the flat of the letter above in flat keys
(61 is D-flat and sits on D's line or space, so stems and ledger lines follow the flat's place). A note matching what the
key does to its letter needs no sign (B-flat in F, F-sharp in G); the natural of such a letter gets a natural sign; a
black key on any other letter gets a sharp or a flat (E-flat in F). An accidental holds until the end of the bar for
that letter and octave on that staff; the next bar starts from the key again.

## Key signature

Drawn after the clef on each staff, before the time signature, in the standard positions: sharps at treble F5 C5 G5 D5
A4 E5 B4, flats at treble B4 E5 A4 D5 G4 C5 F4; the bass staff two octaves lower. The staff reserves 14 dp per sharp or
flat; C reserves none.

Known limitation: a flat key spells every black key as a flat, so a leading-tone sharp (C-sharp in D minor) is not
spelled yet.

## Spacing and colour

Spacing is by ticks with the usual minimum gap; rests occupy tick positions like notes. Stems, flags and dots take their
notehead's colour; a beam takes the colour of its first unplayed note, or "done" once all are played; rests, key
signature and clefs use the staff line colour.

## Pedal marks

`Passage.pedal` lists spans of the sustain pedal. `Engraved.pedal` turns them into lines: spans that meet join into one
line with a notch at each joint (a pedal change); a gap starts a new line. Lines are not split: the app has no
systems of bars yet, so a line runs the whole width. The lines go under the bass staff, or under the treble
staff when the passage has no bass notes (`Engraved.pedalStaff`). Drawn in the staff line colour and stroke: a short
upward tick at the start, a horizontal line, an inverted-V notch at each change and an upward tick at the end.
TalkBack adds "Pedal changes each bar." when every bar starts a span (`Engraved.pedalEachBar`), else "With pedal."
