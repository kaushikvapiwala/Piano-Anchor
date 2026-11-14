# Performance analysis

The offline performance analyser (M1.3) compares a timed run with its passage and reports findings: errors at
score notes. This note covers what M1.2 built first: played notes, the error kinds, and the test harness that
measures the analyser (seeded takes, error injection, scoring). The analyser itself is added in M1.3.

## Played notes
`PlayedNotes.from` (in `:engine`, package `analysis`) turns MIDI events into `PlayedNote`s: pitch, onset velocity,
key down, key up and the time the note stopped sounding.

- A NoteOn starts a note and the next NoteOff of the same pitch ends it. A NoteOn with velocity 0 is a NoteOff on
  the wire; the MIDI parser already turns it into one.
- Notes are paired per pitch on every channel, since a piano is one instrument. A NoteOff for a key that is not
  down is ignored.
- **Sustain pedal.** Control change 64 at 64 or more holds every key that comes up while the pedal is down: it
  keeps sounding until the pedal drops below 64. A key that came up before the pedal went down is not caught.
- Striking a pitch that is still down, or still sounding under the pedal, ends the earlier note there. A piano
  can't sound a string twice at once.
- A key still down, or a note still held, when the events run out ends at the last event's time.
- Events are read in timestamp order. Notes come in onset order, with equal onsets ordered by pitch
  (`PLAYED_NOTE_ORDER`). Other events are ignored.

Key up is what the hand did; the sounding end is what the ear heard. Note-length errors judge key up, and only
with the pedal up.

## Error kinds
Every finding names one score note (`ScoreNoteRef`: step index and pitch) and the played note involved. Only
`MISSED_NOTE` and `INCOMPLETE_CHORD` have no played note, and `Finding` enforces that.

| Kind | Meaning | Points at |
|---|---|---|
| `WRONG_NOTE` | another key, 3–11 semitones away, instead of a single note | the score note |
| `MISSED_NOTE` | a single-note step that was not played | the score note |
| `EXTRA_NOTE` | a key the score doesn't ask for, away from every score note in pitch and time | lowest pitch of the step nearest in time |
| `INCOMPLETE_CHORD` | a chord with one of its notes left out | the left-out note |
| `MISTOUCH` | a softer key one or two semitones from a correct note, at the same moment | the correct note |
| `OCTAVE` | the score note an octave too high or low | the score note |
| `EARLY` / `LATE` | the note starts outside the timing tolerance | the score note |
| `HESITATION` | a pause before a step much longer than the music asks for | lowest pitch of the delayed step |
| `TOO_SHORT` / `TOO_LONG` | key up well before or well after the written length | the score note |

The bands are chosen so the kinds don't overlap. A neighbour one or two semitones away is a mistouch, three to
eleven is a wrong note, and exactly twelve is an octave. A dropped single note is a missed note, while a dropped
note of a chord is an incomplete chord.

## Test harness
The analyser is measured on generated takes, so every error has a known label. Recorded takes from real pianos
join as extra cases once the hardware check records them. All of this lives in `testing/`.

**Clean takes.** `TakeGenerator(seed).clean(passage, tempoBpm)` plays a passage as a steady amateur would:

- Takes start at `TAKE_START_NANOS`.
- Each step's onset gets Gaussian jitter with σ = 15 ms (Box–Muller over a seeded `Random`), and chord notes
  spread over a further 10 ms. Jitter and spread are each capped at a quarter of the gap to the nearest step, so
  even very fast steps keep their order.
- Velocities are 60–90 and lengths are 80–95 % of written. There is no pedal.
- A key comes up 5 ms before its pitch is struck again.

Same seed, same take.

**Error injection.** `ErrorInjector(seed).inject(take, kind)` puts one error into a clean take, at a random
eligible score note, and labels it. The analyser's starting thresholds are a timing tolerance of
±max(60 ms, 12.5 % of a beat), hesitation at 2× the expected gap and 400 ms, a mistouch window of 40 ms, and
length limits of 50 % and 150 %. The injected values sit clearly beyond them:

| Kind | Injection |
|---|---|
| `WRONG_NOTE` | a single note's pitch moves ±3–11 semitones (never ±12) |
| `MISSED_NOTE` | a single note is dropped |
| `EXTRA_NOTE` | a note ≥ 3 semitones from every score pitch, at 40 % of a gap of at least 400 ms between steps, ≥ 120 ms from every onset |
| `INCOMPLETE_CHORD` | one note of a chord is dropped (refused without chords) |
| `MISTOUCH` | a note ±1–2 semitones from a correct note, 5–35 ms after it, at 30–60 % of its velocity |
| `OCTAVE` | a note moves ±12 semitones |
| `EARLY` / `LATE` | a note moves 2–3 timing tolerances, staying between its neighbouring steps |
| `HESITATION` | a step and every later step move later by 2.5–4× the written gap before it, and at least 500 ms |
| `TOO_SHORT` / `TOO_LONG` | key up at 20–40 % or 180–250 % of the written length, with no pedal |

The injector never makes a key go down twice at once: a change that would overlap another note of the same pitch
is not eligible. It refuses a kind with no eligible note, and a take that already has labels (M1 uses one error
per take). Its thresholds are private copies until M1.3 moves them into `Tunables`.

**Scoring.** `ClassificationScore.of` compares the analyser's findings with the labels:

- A label is matched by a finding of the same kind at the same score note, one to one.
- `overall` is matched / injected across all kinds, and `kindRate` is the same for one kind.
- On error takes, findings that match no label are ignored. One error can rightly cause a second finding, such as
  a missed note followed by a late one.
- Clean takes count false alarms: `falseAlarmRate` is findings on clean takes / played notes in clean takes.
- A rate over nothing throws, so a harness bug can't pass the exit check by accident.

M1's exit check (M1.3) asks for ≥ 95 % overall, ≥ 90 % per kind and ≤ 5 % false alarms.
