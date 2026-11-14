package pianoanchor.feature.practice

import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Passage
import pianoanchor.core.model.TICKS_PER_QUARTER

/** Width of the treble clef at the staff's left edge, in dp. */
internal const val CLEF_AREA_DP = 64f

/** Width of the time signature after the clef, in dp. */
internal const val TIME_SIGNATURE_AREA_DP = 32f

/** Space between the time signature and the first note's centre, in dp (room for a sharp before it). */
private const val LEAD_DP = 28f

/** Space kept after the final double bar, in dp. */
private const val END_MARGIN_DP = 24f

/** The smallest gap between two steps, in dp: 19 dp noteheads stay apart (sharps and ledger lines may touch). */
private const val MIN_GAP_DP = 28f

/** The most a bar line sits before the note that starts its bar, in dp: clear of its sharp when gaps allow. */
private const val BAR_CLEARANCE_DP = 30f

private const val QUARTERS_PER_WHOLE = 4

/** Notes this long or longer (a half note) are drawn hollow. */
private const val HOLLOW_MIN_TICKS = 2 * TICKS_PER_QUARTER

/**
 * Where the staff draws a passage, in dp from its left edge: each step's [noteX], the inner [barLineX], the
 * [finalBarX] double bar, and per step the pitches drawn [hollow] (written length at least a half note).
 */
internal data class StaffLayout(
    val noteX: List<Float>,
    val barLineX: List<Float>,
    val finalBarX: Float,
    val hollow: List<Set<MidiNote>>,
)

/**
 * Lays [passage] out on a staff [widthDp] wide. x is linear in ticks from just after the time signature to the
 * final bar at the last note's end, except that every stretch between two steps (and the last one, to the final bar)
 * is at least [MIN_GAP_DP]; the other stretches share what is left in proportion to their ticks. If even the
 * minimum gaps don't fit, every stretch gets the same width (long passages wait for scrolling).
 *
 * A bar line inside a note or rest sits at its tick; one where a step starts sits before that step, by
 * [BAR_CLEARANCE_DP] or half the gap to the step (or time signature) before, whichever is less. A staff too narrow
 * for even the time signature stacks every step at the origin rather than drawing right to left.
 */
internal fun staffLayout(
    passage: Passage,
    widthDp: Float,
): StaffLayout {
    val steps = passage.steps
    val endTicks = steps.maxOf { step -> step.onsetTicks + step.notes.maxOf { it.lengthTicks } }
    val origin = CLEF_AREA_DP + TIME_SIGNATURE_AREA_DP + LEAD_DP
    val ticks = (listOf(0) + steps.map { it.onsetTicks } + endTicks).distinct()
    val available = (widthDp - END_MARGIN_DP - origin).coerceAtLeast(0f)
    val widths = stretchWidths(ticks.zipWithNext { a, b -> b - a }, available)
    val breakX = widths.runningFold(origin) { x, width -> x + width }

    fun x(tick: Int): Float {
        val i = (ticks.indexOfLast { it <= tick }).coerceAtMost(widths.lastIndex)
        return breakX[i] + widths[i] * (tick - ticks[i]) / (ticks[i + 1] - ticks[i])
    }
    val noteX = steps.map { x(it.onsetTicks) }
    val barTicks = passage.timeSignature.let { it.beats * QUARTERS_PER_WHOLE * TICKS_PER_QUARTER / it.beatUnit }
    val barLineX =
        (barTicks until endTicks step barTicks).map { tick ->
            val starting = steps.indexOfFirst { it.onsetTicks == tick }
            if (starting < 0) {
                x(tick)
            } else {
                val before = if (starting == 0) origin - LEAD_DP else noteX[starting - 1]
                noteX[starting] - minOf(BAR_CLEARANCE_DP, (noteX[starting] - before) / 2)
            }
        }
    return StaffLayout(
        noteX = noteX,
        barLineX = barLineX,
        finalBarX = x(endTicks),
        hollow =
            steps.map { step ->
                step.notes.filter { it.lengthTicks >= HOLLOW_MIN_TICKS }.mapTo(LinkedHashSet()) { it.pitch }
            },
    )
}

/**
 * Widths of stretches [lengths] ticks long that fill [available] dp: proportional to ticks, each at least
 * [MIN_GAP_DP]. The shortest stretches are held at the minimum, one at a time, until the rest are wide enough.
 */
private fun stretchWidths(
    lengths: List<Int>,
    available: Float,
): List<Float> {
    if (lengths.size * MIN_GAP_DP >= available) return lengths.map { available / lengths.size }
    val sorted = lengths.sorted()
    var held = 0
    var rest = sorted.sum()
    var scale = available / rest
    while (sorted[held] * scale < MIN_GAP_DP) {
        rest -= sorted[held]
        held++
        scale = (available - held * MIN_GAP_DP) / rest
    }
    return lengths.map { maxOf(it * scale, MIN_GAP_DP) }
}
