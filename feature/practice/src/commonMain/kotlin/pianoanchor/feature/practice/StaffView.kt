package pianoanchor.feature.practice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import pianoanchor.core.model.KeySignature
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Passage
import pianoanchor.core.model.ScoreNoteRef
import pianoanchor.core.model.TimeSignature
import pianoanchor.engine.notation.Accidental
import pianoanchor.engine.notation.Engraved
import pianoanchor.engine.notation.Engraving
import pianoanchor.engine.notation.NoteValue
import pianoanchor.engine.notation.Staff
import pianoanchor.feature.practice.resources.Res
import pianoanchor.feature.practice.resources.practice_chord_join
import pianoanchor.feature.practice.resources.practice_legend_correct
import pianoanchor.feature.practice.resources.practice_legend_waiting
import pianoanchor.feature.practice.resources.practice_list_join
import pianoanchor.feature.practice.resources.practice_major_keys
import pianoanchor.feature.practice.resources.practice_staff_cursor
import pianoanchor.feature.practice.resources.practice_staff_done
import pianoanchor.feature.practice.resources.practice_staff_waiting
import pianoanchor.feature.practice.resources.practice_time_key
import pianoanchor.feature.practice.resources.practice_time_signature
import pianoanchor.ui.shell.PianoAnchorColors
import pianoanchor.ui.shell.displayFontFamily
import pianoanchor.ui.shell.notationFontFamily
import pianoanchor.ui.shell.pianoAnchorColors

private const val STAFF_LINES = 5
private val LINE_GAP = STAFF_SPACE_DP.dp
private val LINE_WIDTH = 1.5.dp
private val NOTEHEAD = 19.dp to 14.dp
private val HOLLOW_RING = 2.5.dp
private val THICK_BAR_WIDTH = 3.dp
private val DOUBLE_BAR_SPACE = 3.dp
private const val NOTEHEAD_ANGLE = -18f
private val LEDGER_HALF_WIDTH = 15.dp
private val CURSOR_WIDTH = 3.dp
private val CURSOR_HEIGHT = 110.dp
private val CURSOR_GAP = 22.dp
private const val CURSOR_ALPHA = 0.85f
private val ACCIDENTAL_GAP = 22.dp

/** The most sharps or flats a key signature has: the offset into the array of major keys, which starts at 7 flats. */
private const val MAX_ACCIDENTALS = 7
private val CLEF_LEFT = 6.dp

/** SMuFL code points of the Bravura glyphs the staff draws (https://w3c.github.io/smufl/latest/). */
private const val G_CLEF = "\uE050"
private const val F_CLEF = "\uE062"
private const val SHARP = "\uE262"
private const val NATURAL = "\uE261"
private const val FLAG_EIGHTH_UP = "\uE240"
private const val FLAG_EIGHTH_DOWN = "\uE241"
private const val REST_WHOLE = "\uE4E3"
private const val REST_HALF = "\uE4E4"
private const val REST_QUARTER = "\uE4E5"
private const val REST_EIGHTH = "\uE4E6"
private const val AUGMENTATION_DOT = "\uE1E7"

/** One Bravura em is four staff spaces (the SMuFL rule). */
private const val EM_SPACES = 4

/** Extra height of the cursor above and below the staff lines it spans, in dp. */
private val CURSOR_OVERHANG = 23.dp
private const val TAG_SP = 11
private val TAG_GAP = 20.dp

/** Staff positions (half spaces above E4) of each staff's bottom and top line; ledger lines start beyond them. */
private const val TREBLE_BOTTOM = 0
private const val TREBLE_TOP = 8
private const val BASS_BOTTOM = -12
private const val BASS_TOP = -4

/** The treble clef curls round the G line (position 2); the bass clef's dots sit either side of the F line (-6). */
private const val G_LINE = 2
private const val F_LINE = -6

/** Positions above a staff's bottom line: its middle line (rests centre on it) and the line a whole rest hangs from. */
private const val MIDDLE_ABOVE_BOTTOM = 4
private const val WHOLE_REST_ABOVE_BOTTOM = 6

/** Positions above a staff's bottom line the time signature's numbers centre on: the upper and lower halves. */
private const val TIME_TOP_ABOVE_BOTTOM = 6
private const val TIME_BOTTOM_ABOVE_BOTTOM = 2

/**
 * The staff of [passage] as [Engraving] writes it: the staves that carry notes (treble, bass or both as a grand
 * staff), each with its clef, key signature and time signature; one notehead (or stacked chord) per step spaced by
 * onset ([staffLayout]) and coloured by the matching [steps] entry's [StepStatus], hollow from a half note up, with
 * stems, flags, beams, augmentation dots, accidentals and rests; bar lines and a final double bar, a cursor left of
 * the waiting step and a legend. Every music glyph is Bravura. TalkBack reads one description: the time signature (and
 * key), the notes and the one being waited for.
 */
@Composable
internal fun StaffView(
    passage: Passage,
    steps: List<StepView>,
    modifier: Modifier = Modifier,
    liveMarks: Map<ScoreNoteRef, LiveMark> = emptyMap(),
    timed: Boolean = false,
    legend: Boolean = true,
) {
    val colors = MaterialTheme.pianoAnchorColors
    val measurer = rememberTextMeasurer()
    val engraved = remember(passage) { Engraving.engrave(passage) }
    val notation = notationFontFamily
    val time = passage.timeSignature
    val display = displayFontFamily
    val timeNumbers =
        remember(measurer, colors.upcomingNote, display, time) {
            val style = TextStyle(colors.upcomingNote, 26.sp, FontWeight.Bold, fontFamily = display)
            measurer.measure(time.beats.toString(), style) to measurer.measure(time.beatUnit.toString(), style)
        }
    val description = staffDescription(steps, time, passage.keySignature, timed)
    val tags = timingTags(liveMarks)
    val tagStyle = TextStyle(colors.timing, TAG_SP.sp, FontWeight.SemiBold)
    val primary = MaterialTheme.colorScheme.primary
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = description }) {
            val layout = staffLayout(passage, size.width.toDp().value, engraved)
            val painter = StaffPainter(this, colors, measurer, notation, engraved, layout)
            painter.drawStaves(passage.keySignature.sharps, timeNumbers)
            painter.drawRests()
            val placed = placedNotes(engraved)
            steps.forEachIndexed { i, step ->
                val x = layout.noteX[i].dp.toPx()
                step.notes.forEach { note ->
                    val ref = ScoreNoteRef(i, note)
                    val mark = liveMarks[ref]
                    val color = mark.color(step.status, colors)
                    val (staff, accidental) = placed.getValue(ref)
                    val hollow = note in layout.hollow[i] || mark is LiveMark.TimingOff
                    painter.drawNote(note, x, color, hollow, staff, accidental)
                    tags[ref]?.let {
                        val head = Offset(x, painter.y(note.staffPosition))
                        centered(measurer.measure(it, tagStyle), head - Offset(0f, TAG_GAP.toPx()))
                    }
                }
                if (step.status == StepStatus.WAITING) painter.drawCursor(x, primary)
            }
            painter.drawStemsAndBeams(steps, liveMarks)
        }
        if (legend) Legend(colors, Modifier.align(Alignment.TopEnd))
    }
}

/** The staff and accidental of every note of [engraved]. */
private fun placedNotes(engraved: Engraved): Map<ScoreNoteRef, Pair<Staff, Accidental?>> =
    engraved.chords
        .flatMap { c -> c.notes.map { ScoreNoteRef(c.stepIndex, it.pitch) to (c.staff to it.accidental) } }
        .toMap()

/**
 * Draws one [engraved] passage laid out by [layout] into [scope]: the staves that carry notes (treble, bass or both),
 * and the symbols on them. Vertical places are staff positions (half spaces above E4) turned into y by [y].
 */
private class StaffPainter(
    private val scope: DrawScope,
    private val colors: PianoAnchorColors,
    private val measurer: TextMeasurer,
    private val notation: FontFamily,
    private val engraved: Engraved,
    private val layout: StaffLayout,
) {
    private val gap = with(scope) { LINE_GAP.toPx() }
    private val staves = Staff.entries.filter { staff -> engraved.chords.any { it.staff == staff } }
    private val top = if (Staff.TREBLE in staves) TREBLE_TOP else BASS_TOP
    private val bottom = if (Staff.BASS in staves) BASS_BOTTOM else TREBLE_BOTTOM
    private val originY = scope.size.height / 2 + (top + bottom) * gap / 4

    fun y(position: Number): Float = originY - position.toFloat() * gap / 2

    private fun Staff.bottom() = if (this == Staff.TREBLE) TREBLE_BOTTOM else BASS_BOTTOM

    private fun px(dp: Float) = with(scope) { dp.dp.toPx() }

    private fun glyph(
        text: String,
        color: Color,
    ) = measurer.measure(text, TextStyle(color, with(scope) { (EM_SPACES * gap).toSp() }, fontFamily = notation))

    /** Draws a Bravura glyph with its origin (left edge, baseline) at [origin]. */
    private fun music(
        text: String,
        color: Color,
        origin: Offset,
    ) = glyph(text, color).let { scope.drawText(it, topLeft = Offset(origin.x, origin.y - it.firstBaseline)) }

    /** Draws a Bravura glyph horizontally centred on [x] with its baseline at [baselineY]. */
    private fun musicCentered(
        text: String,
        color: Color,
        x: Float,
        baselineY: Float,
    ) = glyph(text, color).let {
        scope.drawText(it, topLeft = Offset(x - it.size.width / 2f, baselineY - it.firstBaseline))
    }

    /** Lines, clef, key signature and time signature of each staff, then the bar lines. */
    fun drawStaves(
        sharps: Int,
        timeNumbers: Pair<TextLayoutResult, TextLayoutResult>,
    ) = with(scope) {
        val line = colors.staffLine
        staves.forEach { staff ->
            val base = staff.bottom()
            repeat(STAFF_LINES) { n ->
                drawLine(line, Offset(0f, y(base + n * 2)), Offset(size.width, y(base + n * 2)), LINE_WIDTH.toPx())
            }
            val (clef, clefLine) = if (staff == Staff.TREBLE) G_CLEF to G_LINE else F_CLEF to F_LINE
            music(clef, line, Offset(CLEF_LEFT.toPx(), y(clefLine)))
            keySignaturePositions(sharps, staff).forEachIndexed { i, position ->
                music(SHARP, line, Offset(px(CLEF_AREA_DP + i * KEY_SHARP_WIDTH_DP), y(position)))
            }
            val timeX = px(CLEF_AREA_DP + layout.keyWidth + TIME_SIGNATURE_AREA_DP / 2)
            centered(timeNumbers.first, Offset(timeX, y(base + TIME_TOP_ABOVE_BOTTOM)))
            centered(timeNumbers.second, Offset(timeX, y(base + TIME_BOTTOM_ABOVE_BOTTOM)))
        }
        if (staves.size == 2) drawLine(line, Offset(0f, y(top)), Offset(0f, y(bottom)), LINE_WIDTH.toPx())
        barLines(layout, y(top)..y(bottom), line)
    }

    fun drawRests() {
        engraved.rests.forEachIndexed { i, rest ->
            val base = rest.staff.bottom()
            val (text, position) =
                when (rest.value) {
                    NoteValue.WHOLE -> REST_WHOLE to base + WHOLE_REST_ABOVE_BOTTOM
                    NoteValue.HALF -> REST_HALF to base + MIDDLE_ABOVE_BOTTOM
                    NoteValue.QUARTER -> REST_QUARTER to base + MIDDLE_ABOVE_BOTTOM
                    else -> REST_EIGHTH to base + MIDDLE_ABOVE_BOTTOM
                }
            musicCentered(text, colors.staffLine, px(layout.restX[i]), y(position))
        }
    }

    /** A notehead with its ledger lines and accidental. */
    fun drawNote(
        note: MidiNote,
        x: Float,
        color: Color,
        hollow: Boolean,
        staff: Staff,
        accidental: Accidental?,
    ) = with(scope) {
        ledgerLines(note.staffPosition, x, ::y, colors.staffLine, staff)
        notehead(Offset(x, y(note.staffPosition)), color, hollow)
        when (accidental) {
            Accidental.SHARP -> SHARP
            Accidental.NATURAL -> NATURAL
            null -> null
        }?.let { musicCentered(it, color, x - ACCIDENTAL_GAP.toPx(), y(note.staffPosition)) }
    }

    /** The cursor left of the waiting step at [x], spanning every drawn staff. */
    fun drawCursor(
        x: Float,
        color: Color,
    ) = with(scope) {
        val spanTop = y(top) - CURSOR_OVERHANG.toPx()
        drawRect(
            color.copy(alpha = CURSOR_ALPHA),
            topLeft = Offset(x - CURSOR_GAP.toPx(), spanTop),
            size = Size(CURSOR_WIDTH.toPx(), y(bottom) + CURSOR_OVERHANG.toPx() - spanTop),
        )
    }

    /** Stems, flags and dots in each chord's colour, then the beams. */
    fun drawStemsAndBeams(
        steps: List<StepView>,
        liveMarks: Map<ScoreNoteRef, LiveMark>,
    ) = with(scope) {
        engraved.chords.forEachIndexed { i, chord ->
            val shape = layout.chords[i]
            val ref = ScoreNoteRef(chord.stepIndex, chord.notes.first().pitch)
            val color = liveMarks[ref].color(steps[chord.stepIndex].status, colors)
            shape.stem?.let { stem ->
                val x = px(stem.x)
                drawLine(color, Offset(x, y(stem.fromPosition)), Offset(x, y(stem.tipPosition)), px(STEM_WIDTH_DP))
                if (shape.flag) {
                    val origin = Offset(x - px(STEM_WIDTH_DP) / 2, y(stem.tipPosition))
                    music(if (stem.up) FLAG_EIGHTH_UP else FLAG_EIGHTH_DOWN, color, origin)
                }
            }
            shape.dotPositions.forEach { musicCentered(AUGMENTATION_DOT, color, px(shape.dotX), y(it)) }
        }
        layout.beams.forEach { beam ->
            val statuses = engraved.chords.filter { it.beamGroup == beam.group }.map { steps[it.stepIndex].status }
            val color = (statuses.firstOrNull { it != StepStatus.DONE } ?: StepStatus.DONE).color(colors)
            val thickness = px(BEAM_THICKNESS_DP)
            val half = px(STEM_WIDTH_DP) / 2
            val edge = y(beam.position)
            drawRect(
                color,
                topLeft = Offset(px(beam.x1) - half, if (beam.up) edge else edge - thickness),
                size = Size(px(beam.x2) - px(beam.x1) + 2 * half, thickness),
            )
        }
    }
}

/**
 * TalkBack's text for the staff: "Treble staff, 4/4. Notes C4, D4, E4. Waiting for D4." (or "… Done."); in a [timed]
 * run the cursor is read instead: "… Cursor on D4."
 */
@Composable
private fun staffDescription(
    steps: List<StepView>,
    time: TimeSignature,
    key: KeySignature,
    timed: Boolean,
): String {
    val signature = stringResource(Res.string.practice_time_signature, time.beats.toString(), time.beatUnit.toString())
    val timeText =
        if (key ==
            KeySignature.C
        ) {
            signature
        } else {
            stringResource(
                Res.string.practice_time_key,
                signature,
                stringArrayResource(Res.array.practice_major_keys)[
                    key.sharps +
                        MAX_ACCIDENTALS,
                ],
            )
        }
    val names = steps.map { it.notes.chordName() }.joinToString(stringResource(Res.string.practice_list_join))
    val waiting = steps.firstOrNull { it.status == StepStatus.WAITING }
    return if (waiting == null) {
        stringResource(Res.string.practice_staff_done, timeText, names)
    } else {
        val text = if (timed) Res.string.practice_staff_cursor else Res.string.practice_staff_waiting
        stringResource(text, timeText, names, waiting.notes.chordName())
    }
}

/** The inner bar lines and the final double bar of [layout], spanning [staff] (top to bottom y). */
private fun DrawScope.barLines(
    layout: StaffLayout,
    staff: ClosedFloatingPointRange<Float>,
    color: Color,
) {
    fun bar(
        x: Float,
        width: Dp,
    ) = drawLine(color, Offset(x, staff.start), Offset(x, staff.endInclusive), width.toPx())
    layout.barLineX.forEach { bar(it.dp.toPx(), LINE_WIDTH) }
    val finalX = layout.finalBarX.dp.toPx()
    bar(finalX, LINE_WIDTH)
    bar(finalX + (LINE_WIDTH / 2 + DOUBLE_BAR_SPACE + THICK_BAR_WIDTH / 2).toPx(), THICK_BAR_WIDTH)
}

/** The notes of a step, low to high, joined as a chord ("C4+E4+G4"; one note is its name). */
@Composable
internal fun Set<MidiNote>.chordName(): String =
    sortedBy { it.number }.joinToString(stringResource(Res.string.practice_chord_join)) { it.name }

/** Draws [text] centred on [center]. */
private fun DrawScope.centered(
    text: TextLayoutResult,
    center: Offset,
) = drawText(text, topLeft = Offset(center.x - text.size.width / 2f, center.y - text.size.height / 2f))

/** A notehead at [center]: filled, or a [HOLLOW_RING] ring of the same outer size when [hollow]. */
private fun DrawScope.notehead(
    center: Offset,
    color: Color,
    hollow: Boolean,
) {
    val (width, height) = NOTEHEAD
    val inset = if (hollow) HOLLOW_RING.toPx() / 2 else 0f
    rotate(NOTEHEAD_ANGLE, pivot = center) {
        drawOval(
            color,
            topLeft = Offset(center.x - width.toPx() / 2 + inset, center.y - height.toPx() / 2 + inset),
            size = Size(width.toPx() - 2 * inset, height.toPx() - 2 * inset),
            style = if (hollow) Stroke(HOLLOW_RING.toPx()) else Fill,
        )
    }
}

/** Ledger lines from [staff]'s lines out to [position] (every even position beyond its bottom or top line). */
private fun DrawScope.ledgerLines(
    position: Int,
    x: Float,
    y: (Int) -> Float,
    color: Color,
    staff: Staff,
) {
    val bottom = if (staff == Staff.TREBLE) TREBLE_BOTTOM else BASS_BOTTOM
    val top = if (staff == Staff.TREBLE) TREBLE_TOP else BASS_TOP
    val ledgers =
        when {
            position < bottom -> (bottom - 2 downTo position step 2)
            position > top -> (top + 2..position step 2)
            else -> IntProgression.fromClosedRange(0, -1, 1)
        }
    val half = LEDGER_HALF_WIDTH.toPx()
    ledgers.forEach { line -> drawLine(color, Offset(x - half, y(line)), Offset(x + half, y(line)), LINE_WIDTH.toPx()) }
}

@Composable
private fun Legend(
    colors: PianoAnchorColors,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendEntry(colors.correct, stringResource(Res.string.practice_legend_correct))
        LegendEntry(colors.waiting, stringResource(Res.string.practice_legend_waiting))
    }
}

@Composable
private fun LegendEntry(
    color: Color,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.offset(x = 4.dp),
        )
    }
}
