package pianoanchor.feature.practice

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import pianoanchor.core.model.ScoreNoteRef
import pianoanchor.feature.practice.resources.Res
import pianoanchor.feature.practice.resources.practice_timing_early_tag
import pianoanchor.feature.practice.resources.practice_timing_late_tag
import pianoanchor.ui.shell.PianoAnchorColors
import kotlin.math.abs

/** The tags of timing-off notes: "+92 ms" (late) or "−40 ms" (early). */
@Composable
internal fun timingTags(marks: Map<ScoreNoteRef, LiveMark>): Map<ScoreNoteRef, String> {
    val late = Res.string.practice_timing_late_tag
    val early = Res.string.practice_timing_early_tag
    return marks.entries
        .mapNotNull { (ref, mark) -> (mark as? LiveMark.TimingOff)?.let { ref to it.deviationMs } }
        .associate { (ref, ms) -> ref to stringResource(if (ms > 0) late else early, abs(ms).toString()) }
}

internal fun LiveMark?.color(
    status: StepStatus,
    colors: PianoAnchorColors,
): Color =
    when (this) {
        LiveMark.Correct -> colors.correct
        is LiveMark.TimingOff -> colors.timing
        null -> status.color(colors)
    }

internal fun StepStatus.color(colors: PianoAnchorColors): Color =
    when (this) {
        StepStatus.DONE -> colors.correct
        StepStatus.WAITING -> colors.waiting
        StepStatus.UPCOMING -> colors.upcomingNote
    }
