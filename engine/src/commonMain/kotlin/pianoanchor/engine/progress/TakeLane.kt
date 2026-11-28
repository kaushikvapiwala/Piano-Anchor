package pianoanchor.engine.progress

import pianoanchor.core.model.Attempt
import pianoanchor.core.model.PLAYED_NOTE_ORDER
import pianoanchor.core.model.Passage
import pianoanchor.core.model.Strictness
import pianoanchor.core.model.StrictnessProfile
import pianoanchor.engine.analysis.PerformanceAnalyser
import pianoanchor.engine.analysis.TempoReference
import pianoanchor.engine.analysis.timingToleranceNanos
import pianoanchor.engine.calibration.LatencyCalibration
import pianoanchor.engine.profile
import kotlin.math.abs

private const val NANOS_PER_MS = 1_000_000L
private const val NANOS_PER_MINUTE = 60_000_000_000.0

/** One played note of a take on the timing lane: [offsetMs] after the first note and whether it was [onBeat]. */
public data class LaneDot(
    val offsetMs: Long,
    val onBeat: Boolean,
)

/** The timing lane of a take in "Day 1 vs today" (`docs/algorithms/take-replay.md`). Pure. */
public object TakeLane {
    /**
     * A dot per played note of [attempt] (of [passage]), in time order. A note of a timed run is off the beat when it
     * starts further from where the tempo puts its score note than the early/late band of [strictness] (as the Results
     * timing tile judges it); every note of a wait-mode run, which has no beat, and every note that matches no score
     * note is on the beat. Empty for a take with no notes.
     */
    public fun dots(
        passage: Passage,
        attempt: Attempt,
        strictness: StrictnessProfile = Strictness.STANDARD.profile(),
    ): List<LaneDot> {
        val notes = LatencyCalibration.corrected(attempt.playedNotes, attempt.latencyOffsetNanos)
        val sorted = notes.sortedWith(PLAYED_NOTE_ORDER)
        val origin = sorted.firstOrNull()?.downNanos ?: return emptyList()
        val bpm = attempt.tempoBpm
        val tolerance = bpm?.let { timingToleranceNanos(NANOS_PER_MINUTE / it, strictness) }
        val tempo = bpm?.let { TempoReference.Target(it, startNanos = 0) } ?: TempoReference.Untimed
        val deviations =
            PerformanceAnalyser
                .analyse(passage, notes, tempo, strictness)
                .matches
                .mapNotNull { match -> match.played?.let { it to match.onsetDeviationNanos } }
                .toMap()
        return sorted.map { note ->
            val deviation = deviations[note]
            LaneDot(
                offsetMs = (note.downNanos - origin) / NANOS_PER_MS,
                onBeat = tolerance == null || deviation == null || abs(deviation) <= tolerance,
            )
        }
    }
}
