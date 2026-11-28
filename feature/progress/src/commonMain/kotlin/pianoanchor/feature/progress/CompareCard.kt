package pianoanchor.feature.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import pianoanchor.engine.progress.LaneDot
import pianoanchor.feature.progress.resources.Res
import pianoanchor.feature.progress.resources.progress_compare_title
import pianoanchor.feature.progress.resources.progress_date
import pianoanchor.feature.progress.resources.progress_lane_description
import pianoanchor.feature.progress.resources.progress_months
import pianoanchor.feature.progress.resources.progress_play
import pianoanchor.feature.progress.resources.progress_play_description
import pianoanchor.feature.progress.resources.progress_stop
import pianoanchor.feature.progress.resources.progress_stop_description
import pianoanchor.feature.progress.resources.progress_take_day1
import pianoanchor.feature.progress.resources.progress_take_today
import pianoanchor.ui.shell.pianoAnchorColors

private val LaneHeight = 20.dp
private val DotRadius = 5.dp

/**
 * "Day 1 vs today": the piece's title, then a row per take with its date, a play (or stop) button and the note-timing
 * lane. The button of the take that sounds ([playing]) stops it; the other one starts its own, which stops the first.
 */
@Composable
internal fun CompareCard(
    compare: Compare,
    playing: Take?,
    actions: ProgressActions,
) {
    OutlinedCard(shape = CardShape, border = cardBorder(), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.progress_compare_title), style = MaterialTheme.typography.titleMedium)
            Text(compare.passage.title, style = MaterialTheme.typography.bodyLarge)
            TakeRow(
                Take.DAY_1,
                stringResource(Res.string.progress_take_day1),
                compare.day1Date,
                compare.day1Lane,
                playing,
                actions,
            )
            TakeRow(
                Take.TODAY,
                stringResource(Res.string.progress_take_today),
                compare.todayDate,
                compare.todayLane,
                playing,
                actions,
            )
        }
    }
}

@Composable
private fun TakeRow(
    take: Take,
    name: String,
    date: LocalDate,
    lane: List<LaneDot>,
    playing: Take?,
    actions: ProgressActions,
) {
    val dateText = formatDate(date)
    val sounding = playing == take
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, style = MaterialTheme.typography.labelLarge)
            Text(
                dateText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val description =
                if (sounding) {
                    stringResource(Res.string.progress_stop_description, name, dateText)
                } else {
                    stringResource(Res.string.progress_play_description, name, dateText)
                }
            TextButton(
                onClick = { if (sounding) actions.onStop() else actions.onPlay(take) },
                modifier =
                    Modifier
                        .heightIn(
                            min = TouchTarget,
                        ).clearAndSetSemantics { contentDescription = description },
            ) {
                Text(stringResource(if (sounding) Res.string.progress_stop else Res.string.progress_play))
            }
        }
        Lane(name, lane)
    }
}

@Composable
private fun formatDate(date: LocalDate): String =
    stringResource(
        Res.string.progress_date,
        date.day,
        stringArrayResource(Res.array.progress_months)[date.month.ordinal],
    )

/** A dot per played note along the take's length: blue on the beat, timing orange off it. */
@Composable
private fun Lane(
    name: String,
    dots: List<LaneDot>,
) {
    val colors = MaterialTheme.pianoAnchorColors
    val description =
        stringResource(Res.string.progress_lane_description, name, dots.size, dots.count { !it.onBeat })
    val track = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(LaneHeight)
                .clearAndSetSemantics { contentDescription = description },
    ) {
        val radius = DotRadius.toPx()
        val span = (dots.maxOfOrNull { it.offsetMs } ?: 0L).coerceAtLeast(1L)
        val width = size.width - 2 * radius
        val y = size.height / 2
        drawLine(track, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        dots.forEach { dot ->
            val x = radius + width * dot.offsetMs / span
            drawCircle(if (dot.onBeat) colors.correct else colors.timing, radius, Offset(x, y))
        }
    }
}
