package pianoanchor.feature.lesson

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import pianoanchor.core.model.CardScene
import pianoanchor.core.model.SceneAction
import pianoanchor.core.model.SceneCue
import pianoanchor.core.model.SceneStaff
import kotlin.math.abs

private val STROKE = 2.dp
private const val SEMITONES = 12
private const val LETTERS = 7
private const val STAFF_TOP_SHARE = 0.14f
private const val STAFF_HEIGHT_SHARE = 0.42f
private const val STAFF_MARGIN_SHARE = 0.08f
private const val NOTE_AREA_START = 0.2f
private const val NOTE_AREA_WIDTH = 0.6f
private const val NOTEHEAD_WIDTH_UNITS = 3f
private const val NOTEHEAD_HEIGHT_UNITS = 2.2f
private const val NOTEHEAD_ANGLE = -18f
private const val LEDGER_HALF_UNITS = 2.6f
private const val HOLLOW_FROM_BEATS = 2
private const val MIDI_OCTAVE_OF_E4 = 5
private const val LETTER_OF_E4 = 2
private val LETTER_OF_PITCH_CLASS = listOf(0, 0, 1, 1, 2, 3, 3, 4, 4, 5, 5, 6)

/** Half spaces above the treble staff's bottom line E4 (middle C = -2, the bass's top line = -4). */
internal fun staffPosition(note: Int): Int =
    note / SEMITONES * LETTERS + LETTER_OF_PITCH_CLASS[note % SEMITONES] - (MIDI_OCTAVE_OF_E4 * LETTERS + LETTER_OF_E4)

internal const val TREBLE_LOW = -4
internal const val TREBLE_HIGH = 12
private const val BASS_LOW = -16
private const val BASS_HIGH = 0
internal val TREBLE_LINES = listOf(0, 2, 4, 6, 8)
private val BASS_LINES = listOf(-12, -10, -8, -6, -4)

private fun lines(staff: SceneStaff): List<Int> =
    when (staff) {
        SceneStaff.TREBLE -> TREBLE_LINES
        SceneStaff.BASS -> BASS_LINES
        SceneStaff.GRAND -> TREBLE_LINES + BASS_LINES
    }

/** The positions the staff's area spans, with room for ledger lines. */
private fun span(staff: SceneStaff): IntRange =
    when (staff) {
        SceneStaff.TREBLE -> TREBLE_LOW..TREBLE_HIGH
        SceneStaff.BASS -> BASS_LOW..BASS_HIGH
        SceneStaff.GRAND -> BASS_LOW..TREBLE_HIGH
    }

private class StaffGeometry(
    val stage: Stage,
    val staff: SceneStaff,
) {
    private val area = span(staff)
    val unit = stage.size.height * STAFF_HEIGHT_SHARE / (area.last - area.first)
    private val top = stage.size.height * STAFF_TOP_SHARE

    fun y(position: Int): Float = top + (area.last - position) * unit

    val left = stage.size.width * STAFF_MARGIN_SHARE
    val right = stage.size.width * (1 - STAFF_MARGIN_SHARE)
}

/** The staff's lines in the practice staff's colour and the scene's one stroke weight. */
internal fun DrawScope.drawStaff(
    staff: SceneStaff,
    stage: Stage,
    color: Color,
) {
    val geometry = StaffGeometry(stage, staff)
    lines(staff).forEach {
        drawLine(color, Offset(geometry.left, geometry.y(it)), Offset(geometry.right, geometry.y(it)), STROKE.toPx())
    }
}

/** A note on the staff: an accent notehead (hollow from a half note), with ledger lines, spread along the staff. */
internal fun DrawScope.drawStaffNote(
    cue: SceneCue,
    scene: CardScene,
    stage: Stage,
    alpha: Float,
    palette: ScenePalette,
) {
    val staff = checkNotNull(scene.stage.staff)
    val geometry = StaffGeometry(stage, staff)
    val action = cue.action as SceneAction.StaffNote
    val slot = staffSlot(scene, cue)
    val x = stage.size.width * (NOTE_AREA_START + NOTE_AREA_WIDTH * slot)
    val position = staffPosition(action.note)
    val width = geometry.unit * NOTEHEAD_WIDTH_UNITS
    val height = geometry.unit * NOTEHEAD_HEIGHT_UNITS
    ledgerLines(position, lines(staff)).forEach {
        val half = geometry.unit * LEDGER_HALF_UNITS
        drawLine(
            palette.colors.staffLine.copy(alpha = alpha),
            Offset(x - half, geometry.y(it)),
            Offset(x + half, geometry.y(it)),
            STROKE.toPx(),
        )
    }
    val topLeft = Offset(x - width / 2, geometry.y(position) - height / 2)
    rotate(NOTEHEAD_ANGLE, Offset(x, geometry.y(position))) {
        val color = palette.accent.copy(alpha = alpha)
        if (action.beats >= HOLLOW_FROM_BEATS) {
            drawOval(color, topLeft, Size(width, height), style = Stroke(STROKE.toPx()))
        } else {
            drawOval(color, topLeft, Size(width, height))
        }
    }
}

/** The even positions of the ledger lines a note at [position] needs to reach the nearest of the staff [lines]. */
internal fun ledgerLines(
    position: Int,
    lines: List<Int>,
): List<Int> {
    val nearest = lines.minBy { abs(it - position) }
    val toward = if (nearest > position) 1 else -1
    val ledgers = mutableListOf<Int>()
    var q = if (position % 2 == 0) position else position + toward
    while (q !in lines && (q - nearest) * toward < 0) {
        ledgers += q
        q += 2 * toward
    }
    return ledgers
}
