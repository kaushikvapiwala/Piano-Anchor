package pianoanchor.feature.progress

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import pianoanchor.engine.Tunables
import pianoanchor.engine.session.WeekStatus
import pianoanchor.feature.progress.resources.Res
import pianoanchor.feature.progress.resources.progress_bar_minutes
import pianoanchor.feature.progress.resources.progress_bar_minutes_today
import pianoanchor.feature.progress.resources.progress_bar_none
import pianoanchor.feature.progress.resources.progress_bar_none_today
import pianoanchor.feature.progress.resources.progress_dash
import pianoanchor.feature.progress.resources.progress_day_name
import pianoanchor.feature.progress.resources.progress_day_short
import pianoanchor.feature.progress.resources.progress_minutes_title
import pianoanchor.feature.progress.resources.progress_week_goal
import pianoanchor.feature.progress.resources.progress_week_paused
import pianoanchor.feature.progress.resources.progress_week_pauses_left
import pianoanchor.feature.progress.resources.progress_week_streak
import pianoanchor.feature.progress.resources.progress_week_title

internal val CardShape = RoundedCornerShape(20.dp)
private val DotSize = 12.dp
private val BarWidth = 28.dp
private val BarAreaHeight = 96.dp
private val BarMinHeight = 4.dp

@Composable
internal fun cardBorder() = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

/** The week card: dots for the days played against the goal, the streak in weeks and the pause status. Read-only. */
@Composable
internal fun WeekCard(week: WeekStatus) {
    OutlinedCard(shape = CardShape, border = cardBorder(), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(Res.string.progress_week_title, week.daysPlayed, week.goalDays),
                style = MaterialTheme.typography.titleMedium,
            )
            Dots(
                filled = week.daysPlayed,
                total = maxOf(week.goalDays, week.daysPlayed),
                description = stringResource(Res.string.progress_week_goal, week.daysPlayed, week.goalDays),
            )
            Text(
                stringResource(Res.string.progress_week_streak, week.streakWeeks),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (week.paused) {
                Text(stringResource(Res.string.progress_week_paused), style = MaterialTheme.typography.bodyMedium)
            }
            Dots(
                filled = week.pausesLeft,
                total = Tunables.WEEK_PAUSES_MAX,
                description =
                    stringResource(Res.string.progress_week_pauses_left, week.pausesLeft, Tunables.WEEK_PAUSES_MAX),
            )
        }
    }
}

/** [total] dots, the first [filled] solid; read aloud as [description]. */
@Composable
private fun Dots(
    filled: Int,
    total: Int,
    description: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        repeat(total) { index ->
            val color =
                if (index < filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            Box(modifier = Modifier.size(DotSize).background(color, CircleShape))
        }
    }
}

/**
 * Seven plum bars, Monday to Sunday, as tall as the day's [minutes] against the longest day, each with its day label
 * below; today's bar ([todayIndex]) is outlined and a day without practice shows a dash. TalkBack reads each column
 * as "Monday, 12 minutes".
 */
@Composable
internal fun MinutesBars(
    minutes: List<Int>,
    todayIndex: Int,
) {
    val shortNames = stringArrayResource(Res.array.progress_day_short)
    val names = stringArrayResource(Res.array.progress_day_name)
    val longest = minutes.max().coerceAtLeast(1)
    OutlinedCard(shape = CardShape, border = cardBorder(), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.progress_minutes_title), style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                minutes.forEachIndexed { index, value ->
                    val today = index == todayIndex
                    val description =
                        when {
                            value == 0 && today -> stringResource(Res.string.progress_bar_none_today, names[index])
                            value == 0 -> stringResource(Res.string.progress_bar_none, names[index])
                            today -> stringResource(Res.string.progress_bar_minutes_today, names[index], value)
                            else -> stringResource(Res.string.progress_bar_minutes, names[index], value)
                        }
                    Bar(shortNames[index], value, longest, today, description)
                }
            }
        }
    }
}

@Composable
private fun Bar(
    label: String,
    value: Int,
    longest: Int,
    today: Boolean,
    description: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        Box(modifier = Modifier.height(BarAreaHeight), contentAlignment = Alignment.BottomCenter) {
            if (value == 0) {
                Text(stringResource(Res.string.progress_dash), style = MaterialTheme.typography.bodyMedium)
            } else {
                val height = maxOf(BarMinHeight, BarAreaHeight * value / longest)
                val outline =
                    if (today) {
                        Modifier.border(
                            2.dp,
                            MaterialTheme.colorScheme.onSurface,
                            BarShape,
                        )
                    } else {
                        Modifier
                    }
                Box(
                    modifier =
                        Modifier
                            .size(BarWidth, height)
                            .background(MaterialTheme.colorScheme.primary, BarShape)
                            .then(outline),
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            color = if (today) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val BarShape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
