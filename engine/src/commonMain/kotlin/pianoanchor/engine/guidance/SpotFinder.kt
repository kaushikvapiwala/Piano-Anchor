package pianoanchor.engine.guidance

import pianoanchor.core.model.Attempt
import pianoanchor.core.model.BarRange
import pianoanchor.core.model.ChordIncomplete
import pianoanchor.core.model.Passage
import pianoanchor.core.model.WrongNote
import pianoanchor.engine.Tunables
import pianoanchor.engine.analysis.PerformanceAnalyser
import pianoanchor.engine.analysis.TempoReference
import pianoanchor.engine.calibration.LatencyCalibration

/** Which bars of a run to loop (`docs/algorithms/guidance.md`, spot loops). */
public object SpotFinder {
    /**
     * The window of [Tunables.SPOT_LOOP_BARS] consecutive bars with the most findings in [attempt], or null when it
     * has none. Ties go to the earliest window. A passage shorter than the window gives its whole range. Findings of
     * a wait-mode run are its `WrongNote` and `ChordIncomplete` events, each counted at its step's bar; of a timed
     * run, the performance analyser's findings, each counted at its score note's bar.
     */
    public fun worst(
        passage: Passage,
        attempt: Attempt,
    ): BarRange? {
        val steps = findingSteps(passage, attempt)
        val perBar = IntArray(passage.barCount() + 1)
        steps.forEach { perBar[passage.barOf(it)]++ }
        val length = minOf(Tunables.SPOT_LOOP_BARS, passage.barCount())
        val windows =
            (1..passage.barCount() - length + 1).map { first ->
                first to
                    (first until first + length).sumOf { perBar[it] }
            }
        // maxBy keeps the first of equal windows: ties go to the earliest.
        val best = windows.maxBy { it.second }
        return if (best.second == 0) null else BarRange(best.first, best.first + length - 1)
    }

    /** The step index of every finding of [attempt]. */
    private fun findingSteps(
        passage: Passage,
        attempt: Attempt,
    ): List<Int> {
        val bpm =
            attempt.tempoBpm
                ?: return attempt.events.filter { it is WrongNote || it is ChordIncomplete }.map { it.stepIndex }
        val notes = LatencyCalibration.corrected(attempt.playedNotes, attempt.latencyOffsetNanos)
        val tempo = TempoReference.Target(bpm, startNanos = 0)
        return PerformanceAnalyser.analyse(passage, notes, tempo).findings.map { it.at.stepIndex }
    }
}
