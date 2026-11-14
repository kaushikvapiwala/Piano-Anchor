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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Passage
import pianoanchor.core.model.ScoreNoteRef
import pianoanchor.core.model.TimeSignature
import pianoanchor.feature.practice.resources.Res
import pianoanchor.feature.practice.resources.practice_chord_join
import pianoanchor.feature.practice.resources.practice_legend_correct
import pianoanchor.feature.practice.resources.practice_legend_waiting
import pianoanchor.feature.practice.resources.practice_list_join
import pianoanchor.feature.practice.resources.practice_staff_cursor
import pianoanchor.feature.practice.resources.practice_staff_done
import pianoanchor.feature.practice.resources.practice_staff_waiting
import pianoanchor.feature.practice.resources.practice_time_signature
import pianoanchor.ui.shell.PianoAnchorColors
import pianoanchor.ui.shell.displayFontFamily
import pianoanchor.ui.shell.pianoAnchorColors
import kotlin.math.abs

private const val STAFF_LINES = 5
private val LINE_GAP = 16.dp
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
private val SHARP_GAP = 22.dp
private const val TREBLE_CLEF = "𝄞"
private const val SHARP = "♯"
private const val TAG_SP = 11
private val TAG_GAP = 20.dp

/** Staff positions of the staff's bottom (E4) and top (F5) lines; ledger lines start one line beyond them. */
private const val LOWEST_LINE = 0
private const val HIGHEST_LINE = 8

/** Staff positions the time signature's numbers centre on: the upper and lower halves of the staff. */
private const val TIME_TOP_POSITION = 6
private const val TIME_BOTTOM_POSITION = 2

/**
 * The treble staff of [passage]: its time signature, one notehead (or stacked chord) per step spaced by onset
 * ([staffLayout]) and coloured by the matching [steps] entry's [StepStatus], hollow from a half note up, bar lines and
 * a final double bar, a cursor left of the waiting step and a legend. TalkBack reads it as one description: the time
 * signature, the notes and the one being waited for.
 */
@Composable
internal fun StaffView(
    passage: Passage,
    steps: List<StepView>,
    modifier: Modifier = Modifier,
    liveMarks: Map<ScoreNoteRef, LiveMark> = emptyMap(),
    timed: Boolean = false,
) {
    val colors = MaterialTheme.pianoAnchorColors
    val primary = MaterialTheme.colorScheme.primary
    val measurer = rememberTextMeasurer()
    val clef =
        remember(measurer, colors.staffLine) {
            measurer.measure(TREBLE_CLEF, TextStyle(color = colors.staffLine, fontSize = 64.sp))
        }
    val sharpStyle = TextStyle(fontSize = 20.sp)
    val time = passage.timeSignature
    val display = displayFontFamily
    val (timeTop, timeBottom) =
        remember(measurer, colors.upcomingNote, display, time) {
            val style = TextStyle(colors.upcomingNote, 26.sp, FontWeight.Bold, fontFamily = display)
            measurer.measure(time.beats.toString(), style) to measurer.measure(time.beatUnit.toString(), style)
        }
    val description = staffDescription(steps, time, timed)
    val tags = timingTags(liveMarks)
    val tagStyle = TextStyle(colors.timing, TAG_SP.sp, FontWeight.SemiBold)
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = description }) {
            val gap = LINE_GAP.toPx()
            val bottomLine = size.height / 2 + gap * 2

            fun y(position: Int) = bottomLine - position * gap / 2
            repeat(STAFF_LINES) { line ->
                drawLine(colors.staffLine, Offset(0f, y(line * 2)), Offset(size.width, y(line * 2)), LINE_WIDTH.toPx())
            }
            drawText(clef, topLeft = Offset(4.dp.toPx(), y(2) - clef.size.height / 2f - gap / 2))
            val timeCenter = (CLEF_AREA_DP + TIME_SIGNATURE_AREA_DP / 2).dp.toPx()
            centered(timeTop, Offset(timeCenter, y(TIME_TOP_POSITION)))
            centered(timeBottom, Offset(timeCenter, y(TIME_BOTTOM_POSITION)))
            val layout = staffLayout(passage, size.width.toDp().value)
            barLines(layout, y(HIGHEST_LINE)..y(LOWEST_LINE), colors.staffLine)
            steps.forEachIndexed { i, step ->
                val x = layout.noteX[i].dp.toPx()
                step.notes.forEach { note ->
                    val ref = ScoreNoteRef(i, note)
                    val mark = liveMarks[ref]
                    val color = mark.color(step.status, colors)
                    ledgerLines(note.staffPosition, x, ::y, colors.staffLine)
                    val head = Offset(x, y(note.staffPosition))
                    notehead(head, color, note in layout.hollow[i] || mark is LiveMark.TimingOff)
                    tags[ref]?.let { centered(measurer.measure(it, tagStyle), head - Offset(0f, TAG_GAP.toPx())) }
                    if (note.isBlack) {
                        val sharp = measurer.measure(SHARP, sharpStyle.copy(color = color))
                        centered(sharp, Offset(x - SHARP_GAP.toPx(), y(note.staffPosition)))
                    }
                }
                if (step.status == StepStatus.WAITING) {
                    drawRect(
                        primary.copy(alpha = CURSOR_ALPHA),
                        topLeft = Offset(x - CURSOR_GAP.toPx(), size.height / 2 - CURSOR_HEIGHT.toPx() / 2),
                        size = Size(CURSOR_WIDTH.toPx(), CURSOR_HEIGHT.toPx()),
                    )
                }
            }
        }
        Legend(colors, Modifier.align(Alignment.TopEnd))
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
    timed: Boolean,
): String {
    val timeText = stringResource(Res.string.practice_time_signature, time.beats.toString(), time.beatUnit.toString())
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

/** Ledger lines from the staff out to [position] (every even position below the bottom or above the top line). */
private fun DrawScope.ledgerLines(
    position: Int,
    x: Float,
    y: (Int) -> Float,
    color: Color,
) {
    val ledgers =
        when {
            position < LOWEST_LINE -> (LOWEST_LINE - 2 downTo position step 2)
            position > HIGHEST_LINE -> (HIGHEST_LINE + 2..position step 2)
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
