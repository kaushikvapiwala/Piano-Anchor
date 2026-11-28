# Play-along

The play-along lets the learner play today's song over a soft chord backing and the click, so practice feels like
music. It is the session's step after Practise, and the "Chords + click" chip of the Practice screen turns the same
backing on in any timed run of a piece that has a chord line. Nothing here changes grading.

## Chord lines
A passage may carry a chord line (`Passage.chords`): a sorted list of `ChordMark`s, each a root (pitch class), a quality
(major, minor or dominant seventh) and an onset in ticks. A chord sounds until the next one or the end of the passage.
The lines are our own simple harmony, mostly I, IV and V, written next to the melody and checked bar by bar so the
chord fits the melody's notes on beats 1 and 3. Content writes them as text, `"C | F G | G7"`: bars between `|`, the
chords of a bar between spaces, sharing the bar evenly. A line may not give a chord a part of a beat, so every chord
starts on a beat (a content test checks that, and that each starts before the passage ends).

Pieces of Unit 1 and the More songs unit all have a line. So do the course-2 pieces that already ask the left hand
for chords (Lightly Row and Ode to Joy with chords, Jingle Bells, Go Tell Aunt Rhody): their lines are those left-hand
chords, so the backing and the learner's left hand agree. Ode to Joy with a held bass has only bass notes, no chords, and
gets no line. The Four-chord walk keeps its own four chords (C, G, A minor, F) instead of I, IV and V.

## The backing
`Backing.tones` turns a chord line into `Tone`s at a tempo: offsets count from the first beat of bar 1. Each chord is a
root-position triad, with a minor seventh added for a dominant seventh, its root placed in the octave starting at
`BACKING_LOWEST_ROOT` so it stays under a right-hand melody, at the soft `BACKING_VELOCITY`. It lasts to the next chord
or to the end of the passage (the end of its last note). A spot loop's slice has no chord line: the backing belongs to
whole passages.

## In a run
Backing only plays in a timed run (wait mode has no clock). When the run arms and counts in, the ViewModel reads the
clock once: the click starts a short lead later, with one bar of count-in, and the chords are started to land on bar 1
beat 1, the same moment the judge's tick 0 falls. The chords go to the speaker (`TonePort`), never through the piano
input, so the matcher and the grader see exactly what they saw before.

The backing is tied to the run, not to the screen. A wrong or missed note restarts the run from the top: the chords are
cancelled with it and started again with the new count-in. The end of the run, leaving the screen, and turning the chip
off all cancel them. Turning the chip on in mid-run waits for the next count-in, since chords can only start on the
first beat.

## In the session
A song whose passage has a chord line adds a play-along step right after its Practise step
(`docs/algorithms/session-composer.md`). It takes `PLAY_ALONG_MINUTES` out of the song's share, never more than half of
it, so the session's total (and Today's headline) stays the same. It opens Practice on the song's current timed rung,
or on the slowest timed rung when the song has no clean timed run yet, with the backing on. Normal practice opens with
the backing off; the chip is offered whenever the passage has chords. The song's two steps are one card: Today's
"Choose steps" switches them together, and each is finished and remembered on its own.
