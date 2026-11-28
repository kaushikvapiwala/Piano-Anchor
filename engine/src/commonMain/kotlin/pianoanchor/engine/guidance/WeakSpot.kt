package pianoanchor.engine.guidance

import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.BarRange
import pianoanchor.core.model.Finding
import pianoanchor.core.model.Passage
import pianoanchor.core.model.RunMode
import pianoanchor.core.model.StrictnessProfile
import pianoanchor.engine.analysis.PerformanceAnalyser
import pianoanchor.engine.analysis.PerformanceAnalysis
import pianoanchor.engine.analysis.TempoReference
import pianoanchor.engine.calibration.LatencyCalibration
import pianoanchor.engine.sight.SightReadingGenerator

/** The worst spot of a run, for the Results spot loop and the session recap's tip. Pure. */
public object WeakSpot {
    /** The analysis of a timed run, or of a wait-mode run with played notes; null for older wait-mode runs. */
    public fun analyse(
        passage: Passage,
        attempt: Attempt,
        strictness: StrictnessProfile,
    ): PerformanceAnalysis? {
        if (attempt.tempoBpm == null && attempt.playedNotes.isEmpty()) return null
        val tempo = attempt.tempoBpm?.let { TempoReference.Target(it, startNanos = 0) } ?: TempoReference.Untimed
        val notes = LatencyCalibration.corrected(attempt.playedNotes, attempt.latencyOffsetNanos)
        return PerformanceAnalyser.analyse(passage, notes, tempo, strictness)
    }

    /**
     * The bars to loop for [attempt] given its analysis [findings] (`SpotFinder.worst`): none for a perform take, a
     * CLEAN run or a sight-reading exercise.
     */
    public fun find(
        passage: Passage,
        attempt: Attempt,
        findings: List<Finding>,
    ): BarRange? {
        val offered =
            attempt.mode == RunMode.PRACTICE && attempt.band != Band.CLEAN &&
                SightReadingGenerator.parse(attempt.passageId) == null
        return if (offered) SpotFinder.worst(passage, attempt, findings) else null
    }

    /** The worst bars of [attempt] under [strictness], or null where [find] offers none. */
    public fun of(
        passage: Passage,
        attempt: Attempt,
        strictness: StrictnessProfile,
    ): BarRange? = find(passage, attempt, analyse(passage, attempt, strictness)?.findings.orEmpty())
}
