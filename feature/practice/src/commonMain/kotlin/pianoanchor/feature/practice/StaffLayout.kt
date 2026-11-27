package pianoanchor.feature.practice

import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Passage
import pianoanchor.core.model.TICKS_PER_QUARTER
import pianoanchor.engine.notation.Engraved
import pianoanchor.engine.notation.EngravedRest
import pianoanchor.engine.notation.Engraving
import pianoanchor.engine.notation.NoteValue
import pianoanchor.engine.notation.Staff
import pianoanchor.engine.notation.StemDirection

/** Width of the treble clef at the staff's left edge, in dp. */
internal const val CLEF_AREA_DP = 64f

/** Width of the time signature after the clef, in dp. */
internal const val TIME_SIGNATURE_AREA_DP = 32f

/** Width one sharp of a key signature takes after the clef, in dp (a sharp glyph is a little under one staff space). */
internal const val KEY_SHARP_WIDTH_DP = 14f

/**
 * One staff space, in dp: the gap between two staff lines. A Bravura em is four of these (the SMuFL rule), so the
 * font size of every music glyph is `4 * STAFF_SPACE_DP`.
 */
internal const val STAFF_SPACE_DP = 16f

/** Stem length in half staff spaces: 3.5 spaces from the outer notehead (standard engraving practice, SMuFL). */
private const val STEM_LENGTH_HALF_SPACES = 7

/** Notehead width in dp (the notehead is drawn 19 dp wide); the stem sits on its edge. */
private const val NOTEHEAD_WIDTH_DP = 19f

/** Stem thickness in dp: SMuFL's engraving default of 0.12 staff spaces, rounded up to stay visible. */
internal const val STEM_WIDTH_DP = 1.5f

/** Beam thickness in dp: SMuFL's engraving default of 0.5 staff spaces. */
internal const val BEAM_THICKNESS_DP = STAFF_SPACE_DP / 2

/** Distance from a notehead's centre to the centre of its augmentation dot, in dp. */
private const val DOT_OFFSET_DP = 17f

/** Treble key-signature sharps as staff positions (half spaces above E4): F5 C5 G5 D5 A4 E5 B4, the standard zigzag. */
private val TREBLE_SHARP_POSITIONS = listOf(8, 5, 9, 6, 3, 7, 4)

/** The bass clef's sharps sit this many half spaces (two octaves) below the treble clef's. */
private const val BASS_SHARP_SHIFT = 14

/** Height of the staff box with one staff: 64 dp of lines, a cursor overhang and room for a tag above a high head. */
internal const val SINGLE_STAFF_HEIGHT_DP = 196f

/**
 * Height of the staff box of a grand staff: 160 dp from the treble's top line to the bass's bottom line, and 40 dp
 * above and below it, enough for the cursor's overhang, a timing tag over an F5 head and the legend, which sits above
 * the top line.
 */
internal const val GRAND_STAFF_HEIGHT_DP = 240f

/** Space between the time signature and the first note's centre, in dp (room for a sharp before it). */
private const val LEAD_DP = 28f

/** Space kept after the final double bar, in dp. */
private const val END_MARGIN_DP = 24f

/** The smallest gap between two steps, in dp: 19 dp noteheads stay apart (sharps and ledger lines may touch). */
private const val MIN_GAP_DP = 28f

/** The most a bar line sits before the note that starts its bar, in dp: clear of its sharp when gaps allow. */
private const val BAR_CLEARANCE_DP = 30f

/** Chords of this value or longer (a half note) are drawn hollow, every head of them. */
private const val HOLLOW_MIN_TICKS = 2 * TICKS_PER_QUARTER

/**
 * Where the staff draws a passage, in dp from its left edge: each step's [noteX], the inner [barLineX], the
 * [finalBarX] double bar, per step the pitches drawn [hollow] (their chord's value is at least a half note), the width
 * [keyWidth] the key signature reserves after the clef, each rest's [restX] and the [chords] and [beams] to draw, in
 * the order of the [Engraved] they were laid out from. Vertical places are staff positions (half spaces above E4).
 */
internal data class StaffLayout(
    val noteX: List<Float>,
    val barLineX: List<Float>,
    val finalBarX: Float,
    val hollow: List<Set<MidiNote>>,
    val keyWidth: Float = 0f,
    val restX: List<Float> = emptyList(),
    val chords: List<ChordShape> = emptyList(),
    val beams: List<BeamShape> = emptyList(),
)

/** A stem at [x] from staff position [fromPosition] (the notehead it grows from) to [tipPosition]; [up] or down. */
internal data class StemShape(
    val x: Float,
    val fromPosition: Int,
    val tipPosition: Int,
    val up: Boolean,
)

/**
 * What a chord adds to its noteheads: its [stem] (null on a whole note), whether it has a [flag] at the stem's tip,
 * and its augmentation dots at [dotX], one at each of [dotPositions].
 */
internal data class ChordShape(
    val stem: StemShape?,
    val flag: Boolean,
    val dotX: Float,
    val dotPositions: List<Int>,
)

/** One horizontal beam of beam group [group], from [x1] to [x2] at staff position [position] (the stem tips). */
internal data class BeamShape(
    val group: Int,
    val x1: Float,
    val x2: Float,
    val position: Int,
    val up: Boolean,
)

/** The height of the staff box that fits [engraved]'s staves: taller when both staves are drawn. */
internal fun staffHeightDp(engraved: Engraved): Float =
    if (Staff.entries.all { staff -> engraved.chords.any { it.staff == staff } }) {
        GRAND_STAFF_HEIGHT_DP
    } else {
        SINGLE_STAFF_HEIGHT_DP
    }

/** The staff positions the key signature's sharps sit on, for [staff]: none for C and for flat keys (not drawn yet). */
internal fun keySignaturePositions(
    sharps: Int,
    staff: Staff,
): List<Int> {
    val shift = if (staff == Staff.BASS) BASS_SHARP_SHIFT else 0
    return TREBLE_SHARP_POSITIONS.take(sharps.coerceAtLeast(0)).map { it - shift }
}

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
    engraved: Engraved = Engraving.engrave(passage),
): StaffLayout {
    val steps = passage.steps
    val barTicks = passage.timeSignature.barTicks
    val noteEnd = steps.maxOf { step -> step.onsetTicks + step.notes.maxOf { it.lengthTicks } }
    val endTicks = maxOf(noteEnd, engraved.rests.maxOfOrNull { it.endTicks(barTicks) } ?: 0)
    val keyWidth = passage.keySignature.sharps.coerceAtLeast(0) * KEY_SHARP_WIDTH_DP
    val origin = CLEF_AREA_DP + keyWidth + TIME_SIGNATURE_AREA_DP + LEAD_DP
    val events = (steps.map { it.onsetTicks } + engraved.rests.map { it.onsetTicks }).distinct().sorted()
    val ticks = (listOf(0) + events + endTicks).distinct()
    val available = (widthDp - END_MARGIN_DP - origin).coerceAtLeast(0f)
    val widths = stretchWidths(ticks.zipWithNext { a, b -> b - a }, available)
    val breakX = widths.runningFold(origin) { x, width -> x + width }

    fun x(tick: Int): Float {
        val i = (ticks.indexOfLast { it <= tick }).coerceAtMost(widths.lastIndex)
        return breakX[i] + widths[i] * (tick - ticks[i]) / (ticks[i + 1] - ticks[i])
    }
    val noteX = steps.map { x(it.onsetTicks) }
    val eventX = events.map { x(it) }
    val barLineX =
        (barTicks until endTicks step barTicks).map { tick ->
            val starting = events.indexOf(tick)
            if (starting < 0) {
                x(tick)
            } else {
                val before = if (starting == 0) origin - LEAD_DP else eventX[starting - 1]
                eventX[starting] - minOf(BAR_CLEARANCE_DP, (eventX[starting] - before) / 2)
            }
        }
    val (chords, beams) = chordShapes(engraved, noteX)
    return StaffLayout(
        noteX = noteX,
        barLineX = barLineX,
        finalBarX = x(endTicks),
        hollow =
            steps.indices.map { i ->
                engraved.chords
                    .filter { it.stepIndex == i && it.value.ticks >= HOLLOW_MIN_TICKS }
                    .flatMapTo(LinkedHashSet()) { chord -> chord.notes.map { it.pitch } }
            },
        keyWidth = keyWidth,
        restX = engraved.rests.map { x(it.onsetTicks) },
        chords = chords,
        beams = beams,
    )
}

/** Where a rest's symbol ends: a whole rest fills its bar, whatever the metre. */
private fun EngravedRest.endTicks(barTicks: Int): Int =
    if (value == NoteValue.WHOLE) (onsetTicks / barTicks + 1) * barTicks else onsetTicks + value.ticks

/**
 * Stems, dots and beams for [engraved]'s chords, centred on [noteX] (one entry per step). A stem grows
 * [STEM_LENGTH_HALF_SPACES] half spaces from the notehead farthest along it; the stems of a beam group all end at the
 * farthest tip of the group, where its beam runs.
 */
private fun chordShapes(
    engraved: Engraved,
    noteX: List<Float>,
): Pair<List<ChordShape>, List<BeamShape>> {
    val positions = engraved.chords.map { chord -> chord.notes.map { it.pitch.staffPosition } }
    val tips =
        engraved.chords.mapIndexed { i, chord ->
            when (chord.stem) {
                StemDirection.UP -> positions[i].max() + STEM_LENGTH_HALF_SPACES
                StemDirection.DOWN -> positions[i].min() - STEM_LENGTH_HALF_SPACES
                StemDirection.NONE -> 0
            }
        }
    val members = HashMap<Int, MutableList<Int>>()
    engraved.chords.forEachIndexed { i, chord -> chord.beamGroup?.let { members.getOrPut(it) { ArrayList() } += i } }
    val groupTip =
        members.mapValues { (_, indices) ->
            if (engraved.chords[indices.first()].stem == StemDirection.UP) {
                indices.maxOf { tips[it] }
            } else {
                indices.minOf { tips[it] }
            }
        }
    val shapes =
        engraved.chords.mapIndexed { i, chord ->
            val x = noteX[chord.stepIndex]
            val up = chord.stem == StemDirection.UP
            val stem =
                if (chord.stem == StemDirection.NONE) {
                    null
                } else {
                    val offset = (NOTEHEAD_WIDTH_DP - STEM_WIDTH_DP) / 2
                    StemShape(
                        x = if (up) x + offset else x - offset,
                        fromPosition = if (up) positions[i].min() else positions[i].max(),
                        tipPosition = chord.beamGroup?.let { groupTip.getValue(it) } ?: tips[i],
                        up = up,
                    )
                }
            ChordShape(
                stem = stem,
                flag = chord.flag,
                dotX = x + DOT_OFFSET_DP,
                dotPositions = if (chord.value.dotted) positions[i].map { dotPosition(it) }.distinct() else emptyList(),
            )
        }
    val beams =
        members.entries.sortedBy { it.key }.map { (group, indices) ->
            val stems = indices.mapNotNull { shapes[it].stem }
            BeamShape(group, stems.minOf { it.x }, stems.maxOf { it.x }, groupTip.getValue(group), stems.first().up)
        }
    return shapes to beams
}

/** A dot sits in the space above when its notehead is on a line (an even position), else beside the head. */
private fun dotPosition(position: Int): Int = if (position % 2 == 0) position + 1 else position

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
