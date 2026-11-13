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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import pianoanchor.core.model.MidiNote
import pianoanchor.feature.practice.resources.Res
import pianoanchor.feature.practice.resources.practice_chord_join
import pianoanchor.feature.practice.resources.practice_legend_correct
import pianoanchor.feature.practice.resources.practice_legend_waiting
import pianoanchor.feature.practice.resources.practice_list_join
import pianoanchor.feature.practice.resources.practice_staff_done
import pianoanchor.feature.practice.resources.practice_staff_waiting
import pianoanchor.ui.shell.PianoAnchorColors
import pianoanchor.ui.shell.pianoAnchorColors

private const val STAFF_LINES = 5
private val LINE_GAP = 16.dp
private val LINE_WIDTH = 1.5.dp
private val CLEF_AREA = 64.dp
private val END_MARGIN = 24.dp
private val NOTEHEAD = 19.dp to 14.dp
private const val NOTEHEAD_ANGLE = -18f
private val LEDGER_HALF_WIDTH = 15.dp
private val CURSOR_WIDTH = 3.dp
private val CURSOR_HEIGHT = 110.dp
private val CURSOR_GAP = 22.dp
private const val CURSOR_ALPHA = 0.85f
private val SHARP_GAP = 22.dp
private const val TREBLE_CLEF = "𝄞"
private const val SHARP = "♯"

/** Staff positions of the staff's bottom (E4) and top (F5) lines; ledger lines start one line beyond them. */
private const val LOWEST_LINE = 0
private const val HIGHEST_LINE = 8

/**
 * The treble staff with one notehead (or stacked chord) per step, evenly spaced and coloured by [StepStatus], a
 * cursor left of the waiting step and a legend. No time signature or bar lines yet. TalkBack reads it as one
 * description: the notes and the one being waited for.
 */
@Composable
internal fun StaffView(
    steps: List<StepView>,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.pianoAnchorColors
    val primary = MaterialTheme.colorScheme.primary
    val measurer = rememberTextMeasurer()
    val clef =
        remember(measurer, colors.staffLine) {
            measurer.measure(TREBLE_CLEF, TextStyle(color = colors.staffLine, fontSize = 64.sp))
        }
    val sharpStyle = TextStyle(fontSize = 20.sp)
    val names = steps.map { it.notes.chordName() }.joinToString(stringResource(Res.string.practice_list_join))
    val waiting = steps.firstOrNull { it.status == StepStatus.WAITING }
    val description =
        if (waiting == null) {
            stringResource(Res.string.practice_staff_done, names)
        } else {
            stringResource(Res.string.practice_staff_waiting, names, waiting.notes.chordName())
        }
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = description }) {
            val gap = LINE_GAP.toPx()
            val bottomLine = size.height / 2 + gap * 2

            fun y(position: Int) = bottomLine - position * gap / 2
            repeat(STAFF_LINES) { line ->
                drawLine(colors.staffLine, Offset(0f, y(line * 2)), Offset(size.width, y(line * 2)), LINE_WIDTH.toPx())
            }
            drawText(clef, topLeft = Offset(4.dp.toPx(), y(2) - clef.size.height / 2f - gap / 2))
            val start = CLEF_AREA.toPx()
            val slot = (size.width - start - END_MARGIN.toPx()) / steps.size
            steps.forEachIndexed { i, step ->
                val x = start + slot * (i + 0.5f)
                val color = step.status.color(colors)
                step.notes.forEach { note ->
                    ledgerLines(note.staffPosition, x, ::y, colors.staffLine)
                    notehead(Offset(x, y(note.staffPosition)), color)
                    if (note.isBlack) {
                        val sharp = measurer.measure(SHARP, sharpStyle.copy(color = color))
                        drawText(
                            sharp,
                            topLeft =
                                Offset(
                                    x - SHARP_GAP.toPx() - sharp.size.width / 2f,
                                    y(note.staffPosition) - sharp.size.height / 2f,
                                ),
                        )
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

/** The notes of a step, low to high, joined as a chord ("C4+E4+G4"; one note is its name). */
@Composable
internal fun Set<MidiNote>.chordName(): String =
    sortedBy { it.number }.joinToString(stringResource(Res.string.practice_chord_join)) { it.name }

private fun StepStatus.color(colors: PianoAnchorColors): Color =
    when (this) {
        StepStatus.DONE -> colors.correct
        StepStatus.WAITING -> colors.waiting
        StepStatus.UPCOMING -> colors.upcomingNote
    }

private fun DrawScope.notehead(
    center: Offset,
    color: Color,
) {
    val (width, height) = NOTEHEAD
    rotate(NOTEHEAD_ANGLE, pivot = center) {
        drawOval(
            color,
            topLeft = Offset(center.x - width.toPx() / 2, center.y - height.toPx() / 2),
            size = Size(width.toPx(), height.toPx()),
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
