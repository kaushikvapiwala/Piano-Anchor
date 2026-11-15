# Note text

Each passage's notes are written as one compact text line and parsed once, when the content loads. Units, skills and
passage metadata (title, tempo, time signature, rights) stay typed Kotlin in `engine/src/commonMain/kotlin/pianoanchor/engine/content`. When MusicXML import
lands (M6) the content moves to files behind a content port; see `docs/adr/0023-content-in-kotlin.md`.

## Why a text line

A line like `C4q D4q E4h` is short enough to read and review in a diff, and the parser rejects typos at once. A subset of
ABC notation of our own keeps it about 80 lines: full ABC and LilyPond parsers are not Kotlin Multiplatform, are far
bigger than needed, and some are GPL.

## Grammar

Tokens are separated by spaces.

- **Note:** `<name><accidental?><octave><length>`, e.g. `E4q` or `F#3h`. Names are `A`-`G`, the accidental is `#` or
  `b`, the octave is -1..9 and the pitch must stay within MIDI 0..127 (so C-1 is the lowest note and G9 the highest).
- **Length:** `w` whole, `h` half, `q` quarter, `e` eighth, `s` sixteenth. A trailing `.` dots it (one and a half
  times as long).
- **Chord:** `[C4 E4 G4]q`: the notes sit inside brackets and share the one length after the closing bracket. A chord is
  one token for numbering and may not repeat a pitch.
- **Rest:** `r` and a length, e.g. `rq`.
- **Bar line:** `|`, optional.

Each note, chord or rest starts where the previous one ended; the first starts at tick 0. A rest only moves later
onsets: it produces no step, and the staff does not draw it until stems and rests land.

## Checks

- Every bar line must close a bar that fills the time signature exactly. Without bar lines, the whole text must still
  be a whole number of bars. Pickup bars (anacrusis) are not supported yet.
- Errors throw `IllegalArgumentException` and name the token by its 1-based position (bar lines and chords count as
  one token each) and its text, e.g. `token 7 'H4q': unknown note name`.
- Empty text is an error.
