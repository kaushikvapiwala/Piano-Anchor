package pianoanchor.engine.matcher

import pianoanchor.core.model.MAX_TEMPO_BPM
import pianoanchor.core.model.MIN_TEMPO_BPM
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.NoteOn
import pianoanchor.core.model.Passage
import pianoanchor.core.model.ScoreNoteRef
import pianoanchor.core.model.TICKS_PER_QUARTER
import pianoanchor.engine.analysis.timingToleranceNanos
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val NANOS_PER_MINUTE = 60_000_000_000L
private const val NANOS_PER_MILLI = 1_000_000.0
private const val PERCENT = 100
private const val PERCENT_60 = 60
private const val PERCENT_70 = 70
private const val PERCENT_85 = 85

/** The length of one beat (a quarter note) at [bpm] quarter notes per minute, in nanoseconds. */
public fun beatNanos(bpm: Int): Long {
    require(bpm in MIN_TEMPO_BPM..MAX_TEMPO_BPM) { "tempo must be in $MIN_TEMPO_BPM..$MAX_TEMPO_BPM BPM, was $bpm" }
    return NANOS_PER_MINUTE / bpm
}

/**
 * The rungs of the tempo ladder: [WAIT] (no clock) and timed runs at [percent] of the passage's written tempo.
 */
public enum class TempoRung(
    public val percent: Int?,
) {
    WAIT(null),
    P60(PERCENT_60),
    P70(PERCENT_70),
    P85(PERCENT_85),
    P100(PERCENT),
    ;

    /**
     * The rung's tempo for [passage]: written tempo x percent / 100 rounded half up and clamped to
     * [MIN_TEMPO_BPM]..[MAX_TEMPO_BPM]; null for [WAIT].
     */
    public fun bpm(passage: Passage): Int? =
        percent?.let { ((passage.tempoBpm * it + PERCENT / 2) / PERCENT).coerceIn(MIN_TEMPO_BPM, MAX_TEMPO_BPM) }
}

/** What the live judge says about one NoteOn. */
public sealed interface LiveVerdict {
    /** The note is the score note [at], on time. */
    public data class Correct(
        val at: ScoreNoteRef,
    ) : LiveVerdict

    /** The note is the score note [at] but [deviationMs] off its time (+ late, - early). */
    public data class TimingOff(
        val at: ScoreNoteRef,
        val deviationMs: Int,
    ) : LiveVerdict

    /** No unjudged score note of that pitch is near this moment. */
    public data class Wrong(
        val pitch: MidiNote,
    ) : LiveVerdict
}

/**
 * Live, approximate per-note judge for a timed run; the analyser gives the verdict afterwards
 * (`docs/algorithms/tempo-judge.md`). Tick 0 of [passage] sounds at [startNanos], tempo [bpm].
 */
public class TempoJudge(
    private val passage: Passage,
    bpm: Int,
    startNanos: Long,
) {
    private val nanosPerTick = beatNanos(bpm).toDouble() / TICKS_PER_QUARTER
    private val tolerance = timingToleranceNanos(beatNanos(bpm).toDouble())
    private val onsets = passage.steps.map { startNanos + (it.onsetTicks * nanosPerTick).roundToLong() }
    private val judged = HashSet<ScoreNoteRef>()

    /** One beat after the last note's written end: when the run is over. */
    public val endNanos: Long =
        startNanos +
            ((writtenEndTicks(passage) + TICKS_PER_QUARTER) * nanosPerTick).roundToLong()

    /**
     * Judges [event] against the nearest unjudged score note of its pitch whose written onset is within half the gap to
     * the step before and after it (the first and last step use their one neighbour; a lone step one beat).
     */
    public fun onNoteOn(event: NoteOn): LiveVerdict {
        val t = event.timestampNanos
        val match =
            passage.steps.indices
                .filter { s ->
                    event.note in passage.steps[s].pitches &&
                        ScoreNoteRef(s, event.note) !in judged &&
                        t in window(s)
                }.minByOrNull { abs(t - onsets[it]) }
                ?: return LiveVerdict.Wrong(event.note)
        val ref = ScoreNoteRef(match, event.note)
        judged += ref
        val deviation = t - onsets[match]
        return if (abs(deviation) <= tolerance) {
            LiveVerdict.Correct(ref)
        } else {
            LiveVerdict.TimingOff(ref, (deviation / NANOS_PER_MILLI).roundToInt())
        }
    }

    /** The step the cursor is on at [nowNanos]: the last step whose written onset is <= now (clamped to the steps). */
    public fun cursorStep(nowNanos: Long): Int = onsets.indexOfLast { it <= nowNanos }.coerceAtLeast(0)

    private fun window(step: Int): LongRange {
        val before = onsets.getOrNull(step - 1)?.let { onsets[step] - it }
        val after = onsets.getOrNull(step + 1)?.let { it - onsets[step] }
        val lone = (nanosPerTick * TICKS_PER_QUARTER).roundToLong()
        val low = before ?: after ?: lone
        val high = after ?: before ?: lone
        return onsets[step] - low / 2..onsets[step] + high / 2
    }
}

/** The tick where the last written note ends. */
private fun writtenEndTicks(passage: Passage): Int =
    passage.steps
        .flatMap { step -> step.notes.map { step.onsetTicks + it.lengthTicks } }
        .fold(0) { latest, end -> max(latest, end) }
