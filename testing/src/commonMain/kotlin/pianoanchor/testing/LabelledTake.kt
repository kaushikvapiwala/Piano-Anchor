package pianoanchor.testing

import pianoanchor.core.model.Finding
import pianoanchor.core.model.MAX_TEMPO_BPM
import pianoanchor.core.model.MIN_TEMPO_BPM
import pianoanchor.core.model.Passage
import pianoanchor.core.model.PlayedNote
import pianoanchor.core.model.ScoreNote
import pianoanchor.core.model.TICKS_PER_QUARTER
import kotlin.math.roundToLong

/** Where a generated take's first written onset lies, in nanoseconds: far enough from 0 that jitter stays positive. */
const val TAKE_START_NANOS: Long = 1_000_000_000

private const val NANOS_PER_MINUTE = 60_000_000_000.0

/**
 * A take of [passage] at [tempoBpm] (quarter notes per minute) and the errors injected into it ([labels], empty for
 * a clean take). Its written times start at [TAKE_START_NANOS].
 */
data class LabelledTake(
    val passage: Passage,
    val tempoBpm: Int,
    val notes: List<PlayedNote>,
    val labels: List<Finding>,
) {
    init {
        require(tempoBpm in MIN_TEMPO_BPM..MAX_TEMPO_BPM) {
            "take tempo must be in $MIN_TEMPO_BPM..$MAX_TEMPO_BPM BPM, was $tempoBpm"
        }
    }

    /** One quarter note at [tempoBpm], in nanoseconds. */
    val beatNanos: Double get() = NANOS_PER_MINUTE / tempoBpm

    /** When step [stepIndex] is written to start at [tempoBpm]. */
    fun writtenOnsetNanos(stepIndex: Int): Long = TAKE_START_NANOS + ticksToNanos(passage.steps[stepIndex].onsetTicks)

    /** How long [note] is written to last at [tempoBpm]. */
    fun writtenLengthNanos(note: ScoreNote): Long = ticksToNanos(note.lengthTicks)

    private fun ticksToNanos(ticks: Int): Long = (ticks * beatNanos / TICKS_PER_QUARTER).roundToLong()
}
