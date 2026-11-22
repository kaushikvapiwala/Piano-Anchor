package pianoanchor.engine.guidance

import pianoanchor.core.model.Attempt
import pianoanchor.core.model.BarRange
import pianoanchor.core.model.ChordIncomplete
import pianoanchor.core.model.Finding
import pianoanchor.core.model.Passage
import pianoanchor.core.model.WrongNote
import pianoanchor.engine.Tunables

/** Which bars of a run to loop (`docs/algorithms/guidance.md`, spot loops). */
public object SpotFinder {
    /**
     * The window of [Tunables.SPOT_LOOP_BARS] consecutive bars with the most findings in [attempt], or null when it
     * has none. Ties go to the earliest window. A passage shorter than the window gives its whole range. Findings of
     * a wait-mode run are its `WrongNote` and `ChordIncomplete` events, each counted at its step's bar; of a timed
     * run, [timedFindings] (the performance analyser's, from the run's own analysis), each counted at its score
     * note's bar. [timedFindings] must come from analysing this [attempt] against this [passage] (latency corrected,
     * with the run's strictness), as Results does; they are ignored for a wait-mode run.
     */
    public fun worst(
        passage: Passage,
        attempt: Attempt,
        timedFindings: List<Finding>,
    ): BarRange? {
        val steps =
            if (attempt.tempoBpm == null) {
                attempt.events.filter { it is WrongNote || it is ChordIncomplete }.map { it.stepIndex }
            } else {
                timedFindings.map { it.at.stepIndex }
            }
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
}
