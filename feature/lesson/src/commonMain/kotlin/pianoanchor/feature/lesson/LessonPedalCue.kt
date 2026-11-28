package pianoanchor.feature.lesson

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import pianoanchor.core.model.LessonStep
import pianoanchor.core.model.TryCheck
import pianoanchor.feature.lesson.resources.Res
import pianoanchor.feature.lesson.resources.lesson_pedal_down
import pianoanchor.feature.lesson.resources.lesson_pedal_up
import pianoanchor.ui.shell.drawPedalLine
import pianoanchor.ui.shell.pianoAnchorColors

private val PEDAL_CUE_HEIGHT = 24.dp
private val PEDAL_CUE_MARGIN = 8.dp

/** True when the See step shows the pedal cue: the demo of a pedal check. */
internal fun showsPedalCue(state: LessonState): Boolean =
    state.step == LessonStep.SEE &&
        (state.check is TryCheck.PedalHold || state.check is TryCheck.PedalChange)

/**
 * The demo's pedal under the key strip, drawn as the staff draws it ([drawPedalLine]): a line with a tick at its start
 * while the pedal is down, and one at its end once it is up. TalkBack hears "Pedal down" or "Pedal up" as it moves.
 * [down] is null before the first cue, when the line is not drawn.
 */
@Composable
internal fun PedalCueLine(
    down: Boolean?,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.pianoAnchorColors.staffLine
    val description =
        when (down) {
            true -> stringResource(Res.string.lesson_pedal_down)
            false -> stringResource(Res.string.lesson_pedal_up)
            null -> ""
        }
    Canvas(
        modifier.fillMaxWidth().height(PEDAL_CUE_HEIGHT).semantics {
            contentDescription = description
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        if (down != null) {
            val margin = PEDAL_CUE_MARGIN.toPx()
            drawPedalLine(color, size.height - margin, margin, size.width - margin, startTick = down, endTick = !down)
        }
    }
}
