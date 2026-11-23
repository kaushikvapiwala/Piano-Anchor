# Beginner course

The design of Piano Anchor's first course, agreed with the owner in a `/grilling` session on 2026-11-15. The decision
is recorded in ADR 0026 (`docs/adr/0026-concept-cards-and-five-step-lessons.md`). The work is in `docs/ROADMAP.md`
under "Next up".

Why a redesign: the owner's hardware check showed that the units test the learner but never explain anything. For
example, the black-keys unit doesn't say what black keys are. On the first timed run the owner played through the
count-in, held every key for 0.3 s and couldn't tell what they had played. Results showed "±1345 ms" and "0%" with
no explanation.

## Who and where to
- **Learner:** an adult who has never played and can't read music. The course assumes nothing, not even what a note
  or a beat is.
- **Goal, after about 3 months:** play simple songs with both hands from music, melody in the right hand and a few
  chords in the left. The last piece is Ode to Joy with left-hand chords.
- **Day:** 10–15 minutes. Review quizzes, then one new concept, then a song.

## How the order was chosen
The order follows two adult method books (Alfred's Adult All-in-One Level 1 and Faber's Adult Piano Adventures
Book 1). They were read for their ideas and order only; no exercises, wording or songs were taken. Both books:
- spend their first stage on the keys only, with no staff, and play by finger numbers and hand positions;
- clap and count before playing, adding note values one at a time;
- read music from landmark notes (middle C, treble G, bass F) and by distance (step, skip), not letter by letter;
- hold sharps and flats until after reading has started.

Neither book says why black keys exist. Piano Anchor does: every concept explains why it exists and why it comes
now.

The owner chose middle C as the first sound, ahead of free play on the black keys. That gives the course its first
hook: middle C glows, the learner plays it, and the app asks how to find it without the light. The black keys are
the answer.

## Concept card
Every concept has a card with four required one-line fields. A content test fails if any field is empty. The 23 cards are
in `ConceptCards.kt` (type `ConceptCard`); showing and animating them comes later.

| Field | Answers | Black keys, for example |
|---|---|---|
| What | What it is | The raised keys, in groups of 2 and 3. |
| Why | Why it exists | The groups let you find any note without labels, and the black keys fill the steps between white keys. |
| How | How it's used | Sharps and flats; songs in other keys. |
| When | When you meet it | Now, as landmarks for finding notes. Later, as sharps and flats. |

Each card has a 3–5 s looping animation drawn in Compose, such as a hand curving or finger numbers lighting up. No
video files: drawn animations add nothing to the APK's size, work offline, follow the theme and text direction, can
be translated and describe themselves to TalkBack.

## Lesson shape
Every lesson has five steps:
1. **Why:** the card, about 10 s.
2. **See:** the app plays the idea while the keys light up and the cursor moves. The learner can skip it.
3. **Try:** one small action, checked by the piano.
4. **Use:** a tiny piece that uses the idea.
5. **Recap:** one line: "You can now ___. Next: ___."

Rules:
- **Hear it first:** every passage plays once before the learner tries it, and they can skip that.
- **Beat from day 1:** the click is there from the beat lesson on, but timing isn't judged until the course says so.
  A timed run shows a visible count-in ("3, 2, 1, play") and a clear start cue.
- **Wrong key:** the key pressed flashes grey and the right key glows. One plain line explains, for example "That was
  D. C is just left of the two black keys." No red, no sound, and the app waits.
- **Nothing locked:** every lesson is open. Today shows one suggested "Next" card. The course map ticks lessons the
  learner has passed (right notes in wait mode) and marks the suggested one. Opening a later lesson is fine; its card
  says what it builds on. Timing and note length improve through review and never block a lesson.
- **Review:** learned concepts come back as 10–20 s quizzes checked by the piano ("play all the Fs", "a skip up from
  E", "hold C for 2 clicks"), scheduled by the existing review planner into the warm-up.
- **Results in words:** say what happened and give one tip, for example "You started a beat late. Notes were short:
  hold each for one full click." The numbers move behind a "Details" tap.

## Screen
Landscape. The card and its animation sit on the left, the task or staff on the right, and a keyboard strip runs
along the bottom and lights keys. Only the right side changes between steps. A dot row shows the five steps.

```
┌──────────────┬───────────────────────┐
│ BLACK KEYS   │ TRY                   │
│ [animation]  │ Play every C you can  │
│ What …       │ find: 3 of 8 ✓        │
│ Why  …       │                       │
│ ●●●○○        │                       │
├──────────────┴───────────────────────┤
│ keyboard strip, lit keys             │
└──────────────────────────────────────┘
```

## The map
"Why here" is the reason for the position. The Try is what the piano checks.

### Unit 0: Meet the piano (no staff, every key lit)
| # | Concept | Why here | Try |
|---|---|---|---|
| 1 | Sit, hand shape, fingers 1–5 | Every later step names a finger. | "Any key with finger 3". The app can't see fingers, so it trusts the learner. |
| 2 | Middle C | The first sound and home base; everything is placed relative to it. | Play the glowing key. |
| 3 | Black keys, groups of 2 and 3 | They answer "how do I find C without the light?" | No light: find C, just left of a 2-group. |
| 4 | High and low, all the Cs | The pattern repeats every 7 white keys, with the same names. | Play every C on the keyboard, from 0 of 8 to 8 of 8 (88 keys). |
| 5 | Steady beat | Music has a pulse; later, timing is judged against it. | Play C eight times with the click. |
| 6 | Quarter and half notes | Notes have lengths; this fixes letting go too soon. | Hold C for 2 clicks. |
| 7 | C D E, then F G A B | The names hang on the 2-groups and 3-groups. | "Play every F". |

### Unit 1: Five-finger tunes (right hand, then the first staff)
| # | Concept | Why here | Try |
|---|---|---|---|
| 8 | Right-hand C position | One finger per key: play without looking or moving. | Play C to G and back. |
| 9 | Steps and skips | Tunes move by the next key or by jumping one key; reading by distance builds on this. | "A skip up from E". |
| 10 | First tunes by finger number | Real music with what is known. Public domain only. | Hot Cross Buns, Mary Had a Little Lamb. |
| 11 | Bar lines, counting to 4 | They group the beats so the learner knows where they are. | Stress beat 1. |
| 12 | The staff, middle C, steps | Higher on the page is higher on the piano; steps go line to space. | Read 3 notes and play them. |
| 13 | Treble clef = G, skips | A second landmark; skips go line to line. | Find G from the clef. |
| 14 | Soft and loud (p, f) | Expression; the piano measures how hard keys are struck. | Play soft, then loud. |

### Unit 2: Left hand, then both hands
| # | Concept | Why here | Try |
|---|---|---|---|
| 15 | Left-hand C position | A mirror of the right: finger numbers run the other way. | Finger 5 on the C below middle C. |
| 16 | Bass clef = F, whole note | The left hand's landmark. | Hold for 4 clicks. |
| 17 | Grand staff, hands taking turns, rests | Both staves at once; a rest is silence that is still counted. | A turn-taking piece. |
| 18 | 3/4 time, dotted half note | A second metre: the waltz. | A waltz. |
| 19 | Hands together, left hand holding | The first step to two parts. | Hold a note and play the tune. |

### Unit 3: Chords and songs (the goal)
| # | Concept | Why here | Try |
|---|---|---|---|
| 20 | C chord, fingers 1-3-5 | A chord is notes played together; it backs a tune. | All three notes land together. |
| 21 | G and F chords, chord symbols | Fewest finger moves between chords. | Change chord on beat 1. |
| 22 | Melody plus chords | The goal. | Ode to Joy with chords. |
| 23 | Sharps and flats | The black keys' second job, closing the loop from step 3. | Play C♯. |

Left for the next course: eighth notes, the G position, the pedal and scales.

## Existing content
The new map came first, then each existing passage was kept under the concept it exercises. Done in issue 181. Ids
and notes are unchanged; only the skills they list changed. Pieces stay pieces.

| Concept | Passages |
|---|---|
| Middle C | Middle C march |
| Black keys, groups of 2 and 3 | Twos and threes, Twos and threes coming down, Which C is middle? |
| High and low, all the Cs | All the Cs, Cs up and down |
| Steady beat | Steady quarters |
| Quarter and half notes | Half and quarter |
| Right-hand C position | C-D-E, Five-finger walk |
| Steps and skips | Step up, step down; Skipping stones |
| First tunes by finger number | Hot Cross Buns, Mary Had a Little Lamb, Au clair de la lune |
| Bar lines, counting to 4 | Steady skipping, Lightly Row |
| Treble clef = G, skips | Ode to Joy |
| Left-hand C position | Left hand walk, Left hand steps |
| Bass clef = F, whole note | Long notes, Low whole notes |
| Grand staff, hands taking turns, rests | Turn-taking, Echo |
| 3/4 time, dotted half note | Waltz in C, Three-beat steps |
| Hands together, left hand holding | Hold and play, Ode to Joy with a held bass |
| G and F chords, chord symbols | Three chords, Change on the beat |
| Melody plus chords | Hold and sing, Lightly Row with chords, Ode to Joy with chords |

The optional "More songs" unit after Unit 3 keeps Close moves, Minor colour, When the Saints Go Marching In and
Four-chord walk (skills: first inversions, A minor, the four-chord progression).

New original passages, one per concept that had none: Five fingers (hand shape), Every F, every B (white key names),
Steps on the staff, Soft, then loud, C chord held then on the beat, and Sharp phrase (sharps and flats).
