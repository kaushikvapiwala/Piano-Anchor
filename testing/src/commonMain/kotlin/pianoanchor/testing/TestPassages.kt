package pianoanchor.testing

import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Passage
import pianoanchor.core.model.ScoreNote
import pianoanchor.core.model.Step
import pianoanchor.core.model.TICKS_PER_QUARTER
import pianoanchor.core.model.TimeSignature

/** The written tempo of every [passageOf] passage, in quarter notes per minute. */
const val TEST_TEMPO_BPM = 60

/**
 * A test passage in 4/4 at [TEST_TEMPO_BPM]: each of [steps] is the pitches of one step, played as quarter notes
 * one beat apart from tick 0. For tests whose code reads pitches only.
 */
fun passageOf(
    vararg steps: Set<MidiNote>,
    id: String = "test:passage",
    title: String = "Test passage",
): Passage =
    Passage(
        id = id,
        title = title,
        steps =
            steps.mapIndexed { i, pitches ->
                Step(i * TICKS_PER_QUARTER, pitches.mapTo(LinkedHashSet()) { ScoreNote(it, TICKS_PER_QUARTER) })
            },
        timeSignature = TimeSignature(beats = 4, beatUnit = 4),
        tempoBpm = TEST_TEMPO_BPM,
    )
