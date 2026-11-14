package pianoanchor.engine.grading

import pianoanchor.core.model.Passage
import pianoanchor.core.model.ScoreNoteRef
import pianoanchor.core.model.TICKS_PER_QUARTER

private const val QUARTERS_IN_WHOLE = 4
private const val QUARTERS_IN_HALF = 2

/** The written note values the Results tiles name. */
public enum class NoteValue { WHOLE, HALF, QUARTER }

/** The written length of [ref]'s note in ticks. */
internal fun lengthTicks(
    passage: Passage,
    ref: ScoreNoteRef,
): Int =
    passage.steps[ref.stepIndex]
        .notes
        .first { it.pitch == ref.pitch }
        .lengthTicks

/** The 1-based place of [ref] among [passage]'s score notes, in score order (step, then pitch). */
public fun noteNumber(
    passage: Passage,
    ref: ScoreNoteRef,
): Int =
    passage.steps.take(ref.stepIndex).sumOf { it.notes.size } +
        passage.steps[ref.stepIndex].notes.count { it.pitch.number < ref.pitch.number } + 1

/** The written value of [ref]'s note when it is a whole, half or quarter note, else null. */
public fun noteValue(
    passage: Passage,
    ref: ScoreNoteRef,
): NoteValue? =
    when (lengthTicks(passage, ref)) {
        QUARTERS_IN_WHOLE * TICKS_PER_QUARTER -> NoteValue.WHOLE
        QUARTERS_IN_HALF * TICKS_PER_QUARTER -> NoteValue.HALF
        TICKS_PER_QUARTER -> NoteValue.QUARTER
        else -> null
    }
