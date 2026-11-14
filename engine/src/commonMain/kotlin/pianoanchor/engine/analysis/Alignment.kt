package pianoanchor.engine.analysis

import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.PLAYED_NOTE_ORDER
import pianoanchor.core.model.Passage
import pianoanchor.core.model.PlayedNote
import pianoanchor.core.model.ScoreNoteRef
import pianoanchor.core.model.Step
import pianoanchor.core.model.TICKS_PER_QUARTER
import kotlin.math.abs
import kotlin.math.min

// Alignment costs (`docs/algorithms/performance-analysis.md`): internal weights, not coach-facing tunables. Every
// pairing costs less than leaving both notes unpaired (MISSED_COST + EXTRA_COST), so a step pairs as many notes as it
// can.
private const val MISSED_COST = 1.0
private const val EXTRA_COST = 1.0
private const val OCTAVE_COST = 0.5
private const val SUBSTITUTION_COST = 1.0
private const val ONSET_COST_PER_BEAT = 0.5
private const val ONSET_COST_CAP = 0.5

/** A candidate must be cheaper by more than this to replace the best path found so far (float rounding). */
private const val COST_EPSILON = 1e-9

/** Semitone distances that count as the same note in another octave. */
private val OCTAVE_SHIFTS = setOf(12, 24)

/** True when [a] and [b] are one or two octaves apart. */
internal fun isOctave(
    a: MidiNote,
    b: MidiNote,
): Boolean = abs(a.number - b.number) in OCTAVE_SHIFTS

/** Score notes paired with played notes ([matched], in score order) and the played notes left unpaired ([extras]). */
internal data class Alignment(
    val matched: Map<ScoreNoteRef, PlayedNote>,
    val extras: List<PlayedNote>,
)

/** Where the time map is pinned: a step at [ticks] sounded at [nanos]. */
private class Anchor(
    val ticks: Int,
    val nanos: Double,
)

/** The best path to a DP cell: its [cost], the step's pairings (score note to played index) and where it came from. */
private class Cell(
    val cost: Double,
    val anchor: Anchor,
    val matched: Map<ScoreNoteRef, Int>,
    val previous: Cell?,
)

/** One step's pairing of a run of played notes: its [cost], pairings and the earliest paired onset. */
private class StepPairing(
    val cost: Double,
    val matched: Map<ScoreNoteRef, Int>,
    val onsetNanos: Long?,
)

/**
 * Aligns [played] notes (taken in [PLAYED_NOTE_ORDER]) to [passage] (`docs/algorithms/performance-analysis.md`).
 * A dynamic programme gives each step a contiguous run of the played notes, so a chord's notes may come in any
 * order. A step's onset cost compares its earliest paired onset with the last paired step at [nanosPerTick]; before
 * the first one, with [startNanos] at tick 0, or with the first played note at the first step when that is null.
 */
internal fun align(
    passage: Passage,
    played: List<PlayedNote>,
    nanosPerTick: Double,
    startNanos: Long?,
): Alignment {
    val notes = played.sortedWith(PLAYED_NOTE_ORDER)
    if (notes.isEmpty()) return Alignment(emptyMap(), emptyList())
    val steps = passage.steps
    val beatNanos = nanosPerTick * TICKS_PER_QUARTER
    val cells = Array(steps.size + 1) { arrayOfNulls<Cell>(notes.size + 1) }
    val start =
        startNanos?.let { Anchor(0, it.toDouble()) } ?: Anchor(steps[0].onsetTicks, notes[0].downNanos.toDouble())
    cells[0][0] = Cell(0.0, start, emptyMap(), null)
    for (s in steps.indices) {
        for (i in 0 until notes.size + 1) {
            val from = cells[s][i] ?: continue
            val expected = from.anchor.nanos + (steps[s].onsetTicks - from.anchor.ticks) * nanosPerTick
            for (k in i until notes.size + 1) {
                val pairing = pair(s, steps[s], notes.subList(i, k), i, expected, beatNanos)
                val anchor = pairing.onsetNanos?.let { Anchor(steps[s].onsetTicks, it.toDouble()) } ?: from.anchor
                val candidate = Cell(from.cost + pairing.cost, anchor, pairing.matched, from)
                val best = cells[s + 1][k]
                if (best == null || candidate.cost < best.cost - COST_EPSILON) cells[s + 1][k] = candidate
            }
        }
    }
    val path = generateSequence(cells[steps.size][notes.size]) { it.previous }
    val indices = path.flatMap { it.matched.entries }.sortedBy { it.key.stepIndex }.associate { it.key to it.value }
    val used = indices.values.toSet()
    return Alignment(
        matched = indices.mapValues { notes[it.value] },
        extras = notes.filterIndexed { index, _ -> index !in used },
    )
}

/**
 * Pairs [step]'s notes with the played notes in [run] (whose first note is at [offset] in the take): the lowest pitch
 * cost first, then the onset nearest [expectedNanos], then score order and onset order. The step's onset cost is
 * paid once, on its earliest paired onset.
 */
private fun pair(
    stepIndex: Int,
    step: Step,
    run: List<PlayedNote>,
    offset: Int,
    expectedNanos: Double,
    beatNanos: Double,
): StepPairing {
    val byCost = compareBy<Pair<MidiNote, Int>>({ (pitch, j) -> pitchCost(pitch, run[j].pitch) })
    val candidates =
        step.pitches
            .sortedBy { it.number }
            .flatMap { pitch -> run.indices.map { pitch to it } }
            .sortedWith(byCost.thenBy { (_, j) -> abs(run[j].downNanos - expectedNanos) })
    val matched = LinkedHashMap<ScoreNoteRef, Int>()
    var cost = 0.0
    candidates.forEach { (pitch, j) ->
        val ref = ScoreNoteRef(stepIndex, pitch)
        if (ref !in matched && offset + j !in matched.values) {
            matched[ref] = offset + j
            cost += pitchCost(pitch, run[j].pitch)
        }
    }
    val onset = matched.values.minOfOrNull { run[it - offset].downNanos }
    if (onset != null) cost += min(abs(onset - expectedNanos) / beatNanos * ONSET_COST_PER_BEAT, ONSET_COST_CAP)
    cost += (step.notes.size - matched.size) * MISSED_COST + (run.size - matched.size) * EXTRA_COST
    return StepPairing(cost, matched.entries.sortedBy { it.key.pitch.number }.associate { it.key to it.value }, onset)
}

private fun pitchCost(
    scorePitch: MidiNote,
    playedPitch: MidiNote,
): Double =
    when {
        playedPitch == scorePitch -> 0.0
        isOctave(playedPitch, scorePitch) -> OCTAVE_COST
        else -> SUBSTITUTION_COST
    }
