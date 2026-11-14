# Performance analysis

The offline performance analyser (M1.3) compares a take with its passage and reports findings: errors at score
notes. It works on played notes, aligns them to the score, fits a tempo and classifies every error kind. A seeded
test harness measures it (generated takes, error injection, scoring).

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

Key up is what the hand did; the sounding end is what the ear heard. Note-length errors judge the sounding end, so
a note held by the pedal is as long as it sounded.

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
per take). It reads the timing tolerance from `Tunables`, the same entries the analyser uses.

**Scoring.** `ClassificationScore.of` compares the analyser's findings with the labels:

- A label is matched by a finding of the same kind at the same score note, one to one.
- `overall` is matched / injected across all kinds, and `kindRate` is the same for one kind.
- On error takes, findings that match no label are ignored. One error can rightly cause a second finding, such as
  a missed note followed by a late one.
- Clean takes count false alarms: `falseAlarmRate` is findings on clean takes / played notes in clean takes.
- A rate over nothing throws, so a harness bug can't pass the exit check by accident.

M1's exit check (M1.3) asks for ≥ 95 % overall, ≥ 90 % per kind and ≤ 5 % false alarms.

## The analyser
`PerformanceAnalyser.analyse(passage, notes, tempo)` (in `:engine`, package `analysis`) returns the findings, one
`NoteMatch` per score note and the fitted tempo. `tempo` is a `TempoReference`:

- `Target(bpm, startNanos)`: early and late against a set tempo (a tempo-ladder rung). `startNanos` is when tick 0
  should sound, such as a tempo run's downbeat after the count-in. When it is null, the first matched step is on
  time by definition.
- `Fitted`: early and late against the tempo fitted to the take itself.
- `Untimed`: wait mode. Only pitch kinds and hesitation; no early, late or note length.

### 1. Alignment
A dynamic programme gives each score step a contiguous run of the played notes, in onset order. Within a run the
step pairs its notes with the run's notes, so a chord's notes may arrive in any order, and a late chord note still
belongs to its chord as long as it comes before the next step. Notes left in a run are extras; score notes left
unpaired are missed. Every played note lands in some step's run.

Costs (private constants in `Alignment.kt`; internal weights, not coach-facing tunables):

| Move | Cost | Why |
|---|---|---|
| missed score note | 1.0 | |
| extra played note | 1.0 | |
| pair, same pitch | 0 | |
| pair, ±12 or ±24 semitones | 0.5 | an octave slip is nearly right |
| pair, any other pitch | 1.0 | a wrong key in the right place is one error, not a miss plus an extra |
| step onset | 0.5 per beat off, at most 0.5, paid once per step | breaks ties between repeated pitches; capped so a hesitation never turns a played step into misses |

Every pair costs less than a miss plus an extra (2.0), so a step pairs as many notes as its run allows. Within a run,
pairs are taken cheapest pitch first, then nearest the expected onset, then in score order and onset order. The
expected onset runs from the last paired step at the reference tempo (the target, else the written tempo), so one
pause doesn't shift everything after it. Before the first paired step it runs from `startNanos` at tick 0, or else
from the first played note at the first step. Between equal-cost paths the first one found is kept, which is
deterministic.

The work grows with steps × notes², which is fine for passages of a few dozen notes.

**Mistouch pre-pass.** A first alignment finds the brushed keys: a played note within `MISTOUCH_WINDOW_MS` of a
louder exact match one or two semitones away, unless it is itself an exact match of that step (a chord with a
second in it). It becomes `MISTOUCH` at the louder note's score note (the nearest in time when several qualify) and
is dropped before the final alignment. A key brushed between two chord notes, each one or two semitones away, could
belong to either; the analyser picks the one nearest in time.

### 2. Tempo fit
A least-squares line of onset nanos against onset ticks, over exact-pitch matches. `fittedTempoBpm` comes from its
slope. It is null with fewer than two exact matches at distinct onsets, or when the line doesn't rise. A long pause
pulls the fitted line too, so `Fitted` and `Untimed` hesitation checks are less sharp than `Target` ones.

### 3. Classification
In score order, step by step:

1. **Pitch.** A matched note at ±12/±24 semitones is `OCTAVE`, at any other wrong pitch `WRONG_NOTE`. Only exact
   pitches are judged for timing and length. A note gets at most one pitch finding.
2. **Missed.** An unmatched score note is `MISSED_NOTE`, or `INCOMPLETE_CHORD` when another note of its step was
   matched (one finding per missing note).
3. **Hesitation** (every reference; `Untimed` and `Fitted` take the expected gap from the fitted tempo). A step whose
   earliest matched onset comes at least `HESITATION_GAP_RATIO` × the expected gap and at least `HESITATION_MIN_MS`
   after the previous step's is `HESITATION`, at its lowest matched pitch, instead of late. The time map is pinned
   again at that step, so later notes are measured from it.
4. **Early and late** (`Target`, `Fitted`). An onset more than max(`TIMING_TOLERANCE_MS`,
   `TIMING_TOLERANCE_BEAT_FRACTION` × beat) from the expected one is `EARLY` or `LATE`.
5. **Length** (`Target`, `Fitted`). Sounding length (key down to sounding end) under `NOTE_SHORT_RATIO` × written is
   `TOO_SHORT`, over `NOTE_LONG_RATIO` × written `TOO_LONG`. A note that stops sounding within the timing tolerance
   of the next step's onset is never `TOO_SHORT`: legato players lift just before the next note.
6. **Extras.** An unmatched played note is `EXTRA_NOTE` at the lowest pitch of the step nearest in time. A matched
   step is at its earliest matched onset; an unmatched one is placed from the nearest matched step before it (else
   after it) at the reference tempo.

Findings come in score order (step, then pitch), then by time, missing notes first. `matches` lists every score
note once: its played note (null when missed), its onset deviation (null when untimed or missed) and the gap from
the previous step's earliest matched onset (null for the first step or when either is missed).

### Worked example
C4-D4-E4-F4 in quarter notes, `Target(60)` (beat 1000 ms, tolerance max(60, 125) = 125 ms), no `startNanos`:

| Played | Paired with | Expected | Finding |
|---|---|---|---|
| C4 at 0 ms, 900 ms long | C4 | 0 ms (anchor) | none |
| C#4 at 20 ms, softer | (pre-pass) | | `MISTOUCH` at C4 |
| D4 at 1200 ms | D4 | 1000 ms | `LATE` (200 ms > 125 ms) |
| E4 at 4000 ms | E4 | 2000 ms | `HESITATION`: gap 2800 ms ≥ 2 × 1000 ms and ≥ 400 ms; pinned at 4000 ms |
| F4 at 5000 ms, 300 ms long | F4 | 5000 ms | `TOO_SHORT` (300 ms < 500 ms; last step, so no legato) |

### Exit check
`PerformanceAnalyserExitTest` runs the harness on three original passages: a stepwise melody, left-hand chords
under a melody, and a line of mixed note values. It makes 30 clean takes and 30 takes per error kind at each
passage's written tempo, analysed with `Target` and the take's start time. With the fixed seeds it classifies
99.7 % overall, every kind ≥ 96 %, with no false alarms on clean takes. Recorded takes from real pianos confirm it
once the hardware check records them.
