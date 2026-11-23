package pianoanchor.engine.grading

import pianoanchor.core.model.Band
import pianoanchor.core.model.ErrorKind
import pianoanchor.engine.Tunables
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MILLIS_PER_MINUTE = 60_000.0
private const val HALVES = 2.0

/** What Results say about a run; the screen turns each into a sentence (`docs/algorithms/grading.md`). */
public enum class Observation {
    STARTED_LATE,
    STARTED_EARLY,
    WRONG_NOTES,
    NOTES_SHORT,
    NOTES_LONG,
    OFF_BEAT,
    PAUSED,
    CLEAN,
}

/** The one piece of advice shown with the first observation. */
public enum class Tip {
    WAIT_FOR_COUNT_IN,
    SLOW_DOWN,
    HOLD_FULL_CLICKS,
    LET_GO_SOONER,
    LISTEN_TO_CLICK,
    PRACTISE_THE_PAUSE,
    NEXT_TEMPO,
}

/** The first one or two [observations] in priority order, and the [tip] for the first. */
public data class ResultsWords(
    val observations: List<Observation>,
    val tip: Tip,
)

/** Picks what Results say about a run. */
public object ResultsWordsRule {
    private const val MAX_OBSERVATIONS = 2

    /** What Results say about [summary] of a run at [tempoBpm] (null = wait mode) and [band]. */
    public fun of(
        summary: ResultsSummary,
        tempoBpm: Int?,
        band: Band,
    ): ResultsWords {
        val found = if (band == Band.CLEAN) emptyList() else observations(summary, tempoBpm)
        val chosen = found.take(MAX_OBSERVATIONS).ifEmpty { listOf(Observation.CLEAN) }
        return ResultsWords(chosen, tipFor(chosen.first()))
    }

    /** The beats a run's median deviation of [medianMs] is off at [tempoBpm], rounded to the nearest half beat. */
    public fun shiftBeats(
        medianMs: Int,
        tempoBpm: Int,
    ): Double = (abs(medianMs) / beatMs(tempoBpm) * HALVES).roundToInt() / HALVES

    private fun beatMs(tempoBpm: Int): Double = MILLIS_PER_MINUTE / tempoBpm

    private fun observations(
        summary: ResultsSummary,
        tempoBpm: Int?,
    ): List<Observation> {
        val timing = if (tempoBpm == null) null else summary.timing
        val shift = timing?.let { shift(it, tempoBpm!!) }
        return listOfNotNull(
            shift,
            Observation.WRONG_NOTES.takeIf { summary.wrongNotes + summary.missedNotes + summary.incompleteChords > 0 },
            lengthObservation(if (tempoBpm == null) null else summary.length),
            Observation.OFF_BEAT.takeIf { timing?.offBeat == true && shift == null },
            Observation.PAUSED.takeIf { (summary.hesitation?.count ?: 0) > 0 },
        )
    }

    private fun shift(
        timing: TimingSummary,
        tempoBpm: Int,
    ): Observation? {
        val median = timing.medianSignedDeviationMs
        if (abs(median) < Tunables.RESULTS_SHIFT_BEATS * beatMs(tempoBpm)) return null
        return if (median > 0) Observation.STARTED_LATE else Observation.STARTED_EARLY
    }

    private fun lengthObservation(length: LengthSummary?): Observation? =
        when {
            length == null || length.heldRightPercent >= Tunables.RESULTS_HELD_RIGHT_MIN_PERCENT -> null
            length.worst?.kind == ErrorKind.TOO_LONG -> Observation.NOTES_LONG
            else -> Observation.NOTES_SHORT
        }

    private fun tipFor(first: Observation): Tip =
        when (first) {
            Observation.STARTED_LATE, Observation.STARTED_EARLY -> Tip.WAIT_FOR_COUNT_IN
            Observation.WRONG_NOTES -> Tip.SLOW_DOWN
            Observation.NOTES_SHORT -> Tip.HOLD_FULL_CLICKS
            Observation.NOTES_LONG -> Tip.LET_GO_SOONER
            Observation.OFF_BEAT -> Tip.LISTEN_TO_CLICK
            Observation.PAUSED -> Tip.PRACTISE_THE_PAUSE
            Observation.CLEAN -> Tip.NEXT_TEMPO
        }
}
